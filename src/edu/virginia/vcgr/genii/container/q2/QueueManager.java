package edu.virginia.vcgr.genii.container.q2;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ggf.jsdl.JobDefinition_Type;
import org.ggf.rns.EntryType;
import org.morgan.util.configuration.ConfigurationException;
import org.morgan.util.io.StreamUtils;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.security.GenesisIISecurityException;
import edu.virginia.vcgr.genii.container.db.DatabaseConnectionPool;
import edu.virginia.vcgr.genii.queue.JobInformationType;
import edu.virginia.vcgr.genii.queue.ReducedJobInformationType;

/**
 * This class is called directly from the queue service.  Mostly, it acts
 * as a conduit between the service impl, and the three managers underneath
 * (the BESManager, JobManager, and Scheduler).  The one wrinkle is that it
 * also implements a factory pattern to automatically load a QueueManager for
 * every queue registered in the system.  It also maintains threaded workers
 * such that there are only the minimum necessary per queue. 
 * 
 * @author mmm2a
 */
public class QueueManager implements Closeable
{
	/**
	 * To optimize hash table size, we assume a small
	 * number of managers to allocate space for.  If we
	 * have more, the table will grow appropriately, but
	 * there's no reason to waste memory.
	 */
	static private final int _DEFAULT_MANAGER_COUNT = 4;
	
	/**
	 * The maximum number of simultaneous outcalls the queue
	 * will allow.  This is reflected as threads in a
	 * thread pool.  This number of threads is shared by ALL
	 * queue instances on a server.
	 */
	static private final int _MAX_SIMULTANEOUS_OUTCALLS = 8;
	
	@SuppressWarnings("unused")
	static private Log _logger = LogFactory.getLog(QueueManager.class);
	
	/**
	 * The database connection pool from whence to acquire
	 * temporary connections to the database.
	 */
	static private DatabaseConnectionPool _connectionPool = null;
	
	/**
	 * The outcall thread pool manager.
	 */
	static private ThreadPool _outcallThreadPool = null;
	
	/**
	 * A map of queue key to queue manager for all instances running
	 * on this container.
	 */
	static private HashMap<String, QueueManager> _queueManager =
		new HashMap<String, QueueManager>(_DEFAULT_MANAGER_COUNT);
	
	/**
	 * Create and start (active threads) all queue managers registered
	 * in the database right now.
	 * 
	 * @param connectionPool The connection pool to store as our 
	 * _connectionPool
	 * 
	 * @throws SQLException
	 * @throws ResourceException
	 */
	static public void startAllManagers(DatabaseConnectionPool connectionPool)
		throws SQLException, ResourceException
	{
		Connection connection = null;
		Statement stmt = null;
		ResultSet rs = null;
		
		synchronized(QueueManager.class)
		{
			if (_connectionPool != null)
				throw new IllegalArgumentException("Queue managers already started.");
			
			_connectionPool = connectionPool;
		}
		
		try
		{
			/* Acquire a new connection to access the database with. */
			connection = _connectionPool.acquire();
			
			/* We look through the resources table to find all queueid's
			 * indicated.  We could equally have used the jobs table, but
			 * there will generally be less entries in the resources
			 * table.  This means that a queue which has no resources
			 * won't get started by default, but then again, it has no
			 * need to get started.  It also means that if someone creates
			 * a brand new system (bootstraps) but doesn't clean up the DB
			 * from the old one, we may have unnecessary queues running, but
			 * that is a very unlikely case.
			 */
			stmt = connection.createStatement();
			rs = stmt.executeQuery("SELECT queueid FROM q2resources");
			
			while (rs.next())
			{
				/* Simplly accessing the manager in question causes it to
				 * get created and started if it isn't already.
				 */
				getManager(rs.getString(1));
			}
		}
		finally
		{
			StreamUtils.close(rs);
			StreamUtils.close(stmt);
			_connectionPool.release(connection);
		}
	}
	
	/**
	 * Get the queue manager for a given queue key (create it if it 
	 * doesn't already exist).
	 * 
	 * @param queueid THe queue key to acquire a manager for.
	 * 
	 * @return The queue manager with the given key.
	 * 
	 * @throws SQLException
	 * @throws ResourceException
	 */
	static public QueueManager getManager(String queueid) 
		throws SQLException, ResourceException
	{
		QueueManager mgr;
		
		synchronized(_queueManager)
		{
			mgr = _queueManager.get(queueid);
			if (mgr == null)
			{
				synchronized(QueueManager.class)
				{
					if (_outcallThreadPool == null)
						_outcallThreadPool = new ThreadPool(_MAX_SIMULTANEOUS_OUTCALLS);
				}
				
				_queueManager.put(queueid, mgr = new QueueManager(queueid));
			}
		}
		
		return mgr;
	}
	
	/**
	 * Destroy an active queue manager.  Destroy is a misnomer here -- actually
	 * it simply stops the threads and shuts it down.  If there is still data
	 * in the database, the manager may get loaded again the next time the
	 * container restarts.  This is mostly for shutting down a queue.
	 * 
	 * @param queueid
	 */
	static public void destroyManager(String queueid)
	{
		boolean empty;
		QueueManager mgr = null;
		
		synchronized(_queueManager)
		{
			mgr = _queueManager.remove(queueid);
			empty = _queueManager.isEmpty();
		}
		
		if (mgr != null)
			StreamUtils.close(mgr);
		
		synchronized(QueueManager.class)
		{
			if (empty && _outcallThreadPool != null)
			{
				StreamUtils.close(_outcallThreadPool);
				_outcallThreadPool = null;
			}
		}
	}
	
