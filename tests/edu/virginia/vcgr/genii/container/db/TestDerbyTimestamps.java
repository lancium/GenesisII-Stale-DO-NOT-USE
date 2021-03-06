package edu.virginia.vcgr.genii.container.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Properties;

import org.junit.Test;
import org.morgan.util.io.StreamUtils;

public class TestDerbyTimestamps
{
	static private ServerDatabaseConnectionPool _pool;

	static public void setUp() throws Throwable
	{
		createConnectionPool();
		createTables();
	}

	public void tearDown() throws Throwable
	{
		dropTables();
	}

	@Test
	public void testNothing()
	{
		// place holder til we resolve the startup issues here.
	}

	// @Test
	public void notAtestDerbyTimeStamp() throws Throwable
	{
		Connection conn = null;
		PreparedStatement insertStmt = null;
		PreparedStatement queryStmt = null;
		ResultSet rs = null;

		try {
			conn = _pool.acquire(false);
			insertStmt = conn.prepareStatement("INSERT INTO test1 VALUES(?, CURRENT_TIMESTAMP)");
			insertStmt.setInt(1, 0);
			insertStmt.executeUpdate();
			conn.commit();

			queryStmt = conn.prepareStatement("SELECT ts, CURRENT_TIMESTAMP, "
				+ "{fn TIMESTAMPDIFF(SQL_TSI_FRAC_SECOND, CURRENT_TIMESTAMP, ts)} " + "FROM test1 WHERE ID = 0");
			rs = queryStmt.executeQuery();
			rs.next();

			Timestamp ts = rs.getTimestamp(1);
			Timestamp current = rs.getTimestamp(2);
			long diff = rs.getLong(3);

			System.err.println("The difference between " + ts + " and " + current + " is " + diff);
		} finally {
			StreamUtils.close(rs);
			StreamUtils.close(queryStmt);
			StreamUtils.close(insertStmt);
			_pool.release(conn);
		}
	}

	static void createConnectionPool() throws Throwable
	{
		Properties props = new Properties();
		props.setProperty("edu.virginia.vcgr.genii.client.db.db-class-name", "org.apache.derby.jdbc.EmbeddedDriver");
		props.setProperty("edu.virginia.vcgr.genii.client.db.db-connect-string", "jdbc:derby:/Users/morgan/test-database;create=true");
		props.setProperty("edu.virginia.vcgr.genii.client.db.db-user", "sa");
		props.setProperty("edu.virginia.vcgr.genii.client.db.db-password", "");
		props.setProperty("edu.virginia.vcgr.genii.client.db.pool-size", "8");

		_pool = new ServerDatabaseConnectionPool(props);
	}

	static public void createTables() throws Throwable
	{
		Connection conn = null;
		Statement stmt = null;

		try {
			conn = _pool.acquire(false);
			stmt = conn.createStatement();
			stmt.executeUpdate("CREATE TABLE test1 (ID INTEGER PRIMARY KEY," + "ts TIMESTAMP)");
			conn.commit();
		} finally {
			StreamUtils.close(stmt);
			_pool.release(conn);
		}
	}

	static public void dropTables() throws Throwable
	{
		Connection conn = null;
		Statement stmt = null;

		try {
			conn = _pool.acquire(false);
			stmt = conn.createStatement();
			stmt.executeUpdate("DROP TABLE test1");
			conn.commit();
		} finally {
			StreamUtils.close(stmt);
			_pool.release(conn);
		}
	}
}
