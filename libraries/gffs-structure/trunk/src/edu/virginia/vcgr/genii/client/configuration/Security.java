package edu.virginia.vcgr.genii.client.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.morgan.util.io.StreamUtils;

import edu.virginia.vcgr.genii.client.InstallationProperties;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.gpath.GeniiPath;
import edu.virginia.vcgr.genii.client.security.KeystoreManager;
import edu.virginia.vcgr.genii.client.security.axis.AclAuthZClientTool;
import edu.virginia.vcgr.genii.security.VerbosityLevel;
import edu.virginia.vcgr.genii.security.identity.Identity;

public class Security
{
	static private Log _logger = LogFactory.getLog(Security.class);

	static private final String SECURITY_DIRECTORY_NAME = "security";
	static private final String SECURITY_PROPERTIES_FILE_NAME = "security.properties";
	static private final String ADMIN_CERTIFICATE_FILE = "admin.cer";

	static private boolean _loadedAdministrator = false;
	static private Identity _administrator = null;
	private HierarchicalDirectory _securityDirectory;
	private File _securityPropertiesFile;
	private Properties _securityProperties;

	Security(HierarchicalDirectory deploymentDirectory, HierarchicalDirectory configurationDirectory)
	{
		_securityDirectory = deploymentDirectory.lookupDirectory(SECURITY_DIRECTORY_NAME);
		_securityPropertiesFile = configurationDirectory.lookupFile(SECURITY_PROPERTIES_FILE_NAME);
		_securityProperties = new Properties();

		if (!_securityDirectory.exists())
			throw new InvalidDeploymentException(deploymentDirectory.getName(), "Couldn't find security directory in deployment.");
		if (!_securityPropertiesFile.exists())
			throw new InvalidDeploymentException(deploymentDirectory.getName(),
				"Couldn't find security properties file \"" + SECURITY_PROPERTIES_FILE_NAME + " in deployment's configuration directory.");

		FileInputStream fin = null;
		try {
			fin = new FileInputStream(_securityPropertiesFile);
			_securityProperties.load(fin);
		} catch (IOException ioe) {
			_logger.fatal("Unable to load security properties from deployment.", ioe);
			throw new InvalidDeploymentException(deploymentDirectory.getName(), "Unable to load security properties from deployment.");
		} finally {
			StreamUtils.close(fin);
		}
	}

	public HierarchicalDirectory getSecurityDirectory()
	{
		return _securityDirectory;
	}

	public File getSecurityFile(String filename)
	{
		File toReturn = InstallationProperties.getInstallationProperties().getSecurityFile(filename);
		if (toReturn == null)
			toReturn = _securityDirectory.lookupFile(filename);
		return toReturn;
	}

	public String getProperty(String propertyName)
	{
		String toReturn = InstallationProperties.getInstallationProperties().getProperty(propertyName, null);
		if (toReturn == null)
			toReturn = getProperty(propertyName, null);
		return toReturn;
	}

	public String getProperty(String propertyName, String def)
	{
		String toReturn = InstallationProperties.getInstallationProperties().getProperty(propertyName, def);
		if (toReturn == null)
			toReturn = _securityProperties.getProperty(propertyName, def);
		return toReturn;
	}

	public String getSigningKeystoreFile()
	{
		Security resourceIdSecProps = Installation.getDeployment(new DeploymentName()).security();
		String keyProp = resourceIdSecProps.getProperty(KeystoreSecurityConstants.Container.RESOURCE_IDENTITY_KEY_STORE_PROP);
		String keystoreLoc = Installation.getDeployment(new DeploymentName()).security().getSecurityFile(keyProp).getAbsolutePath();
		return keystoreLoc;
	}

	public File getAdminCertFile()
	{
		return getSecurityFile(ADMIN_CERTIFICATE_FILE);
	}

	public Identity getAdminIdentity()
	{
		synchronized (Security.class) {
			if (!_loadedAdministrator) {
				File file = getAdminCertFile();
				if (file.exists()) {
					try {
						GeniiPath filePath = new GeniiPath("local:" + file.getAbsolutePath());
						_administrator = AclAuthZClientTool.downloadIdentity(filePath);
						_loadedAdministrator = true;
					} catch (Throwable cause) {
						_logger.warn("Unable to load administrator certificate.", cause);
					}
				}
			}

			return _administrator;
		}
	}

	public boolean isDeploymentAdministrator(ICallingContext callingContext)
	{
		Identity adminIdentity = getAdminIdentity();
		try {
			if (adminIdentity != null) {
				for (Identity id : KeystoreManager.getCallerIdentities(callingContext)) {
					if (adminIdentity.equals(id)) {
						if (_logger.isTraceEnabled())
							_logger.trace("found id matching admin cert: " + adminIdentity.describe(VerbosityLevel.LOW));
						return true;
					}
				}
			}
		} catch (Throwable cause) {
			_logger.warn("Exception during admin identity checking.", cause);
		}

		// try again using any registered owner certificate.
		Identity ownerCert = InstallationProperties.getInstallationProperties().getOwnerCertificate();
		if (ownerCert == null) {
			return false;
		}
		try {
			for (Identity id : KeystoreManager.getCallerIdentities(callingContext)) {
				if (ownerCert.equals(id)) {
					if (_logger.isTraceEnabled())
						_logger.trace("found id matching owner cert: " + ownerCert.describe(VerbosityLevel.LOW));
					return true;
				}
			}
		} catch (Throwable cause) {
			_logger.warn("Exception during owner identity checking.", cause);
		}
		return false;
	}

	static public boolean isAdministrator(ICallingContext callingContext)
	{
		DeploymentName depName = new DeploymentName();
		return Installation.getDeployment(depName).security().isDeploymentAdministrator(callingContext);
	}

	static public boolean isAdministrator()
	{
		try {
			return isAdministrator(ContextManager.getExistingContext());
		} catch (Throwable cause) {
			_logger.warn("Unable to determine if caller is admin.", cause);
			return false;
		}
	}
}
