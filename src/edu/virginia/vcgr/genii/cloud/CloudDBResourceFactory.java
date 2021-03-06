package edu.virginia.vcgr.genii.cloud;

import java.sql.Connection;
import java.sql.SQLException;

import edu.virginia.vcgr.genii.client.db.DatabaseTableUtils;
import edu.virginia.vcgr.genii.container.db.ServerDatabaseConnectionPool;

public class CloudDBResourceFactory
{

	private ServerDatabaseConnectionPool _pool;

	static private final String[] _CREATE_STMTS = new String[] {
		"CREATE TABLE cloudResources (" + "resourceid VARCHAR(256) NOT NULL PRIMARY KEY, " + "host VARCHAR(4096) NOT NULL, "
			+ "port INTEGER NOT NULL, " + "load INTEGER NOT NULL, " + "setup INTEGER NOT NULL, " + "besid VARCHAR(256) NOT NULL) ",
		"CREATE TABLE cloudActivities (" + "activityid VARCHAR(256) NOT NULL PRIMARY KEY, " + "resourceid VARCHAR(256) NOT NULL)" };

	public CloudDBResourceFactory(ServerDatabaseConnectionPool pool) throws SQLException
	{
		_pool = pool;
		createTables();
	}

	protected void createTables() throws SQLException
	{
		Connection conn = null;

		try {
			conn = _pool.acquire(false);
			DatabaseTableUtils.createTables(conn, false, _CREATE_STMTS);
			conn.commit();
		} finally {
			_pool.release(conn);
		}
	}

	public ServerDatabaseConnectionPool getConnectionPool()
	{
		return _pool;
	}
}