	volatile private boolean _closed = false;
	private String _queueid;
	
	private BESManager _besManager;
	private JobManager _jobManager;
	private Scheduler _scheduler;
	private QueueDatabase _database;
	private SchedulingEvent _schedulingEvent;
	
	
	/**
	 * Private constructor used to create a new active queue manager.  This
	 * instance will start up all of the sub-managers like the BESManager, 
	 * JobManager, and Scheduler.
	 * 
	 * @param queueid The ID for the queue.
	 * 
	 * @throws SQLException
	 * @throws ResourceException
	 */
	private QueueManager(String queueid) 
		throws SQLException, ResourceException
	{
		Connection connection = null;
		_queueid = queueid;
		_database = new QueueDatabase(_queueid);
		_schedulingEvent = new SchedulingEvent();
		
		try
		{
			connection = _connectionPool.acquire();
			_besManager = new BESManager(_outcallThreadPool,
				_database, _schedulingEvent, 
				connection, _connectionPool);
			_jobManager = new JobManager(_outcallThreadPool,
				_database, _schedulingEvent, connection, _connectionPool);
			_scheduler = new Scheduler(_schedulingEvent, _connectionPool,
				_jobManager, _besManager);
		}
		catch (GenesisIISecurityException gse)
		{
			throw new ResourceException("UInable to create BES Manager.", gse);
		}
		catch (ConfigurationException ce)
		{
			throw new ResourceException("Unable to create BES Manager.", ce);
		}
		finally
		{
			_connectionPool.release(connection);
		}
	}
	
	protected void finalize() throws Throwable
	{
		close();
	}
	
	synchronized public void close() throws IOException
	{
		if (_closed)
			return;
		
		_closed = true;
		StreamUtils.close(_scheduler);
		StreamUtils.close(_besManager);
		StreamUtils.close(_jobManager);
	}
	
	/************************************************************************/
	/* The remainder of the methods in this class correspond exactly to     */
	/* methods in the various managers (BESManager and JobManager) with the */
	/* exception that they all take a database connection and these don't.  */
	/* These methods acquire the connection and release it and then call    */
	/* through to the back-end methods.                                      */
	/************************************************************************/
	
	public void addNewBES(String name, EndpointReferenceType epr)
		throws SQLException, ResourceException, ConfigurationException,
			GenesisIISecurityException
	{
		Connection connection = null;
		
		try
		{
			connection = _connectionPool.acquire();
			_besManager.addNewBES(connection, name, epr);
		}
		finally
		{
			_connectionPool.release(connection);
		}
	}
	
	public void configureBES(String name, 
		int newSlots) throws SQLException, ResourceException
	{
		Connection connection = null;
		
		try
		{
			connection = _connectionPool.acquire();
			_besManager.configureBES(connection, name, newSlots);
		}
		finally
		{
			_connectionPool.release(connection);
		}
	}
	
	public Collection<EntryType> listBESs(Pattern pattern)
		throws SQLException, ResourceException
	{
		Connection connection = null;
		
		try
		{
			connection = _connectionPool.acquire();
			return _besManager.listBESs(connection, pattern);
		}
		finally
		{
			_connectionPool.release(connection);
		}
	}
	
	public Collection<String> removeBESs(Pattern pattern) throws SQLException
	{
		Connection connection = null;
		
		try
		{
			connection = _connectionPool.acquire();
			return _besManager.removeBESs(connection, pattern);
		}
		finally
		{
			_connectionPool.release(connection);
		}
	}
	
	public String submitJob(short priority, JobDefinition_Type jsdl)
		throws SQLException, ResourceException, ConfigurationException
	{
		Connection connection = null;
		
		try
		{
			connection = _connectionPool.acquire();
			return _jobManager.submitJob(connection, jsdl, priority);
		}
		finally
		{
			_connectionPool.release(connection);
		}
	}
	
	public Collection<ReducedJobInformationType> listJobs()
		throws SQLException, ResourceException
	{
		Connection connection = null;
		
		try
		{
			connection = _connectionPool.acquire();
			return _jobManager.listJobs(connection);
		}
		finally
		{
			_connectionPool.release(connection);
		}
	}
	
	public Collection<JobInformationType> getJobStatus(String []jobs)
		throws SQLException, ResourceException
	{
		Connection connection = null;
		
		try
		{
			connection = _connectionPool.acquire();
			return _jobManager.getJobStatus(connection, jobs);
		}
		finally
		{
			_connectionPool.release(connection);
		}
	}
	
	public void completeJobs(String []jobs)
		throws SQLException, ResourceException, 
			GenesisIISecurityException
	{
		Connection connection = null;
		
		try
		{
			connection = _connectionPool.acquire();
			_jobManager.completeJobs(connection, jobs);
		}
		finally
		{
			_connectionPool.release(connection);
		}
	}
	
	public void killJobs(String []jobs)
		throws SQLException, ResourceException
	{
		Connection connection = null;
		
		try
		{
			connection = _connectionPool.acquire();
			_jobManager.killJobs(connection, jobs);
		}
		finally
		{
			_connectionPool.release(connection);
		}
	}
}