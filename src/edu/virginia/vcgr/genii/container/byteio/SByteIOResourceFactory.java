package edu.virginia.vcgr.genii.container.byteio;

import java.sql.SQLException;

import edu.virginia.vcgr.genii.client.resource.IResource;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.container.db.ServerDatabaseConnectionPool;
import edu.virginia.vcgr.genii.container.resource.ResourceKey;
import edu.virginia.vcgr.genii.container.resource.db.BasicDBResourceFactory;

public class SByteIOResourceFactory extends BasicDBResourceFactory {
	public SByteIOResourceFactory(ServerDatabaseConnectionPool pool)
			throws SQLException {
		super(pool);
	}

	public IResource instantiate(ResourceKey parentKey)
			throws ResourceException {
		try {
			return new SByteIOResource(parentKey, _pool);
		} catch (SQLException sqe) {
			throw new ResourceException(sqe.getLocalizedMessage(), sqe);
		}
	}
}