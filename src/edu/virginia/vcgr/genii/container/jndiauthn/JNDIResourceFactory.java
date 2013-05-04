package edu.virginia.vcgr.genii.container.jndiauthn;

import java.sql.SQLException;

import edu.virginia.vcgr.genii.client.resource.IResource;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.container.db.DatabaseConnectionPool;
import edu.virginia.vcgr.genii.container.resource.IResourceFactory;
import edu.virginia.vcgr.genii.container.resource.ResourceKey;
import edu.virginia.vcgr.genii.container.resource.db.BasicDBResourceFactory;

public class JNDIResourceFactory extends BasicDBResourceFactory implements IResourceFactory
{
	public JNDIResourceFactory(DatabaseConnectionPool pool) throws SQLException
	{
		super(pool);
	}

	public IResource instantiate(ResourceKey parentKey) throws ResourceException
	{
		try {
			return new JNDIResource(parentKey, _pool);
		} catch (SQLException sqe) {
			throw new ResourceException(sqe.getLocalizedMessage(), sqe);
		}
	}

}