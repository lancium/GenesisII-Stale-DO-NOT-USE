package edu.virginia.vcgr.appmgr.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.LinkedList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.virginia.vcgr.appmgr.io.IOUtils;
import edu.virginia.vcgr.appmgr.util.HierarchicalProperties;
import edu.virginia.vcgr.appmgr.util.PropertyUtilities;
import edu.virginia.vcgr.appmgr.version.VersionManager;

public class ApplicationDescription
{
	public static Log _logger = LogFactory.getLog(ApplicationDescription.class);

	static final private String BASE_PROP_NAME = "edu.virginia.vcgr.appwatcher.";

	static final private String DEPENDENT_PROPERTY = BASE_PROP_NAME + "dependent-properties-file";

	static final private String PATCH_SIGNER_CERTIFICATE_PROPERTY_BASE = BASE_PROP_NAME + "patch-signer-certificate";
	static final private String APPLICATION_NAME_PROPERTY = BASE_PROP_NAME + "application-name";
	static final private String APPLICATION_URL_PROPERTY_BASE = BASE_PROP_NAME + "application-url";
	static final private String JAR_DESCRIPTION_FILE_PROPERTY = BASE_PROP_NAME + "jar-description-file";
	static final private String APPLICATION_CLASS_PROPERTY = BASE_PROP_NAME + "application-class";
	static final private String FAKE_UPDATER_CLASS_VALUE = "edu.virginia.vcgr.appmgr.update.UpdaterClass";

	/*
	 * the optional environment variable that points at the installation location. this becomes
	 * required if one is running the software under eclipse.
	 */
	static public final String INSTALLATION_DIR_ENVIRONMENT_VARIABLE = "GENII_INSTALL_DIR";

	private VersionManager _versionManager;
	private String _applicationName;
	private File _applicationDirectory;
	private Collection<URL> _applicationDownloadURLs;
	private File _jarDescriptionFile;
	private String _applicationClassName;
	private Boolean _updateDisabled = false;

	static private HierarchicalProperties readPropertiesFile(File propFile) throws FileNotFoundException, IOException
	{
		HierarchicalProperties tmp = new HierarchicalProperties();
		FileInputStream fin = null;

		try {
			fin = new FileInputStream(propFile);
			tmp.load(fin);

			String dependentFile = tmp.getProperty(DEPENDENT_PROPERTY);
			if (dependentFile != null) {
				HierarchicalProperties parent = readPropertiesFile(new File(dependentFile));
				tmp.setParent(parent);
			}

			return tmp;
		} finally {
			IOUtils.close(fin);
		}
	}

	private void initialize(String applicationClassName, HierarchicalProperties properties) throws IOException,
		CertificateException
	{
		_applicationName = PropertyUtilities.getRequiredProperty(properties, APPLICATION_NAME_PROPERTY);
		if (_logger.isTraceEnabled())
			_logger.trace("app name here is: " + _applicationName);
		_applicationDirectory = new File(getInstallationDirectory());
		if (!_applicationDirectory.exists())
			throw new FileNotFoundException(String.format("Couldn't find application directory \"%s\".", _applicationDirectory));

		_jarDescriptionFile = new File(PropertyUtilities.getRequiredProperty(properties, JAR_DESCRIPTION_FILE_PROPERTY));
		if (!_jarDescriptionFile.exists())
			throw new FileNotFoundException(String.format("Couldn't find jar description file \"%s\".", _jarDescriptionFile));
		_applicationClassName = applicationClassName;

		if (_applicationClassName == null)
			_applicationClassName = PropertyUtilities.getRequiredProperty(properties, APPLICATION_CLASS_PROPERTY);

		_versionManager = new VersionManager();

		_applicationDownloadURLs = new LinkedList<URL>();

		for (String url : PropertyUtilities.getPropertyList(properties, APPLICATION_URL_PROPERTY_BASE)) {
			try {
				_applicationDownloadURLs.add(new URL(url));
			} catch (java.net.MalformedURLException ex) {
				_updateDisabled = true;
				break;
			}
		}

		Collection<File> certificateFiles = new LinkedList<File>();
		for (String certFile : PropertyUtilities.getPropertyList(properties, PATCH_SIGNER_CERTIFICATE_PROPERTY_BASE))
			certificateFiles.add(new File(certFile));
	}

	public ApplicationDescription(String applicationClassName, File propertiesFile) throws IOException, CertificateException
	{
		HierarchicalProperties properties = readPropertiesFile(propertiesFile);
		initialize(applicationClassName, properties);
	}

	public ApplicationDescription(String applicationClassName, String propertiesFileName) throws IOException,
		CertificateException
	{
		this(applicationClassName, new File(propertiesFileName));
	}

	public String getApplicationName()
	{
		return _applicationName;
	}

	public File getApplicationDirectory()
	{
		return _applicationDirectory;
	}

	public boolean updateDisabled()
	{
		return _updateDisabled;
	}

	public Collection<URL> getApplicationDownloadURLs()
	{
		return _applicationDownloadURLs;
	}

	public VersionManager getVersionManager()
	{
		return _versionManager;
	}

	public File getJarDescriptionFile()
	{
		return _jarDescriptionFile;
	}

	public String getApplicationClassName()
	{
		return _applicationClassName;
	}

	public boolean isUpdateRequest()
	{
		return _applicationClassName.equals(FAKE_UPDATER_CLASS_VALUE);
	}

	/**
	 * returns the location where the code is running, as best as can be determined. this uses the
	 * GENII_INSTALL_DIR if set, but it also can find the running location based on jar files. this
	 * cannot use the properties to look up the path, because this function needs to operate before
	 * anything else is loaded (for OSGi usage).
	 */
	static public String getInstallationDirectory()
	{
		String appPath = getEclipseTrunkFromEnvironment();
		if (appPath != null) {
			// so the variable is telling us where we live. we choose to believe it.
			if (_logger.isTraceEnabled())
				_logger.trace("found path in GENII_INSTALL_DIR: " + appPath);
		} else {
			// okay, that was a bust. see if we can intuit our location from living in a jar.
			URL url = ApplicationDescription.class.getProtectionDomain().getCodeSource().getLocation();
			try {
				appPath = new File(url.toURI().getSchemeSpecificPart()).toString();
			} catch (URISyntaxException e) {
				appPath = "";
				_logger.error("failed to convert code source url to app path: " + url);
			}
			if (_logger.isTraceEnabled())
				_logger.trace("got source path as: " + appPath);
			if (appPath.endsWith(".jar")) {
				// we need to chop off the jar file part of the name.
				int lastSlash = appPath.lastIndexOf("/");
				if (lastSlash < 0)
					lastSlash = appPath.lastIndexOf("\\");
				if (lastSlash < 0) {
					String msg = "could not find a slash character in the path: " + appPath;
					_logger.error(msg);
					throw new RuntimeException(msg);
				}
				appPath = appPath.substring(0, lastSlash);
				if (_logger.isTraceEnabled())
					_logger.trace("truncated path since inside jar: " + appPath);
			}
			appPath = appPath.concat("/..");
			if (_logger.isTraceEnabled())
				_logger.trace("jar-intuited startup bundle path: " + appPath);
		}
		appPath = appPath.replace('\\', '/');
		return appPath;
	}

	/*
	 * this provides a way for the osgi support to know we're running inside eclipse, which imposes
	 * a different structure on the locations of files.
	 */
	static public String getEclipseTrunkFromEnvironment()
	{
		return System.getenv(INSTALLATION_DIR_ENVIRONMENT_VARIABLE);
	}
}