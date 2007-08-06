/*
 * Copyright 2006 University of Virginia
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package edu.virginia.vcgr.genii.container.configuration;

import java.util.HashMap;
import java.util.Properties;

import javax.xml.namespace.QName;

import org.morgan.util.configuration.ConfigurationException;
import org.morgan.util.configuration.IXMLConfigurationSectionHandler;
import org.morgan.util.configuration.PropertiesConfigurationSectionHandler;
import org.morgan.util.configuration.XMLConfiguration;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.virginia.vcgr.genii.client.GenesisIIConstants;

public class ServicesConfigurationHandler implements
		IXMLConfigurationSectionHandler
{
	static final public String SERVICE_NAME = "service";
	static final public String SERVICE_NAME_NAME = "name";
	static final public String RESOURCE_PROVIDER_NAME = "resource-provider";
	static final public String RESOURCE_PROVIDER_NAME_NAME = "name";
	
	static public QName SERVICE_ELEMENT_QNAME =
		new QName(GenesisIIConstants.GENESISII_NS, SERVICE_NAME);
	static public QName RESOURCE_FACTORY_ELEMENT_QNAME =
		new QName(GenesisIIConstants.GENESISII_NS, RESOURCE_PROVIDER_NAME);
	static public QName SECURITY_SETTINGS_ELEMENT_QNAME =
		new QName(GenesisIIConstants.GENESISII_NS, "security-settings");
	static public QName DEFAULT_RESOLVER_FACTORY_ELEMENT_QNAME =
		new QName(GenesisIIConstants.GENESISII_NS, "default-resolver-factory-settings");
	
	public Object parse(Node n) throws ConfigurationException
	{
		HashMap<String, ServiceDescription> serviceDescriptions
			= new HashMap<String, ServiceDescription>();
		
		NodeList children = n.getChildNodes();
		int length = children.getLength();
		
		for (int lcv = 0; lcv < length; lcv++)
		{
			Node child = children.item(lcv);
			if (child.getNodeType() == Node.ELEMENT_NODE)
			{
				QName childQName = XMLConfiguration.getQName(child);
				if (!childQName.equals(SERVICE_ELEMENT_QNAME))
					throw new ConfigurationException(
						"Found element with unexpected QName of \"" + 
						childQName + "\".");
				
				NamedNodeMap attrs = child.getAttributes();
				Node nameNode = attrs.getNamedItem(SERVICE_NAME_NAME);
				if (nameNode == null)
					throw new ConfigurationException(
						"Couldn't find name attribute.");
				
				serviceDescriptions.put(nameNode.getTextContent(),
					parseServiceDescription(child));
			}
		}
		
		return serviceDescriptions;
	}
	
	private ServiceDescription parseServiceDescription(Node n)
		throws ConfigurationException
	{
		Properties securityProps = null;
		Properties defaultResolverFactoryProps = null;
		NodeList children = n.getChildNodes();
		int length = children.getLength();
		Node nameNode = null;
		
		for (int lcv = 0; lcv < length; lcv++)
		{
			Node child = children.item(lcv);
			if (child.getNodeType() == Node.ELEMENT_NODE)
			{
				QName childQName = XMLConfiguration.getQName(child);
				if (childQName.equals(RESOURCE_FACTORY_ELEMENT_QNAME))
				{
					NamedNodeMap attrs = child.getAttributes();
					nameNode = attrs.getNamedItem(RESOURCE_PROVIDER_NAME_NAME);
					if (nameNode == null)
						throw new ConfigurationException(
							"Couldn't find name attribute.");
				} else if (childQName.equals(SECURITY_SETTINGS_ELEMENT_QNAME))
				{
					securityProps =
						(Properties)(new PropertiesConfigurationSectionHandler().parse(child));
				}
				else if (childQName.equals(DEFAULT_RESOLVER_FACTORY_ELEMENT_QNAME))
				{
					defaultResolverFactoryProps = (Properties)(new PropertiesConfigurationSectionHandler().parse(child));
				}
			}
		}
		if (nameNode == null)
			throw new ConfigurationException(
			"Couldn't locate name of resource provider.");
				
		return new ServiceDescription(nameNode.getTextContent(), securityProps, defaultResolverFactoryProps);
	}
}
