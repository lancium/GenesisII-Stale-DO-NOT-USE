package edu.virginia.vcgr.genii.client.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.morgan.util.io.StreamUtils;

import edu.virginia.vcgr.genii.client.comm.socket.SocketConfigurer;

public class Deployment
{
	static private Log _logger = LogFactory.getLog(Deployment.class);

	static private final String CONFIGURATION_DIRECTORY_NAME = "configuration";
	static private final String SERVICES_DIRECTORY_NAME = "services";
	static private final String DYNAMIC_PAGES_DIRECTORY_NAME = "dynamic-pages";
	static private final String URI_PROPERTIES_FILENAME = "uri-manager.properties";
	static private final String WEB_CONTAINER_PROPERTIES_FILENAME = "web-container.properties";
	static private final String REJUVENATION_PROPERTYIES_FILENAME = "rejuvenation.properties";
	static private final String CLIENT_SOCKET_PROPERTIES_FILENAME = "client-socket.properties";
	static private final String SECURE_RUNNABLE_DIRECTORY_NAME = "secure-runnable";

	static private Map<String, Deployment> _knownDeployments = new HashMap<String, Deployment>(4);

	private HierarchicalDirectory _deploymentDirectory;
	private HierarchicalDirectory _configurationDirectory;
	private File _secureRunnableDirectory;
	private Security _security;
	private HierarchicalDirectory _servicesDirectory;
	private HierarchicalDirectory _dynamicPagesDirectory;
	private Properties _webContainerProperties;
	private Properties _uriManagerProperties;
	private Properties _rejuvenationProperties;
	private SocketConfigurer _clientSocketConfigurer;
	private NamespaceDefinitions _namespace;

	private Deployment(File deploymentDirectory)
	{
		_deploymentDirectory = HierarchicalDirectory.openRootHierarchicalDirectory(deploymentDirectory);

		_configurationDirectory = _deploymentDirectory.lookupDirectory(CONFIGURATION_DIRECTORY_NAME);

		if (!_configurationDirectory.exists())
			throw new InvalidDeploymentException(_deploymentDirectory.getName(), "Does not contain a "
				+ CONFIGURATION_DIRECTORY_NAME + " directory.");

		_security = new Security(_deploymentDirectory, _configurationDirectory);

		_namespace = new NamespaceDefinitions(_deploymentDirectory, _configurationDirectory);

		_servicesDirectory = _deploymentDirectory.lookupDirectory(SERVICES_DIRECTORY_NAME);

		if (!_servicesDirectory.exists())
			throw new InvalidDeploymentException(_deploymentDirectory.getName(), "Does not contain a "
				+ SERVICES_DIRECTORY_NAME + " directory.");

		_dynamicPagesDirectory = _deploymentDirectory.lookupDirectory(DYNAMIC_PAGES_DIRECTORY_NAME);

		_webContainerProperties = loadWebContainerProperties(_deploymentDirectory.getName(), _configurationDirectory);
		_uriManagerProperties = loadURIManagerProperties(_deploymentDirectory.getName(), _configurationDirectory);
		_clientSocketConfigurer = loadClientSocketConfigurer();
		_rejuvenationProperties = loadRejuvenationProperties(_deploymentDirectory.getName(), _configurationDirectory);

		_secureRunnableDirectory = new File(deploymentDirectory, SECURE_RUNNABLE_DIRECTORY_NAME);
	}

	private SocketConfigurer loadClientSocketConfigurer()
	{
		Properties properties = new Properties();

		File confFile = getConfigurationFile(CLIENT_SOCKET_PROPERTIES_FILENAME);
		if (confFile.exists()) {
			FileInputStream fin = null;

			try {
				fin = new FileInputStream(confFile);
				properties.load(fin);
			} catch (IOException ioe) {
				if (_logger.isDebugEnabled())
					_logger.debug("Unable to load client-socket properties.", ioe);
			} finally {
				StreamUtils.close(fin);
			}
		}

		return new SocketConfigurer(properties);
	}

