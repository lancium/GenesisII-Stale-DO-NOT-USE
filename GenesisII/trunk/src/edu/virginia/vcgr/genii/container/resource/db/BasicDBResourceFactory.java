package edu.virginia.vcgr.genii.container.resource.db;

import java.sql.Connection;
import java.sql.SQLException;

import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.container.db.DatabaseConnectionPool;
import edu.virginia.vcgr.genii.container.db.DatabaseTableUtils;
import edu.virginia.vcgr.genii.container.resource.IResource;
import edu.virginia.vcgr.genii.container.resource.IResourceFactory;
import edu.virginia.vcgr.genii.container.resource.ResourceKey;

public class BasicDBResourceFactory implements IResourceFactory
{
	static private final String _CREATE_KEY_TABLE_STMT =
		"CREATE TABLE resources (resourceid VARCHAR(128) PRIMARY KEY," +
			"createtime TIMESTAMP)";
	static private final String _CREATE_PROPERTY_TABLE_STMT =
		"CREATE TABLE properties (resourceid VARCHAR(128), propname VARCHAR(256)," +
			"propvalue BLOB (2G), CONSTRAINT propertiesconstraint1 " +
			"PRIMARY KEY (resourceid, propname))";
	static private final String _CREATE_MATCHING_PARAMS_STMT =
		"CREATE TABLE matchingparams (" +
			"resourceid VARCHAR(128), paramname VARCHAR(256)," +
			"paramvalue VARCHAR(256), " +
			"CONSTRAINT matchingparamsconstraint1 PRIMARY KEY " +
				"(resourceid, paramname, paramvalue))";
	static private final String _CREATE_RESOURCES_TABLE_STMT =
		"CREATE TABLE resources2(resourceid VARCHAR(128) PRIMARY KEY," +
			"implementingclass VARCHAR(512) NOT NULL, " +
			"epi VARCHAR(512) NOT NULL, " +
			"humanname VARCHAR(512), " +
			"epr BLOB(2G))";
	static private final String _CREATE_RESOURCES_IMPL_CLASS_INDEX =
		"CREATE INDEX resources2implclassindex ON resources2(implementingclass)";
	static private final String _CREATE_RESOURCES_EPI_INDEX =
		"CREATE INDEX resources2epiindex ON resources2(epi)";
	
	protected DatabaseConnectionPool _pool;
	
	public BasicDBResourceFactory(DatabaseConnectionPool pool)
		throws SQLException
	{
		_pool = pool;
		createTables();
	}
	
	public IResource instantiate(ResourceKey parentKey) throws ResourceException
	{
		try
		{
			return new BasicDBResource(parentKey, _pool);
		}
		catch (SQLException sqe)
		{
			throw new ResourceException(sqe.getLocalizedMessage(), sqe);
		}
	}
	
	protected void createTables() throws SQLException
	{
		Connection conn = null;
		
		try
		{
			conn = _pool.acquire(false);
			DatabaseTableUtils.createTables(conn, false,
				_CREATE_KEY_TABLE_STMT,
				_CREATE_PROPERTY_TABLE_STMT,
				_CREATE_MATCHING_PARAMS_STMT,
				_CREATE_RESOURCES_TABLE_STMT,
				_CREATE_RESOURCES_IMPL_CLASS_INDEX,
				_CREATE_RESOURCES_EPI_INDEX);
			conn.commit();
		}
		finally
		{
			_pool.release(conn);
		}
	}
	
	public DatabaseConnectionPool getConnectionPool()
	{
		return _pool;
	}
}