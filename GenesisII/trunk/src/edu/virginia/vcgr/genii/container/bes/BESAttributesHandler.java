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
package edu.virginia.vcgr.genii.container.bes;

import java.lang.management.ManagementFactory;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.axis.message.MessageElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ggf.jsdl.CPUArchitecture_Type;
import org.ggf.jsdl.OperatingSystemTypeEnumeration;
import org.ggf.jsdl.OperatingSystemType_Type;
import org.ggf.jsdl.OperatingSystem_Type;
import org.ggf.jsdl.ProcessorArchitectureEnumeration;
import org.morgan.util.io.StreamUtils;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.bes.BESConstants;
import edu.virginia.vcgr.genii.client.configuration.Hostname;
import edu.virginia.vcgr.genii.client.configuration.Installation;
import edu.virginia.vcgr.genii.client.jsdl.JSDLUtils;
import edu.virginia.vcgr.genii.client.naming.EPRUtils;
import edu.virginia.vcgr.genii.client.nativeq.NativeQProperties;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.ser.ObjectDeserializer;
import edu.virginia.vcgr.genii.client.spmd.SPMDTranslatorFactories;

import org.oasis_open.docs.wsrf.r_2.ResourceUnknownFaultType;

import edu.virginia.vcgr.genii.container.attrs.AbstractAttributeHandler;
import edu.virginia.vcgr.genii.container.attrs.AttributePackage;
import edu.virginia.vcgr.genii.container.bes.resource.IBESResource;
import edu.virginia.vcgr.genii.container.resource.IResource;
import edu.virginia.vcgr.genii.container.resource.ResourceManager;
import edu.virginia.vcgr.genii.container.sysinfo.SystemInfoUtils;

