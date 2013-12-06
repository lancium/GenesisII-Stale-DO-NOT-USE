package edu.virginia.vcgr.genii.client;

import javax.xml.namespace.QName;

public class GenesisIIConstants
{
	static public final String GENESISII_STATE_DIR_NAME = ".genesisII-2.0";

	static public final String GENESISII_NS = "http://vcgr.cs.virginia.edu/Genesis-II";

	static public final String UNICORE_NS = "http://www.unicore.eu/unicore6";

	static public final String OGSA_BP_NS = "http://schemas.ggc.org/ogsa/2006/05/wsrf-bp";

	static public final String NAMING_CLIENT_CONFORMANCE_PROPERTY = "IsWSNamingClient";

	/*
	 * a storage area in the calling context where the original TLS certificate can be remembered
	 * during container operations on behalf of the client.
	 */
	static public final String PASS_THROUGH_IDENTITY = "PassThroughIdentity";

	/*
	 * this holds onto the TLS certificate that the container saw from the client.
	 */
	static public final String LAST_TLS_CERT_FROM_CLIENT = "ClientsLastTLSCert";

	static public final String REGISTERED_TOPICS_ATTR = "registered-topic";
	static public QName REGISTERED_TOPICS_ATTR_QNAME = new QName(GENESISII_NS, REGISTERED_TOPICS_ATTR);

	static public QName AUTHZ_CONFIG_ATTR_QNAME =
		new QName("http://vcgr.cs.virginia.edu/genii/2008/12/security", "AuthZConfig");
	static public final String AUTHZ_CONFIG_ATTR = AUTHZ_CONFIG_ATTR_QNAME.getLocalPart();

	static public final String CACHE_COHERENCE_WINDOW_ATTR_NAME = "CacheCoherenceWindow";
	static public QName CACHE_COHERENCE_WINDOW_ATTR_QNAME = new QName(GENESISII_NS, CACHE_COHERENCE_WINDOW_ATTR_NAME);

	static public final String SCHED_TERM_TIME_PROPERTY_NAME = "scheduled-termintation-time";

	static public QName RESOURCE_PROPERTY_NAMES_QNAME = new QName(OGSA_BP_NS, "ResourcePropertyNames");

	static public QName GLOBAL_PROPERTY_SECTION_NAME = new QName(GenesisIIConstants.GENESISII_NS, "global-properties");

	static public QName CONTEXT_INFORMATION_QNAME = new QName(GenesisIIConstants.GENESISII_NS, "calling-context");

	static public final String OGSA_BSP_NS = "http://schemas.ggf.org/ogsa/2006/01/bsp-core";

	static public final String JSDL_NS = "http://schemas.ggf.org/jsdl/2005/11/jsdl";
	static public final String JSDL_POSIX_NS = "http://schemas.ggf.org/jsdl/2005/11/jsdl-posix";
	static public final String JSDL_HPC_NS = "http://schemas.ggf.org/jsdl/2006/07/jsdl-hpcp";

	static public final String BES_FACTORY_NS = "http://schemas.ggf.org/bes/2006/08/bes-factory";

	static public final String EXECUTION_ENGINE_THREAD_POOL_SIZE_PROPERTY =
		"edu.virginia.vcgr.genii.container.production.bes.thread-pool-size";

	static public final String AUTHZ_ENABLED_CONFIG_PROPERTY = "genii.security.authz.authz-enabled";

	static public final String BOOTSTRAP_OWNER_CERTPATH = "genii.security.authz.bootstrapOwnerCertPath";

	static public QName RNS_CACHED_METADATA_DOCUMENT_QNAME = new QName(GENESISII_NS, "rns-cached-metadata");

	static public final String GENESIS_DAIR_RESULTS = "dair-results";

	static final public String COMMAND_FUNCTION_NAME = "function";
	static final public QName COMMAND_FUNCTION_QNAME = new QName(WellKnownPortTypes.VCGR_COMMON_PORT_TYPE().getQName()
		.getNamespaceURI(), COMMAND_FUNCTION_NAME);

	static public QName NOTIFICATION_MESSAGE_ATTRIBUTES_SEPARATOR = new QName(GENESISII_NS,
		"notification-message-attributes-separator");

	static public final QName NOTIFICATION_BROKER_FACTORY_ADDRESS = new QName(GenesisIIConstants.GENESISII_NS,
		"NotificationBrokerFactory");

	static public final String ENHANCED_NOTIFICATION_BROKER_NS =
		"http://vcgr.cs.virginia.edu/container/2011/07/enhanced-notification-broker";

	static public final String CLIENT_ID_ATTRIBUTE_NAME = "ClientID";
	static public final String MYPROXY_ATTRIBUTE_NAME = "Proxy";

	static public final QName CLIENT_ID_QNAME = new QName(GENESISII_NS, CLIENT_ID_ATTRIBUTE_NAME);

	static public final String DELEGATED_SAML_ASSERTIONS_NAME = "delegated-saml-credentials";
	static public final QName DELEGATED_SAML_ASSERTIONS_QNAME = new QName(GENESISII_NS, DELEGATED_SAML_ASSERTIONS_NAME);

	static public final QName MYPROXY_QNAME = new QName(UNICORE_NS, MYPROXY_ATTRIBUTE_NAME);
	static public final String myproxyFilenameSuffix = "teragrid_x509.pem";
	
	static public final String RPC_ID_ATTRIBUTE_NAME = "RPC-ID";
	static public final QName RPC_ID_QNAME = new QName(GENESISII_NS,
			RPC_ID_ATTRIBUTE_NAME);

	// constant object for the security token ref qname.
	static public final QName WSSE11_NS_SECURITY_QNAME = new QName(org.apache.ws.security.WSConstants.WSSE11_NS,
		"SecurityTokenReference");

	// our old namespace for wss, which has been outdated by using newer wss4j library.
	static public final String INTERMEDIATE_WSE_NS =
		"http://docs.oasis-open.org/wss/2005/xx/oasis-2005xx-wss-wssecurity-secext-1.1.xsd";

	// constant object for the older version of the security token ref qname.
	static public final QName INTERMEDIATE_WSE_NS_SECURITY_QNAME = new QName(INTERMEDIATE_WSE_NS, "SecurityTokenReference");

	// default time for snoozes while waiting for a lock on a file.
	static public final int DEFAULT_FILE_LOCK = 40;

	static public final String CRYPTO_ALIAS = "CRYPTO_ALIAS";

	// all picture resources are expected to live in this path (within jars, generally).
	static public final String IMAGE_RELATIVE_LOCATION = "config/images/";
}
