package edu.virginia.vcgr.genii.container.resource;

import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.namespace.QName;

import org.morgan.util.configuration.XMLConfiguration;

import edu.virginia.vcgr.genii.client.GenesisIIConstants;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.container.Container;
import edu.virginia.vcgr.genii.container.configuration.ServiceDescription;

/**
 * This class is simply a repository for providers for given services.  Each
 * service will have associated with it it's own resource provider.  This allows
 * each one to have its own key translaters and resource types.
 * 
 * @author Mark Morgan (mmm2a@cs.virginia.edu)
 */
class ResourceProviders
{
	static private QName _SERVICES_QNAME = new QName(
		GenesisIIConstants.GENESISII_NS, "services");
	
	static private HashMap<String, IResourceProvider> _providerCache =
		new HashMap<String, IResourceProvider>();
	
	/**
	 * Retrieve the resource provider for a given service name.
	 * 
	 * @param serviceName The name of the service to retrieve resource providers
	 * for.
	 * @return The services resource provider.
	 * @throws ResourceException If anything goes wrong.
	 */
	@SuppressWarnings("unchecked")
	static IResourceProvider getProvider(String serviceName)
		throws ResourceException
	{
		IResourceProvider provider = null;
		
		synchronized(_providerCache)
		{
			provider = _providerCache.get(serviceName);
		}
		
		if (provider != null)
			return provider;
		
		try
		{
			ServiceDescription desc;
			XMLConfiguration conf = 
				Container.getConfigurationManager().getContainerConfiguration();
			ArrayList<Object> sections;
			sections = conf.retrieveSections(_SERVICES_QNAME);
			for (Object obj : sections)
			{
				HashMap<String, ServiceDescription> services =
					(HashMap<String, ServiceDescription>)obj;
				desc = services.get(serviceName);
				if (desc != null)
				{
					provider = desc.retrieveResourceProvider();
					synchronized(_providerCache)
					{
						_providerCache.put(serviceName, provider);
					}
					return provider;
				}
			}
			
			throw new ResourceException(
				"Unable to find factory for service \"" + serviceName + "\".");
		}
		catch (ResourceException re)
		{
			throw re;
		}
		catch (Exception e)
		{
			throw new ResourceException(e.getLocalizedMessage(), e);
		}
	}
}