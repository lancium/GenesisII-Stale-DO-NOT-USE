package edu.virginia.vcgr.genii.container;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import org.apache.axis.AxisFault;
import org.apache.axis.ConfigurationException;
import org.apache.axis.EngineConfiguration;
import org.apache.axis.MessageContext;
import org.apache.axis.SimpleChain;
import org.apache.axis.deployment.wsdd.WSDDProvider;
import org.apache.axis.description.JavaServiceDesc;
import org.apache.axis.handlers.soap.SOAPService;
import org.apache.axis.server.AxisServer;
import org.apache.axis.transport.http.AxisServletBase;
import org.apache.axis.types.URI;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.feistymeow.process.ethread;
import org.morgan.dpage.DynamicPageLoader;
import org.morgan.dpage.ScratchSpaceManager;
import org.morgan.util.GUID;
import org.morgan.util.configuration.XMLConfiguration;
import org.morgan.util.io.GuaranteedDirectory;
import org.ws.addressing.AttributedURIType;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.algorithm.structures.queue.BarrieredWorkQueue;
import edu.virginia.vcgr.genii.algorithm.structures.queue.IServiceWithCleanupHook;
import edu.virginia.vcgr.genii.client.ApplicationBase;
import edu.virginia.vcgr.genii.client.cache.unified.CacheConfigurer;
import edu.virginia.vcgr.genii.client.comm.axis.security.VcgrSslSocketFactory;
import edu.virginia.vcgr.genii.client.comm.jetty.TrustAllSslContextFactory;
import edu.virginia.vcgr.genii.client.configuration.ConfigurationManager;
import edu.virginia.vcgr.genii.client.configuration.ConfiguredHostname;
import edu.virginia.vcgr.genii.client.configuration.ContainerConfiguration;
import edu.virginia.vcgr.genii.client.configuration.DeploymentName;
import edu.virginia.vcgr.genii.client.configuration.HierarchicalDirectory;
import edu.virginia.vcgr.genii.client.configuration.Installation;
import edu.virginia.vcgr.genii.client.configuration.KeystoreSecurityConstants;
import edu.virginia.vcgr.genii.client.configuration.Security;
import edu.virginia.vcgr.genii.client.configuration.SslInformation;
import edu.virginia.vcgr.genii.client.container.ContainerIDFile;
import edu.virginia.vcgr.genii.client.install.InstallationState;
import edu.virginia.vcgr.genii.client.mem.LowMemoryExitHandler;
import edu.virginia.vcgr.genii.client.mem.LowMemoryWarning;
import edu.virginia.vcgr.genii.client.naming.EPRUtils;
import edu.virginia.vcgr.genii.client.security.KeystoreManager;
import edu.virginia.vcgr.genii.client.security.TrustStoreLinkage;
import edu.virginia.vcgr.genii.client.utils.flock.FileLockException;
import edu.virginia.vcgr.genii.container.alarms.AlarmManager;
import edu.virginia.vcgr.genii.container.axis.ServerWSDoAllReceiver;
import edu.virginia.vcgr.genii.container.axis.ServerWSDoAllSender;
import edu.virginia.vcgr.genii.container.cservices.ContainerServices;
import edu.virginia.vcgr.genii.container.deployment.ServiceDeployer;
import edu.virginia.vcgr.genii.container.invoker.GAroundInvokerFactory;
import edu.virginia.vcgr.genii.security.CertificateValidatorFactory;
import edu.virginia.vcgr.genii.security.x509.CertTool;
import edu.virginia.vcgr.genii.security.x509.KeyAndCertMaterial;

public class Container extends ApplicationBase
{
	static private Log _logger = LogFactory.getLog(Container.class);

	static private AxisServer _axisServer = null;
	static private ContainerConfiguration _containerConfiguration;

	static private String _containerURL;

	static private X509Certificate[] _containerCertChain;
	static private PrivateKey _containerPrivateKey;

	static private CrlInvigoratorThread _crlInvigorator = null;

	/*
	 * controls whether the container is allowed to cache things and whether it's allowed to subscribe to notifications.
	 */
	static private boolean allowContainersToCache = true;
	static private boolean allowContainersToSubscribe = true;

