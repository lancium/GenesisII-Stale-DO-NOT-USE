package edu.virginia.vcgr.genii.container.cservices;

import java.util.Properties;
import java.util.concurrent.ExecutorService;

import edu.virginia.vcgr.genii.container.db.ServerDatabaseConnectionPool;

public interface ContainerService
{
	public void setProperties(Properties properties);

	public boolean started();

	public String serviceName();

	public void load(ExecutorService executor, ServerDatabaseConnectionPool connectionPool, ContainerServicesProperties cservicesProperties)
		throws Throwable;

	public void start() throws Throwable;

	public ContainerServicesProperties getContainerServicesProperties();
}