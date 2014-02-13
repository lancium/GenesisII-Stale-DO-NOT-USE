package edu.virginia.vcgr.genii.container.appdesc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.rmi.RemoteException;
import java.util.Collection;

import javax.xml.namespace.QName;

import org.apache.axis.message.MessageElement;
import org.apache.axis.types.URI;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ggf.rns.RNSEntryResponseType;
import org.ggf.rns.RNSEntryType;
import org.morgan.util.io.StreamUtils;
import org.oasis_open.docs.wsrf.r_2.ResourceUnknownFaultType;
import org.oasis_open.docs.wsrf.rl_2.Destroy;
import org.oasis_open.wsrf.basefaults.BaseFaultType;
import org.oasis_open.wsrf.basefaults.BaseFaultTypeDescription;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.appdesc.ApplicationDescriptionPortType;
import edu.virginia.vcgr.genii.appdesc.CreateDeploymentDocumentRequestType;
import edu.virginia.vcgr.genii.appdesc.CreateDeploymentDocumentResponseType;
import edu.virginia.vcgr.genii.appdesc.DeploymentDocumentType;
import edu.virginia.vcgr.genii.appdesc.DeploymentExistsFaultType;
import edu.virginia.vcgr.genii.appdesc.PlatformDescriptionType;
import edu.virginia.vcgr.genii.appdesc.SupportDocumentType;
import edu.virginia.vcgr.genii.byteio.streamable.factory.OpenStreamResponse;
import edu.virginia.vcgr.genii.client.WellKnownPortTypes;
import edu.virginia.vcgr.genii.client.appdesc.ApplicationDescriptionConstants;
import edu.virginia.vcgr.genii.client.appdesc.ApplicationDescriptionCreator;
import edu.virginia.vcgr.genii.client.appdesc.ApplicationDescriptionUtils;
import edu.virginia.vcgr.genii.client.appdesc.ApplicationVersion;
import edu.virginia.vcgr.genii.client.byteio.ByteIOStreamFactory;
import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.common.ConstructionParameters;
import edu.virginia.vcgr.genii.client.common.GenesisHashMap;
import edu.virginia.vcgr.genii.client.resource.IResource;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.ser.ObjectSerializer;
import edu.virginia.vcgr.genii.client.wsrf.FaultManipulator;
import edu.virginia.vcgr.genii.common.GeniiCommon;
import edu.virginia.vcgr.genii.common.rfactory.ResourceCreationFaultType;
import edu.virginia.vcgr.genii.container.resource.ResourceKey;
import edu.virginia.vcgr.genii.container.rns.EnhancedRNSServiceImpl;
import edu.virginia.vcgr.genii.enhancedrns.CreateFileRequestType;
import edu.virginia.vcgr.genii.enhancedrns.CreateFileResponseType;
import edu.virginia.vcgr.genii.security.RWXCategory;
import edu.virginia.vcgr.genii.security.rwx.RWXMapping;

public class ApplicationDescriptionServiceImpl extends EnhancedRNSServiceImpl implements ApplicationDescriptionPortType
{
	static private Log _logger = LogFactory.getLog(ApplicationDescriptionServiceImpl.class);

	static final public String APPLICATION_DESCRIPTION_PROPERTY_NAME =
		"edu.virginia.vcgr.genii.container.appdesc.app_desc_property";
	static final public String APPLICATION_VERSION_PROPERTY_NAME =
		"edu.virginia.vcgr.genii.container.appdesc.app_vers_property";

	@Override
	protected void setAttributeHandlers() throws NoSuchMethodException, ResourceException, ResourceUnknownFaultType
	{
		super.setAttributeHandlers();

		new ApplicationDescriptionAttributeHandler(getAttributePackage());
	}