	// Default to 1 year certificate lifespan.
	static private long _defaultCertificateLifetime = 1000L * 60L * 60L * 24L * 365L;

	static public void usage()
	{
		System.out.println("Container [deployment-name]");
	}

	static private BarrieredWorkQueue _postStartupWorkQueue = new BarrieredWorkQueue();

	static public void main(String[] args)
	{
		if (args.length > 1) {
			usage();
			System.exit(1);
		}
		if (args.length == 1)
			System.setProperty(DeploymentName.DEPLOYMENT_NAME_PROPERTY, args[0]);

		try {
			prepareServerApplication();

			VcgrSslSocketFactory.setupConnectionPool();

			// Set Trust Store Providers.
			java.security.Security.setProperty("ssl.SocketFactory.provider", VcgrSslSocketFactory.class.getName());

			LowMemoryWarning.INSTANCE.addLowMemoryListener(new LowMemoryExitHandler(7));

			_logger.info(String.format("Deployment name is '%s'", new DeploymentName()));

			WSDDProvider.registerProvider(GAroundInvokerFactory.PROVIDER_QNAME, new GAroundInvokerFactory());

			runContainer();

			ContainerIDFile.containerID(getContainerID());

			_logger.info("Container Started");
			AlarmManager.initializeAlarmManager();

			/*
			 * add a periodic drop of the TLS trust store and CRL records, to accommodate updated grid-certificates folder.
			 */
			_crlInvigorator = new CrlInvigoratorThread();
			_crlInvigorator.start();

			_postStartupWorkQueue.release();
		} catch (Throwable t) {
			_logger.error("exception occurred in main", t);
			System.exit(1);
		}

		// this is not the end; processes will keep going even after this function exits.
	}

	static public ConfigurationManager getConfigurationManager()
	{
		return ConfigurationManager.getCurrentConfiguration();
	}

	static public ContainerConfiguration getContainerConfiguration()
	{
		return _containerConfiguration;
	}

	static private org.apache.axis.Handler getHandler(SimpleChain handlerChain, Class<?> handlerClass)
	{

		if (handlerChain == null) {
			return null;
		}
		for (org.apache.axis.Handler h : handlerChain.getHandlers()) {
			if (h instanceof SimpleChain) {
				org.apache.axis.Handler result = getHandler((SimpleChain) h, handlerClass);
				if (result != null) {
					return result;
				}
			} else if (h.getClass().equals(handlerClass)) {
				return h;
			}
		}
		return null;
	}

