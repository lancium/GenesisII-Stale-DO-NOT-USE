package edu.virginia.vcgr.genii.container.resource.db;

import java.sql.SQLException;

import org.morgan.util.configuration.ConfigurationException;

import edu.virginia.vcgr.genii.client.configuration.Initializable;
import edu.virginia.vcgr.genii.client.configuration.NamedInstances;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.container.db.ServerDatabaseConnectionPool;
import edu.virginia.vcgr.genii.container.resource.IResourceFactory;
import edu.virginia.vcgr.genii.container.resource.IResourceProvider;

public class BasicDBResourceProvider implements IResourceProvider, Initializable
{
	private IResourceFactory _factory = null;

	public BasicDBResourceProvider()
	{
	}

	private ServerDatabaseConnectionPool createConnectionPool()
	{
		ServerDatabaseConnectionPool pool = null;

		Object obj = NamedInstances.getServerInstances().lookup("connection-pool");
		if (obj != null) {
			pool = (ServerDatabaseConnectionPool) obj;
			return pool;
		}

		throw new ConfigurationException("Couldn't find connection pool.");
	}

	synchronized public IResourceFactory getFactory()
	{
		if (_factory == null) {
			try {
				ServerDatabaseConnectionPool pool = createConnectionPool();
				_factory = instantiateResourceFactory(pool);
			} catch (Exception e) {
				throw new RuntimeException(e.getLocalizedMessage(), e);
			}
		}

		return _factory;
	}

	protected IResourceFactory instantiateResourceFactory(ServerDatabaseConnectionPool pool) throws SQLException, ResourceException
	{
		return new BasicDBResourceFactory(pool);
	}

	@Override
	public void initialize() throws Throwable
	{
		getFactory();
	}
}