	@Override
	protected void postCreate(ResourceKey rKey, EndpointReferenceType myEPR, ConstructionParameters cParams,
		GenesisHashMap creationParameters, Collection<MessageElement> resolverCreationParams) throws ResourceException,
		BaseFaultType, RemoteException
	{
		super.postCreate(rKey, myEPR, cParams, creationParameters, resolverCreationParams);

		IResource resource = rKey.dereference();

		org.apache.axis.message.MessageElement elem;

		elem = creationParameters.getAxisMessageElement(ApplicationDescriptionCreator.APPLICATION_NAME_CREATION_PARAMETER);
		if (elem != null)
			resource.setProperty(APPLICATION_DESCRIPTION_PROPERTY_NAME, elem.getValue());

		elem = creationParameters.getAxisMessageElement(ApplicationDescriptionCreator.APPLICATION_VERSION_CREATION_PARAMETER);
		if (elem != null) {
			String value = elem.getValue();
			if (value != null)
				resource.setProperty(APPLICATION_VERSION_PROPERTY_NAME, new ApplicationVersion(elem.getValue()));
		}
	}

	public ApplicationDescriptionServiceImpl() throws RemoteException
	{
		super("ApplicationDescriptionPortType");

		addImplementedPortType(WellKnownPortTypes.APPDESC_PORT_TYPE());
	}

	@RWXMapping(RWXCategory.EXECUTE)
	public CreateDeploymentDocumentResponseType createDeploymentDocument(
		CreateDeploymentDocumentRequestType createDeploymentDocumentRequest) throws RemoteException, DeploymentExistsFaultType
	{
		String name = createDeploymentDocumentRequest.getName();
		DeploymentDocumentType deployDoc = createDeploymentDocumentRequest.getDeploymentDocument();

		URI deploymentType = ApplicationDescriptionUtils.determineDeploymentType(deployDoc);
		PlatformDescriptionType[] platformDesc = deployDoc.getPlatformDescription();

		SupportDocumentType supportDoc = new SupportDocumentType(platformDesc, null, deploymentType);

		CreateFileRequestType createFile = new CreateFileRequestType(name);
		EndpointReferenceType newFile = null;
		OutputStream bos = null;
		OutputStreamWriter writer = null;

		try {
			CreateFileResponseType response = null;
			response =
				super.createFile(createFile, new MessageElement[] { new MessageElement(
					ApplicationDescriptionConstants.SUPPORT_DOCUMENT_ATTR_QNAME, supportDoc) });
			newFile = response.getEndpoint();
			bos = ByteIOStreamFactory.createOutputStream(newFile);
			writer = new OutputStreamWriter(bos);
			ObjectSerializer.serialize(writer, deployDoc, new QName(
				"http://vcgr.cs.virginia.edu/genii/application-description", "deployment-description"));
			writer.flush();

			CreateDeploymentDocumentResponseType ret = new CreateDeploymentDocumentResponseType(newFile);
			newFile = null;
			return ret;
		} catch (IOException ioe) {
			throw new RemoteException(ioe.getLocalizedMessage(), ioe);
		} finally {
			StreamUtils.close(writer);

			if (newFile != null) {
				try {
					GeniiCommon common = ClientUtils.createProxy(GeniiCommon.class, newFile);
					common.destroy(new Destroy());
				} catch (Throwable t) {
					_logger.error(t);
				}
			}
		}
	}

	@Override
	protected RNSEntryResponseType add(RNSEntryType entry) throws RemoteException
	{
		if (entry == null || entry.getEndpoint() == null || entry.getEntryName() == null) {
			throw FaultManipulator.fillInFault(new BaseFaultType(null, null, null, null,
				new BaseFaultTypeDescription[] { new BaseFaultTypeDescription(
					"The \"add\" operation is only limitedly supported for this service.") }, null));
		}

		return super.add(entry);
	}

	@RWXMapping(RWXCategory.READ)
	public OpenStreamResponse openStream(Object openStreamRequest) throws RemoteException, ResourceCreationFaultType,
		ResourceUnknownFaultType
	{
		return null;
	}
}