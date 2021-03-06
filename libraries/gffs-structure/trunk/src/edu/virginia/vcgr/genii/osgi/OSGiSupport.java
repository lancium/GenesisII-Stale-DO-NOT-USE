package edu.virginia.vcgr.genii.osgi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import edu.virginia.vcgr.appmgr.launcher.ApplicationDescription;

/**
 * provides the OSGi bundle loading and startup for the application.
 * 
 * @author Chris Koeritz
 */
public class OSGiSupport
{
	static private Log _logger = LogFactory.getLog(OSGiSupport.class);

	// OSGi framework management.
	static private Framework _framework;
	static private String _bundleDir;

	/**
	 * provides a chewed form of the "originalPath" that will be a unique path based on that name and which will reside under the storageArea
	 * provided. The "tag" is used to uniquify the first portion of the name by purpose.
	 */
	static public File chopUpPath(String storageArea, File originalPath, String tag)
	{
		String username = System.getProperty("user.name");
		String justChewedPath = originalPath.getAbsolutePath().replaceAll("[/\\\\: ()]", "-");
		if (_logger.isTraceEnabled())
			_logger.debug("got a chopped path of: " + justChewedPath);
		String tmpDir = storageArea;
		tmpDir = tmpDir.replace('\\', '/');
		File newParent = new File(tmpDir + "/" + tag + "-" + username);
		if (_logger.isTraceEnabled())
			_logger.debug("got new parent location: " + newParent.getAbsolutePath());
		if (!newParent.exists()) {
			boolean parentOkay = newParent.mkdirs();
			if (!parentOkay) {
				throw new RuntimeException("parent directory could not be created for chopped path: '" + newParent.getAbsolutePath() + "'");
			}
		}
		File newPath = new File(newParent, justChewedPath);
		if (_logger.isTraceEnabled())
			_logger.debug("new fully chopped path: '" + newPath.getAbsolutePath() + "'");
		return newPath;
	}

