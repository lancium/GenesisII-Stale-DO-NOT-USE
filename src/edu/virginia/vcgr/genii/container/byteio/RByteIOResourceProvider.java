package edu.virginia.vcgr.genii.container.byteio;

import java.sql.SQLException;

import edu.virginia.vcgr.genii.container.resource.IResourceFactory;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.container.db.ServerDatabaseConnectionPool;
import edu.virginia.vcgr.genii.container.resource.db.BasicDBResourceProvider;

public class RByteIOResourceProvider extends BasicDBResourceProvider
{
	protected IResourceFactory instantiateResourceFactory(ServerDatabaseConnectionPool pool) throws SQLException, ResourceException
	{
		return new RByteIOResourceFactory(pool);
	}
}