	static private void runContainer() throws ConfigurationException, IOException, Exception
	{
		WebAppContext webAppCtxt;
		Server server;

		SelectChannelConnector connector;

		initializeIdentitySecurity(getConfigurationManager().getContainerConfiguration());

		// set our container key to allow for recovery from older serialization.
		TrustStoreLinkage tsl = (TrustStoreLinkage) CertificateValidatorFactory.getValidator().getTrustStoreProvider();
		tsl.setContainerKey(new KeyAndCertMaterial(Container.getContainerCertChain(), Container.getContainerPrivateKey()));

		_containerConfiguration = new ContainerConfiguration(getConfigurationManager());
		ContainerConfiguration.setTheContainerConfig(_containerConfiguration);

		server = new Server();

		if (_containerConfiguration.isSSL()) {
			// Determine if we should accept self signed certificates
			if (_containerConfiguration.trustSelfSigned()) {
				SslInformation sslinfo = _containerConfiguration.getSslInformation();
				SslContextFactory fac = new TrustAllSslContextFactory(sslinfo.getKeystoreFilename(), sslinfo.getKeyPassword(),
					sslinfo.getKeystorePassword(), sslinfo.getKeystoreType());
				connector = new SslSelectChannelConnector(fac);
				_logger.info("Note: accepting connections from self signed certificates");

			} else {
				connector = new SslSelectChannelConnector();
			}
			connector.setPort(_containerConfiguration.getListenPort());
			_containerURL =
				edu.virginia.vcgr.appmgr.net.Hostname.normalizeURL("https://127.0.0.1:" + _containerConfiguration.getListenPort());
		} else {
			connector = new SelectChannelConnector();
			connector.setPort(_containerConfiguration.getListenPort());
			_containerURL = edu.virginia.vcgr.appmgr.net.Hostname.normalizeURL("http://127.0.0.1:" + _containerConfiguration.getListenPort());
		}

		int maxAcceptors = _containerConfiguration.getMaxAcceptorThreads();
		if (maxAcceptors > 2 * Runtime.getRuntime().availableProcessors()) {
			// limit the number of acceptors as expected by jetty.
			maxAcceptors = 2 * Runtime.getRuntime().availableProcessors();
			if (_logger.isDebugEnabled())
				_logger.debug("reducing number of acceptors from " + _containerConfiguration.getMaxAcceptorThreads() + " to " + maxAcceptors
					+ " to meet jetty requirements (<=2*cpus)");
		}
		if (_logger.isDebugEnabled())
			_logger.debug(String.format("Setting max acceptor threads to %d\n", maxAcceptors));
		connector.setAcceptors(maxAcceptors);

		server.setConnectors(new Connector[] { connector });

		ContextHandler context1 = new ContextHandler();
		context1.setContextPath("/axis");
		webAppCtxt = new WebAppContext(Installation.axisWebApplicationPath().getAbsolutePath(), "/");
		context1.setHandler(webAppCtxt);

		Server dServer = loadDynamicPages(_containerConfiguration.getDPagesPort());

		// hmmm: TOP-LEVEL HTTP SETUP IS probably NOT RIGHT YET.
		ContextHandler context2 = new ContextHandler();
		context2.setContextPath("/");
		// doesn't work either yet: context2.addServlet(new ServletHolder(new ResourceFileHandler("edu/virginia/vcgr/genii/container")),
		// "/*");
		context2.setHandler(new ResourceFileHandler("edu/virginia/vcgr/genii/container"));

		HandlerCollection handlers = new HandlerCollection();
		handlers.setHandlers(new ContextHandler[] { context1, context2 });
		server.setHandler(handlers);

		// ContextHandlerCollection contexts = new ContextHandlerCollection();
		// contexts.setHandlers(new ContextHandler[] { context1, context2 });
		// server.setHandler(contexts);

		try {
			recordInstallationState(System.getProperty(DeploymentName.DEPLOYMENT_NAME_PROPERTY, "default"), new URL(_containerURL));
		} catch (Throwable cause) {
			_logger.error("Unable to record installation state -- continuing anyways.", cause);
		}

		server.start();
		if (dServer != null)
			dServer.start();

		_logger.info(String.format("Container ID:  %s", getContainerID()));
		_logger.info("Starting container services.");
		ContainerServices.loadAll();
		ContainerServices.startAll();

		Collection<Class<? extends IServiceWithCleanupHook>> containerServices = initializeServices(webAppCtxt);

		Collection<IServiceWithCleanupHook> containerServiceObjects = new ArrayList<IServiceWithCleanupHook>(containerServices.size());
		for (Class<? extends IServiceWithCleanupHook> service : containerServices) {
			try {
				Constructor<?> cons = service.getConstructor(new Class[0]);
				IServiceWithCleanupHook base = (IServiceWithCleanupHook) cons.newInstance(new Object[0]);
				containerServiceObjects.add(base);

				try {
					base.cleanupHook();
				} catch (Throwable cause) {
					_logger.warn(String.format("Unable to run clean up hook on %s.", service), cause);
				}

				containerServiceObjects.add(base);
			} catch (Throwable cause) {
				_logger.warn(String.format("Unable to configure service:  %s.", service), cause);
			}
		}

		/*
		 * if caching is to be disabled for containers, this is where it's done. we've had some good success with allowing full caching for
		 * the container though.
		 */
		if (!allowContainersToSubscribe)
			CacheConfigurer.disableSubscriptionBasedCaching();
		if (!allowContainersToCache)
			CacheConfigurer.disableCaching();

		for (IServiceWithCleanupHook service : containerServiceObjects) {
			try {
				service.startup();
				_postStartupWorkQueue.enqueue(service);
			} catch (Throwable cause) {
				_logger.warn(String.format("Unable to configure service:  %s.", service), cause);
			}
		}

		ServiceDeployer.startServiceDeployer(_axisServer, _postStartupWorkQueue,
			Installation.getDeployment(new DeploymentName()).getServicesDirectory());

		ServerWSDoAllReceiver.beginNormalRuntime();
	}