	/**
	 * starts up the OSGi framework and loads the bundles required by our application.
	 * 
	 * @return true on success of loading bundles and startup
	 */
	static public Boolean setUpFramework()
	{
		/*
		 * we are trying to build both a user-specific and installation-specific storage area for the OSGi bundles at run-time. this folder
		 * can get "corrupted" if the installer has upgraded the installation, in that eclipse equinox OSGi won't load. this can be solved
		 * manually by cleaning out the storage area, but that's pretty crass. instead, we will try to clean it out once, and if that fails,
		 * then we really do need to fail.
		 */
		// String username = System.getProperty("user.name");
		String installDir = ApplicationDescription.getInstallationDirectory();
		File pathChow = new File(installDir);
		if (_logger.isTraceEnabled())
			_logger.trace("gotta path of: " + pathChow);

		File osgiStorageDir = chopUpPath(System.getProperty("java.io.tmpdir"), pathChow, "osgi-genII");

		/*
		 * // let's not forget ugly paths windows and others might hand us. String justDir = pathChow.getAbsolutePath().replaceAll("[/\\: ()]"
		 * , "-"); if (_logger.isTraceEnabled()) _logger.trace("gotta chopped path of: " + justDir); String tmpDir =
		 * System.getProperty("java.io.tmpdir"); tmpDir = tmpDir.replace('\\', '/'); File osgiStorageDir = new File(tmpDir + "/osgi-genII-" +
		 * username + "/" + justDir); if (_logger.isTraceEnabled()) _logger.trace("osgi storage area is: " +
		 * osgiStorageDir.getAbsolutePath());
		 */
		osgiStorageDir.mkdirs();

		// see if we're running under eclipse or know our installation directory.
		String bundleSourcePath = installDir + "/bundles";

		String saveDrive = ""; // only used for windows.
		if (bundleSourcePath.charAt(1) == ':') {
			// we have a dos path again, let's save the important bits.
			saveDrive = bundleSourcePath.substring(0, 2);
		} else if (bundleSourcePath.charAt(2) == ':') {
			// this is most likely a DOS path.
			if (bundleSourcePath.charAt(0) == '/') {
				bundleSourcePath = bundleSourcePath.substring(1);
				// keep track of the drive letter on windows.
				saveDrive = bundleSourcePath.substring(0, 2);
			}
		}

		if (_logger.isTraceEnabled())
			_logger.trace("after parsing, drive letter is '" + saveDrive + "' and path has become: " + bundleSourcePath);

		_bundleDir = bundleSourcePath;
		if (saveDrive.length() > 0) {
			// on windows we must make the case identical or eclipse has all sorts of problems from
			// mismatches.
			_bundleDir = _bundleDir.toLowerCase();
		}

		Map<String, String> config = new HashMap<String, String>();

		// Control where OSGi stores its persistent data:
		config.put(Constants.FRAMEWORK_STORAGE, osgiStorageDir.getAbsolutePath());

		// Request OSGi to clean its storage area on startup
		config.put(Constants.FRAMEWORK_STORAGE_CLEAN, "false");

		config.put("osgi.install.area", _bundleDir);
		if (_logger.isTraceEnabled())
			_logger.trace("using bundle source at: " + _bundleDir);

		/*
		 * could enable this if we want a remote console to manage OSGi: config.put("osgi.console", "4228");
		 */

		ArrayList<Bundle> loadedBundles = new ArrayList<Bundle>();

		BundleContext context = initializeFrameworkFactory(config, loadedBundles);
		if (context == null) {
			_logger.warn("first attempt to start OSGi failed; now retrying with a clean-up step.");
			try {
				FileUtils.deleteDirectory(osgiStorageDir);
			} catch (IOException e) {
				// if we can't clean up that directory, we can't fix this problem.
				_logger.error("failed to clean up the OSGi storage area: " + osgiStorageDir.getAbsolutePath());
				return false;
			}
			osgiStorageDir.mkdirs();
			loadedBundles.clear();
			context = initializeFrameworkFactory(config, loadedBundles);
			if (context != null) {
				_logger.info("recovered from ill OSGi storage area.  all is well.");
			}
		}
		if (context == null) {
			_logger.error("second attempt to start OSGi failed after cleanup; bailing out.");
			return false;
		}

		// now start all of the bundles.
		for (Bundle bun : loadedBundles) {
			if (_logger.isTraceEnabled())
				_logger.trace("starting bundle: " + bun.getSymbolicName());
			try {
				if (bun.getHeaders().get(Constants.FRAGMENT_HOST) == null) {
					bun.start();
				}
			} catch (Throwable e) {
				_logger.error("failed to start bundle: " + bun.getSymbolicName(), e);
				return false;
			}
		}

		return true;
	}

	/**
	 * a simple wrapper to try to get the framework running. if this fails, null is returned.
	 */
	static private BundleContext initializeFrameworkFactory(Map<String, String> config, ArrayList<Bundle> loadedBundles)
	{
		FrameworkFactory frameworkFactory = ServiceLoader.load(FrameworkFactory.class).iterator().next();
		_framework = frameworkFactory.newFramework(config);
		try {
			_framework.init();
			_framework.start();
		} catch (Throwable cause) {
			_logger.error("failed to load framework factory for OSGi", cause);
			shutDownFramework();
			return null;
		}

		BundleContext context = _framework.getBundleContext();
		// load all of our bundles.
		for (String bunName : OSGiConstants.genesis2ApplicationBundleList) {
			if (_logger.isTraceEnabled())
				_logger.trace("loading bundle: " + bunName);
			Bundle currentBundle = null;
			try {
				currentBundle = context.installBundle("file:" + _bundleDir + "/" + bunName);
			} catch (Throwable e) {
				_logger.error("failed to load bundle: " + bunName, e);
				shutDownFramework();
				return null;
			}
			loadedBundles.add(currentBundle);
		}

		return context;
	}

	/**
	 * stops the OSGi framework, which should be invoked if and only if program is shutting down.
	 */
	static public void shutDownFramework()
	{
		if (_framework != null) {
			try {
				_framework.getBundleContext().getBundle(0).stop();
				_framework.waitForStop(0);
			} catch (Throwable cause) {
				_logger.error("OSGi framework shutdown exception", cause);
			}
			_framework = null;
		}

	}

}
