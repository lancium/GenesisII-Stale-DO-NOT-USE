package edu.virginia.vcgr.genii.container.common.notification;

import java.sql.Connection;
import java.sql.SQLException;

import edu.virginia.vcgr.genii.container.db.ServerDatabaseConnectionPool;
import edu.virginia.vcgr.genii.container.resource.ResourceKey;
import edu.virginia.vcgr.genii.container.resource.db.BasicDBResource;

public class DBBrokerResource extends BasicDBResource
{
	public DBBrokerResource(String key, Connection connection)
	{
		super(key, connection);
	}

	public DBBrokerResource(ResourceKey parentKey, ServerDatabaseConnectionPool connectionPool) throws SQLException
	{
		super(parentKey, connectionPool);
	}
}