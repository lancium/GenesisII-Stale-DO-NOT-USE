package edu.virginia.vcgr.genii.client.cache.unified;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.virginia.vcgr.genii.client.ClientProperties;
import edu.virginia.vcgr.genii.client.cache.unified.subscriptionmanagement.Subscriber;

/*
 * CacheConfigurer holds the configuration properties (e.g. timeout, size) of all elements of the entire client-side cache module. There is no
 * reference of any cache, or related classes elsewhere. Therefore, this is also the only place for enabling or disabling the client-side
 * cache or its component properties.
 */
public class CacheConfigurer
{
	static private Log _logger = LogFactory.getLog(CacheConfigurer.class);

	private static final String CACHE_TIMEOUT = "edu.virginia.vcgr.genii.client.cache.cache-timout";
	private static final String CACHE_ENABLED = "edu.virginia.vcgr.genii.client.cache.cache-enabled";
	private static final String SUBSCRIPTION_ENABLED = "edu.virginia.vcgr.genii.client.cache.subscription-enabled";
	private static final String MAXIMUM_POLLING_DELAY = "edu.virginia.vcgr.genii.client.cache.maximum-polling-delay";
	private static final String BYTE_IO_ATTRS_COUNT = "edu.virginia.vcgr.genii.client.cache.size.byteIO-attributes-cache";
	private static final String PERMISSION_ATTR_COUNT = "edu.virginia.vcgr.genii.client.cache.size.permission-attribute-cache";
	private static final String DIRECTORY_SIZE_ATTR_COUNT = "edu.virginia.vcgr.genii.client.cache.size.directory-size-attribute-cache";
	private static final String EPR_COUNT = "edu.virginia.vcgr.genii.client.cache.size.epr-cache";
	private static final String FUSE_DIR_COUNT = "edu.virginia.vcgr.genii.client.cache.size.fuse-directory-cache";
	private static final String RESOURCE_CONFIG_COUNT = "edu.virginia.vcgr.genii.client.cache.size.resource-config-cache";
	private static final String RESOURCE_CONFIG_TIMOUT = "edu.virginia.vcgr.genii.client.cache.resource-config-cache-timeout";

	public static long DEFAULT_CACHE_TIMOUT_TIME = 30 * 1000L; // 30 seconds
	/*
	 * We keep old subscribed contents in the cache even if no polling is done for 4 minutes. This attribute is used with subscription based
	 * caching and when polling is used to make cached contents temporarily unaccessible if current polling interval is too long. The system
	 * changes from slow to rapid polling mode as soon as any activity from the client is detected.
	 */
	public static long DEFAULT_VALIDITY_PERIOD_FOR_CACHED_CONTENT = 30 * 1000L;
	// Cache sizes increased by grimshaw 1/28/2016
	public static int DEFAULT_ATTRIBUTE_CACHE_SIZE = 20000;
	public static int DEFAULT_EPR_CACHE_SIZE = 4096;
	// caching disabled by default, to avoid UNICORE code trying to cache. it will be enabled based on configuration files below.
	private static boolean CACHING_ENABLED = false;
	private static boolean SUBSCRIPTION_BASED_CACHING_ENABLED = false;

	private static List<CommonCache> LIST_OF_CACHES = new ArrayList<CommonCache>();

	// record when we show the cache stats and don't show them again in same run.
	private static boolean advertisedConfig = false;

