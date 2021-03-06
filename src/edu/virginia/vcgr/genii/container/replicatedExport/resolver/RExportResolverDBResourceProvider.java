package edu.virginia.vcgr.genii.container.replicatedExport.resolver;

import java.sql.SQLException;

import edu.virginia.vcgr.genii.container.resource.IResourceFactory;
import edu.virginia.vcgr.genii.container.db.ServerDatabaseConnectionPool;
import edu.virginia.vcgr.genii.container.resource.db.BasicDBResourceProvider;

public class RExportResolverDBResourceProvider extends BasicDBResourceProvider
{
	protected IResourceFactory instantiateResourceFactory(ServerDatabaseConnectionPool pool) throws SQLException
	{
		return new RExportResolverDBResourceFactory(pool);
	}
}