package edu.virginia.vcgr.genii.container.exportdir;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.HashMap;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ggf.rns.Add;
import org.ggf.rns.AddResponse;
import org.ggf.rns.RNSEntryExistsFaultType;
import org.ggf.rns.RNSEntryNotDirectoryFaultType;
import org.ggf.rns.RNSFaultType;
import org.morgan.util.configuration.ConfigurationException;
import org.oasis_open.wsrf.basefaults.BaseFaultType;
import org.oasis_open.wsrf.basefaults.BaseFaultTypeDescription;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.WellKnownPortTypes;
import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.exportdir.ExportedDirUtils;
import edu.virginia.vcgr.genii.client.resource.AttributedURITypeSmart;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.security.authz.RWXCategory;
import edu.virginia.vcgr.genii.client.security.authz.RWXMapping;
import edu.virginia.vcgr.genii.common.resource.ResourceUnknownFaultType;
import edu.virginia.vcgr.genii.common.rfactory.ResourceCreationFaultType;
import edu.virginia.vcgr.genii.container.Container;
import edu.virginia.vcgr.genii.container.context.WorkingContext;
import edu.virginia.vcgr.genii.container.resource.ResourceKey;
import edu.virginia.vcgr.genii.container.resource.ResourceManager;
import edu.virginia.vcgr.genii.container.util.FaultManipulator;
import edu.virginia.vcgr.genii.exportdir.ExportedDirPortType;
import edu.virginia.vcgr.genii.exportdir.ExportedRootPortType;
import edu.virginia.vcgr.genii.exportdir.QuitExport;
import edu.virginia.vcgr.genii.exportdir.QuitExportResponse;

public class ExportedRootServiceImpl extends ExportedDirServiceImpl implements
		ExportedRootPortType
{
	@SuppressWarnings("unused")
	static private Log _logger = LogFactory.getLog(ExportedRootServiceImpl.class);
	
	public ExportedRootServiceImpl() throws RemoteException
	{
		this("ExportedRootPortType");
	}
	
	protected ExportedRootServiceImpl(String serviceName) throws RemoteException
	{
		super(serviceName);
		
		addImplementedPortType(WellKnownPortTypes.EXPORTED_ROOT_SERVICE_PORT_TYPE);
	}
	
	protected ResourceKey createResource(HashMap<QName, Object> creationParameters)
		throws ResourceException, BaseFaultType
	{
		ExportedDirUtils.ExportedDirInitInfo initInfo = 
			ExportedDirUtils.extractCreationProperties(creationParameters);
		
		try
		{
			// check if directory exists
			if (!ExportedDirUtils.dirReadable(initInfo.getPath()))
			{
				throw FaultManipulator.fillInFault(
					new ResourceCreationFaultType(null, null, null, null, 
						new BaseFaultTypeDescription[] {
							new BaseFaultTypeDescription("Target directory " + 
								initInfo.getPath() + 
								" does not exist or is not readable.  " +
								"Cannot create export from this path.")	
				}, null));
			}
		}
		catch (IOException ioe)
		{
			throw new ResourceException(ioe.getLocalizedMessage(), ioe);
		}
		
		return super.createResource(creationParameters);
	}
	
	@RWXMapping(RWXCategory.INHERITED)
	public AddResponse add(Add addRequest) throws RemoteException,
		RNSEntryExistsFaultType, ResourceUnknownFaultType,
		RNSEntryNotDirectoryFaultType, RNSFaultType
	{
		try
		{
			EndpointReferenceType myEPR = 
				(EndpointReferenceType)WorkingContext.getCurrentWorkingContext().getProperty(
					WorkingContext.EPR_PROPERTY_NAME);
			myEPR.setAddress(new AttributedURITypeSmart(
				Container.getServiceURL("ExportedDirPortType")));
			ExportedDirPortType ed = ClientUtils.createProxy(ExportedDirPortType.class, myEPR);
			return ed.add(addRequest);
		}
		catch (ConfigurationException ce)
		{
			throw FaultManipulator.fillInFault(new RNSFaultType(null, null, null, null,
				new BaseFaultTypeDescription[] {
					new BaseFaultTypeDescription(ce.getLocalizedMessage())
			}, null, null));
		}
	}
	
	@RWXMapping(RWXCategory.EXECUTE)
	public QuitExportResponse quitExport(QuitExport quitExportRequest) throws RemoteException, ResourceUnknownFaultType
	{
		IExportedRootResource resource = 
			(IExportedRootResource)ResourceManager.getCurrentResource().dereference();
		
		resource.destroy(false);
		resource.commit();
		
		return new QuitExportResponse(true);
	}
}