	public static void initializeCaches()
	{
		Properties properties = ClientProperties.getClientProperties().getClientCacheProperties();

		String cacheEnabled = properties.getProperty(CACHE_ENABLED);
		if (cacheEnabled != null)
			CACHING_ENABLED = Boolean.parseBoolean(cacheEnabled);
		String subscriptionEnabled = properties.getProperty(SUBSCRIPTION_ENABLED);
		if (subscriptionEnabled != null)
			SUBSCRIPTION_BASED_CACHING_ENABLED = Boolean.parseBoolean(subscriptionEnabled);
		String cacheTimeout = properties.getProperty(CACHE_TIMEOUT);
		if (cacheTimeout != null)
			DEFAULT_CACHE_TIMOUT_TIME = Integer.parseInt(cacheTimeout) * 1000L;
		String validityPeriod = properties.getProperty(MAXIMUM_POLLING_DELAY);
		if (validityPeriod != null)
			DEFAULT_VALIDITY_PERIOD_FOR_CACHED_CONTENT = Integer.parseInt(validityPeriod) * 1000L;

		if (!advertisedConfig) {
			advertisedConfig = true;
			_logger.debug("Caching enabled: " + Boolean.toString(CACHING_ENABLED) + ", Subscriptions enabled: "
				+ Boolean.toString(SUBSCRIPTION_BASED_CACHING_ENABLED) + ", Cache timeout: " + DEFAULT_CACHE_TIMOUT_TIME + "ms"
				+ ", Max polling delay: " + DEFAULT_VALIDITY_PERIOD_FOR_CACHED_CONTENT + "ms ");
		}

		if (CACHING_ENABLED) {
			// setting the attribute caches
			String countStr = properties.getProperty(BYTE_IO_ATTRS_COUNT);
			int entryCount = (countStr != null) ? Integer.parseInt(countStr) : DEFAULT_ATTRIBUTE_CACHE_SIZE;
			LIST_OF_CACHES.add(new ByteIORPCache(0, entryCount, DEFAULT_CACHE_TIMOUT_TIME, false));
			countStr = properties.getProperty(PERMISSION_ATTR_COUNT);
			entryCount = (countStr != null) ? Integer.parseInt(countStr) : DEFAULT_ATTRIBUTE_CACHE_SIZE;
			LIST_OF_CACHES.add(new AuthZConfigCache(0, entryCount, DEFAULT_CACHE_TIMOUT_TIME, false));
			countStr = properties.getProperty(DIRECTORY_SIZE_ATTR_COUNT);
			entryCount = (countStr != null) ? Integer.parseInt(countStr) : DEFAULT_ATTRIBUTE_CACHE_SIZE;
			LIST_OF_CACHES.add(new RNSElementCountCache(0, entryCount, DEFAULT_CACHE_TIMOUT_TIME, false));

			// setting the FUSE directory cache
			countStr = properties.getProperty(FUSE_DIR_COUNT);
			entryCount = (countStr != null) ? Integer.parseInt(countStr) : DEFAULT_EPR_CACHE_SIZE;
			LIST_OF_CACHES.add(new FuseDirCache(0, entryCount, DEFAULT_CACHE_TIMOUT_TIME, false));

			// setting the EPR cache
			// Monitoring is enabled for only RNSLookup and ResourceConfig caches as these two
			// are sufficient to collect per-container usage statistics.
			countStr = properties.getProperty(EPR_COUNT);
			entryCount = (countStr != null) ? Integer.parseInt(countStr) : DEFAULT_EPR_CACHE_SIZE;
			LIST_OF_CACHES.add(new RNSLookupCache(0, entryCount, DEFAULT_CACHE_TIMOUT_TIME, true));

			// Identifiers linker, we need to keep info in the cache as long as possible and
			// as much as possible too. Furthermore, as this cache gets used the most therefore
			// it may be better to always use memory-based cache implementation for this cache
			// even if we change the implementation of other caches in future.
			String timeoutStr = properties.getProperty(RESOURCE_CONFIG_TIMOUT);
			long configCacheTimeout = 15 * 60 * 1000L;
			if (timeoutStr != null)
				configCacheTimeout = Integer.parseInt(timeoutStr) * 60 * 1000L;
			countStr = properties.getProperty(RESOURCE_CONFIG_COUNT);
			entryCount = (countStr != null) ? Integer.parseInt(countStr) : DEFAULT_ATTRIBUTE_CACHE_SIZE;
			LIST_OF_CACHES.add(new ResourceConfigCache(0, entryCount, configCacheTimeout, true));
		}

		Collections.sort(LIST_OF_CACHES);
	}

	private static Collection<CacheableItemsGenerator> CACHEABLE_ITEMS_GENERATORS;

	private static void initializeGenerators()
	{
		if (CACHEABLE_ITEMS_GENERATORS == null) {
			CACHEABLE_ITEMS_GENERATORS = new ArrayList<CacheableItemsGenerator>();
		}
		CACHEABLE_ITEMS_GENERATORS.add(new RNSEntryResponseTranslator());
	}

	static {
		initializeGenerators();
	}

	public static boolean isCachingEnabled()
	{
		return CACHING_ENABLED;
	}

	public static boolean isSubscriptionEnabled()
	{
		return (CACHING_ENABLED && SUBSCRIPTION_BASED_CACHING_ENABLED);
	}

	public static void disableCaching()
	{
		CACHING_ENABLED = false;
		SUBSCRIPTION_BASED_CACHING_ENABLED = false;
		LIST_OF_CACHES.clear();
		CACHEABLE_ITEMS_GENERATORS.clear();
		if (_logger.isDebugEnabled())
			_logger.debug("Caching has been disabled");
	}

	public static void disableSubscriptionBasedCaching()
	{
		SUBSCRIPTION_BASED_CACHING_ENABLED = false;
		_logger.info("Subscription based caching has been disabled");
	}

	public static void setSubscriptionBasedCaching(boolean state)
	{
		SUBSCRIPTION_BASED_CACHING_ENABLED = state;
	}

	public static void enableCaching()
	{
		CACHING_ENABLED = true;
		SUBSCRIPTION_BASED_CACHING_ENABLED = true;
		initializeCaches();
		initializeGenerators();
		if (_logger.isDebugEnabled()) {
			if (CACHING_ENABLED)
				_logger.debug("Caching has been enabled");
			else
				_logger.debug("Caching is still disabled");
		}
	}

	public static Collection<CommonCache> getCaches()
	{
		return Collections.unmodifiableCollection(LIST_OF_CACHES);
	}

	public static Collection<CacheableItemsGenerator> getGenerators()
	{
		return Collections.unmodifiableCollection(CACHEABLE_ITEMS_GENERATORS);
	}

	public static void resetCaches()
	{
		try {
			boolean subscriptionStatus = isSubscriptionEnabled();
			disableCaching();
			if (subscriptionStatus) {
				Subscriber.resetCallingContext();
			}
			enableCaching();
			setSubscriptionBasedCaching(subscriptionStatus);
		} catch (Exception ex) {
			_logger.error("Exception occurred while resetting the cache management system " + ex.getMessage());
		}
	}
}