	static private Properties loadURIManagerProperties(String deploymentName, HierarchicalDirectory configurationDirectory)
	{
		FileInputStream fin = null;
		Properties ret = new Properties();

		try {
			fin = new FileInputStream(configurationDirectory.lookupFile(URI_PROPERTIES_FILENAME));
			ret.load(fin);
			return ret;
		} catch (IOException ioe) {
			if (_logger.isDebugEnabled())
				_logger.debug("Unable to load uri manager properties from deployment.", ioe);
			return new Properties();
		} finally {
			StreamUtils.close(fin);
		}
	}

	static private Properties loadWebContainerProperties(String deploymentName, HierarchicalDirectory configurationDirectory)
	{
		FileInputStream fin = null;
		Properties ret = new Properties();

		try {
			fin = new FileInputStream(configurationDirectory.lookupFile(WEB_CONTAINER_PROPERTIES_FILENAME));
			ret.load(fin);
			return ret;
		} catch (IOException ioe) {
			_logger.fatal("Unable to load web container properties from deployment.", ioe);
			throw new InvalidDeploymentException(deploymentName, "Unable to load web container properties from deployment.");
		} finally {
			StreamUtils.close(fin);
		}
	}

	static private Properties loadRejuvenationProperties(String deploymentName, HierarchicalDirectory configurationDirectory)
	{
		FileInputStream fin = null;
		Properties ret = new Properties();

		try {
			fin = new FileInputStream(configurationDirectory.lookupFile(REJUVENATION_PROPERTYIES_FILENAME));
			ret.load(fin);
			return ret;
		} catch (IOException ioe) {
			if (_logger.isDebugEnabled())
				_logger.debug("Unable to load software rejuvenation information.  " + "Assuming there isn't any.", ioe);
			return new Properties();
		} finally {
			StreamUtils.close(fin);
		}
	}

	public File secureRunnableDirectory()
	{
		return _secureRunnableDirectory;
	}

	public HierarchicalDirectory getConfigurationDirectory()
	{
		return _configurationDirectory;
	}

	public File getConfigurationFile(String configurationFilename)
	{
		return _configurationDirectory.lookupFile(configurationFilename);
	}

	public Security security()
	{
		return _security;
	}

	public NamespaceDefinitions namespace()
	{
		return _namespace;
	}

	public HierarchicalDirectory getServicesDirectory()
	{
		return _servicesDirectory;
	}

	public HierarchicalDirectory getDynamicPagesDirectory()
	{
		return _dynamicPagesDirectory;
	}

	public Properties uriManagerProperties()
	{
		return _uriManagerProperties;
	}

	public Properties webContainerProperties()
	{
		return _webContainerProperties;
	}

	public SocketConfigurer clientSocketConfigurer()
	{
		return _clientSocketConfigurer;
	}

	public Properties softwareRejuvenationProperties()
	{
		return _rejuvenationProperties;
	}

	public DeploymentName getName()
	{
		return new DeploymentName(_deploymentDirectory.getName());
	}

	static void reload()
	{
		synchronized (_knownDeployments) {
			_knownDeployments.clear();
		}
	}

	static Deployment getDeployment(File deploymentsDirectory, DeploymentName deploymentName)
	{
		Deployment ret;

		String deploymentNameString = deploymentName.toString();

		synchronized (_knownDeployments) {
			ret = _knownDeployments.get(deploymentNameString);
			if (ret == null) {
				File dep = new File(deploymentsDirectory, deploymentNameString);
				if (!dep.exists())
					throw new NoSuchDeploymentException(deploymentNameString);
				if (!dep.isDirectory())
					throw new InvalidDeploymentException(deploymentNameString, "Not a directory");
				_knownDeployments.put(deploymentNameString, ret = new Deployment(dep));
			}
		}

		return ret;
	}
}