	@SuppressWarnings("unchecked")
	static private Collection<Class<? extends IServiceWithCleanupHook>> initializeServices(WebAppContext ctxt)
		throws ServletException, AxisFault
	{
		Collection<Class<? extends IServiceWithCleanupHook>> managedServiceClasses =
			new LinkedList<Class<? extends IServiceWithCleanupHook>>();

		ServletHandler han = ctxt.getServletHandler();
		ServletHolder[] holders = han.getServlets();
		for (ServletHolder holder : holders) {
			if (holder.getName().equals("AxisServlet")) {
				_axisServer = ((AxisServletBase) holder.getServlet()).getEngine();
			}
		}

		if (_axisServer == null)
			throw new AxisFault("Internal error trying to start container.");

		_axisServer.setShouldSaveConfig(false);

		try {
			EngineConfiguration config = _axisServer.getConfig();

			// configure the listening request security handler
			ServerWSDoAllReceiver receiver =
				(ServerWSDoAllReceiver) getHandler((SimpleChain) config.getGlobalRequest(), ServerWSDoAllReceiver.class);
			receiver.configure(_containerPrivateKey);

			// configure listening response security handler
			ServerWSDoAllSender sender =
				(ServerWSDoAllSender) getHandler((SimpleChain) config.getGlobalResponse(), ServerWSDoAllSender.class);
			sender.configure(_containerPrivateKey);

			// configure the services individually
			Iterator<?> iter = _axisServer.getConfig().getDeployedServices();
			while (iter.hasNext()) {
				Object obj = iter.next();
				if (obj instanceof JavaServiceDesc) {
					Class<?> implClass = ((JavaServiceDesc) obj).getImplClass();
					if (IServiceWithCleanupHook.class.isAssignableFrom(implClass))
						managedServiceClasses.add((Class<? extends IServiceWithCleanupHook>) implClass);
				}
			}

			return managedServiceClasses;
		} catch (ConfigurationException e) {
			throw new AxisFault("Unable to configure deployment services.", e);
		}
	}

	static private void initializeIdentitySecurity(XMLConfiguration serverConf)
		throws ConfigurationException, KeyStoreException, GeneralSecurityException, IOException
	{
		Security resourceIdSecProps = Installation.getDeployment(new DeploymentName()).security();
		String keyStoreLoc = resourceIdSecProps.getSigningKeystoreFile();
		if (keyStoreLoc == null) {
			String msg = "Key Store Location not specified for message security.";
			_logger.error(msg);
			throw new ConfigurationException(msg);
		}
		File keystoreLocPath = new File(keyStoreLoc);

		// the rest come from deployment still...
		String keyStoreType =
			resourceIdSecProps.getProperty(KeystoreSecurityConstants.Container.RESOURCE_IDENTITY_KEY_STORE_TYPE_PROP, "PKCS12");
		String keyPassword = resourceIdSecProps.getProperty(KeystoreSecurityConstants.Container.RESOURCE_IDENTITY_KEY_PASSWORD_PROP);
		String keyStorePassword =
			resourceIdSecProps.getProperty(KeystoreSecurityConstants.Container.RESOURCE_IDENTITY_KEY_STORE_PASSWORD_PROP);
		String containerAlias = resourceIdSecProps.getProperty(KeystoreSecurityConstants.Container.RESOURCE_IDENTITY_CONTAINER_ALIAS_PROP);

		String certificateLifetime =
			resourceIdSecProps.getProperty(KeystoreSecurityConstants.Container.RESOURCE_IDENTITY_DEFAULT_CERT_LIFETIME_PROP);
		if (certificateLifetime != null)
			_defaultCertificateLifetime = Long.parseLong(certificateLifetime);

		// open the keystore.
		char[] keyStorePassChars = null;
		if (keyStorePassword != null)
			keyStorePassChars = keyStorePassword.toCharArray();
		char[] keyPassChars = null;
		if (keyPassword != null)
			keyPassChars = keyPassword.toCharArray();

		KeyStore ks = CertTool.openStoreDirectPath(keystoreLocPath, keyStoreType, keyStorePassChars);
		// load the container private key and certificate
		_containerPrivateKey = (PrivateKey) ks.getKey(containerAlias, keyPassChars);

		Certificate[] chain = ks.getCertificateChain(containerAlias);
		_containerCertChain = new X509Certificate[chain.length];
		for (int i = 0; i < chain.length; i++)
			_containerCertChain[i] = (X509Certificate) chain[i];

	}

