package edu.virginia.vcgr.genii.container.common.notification;

import java.sql.SQLException;

import edu.virginia.vcgr.genii.container.resource.IResourceFactory;
import edu.virginia.vcgr.genii.container.db.ServerDatabaseConnectionPool;
import edu.virginia.vcgr.genii.container.resource.db.BasicDBResourceProvider;

public class DBBrokerResourceProvider extends BasicDBResourceProvider {
	@Override
	protected IResourceFactory instantiateResourceFactory(
			ServerDatabaseConnectionPool pool) throws SQLException {
		return new DBBrokerResourceFactory(pool);
	}
}