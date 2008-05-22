package edu.virginia.vcgr.genii.container.jndiauthn;

import java.sql.SQLException;

import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.container.db.DatabaseConnectionPool;
import edu.virginia.vcgr.genii.container.resource.IResource;
import edu.virginia.vcgr.genii.container.resource.IResourceFactory;
import edu.virginia.vcgr.genii.container.resource.IResourceKeyTranslater;
import edu.virginia.vcgr.genii.container.resource.ResourceKey;
import edu.virginia.vcgr.genii.container.resource.db.BasicDBResourceFactory;

public class JNDIResourceFactory extends BasicDBResourceFactory implements
		IResourceFactory
{
	public JNDIResourceFactory(DatabaseConnectionPool pool,
			IResourceKeyTranslater translator) throws SQLException
	{
		super(pool, translator);
	}

	public IResource instantiate(ResourceKey parentKey)
			throws ResourceException
	{
		try
		{
			return new JNDIResource(parentKey, _pool, _translater);
		}
		catch (SQLException sqe)
		{
			throw new ResourceException(sqe.getLocalizedMessage(), sqe);
		}
	}

}