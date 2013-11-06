package edu.virginia.vcgr.genii.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.morgan.util.io.StreamUtils;

import edu.virginia.vcgr.genii.client.configuration.Installation;

public class ContainerProperties extends Properties
{
	static final long serialVersionUID = 0L;

	static final private String CONTAINER_PROPERTIES_FILENAME = "container.properties";

	static private final String DEPLOYMENTS_DIRECTORY_NAME = "deployments";

	static final private String GENII_USER_DIR_PROPERTY_NAME = "edu.virginia.vcgr.genii.container.user-dir";
	static final private String GENII_DEPLOYMENT_DIRECTORY_PROPERTY_NAME =
		"edu.virginia.vcgr.genii.container.deployment-directory";
	static final private String GENII_DEPLOYMENT_NAME_PROPERTY_NAME = "edu.virginia.vcgr.genii.container.deployment-name";
	static final public String GRID_CONNECTION_COMMAND_PROPERTY = "edu.virginia.vcgr.genii.gridInitCommand";

	static private ContainerProperties _realContainerProperties = new ContainerProperties();

	private boolean _existed = false;

	/**
	 * for all normal run-time classes, the container properties is accessed this way.
	 */
	static public ContainerProperties getContainerProperties()
	{
		return _realContainerProperties;
	}

	/**
	 * This is not to be used in general; normally the containerProperties member should be used.
	 */
	public ContainerProperties()
	{
		File file = getContainerPropertiesFile();
		if (file != null) {
			InputStream in = null;
			try {
				in = new FileInputStream(file);
				load(in);
				_existed = true;
			} catch (IOException e) {
				return;
			} finally {
				StreamUtils.close(in);
			}
		}
	}

	public boolean existed()
	{
		return _existed;
	}

	static private File getContainerPropertiesFile()
	{
		File ret = new File(Installation.getInstallDirectory(), CONTAINER_PROPERTIES_FILENAME);
		if (ret.exists() && ret.isFile() && ret.canRead())
			return ret;

		return null;
	}

	/**
	 * Intended only for use by ApplicationBase.getUserDir. This loads the user directory setting
	 * from container.properties if it exists or returns null.
	 */
	public String getUserDirectoryProperty()
	{
		return getProperty(GENII_USER_DIR_PROPERTY_NAME);
	}

	/**
	 * returns the configured deployments directory, if one is defined. otherwise returns the
	 * default location.
	 */
	public String getDeploymentsDirectory()
	{
		// use the environment variable first.
		String toReturn = ApplicationBase.getDeploymentDirFromEnvironment();
		if (toReturn == null)
			toReturn = getProperty(GENII_DEPLOYMENT_DIRECTORY_PROPERTY_NAME);
		// well, nothing worked, so use a default based on the installation directory.
		if (toReturn == null)
			toReturn = new File(Installation.getInstallDirectory(), DEPLOYMENTS_DIRECTORY_NAME).getAbsolutePath();
		return toReturn;
	}

	public String getDeploymentName()
	{
		return getProperty(GENII_DEPLOYMENT_NAME_PROPERTY_NAME);
	}

	/**
	 * returns the command line for a grid connect command (minus 'grid' and 'connect'). if this
	 * property has not been set, then this returns null.
	 */
	public String getConnectionCommand()
	{
		return getProperty(GRID_CONNECTION_COMMAND_PROPERTY);
	}
}