	static public JavaServiceDesc findService(EndpointReferenceType epr) throws AxisFault
	{
		return findService(epr.getAddress());
	}

	static public JavaServiceDesc findService(AttributedURIType uri) throws AxisFault
	{
		return findService(uri.get_value());
	}

	static public JavaServiceDesc findService(URI uri) throws AxisFault
	{
		return findService(uri.getPath());
	}

	static public JavaServiceDesc findService(java.net.URI uri) throws AxisFault
	{
		return findService(uri.getPath());
	}

	static public JavaServiceDesc findService(String pathOrName) throws AxisFault
	{
		int index = pathOrName.lastIndexOf('/');
		if (index >= 0)
			pathOrName = pathOrName.substring(index + 1);

		SOAPService ss = _axisServer.getService(pathOrName);
		if (ss == null)
			return null;

		return (JavaServiceDesc) ss.getServiceDescription();
	}

	static public Class<?> classForService(String serviceName)
	{
		try {
			JavaServiceDesc desc = findService(serviceName);
			if (desc != null)
				return desc.getImplClass();
		} catch (AxisFault fault) {
			_logger.error(String.format("Error find service description for service %s.", serviceName), fault);
		}

		return null;
	}

	static public ArrayList<JavaServiceDesc> getInstalledServices()
	{
		ArrayList<JavaServiceDesc> installedServices = new ArrayList<JavaServiceDesc>();

		Iterator<?> iter = null;
		try {
			iter = _axisServer.getConfig().getDeployedServices();
			while (iter.hasNext()) {
				Object obj = iter.next();
				if (obj instanceof JavaServiceDesc)
					installedServices.add((JavaServiceDesc) obj);
			}
		} catch (org.apache.axis.ConfigurationException ce) {
			_logger.info(ce.getLocalizedMessage(), ce);
		}

		return installedServices;
	}

	static final private Pattern SERVICE_NAME_FROM_URL = Pattern.compile("^https?://[^/]*/axis/services/(\\w+).*$", Pattern.CASE_INSENSITIVE);

	static public Class<?> getClassForServiceURL(String serviceURL)
	{
		Class<?> ret = null;

		if (_logger.isDebugEnabled())
			_logger.debug(String.format("Asked to get class for service URL \"%s\".", serviceURL));

		Matcher matcher = SERVICE_NAME_FROM_URL.matcher(serviceURL);
		if (!matcher.matches()) {
			_logger.warn(String.format("Unable to get service name from URL \"%s\".", serviceURL));
			return null;
		}

		String serviceName = matcher.group(1);
		for (JavaServiceDesc desc : getInstalledServices()) {
			if (serviceName.equalsIgnoreCase(desc.getName())) {
				ret = desc.getImplClass();
				break;
			}
		}

		if (_logger.isDebugEnabled())
			_logger.debug(String.format("Class for %s is %s.", serviceURL, ret.getName()));
		return ret;
	}

	static public String getServiceURL(String serviceName)
	{
		MessageContext ctxt = MessageContext.getCurrentContext();
		if (ctxt != null) {
			String currentURL = getCurrentServiceURL(ctxt);
			int index = currentURL.lastIndexOf('/');
			if (index > 0)
				return currentURL.substring(0, index + 1) + serviceName + "?" + EPRUtils.GENII_CONTAINER_ID_PARAMETER + "="
					+ Container.getContainerID();
		}

		return _containerURL + "/axis/services/" + serviceName + "?" + EPRUtils.GENII_CONTAINER_ID_PARAMETER + "="
			+ Container.getContainerID();
	}

	static public boolean onThisServer(EndpointReferenceType target)
	{
		String urlString = target.getAddress().toString();
		String containerURL = _containerURL + "/axis/services/";

		if (urlString.startsWith(containerURL))
			return true;

		return false;
	}

