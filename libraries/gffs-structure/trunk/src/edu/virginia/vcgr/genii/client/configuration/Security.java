package edu.virginia.vcgr.genii.client.configuration;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.morgan.util.io.StreamUtils;

import edu.virginia.vcgr.genii.client.InstallationConstants;
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
			throw new InvalidDeploymentException(deploymentDirectory.getName(),
				"Couldn't find security directory in deployment.");
		if (!_securityPropertiesFile.exists())
			throw new InvalidDeploymentException(deploymentDirectory.getName(), "Couldn't find security properties file \""
				+ SECURITY_PROPERTIES_FILE_NAME + " in deployment's configuration directory.");

		FileInputStream fin = null;
		try {
			fin = new FileInputStream(_securityPropertiesFile);
			_securityProperties.load(fin);
		} catch (IOException ioe) {
			_logger.fatal("Unable to load security properties from deployment.", ioe);
			throw new InvalidDeploymentException(deploymentDirectory.getName(),
				"Unable to load security properties from deployment.");
		} finally {
			StreamUtils.close(fin);
		}
	}

	public Collection<File> getDeploymentDefaultOwnerFiles()
	{
		Collection<File> ret = new LinkedList<File>();
		HierarchicalDirectory ownersDir = _securityDirectory.lookupDirectory(InstallationConstants.OWNER_CERTS_DIRECTORY_NAME);
		if (ownersDir.exists()) {
			File[] files = ownersDir.listFiles(new FileFilter()
			{
				@Override
				public boolean accept(File pathname)
				{
					return pathname.getName().endsWith(".cer");
				}
			});

			if (files != null && files.length > 0) {
				for (File file : files)
					ret.add(file);
			}
		}

		return ret;
	}

	public HierarchicalDirectory getSecurityDirectory()
	{
		return _securityDirectory;
	}

	public File getSecurityFile(String filename)
	{
		return _securityDirectory.lookupFile(filename);
	}

	public String getProperty(String propertyName)
	{
		return getProperty(propertyName, null);
	}

	public String getProperty(String propertyName, String def)
	{
		return _securityProperties.getProperty(propertyName, def);
	}

	public Identity getAdminIdentity()
	{
		synchronized (Security.class) {
			if (!_loadedAdministrator) {
				_loadedAdministrator = true;

				File file = getSecurityFile(ADMIN_CERTIFICATE_FILE);
				if (file.exists()) {
					try {
						GeniiPath filePath = new GeniiPath("local:" + file.getAbsolutePath());
						_administrator = AclAuthZClientTool.downloadIdentity(filePath);
					} catch (Throwable cause) {
						_logger.warn("Unable to get administrator certificate.", cause);
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