public class BESAttributesHandler extends AbstractAttributeHandler
	implements BESConstants
{
	@SuppressWarnings("unused")
	static private Log _logger = LogFactory.getLog(BESAttributesHandler.class);
	
	static private final String _DESCRIPTION_PROPERTY = "attribute:description";
	static private final String _DEPLOYER_PROPERTY = "attribute:deployer";
	
	public BESAttributesHandler(AttributePackage pkg) throws NoSuchMethodException
	{
		super(pkg);
	}
	
	@Override
	protected void registerHandlers() throws NoSuchMethodException
	{
		addHandler(NAME_ATTR, "getNameAttr");
		addHandler(TOTAL_NUMBER_OF_ACTIVITIES_ATTR,
			"getTotalNumberOfActivitiesAttr");
		addHandler(ACTIVITY_REFERENCE_ATTR, "getActivityReferencesAttr");
		addHandler(DESCRIPTION_ATTR, "getDescriptionAttr", "setDescriptionAttr");
		addHandler(OPERATING_SYSTEM_ATTR, "getOperatingSystemAttr");
		addHandler(CPU_ARCHITECTURE_ATTR, "getCPUArchitectureAttr");
		addHandler(CPU_COUNT_ATTR, "getCPUCountAttr");
		addHandler(IS_ACCEPTING_NEW_ACTIVITIES_ATTR, "getIsAcceptingNewActivitiesAttr");
		addHandler(SPMD_PROVIDER_ATTR, "getSPMDProvidersAttr");
		addHandler(BES_POLICY_ATTR, "getBESPolicyAttr", "setBESPolicyAttr");
		addHandler(BES_THRESHOLD_ATTR, "getBESThresholdAttr", "setBESThresholdAttr");
		addHandler(CPU_SPEED_ATTR, "getCPUSpeedAttr");
		addHandler(PHYSICAL_MEMORY_ATTR, "getPhysicalMemoryAttr");
		addHandler(VIRTUAL_MEMORY_ATTR, "getVirtualMemoryAttr");
		addHandler(BESConstants.DEPLOYER_EPR_ATTR, 
			"getDeployersAttr", "setDeployersAttr");
		addHandler(OGRSH_VERSIONS_ATTR, "getOGRSHVersionsAttr");
	}
	
	static public String getName()
	{
		return Hostname.getLocalHostname().toString();
	}
	
	static public int getTotalNumberOfActivities() 
		throws ResourceUnknownFaultType, ResourceException, 
			RemoteException, SQLException
	{
		return getActivityReferences().length;
	}
	
	static public EndpointReferenceType[] getActivityReferences()
		throws ResourceUnknownFaultType, ResourceException, 
			RemoteException, SQLException
	{
		/* THIS IS TOO HUGE!!! */
		/*
		IBESResource resource = null;
	
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
	
		Collection<EndpointReferenceType> eprs = 
			new LinkedList<EndpointReferenceType>();
		for (BESActivity activity : resource.getContainedActivities())
		{
			try
			{
				eprs.add(activity.getActivityEPR());
			}
			catch (NoSuchActivityFault nsaf)
			{
				// We lost the activity while we were waiting...just ignore it
				_logger.debug("Lost an activity between the time we got it's " +
					"key and asked for it's EPR.", nsaf);
			}
		}
		
		return eprs.toArray(new EndpointReferenceType[0]);
		*/
		
		return new EndpointReferenceType[0];
	}
	
	static public String getDescription()
		throws ResourceException, ResourceUnknownFaultType
	{
		IBESResource resource = null;
		
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
		return (String)resource.getProperty(_DESCRIPTION_PROPERTY);
	}
	
	static public OperatingSystem_Type getOperatingSystem() throws RemoteException
	{
		IBESResource resource = null;
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
		NativeQProperties qProps = new NativeQProperties(resource.nativeQProperties());
		OperatingSystemTypeEnumeration osType = qProps.operatingSystemName();
		String osVersion = qProps.operatingSystemVersion();
		
		OperatingSystem_Type ret = JSDLUtils.getLocalOperatingSystem();
		if (osType != null)
			ret.setOperatingSystemType(new OperatingSystemType_Type(osType, null));
		if (osVersion != null)
			ret.setOperatingSystemVersion(osVersion);
		
		return ret;
	}
	
	static public CPUArchitecture_Type getCPUArchitecture() throws RemoteException
	{
		IBESResource resource = null;
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
		NativeQProperties qProps = new NativeQProperties(resource.nativeQProperties());
		ProcessorArchitectureEnumeration override = qProps.cpuArchitecture();
		
		if (override != null)
			return new CPUArchitecture_Type(override, null);
		
		return JSDLUtils.getLocalCPUArchitecture();
	}
	
	static public int getCPUCount() throws RemoteException
	{
		IBESResource resource = null;
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
		NativeQProperties qProps = new NativeQProperties(resource.nativeQProperties());
		Integer cpuCount = qProps.cpuCount();
		if (cpuCount != null)
			return cpuCount.intValue();
		
		return ManagementFactory.getOperatingSystemMXBean(
			).getAvailableProcessors();
	}
	
	static public Boolean getIsAcceptingNewActivities()
		throws ResourceException, ResourceUnknownFaultType, RemoteException
	{
		IBESResource resource = null;
		
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
		Boolean isAccepting = new Boolean(resource.isAcceptingNewActivities());
		
		return isAccepting;
	}
	
	static public Collection<String> getSPMDProviders()
		throws RemoteException
	{
		return SPMDTranslatorFactories.listSPMDTranslatorFactories();
	}
	
	static public long getCPUSpeed() throws RemoteException
	{
		IBESResource resource = null;
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
		NativeQProperties qProps = new NativeQProperties(resource.nativeQProperties());
		Long override = qProps.cpuSpeed();
		if (override != null)
			return override.longValue();
		
		return SystemInfoUtils.getIndividualCPUSpeed();
	}
	
	static public long getPhysicalMemory() throws RemoteException
	{
		IBESResource resource = null;
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
		NativeQProperties qProps = new NativeQProperties(resource.nativeQProperties());
		Long override = qProps.physicalMemory();
		if (override != null)
			return override.longValue();
		
		return SystemInfoUtils.getPhysicalMemory();
	}
	
	static public long getVirtualMemory() throws RemoteException
	{
		IBESResource resource = null;
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
		NativeQProperties qProps = new NativeQProperties(resource.nativeQProperties());
		Long override = qProps.virtualMemory();
		if (override != null)
			return override.longValue();
		
		return SystemInfoUtils.getVirtualMemory();
	}
	
	public void setDescription(String description)
		throws ResourceException, ResourceUnknownFaultType
	{
		IBESResource resource = null;
		
		try
		{
			resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
			resource.setProperty(_DESCRIPTION_PROPERTY, description);
			resource.commit();
		}
		finally
		{
			StreamUtils.close(resource);
		}
	}
	
	@SuppressWarnings("unchecked")
	public Collection<EndpointReferenceType> getDeployers()
		throws ResourceException, ResourceUnknownFaultType
	{
		ArrayList<EndpointReferenceType> eprs 
			= new ArrayList<EndpointReferenceType>();
		IResource resource;
		resource = ResourceManager.getCurrentResource().dereference();
		Collection<byte[]> ret = (Collection<byte[]>)resource.getProperty(
			_DEPLOYER_PROPERTY);
		if (ret != null)
		{
			for (byte[] bytes : ret)
				eprs.add(EPRUtils.fromBytes(bytes));
		}
		
		return eprs;
	}
	
	public void setDeployers(Collection<EndpointReferenceType> deployers)
		throws ResourceException, ResourceUnknownFaultType
	{
		ArrayList<byte[]> toStore = new ArrayList<byte[]>();
		for (EndpointReferenceType deployer : deployers)
			toStore.add(EPRUtils.toBytes(deployer));
		
		IResource resource;
		resource = ResourceManager.getCurrentResource().dereference();
		resource.setProperty(_DEPLOYER_PROPERTY, toStore);
		resource.commit();
	}
	
	public MessageElement getIsAcceptingNewActivitiesAttr()
		throws ResourceUnknownFaultType, ResourceException, RemoteException
	{
		return new MessageElement(IS_ACCEPTING_NEW_ACTIVITIES_ATTR, 
			getIsAcceptingNewActivities());
	}
	
	public MessageElement getBESPolicyAttr()
		throws ResourceUnknownFaultType, ResourceException, 
			RemoteException
	{
		IBESResource resource;
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
		return resource.getPolicy().toMessageElement(BES_POLICY_ATTR);
	}
	
	public void setBESPolicyAttr(MessageElement policy)
		throws ResourceUnknownFaultType, ResourceException, 
			RemoteException
	{
		BESPolicy p = BESPolicy.fromMessageElement(policy);
		IBESResource resource;
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
		resource.setPolicy(p);
		resource.commit();
	}
	
	public MessageElement getBESThresholdAttr()
		throws ResourceUnknownFaultType, ResourceException, 
			RemoteException
	{
		IBESResource resource;
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
		Integer threshold = (Integer)resource.getProperty(
			IBESResource.THRESHOLD_DB_PROPERTY_NAME);
		return new MessageElement(BES_THRESHOLD_ATTR, threshold);
	}
	
	public void setBESThresholdAttr(MessageElement policy)
		throws ResourceUnknownFaultType, ResourceException, 
			RemoteException
	{
		Integer threshold = Integer.class.cast(
			ObjectDeserializer.toObject(policy, Integer.class));
		
		IBESResource resource;
		resource = (IBESResource)ResourceManager.getCurrentResource().dereference();
		resource.setProperty(
			IBESResource.THRESHOLD_DB_PROPERTY_NAME, threshold);
		resource.commit();
	}
	
	public MessageElement getNameAttr()
	{
		return new MessageElement(NAME_ATTR, getName());
	}
	
	public MessageElement getTotalNumberOfActivitiesAttr()
		throws ResourceException, ResourceUnknownFaultType, 
			RemoteException, SQLException
	{
		return new MessageElement(TOTAL_NUMBER_OF_ACTIVITIES_ATTR,
			getTotalNumberOfActivities());
	}
	
	public ArrayList<MessageElement> getSPMDProvidersAttr()
		throws RemoteException
	{
		Collection<String> spmdProviders = getSPMDProviders();
		ArrayList<MessageElement> ret = new ArrayList<MessageElement>(
			spmdProviders.size());
		for (String provider : spmdProviders)
		{
			ret.add(new MessageElement(SPMD_PROVIDER_ATTR, provider));
		}
		
		return ret;
	}
	
	public ArrayList<MessageElement> getActivityReferencesAttr()
		throws ResourceException, ResourceUnknownFaultType, 
			RemoteException, SQLException
	{
		EndpointReferenceType []eprs = getActivityReferences();
		ArrayList<MessageElement> ret = new ArrayList<MessageElement>(eprs.length);
		for (int lcv = 0; lcv < eprs.length; lcv++)
		{
			ret.add(new MessageElement(ACTIVITY_REFERENCE_ATTR, eprs[lcv]));
		}
		
		return ret;
	}
	
	public ArrayList<MessageElement> getOGRSHVersionsAttr()
	{
		ArrayList<MessageElement> ret = new ArrayList<MessageElement>();
		for (String version : 
			Installation.getOGRSH().getInstalledVersions().keySet())
		{
			ret.add(new MessageElement(OGRSH_VERSIONS_ATTR, version));
		}
		
		return ret;
	}
	
	public ArrayList<MessageElement> getDescriptionAttr()
		throws ResourceException, ResourceUnknownFaultType
	{
		ArrayList<MessageElement> result = new ArrayList<MessageElement>();
		String desc = getDescription();
		if (desc != null)
			result.add(new MessageElement(DESCRIPTION_ATTR, desc));
		
		return result;
	}
	
	public void setDescriptionAttr(MessageElement element)
		throws ResourceException, ResourceUnknownFaultType
	{
		setDescription(element.getValue());
	}
	
	public MessageElement getOperatingSystemAttr() throws RemoteException
	{
		return new MessageElement(OPERATING_SYSTEM_ATTR, getOperatingSystem());
	}
	
	public MessageElement getCPUArchitectureAttr() throws RemoteException
	{
		return new MessageElement(CPU_ARCHITECTURE_ATTR, getCPUArchitecture());
	}
	
	public MessageElement getCPUCountAttr() throws RemoteException
	{
		return new MessageElement(CPU_COUNT_ATTR, getCPUCount());
	}
	
	public MessageElement getCPUSpeedAttr() throws RemoteException
	{
		return new MessageElement(CPU_SPEED_ATTR,
			getCPUSpeed());
	}
	
	public MessageElement getPhysicalMemoryAttr() throws RemoteException
	{
		return new MessageElement(PHYSICAL_MEMORY_ATTR, 
			getPhysicalMemory());
	}
	
	public MessageElement getVirtualMemoryAttr() throws RemoteException
	{
		return new MessageElement(VIRTUAL_MEMORY_ATTR,
			getVirtualMemory());
	}
	
	public ArrayList<MessageElement> getDeployersAttr()
		throws ResourceException, ResourceUnknownFaultType
	{
		ArrayList<MessageElement> deployers = new ArrayList<MessageElement>();
		for (EndpointReferenceType deployer : getDeployers())
		{
			deployers.add(
				new MessageElement(BESConstants.DEPLOYER_EPR_ATTR, deployer));
		}
		
		return deployers;
	}
	
	public void setDeployersAttr(Collection<MessageElement> deployers)
		throws ResourceException, ResourceUnknownFaultType
	{
		Collection<EndpointReferenceType> deployerEPRs = 
			new ArrayList<EndpointReferenceType>();
		
		for (MessageElement deployerM : deployers)
		{
			EndpointReferenceType deployer = 
				ObjectDeserializer.toObject(
					deployerM, EndpointReferenceType.class);
			deployerEPRs.add(deployer);
		}
		
		setDeployers(deployerEPRs);
	}
}