	static public String getCurrentServiceURL(MessageContext ctxt)
	{
		try {
			URL url = new URL(ctxt.getProperty(MessageContext.TRANS_URL).toString());
			URL result = new URL(url.getProtocol(), ConfiguredHostname.lookupHost(null).toString(), url.getPort(), url.getFile());
			return result.toString();
		} catch (MalformedURLException mue) {
			// Can't happen
			_logger.fatal("This shouldn't have happend:  " + mue);
			throw new RuntimeException(mue);
		}
	}

	static public X509Certificate[] getContainerCertChain()
	{
		return _containerCertChain;
	}

	static public PrivateKey getContainerPrivateKey()
	{
		return _containerPrivateKey;
	}

	static public long getDefaultCertificateLifetime()
	{
		return _defaultCertificateLifetime;
	}

	static private GUID _containerID = null;
	static private Object _containerIDLock = new Object();

	static public GUID getContainerID()
	{
		synchronized (_containerIDLock) {
			if (_containerID == null) {
				PersistentContainerProperties properties = PersistentContainerProperties.getProperties();
				try {
					_containerID = (GUID) properties.getProperty("container-id");
					if (_containerID == null) {
						_containerID = new GUID();
						_logger.info("created new container ID for this container: " + _containerID);
					}
					properties.setProperty("container-id", _containerID);
				} catch (Throwable cause) {
					throw new org.morgan.util.configuration.ConfigurationException("Unable to get/set container id.", cause);
				}
			}

			return _containerID;
		}
	}

	static private void recordInstallationState(String deploymentName, URL containerURL) throws IOException, FileLockException
	{
		Thread th = new Thread(new InstallationStateEraser(deploymentName));
		th.setDaemon(false);
		th.setName("Installation Eraser Thread");
		Runtime.getRuntime().addShutdownHook(th);
		InstallationState.addRunningContainer(deploymentName, containerURL);
	}

	static private class InstallationStateEraser implements Runnable
	{
		private String _deploymentName;

		public InstallationStateEraser(String deploymentName)
		{
			_deploymentName = deploymentName;
		}

		@Override
		public void run()
		{
			try {
				InstallationState.removeRunningContainer(_deploymentName);
			} catch (Throwable cause) {
				_logger.fatal("Unable to remove container state.", cause);
			}
		}
	}

	static private Server loadDynamicPages(Integer dPagesPort)
	{
		Server server = null;

		if (dPagesPort != null) {
			server = new Server();

			SocketConnector socketConnector = new SocketConnector();
			socketConnector.setPort(dPagesPort.intValue());
			server.addConnector(socketConnector);

			loadDynamicPages(server);
		}

		return server;
	}

	static private void loadDynamicPages(Server server)
	{
		HierarchicalDirectory dynPagesDir = Installation.getDeployment(new DeploymentName()).getDynamicPagesDirectory();
		if (!dynPagesDir.exists())
			return;

		try {
			File scratchSpaceDirectory =
				new GuaranteedDirectory(ConfigurationManager.getCurrentConfiguration().getUserDirectory(), "dynamic-pages-scratch");

			ScratchSpaceManager scratchManager = new ScratchSpaceManager(scratchSpaceDirectory);
			for (File entry : dynPagesDir.listFiles()) {
				try {
					if (entry.getName().endsWith(".dar")) {
						if (server == null) {
							_logger.warn("Attempt to load dynamic page without the dpage-port specified.");
							return;
						}

						DynamicPageLoader.addDynamicPages(server, scratchManager, entry);
					}
				} catch (Throwable cause) {
					_logger.warn(String.format("Unable to load dynamic page package \"%s\".", entry.getName()), cause);
				}
			}
		} catch (Throwable cause) {
			_logger.warn("Unable to load dynamic pages.", cause);
		}
	}

	private static class CrlInvigoratorThread extends ethread
	{
		// how frequently we reload the trust stores and CRLs. currently every 6 hours.
		static long SNOOZE_TIME_BETWEEN_TRUST_STORE_RELOAD = new Long(6 * 60 * 60 * 1000);

		CrlInvigoratorThread()
		{
			super(SNOOZE_TIME_BETWEEN_TRUST_STORE_RELOAD);
		}

		@Override
		public boolean performActivity()
		{
			_logger.info("periodic reload of trust stores and CRLs occurring now.");
			KeystoreManager.dropTrustStores();
			return true;
		}

	}
}
