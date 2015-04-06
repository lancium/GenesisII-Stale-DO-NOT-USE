package edu.virginia.vcgr.genii.container.exportdir;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.rmi.RemoteException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.morgan.util.io.StreamUtils;
import org.oasis_open.docs.wsrf.r_2.ResourceUnknownFaultType;
import org.oasis_open.wsrf.basefaults.BaseFaultType;
import org.oasis_open.wsrf.basefaults.BaseFaultTypeDescription;

import edu.virginia.vcgr.genii.byteio.streamable.factory.OpenStreamResponse;
import edu.virginia.vcgr.genii.client.WellKnownPortTypes;
import edu.virginia.vcgr.genii.client.common.GenesisHashMap;
import edu.virginia.vcgr.genii.client.exportdir.ExportedFileUtils;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.wsrf.FaultManipulator;
import edu.virginia.vcgr.genii.common.rfactory.ResourceCreationFaultType;
import edu.virginia.vcgr.genii.container.byteio.RandomByteIOServiceImpl;
import edu.virginia.vcgr.genii.container.common.SByteIOFactory;
import edu.virginia.vcgr.genii.container.configuration.GeniiServiceConfiguration;
import edu.virginia.vcgr.genii.container.resource.ResourceKey;
import edu.virginia.vcgr.genii.exportdir.ExportedFilePortType;
import edu.virginia.vcgr.genii.security.RWXCategory;
import edu.virginia.vcgr.genii.security.rwx.RWXMapping;

@GeniiServiceConfiguration(resourceProvider = ExportedFileDBResourceProvider.class)
public class ExportedFileServiceImpl extends RandomByteIOServiceImpl implements ExportedFilePortType
{
	static private Log _logger = LogFactory.getLog(ExportedFileServiceImpl.class);

	public ExportedFileServiceImpl() throws RemoteException
	{
		super("ExportedFilePortType");

		addImplementedPortType(WellKnownPortTypes.EXPORTED_FILE_SERVICE_PORT_TYPE());
	}

	@Override
	protected ResourceKey createResource(GenesisHashMap constructionParameters) throws ResourceException, BaseFaultType
	{
		if (_logger.isDebugEnabled())
			_logger.debug("Creating new ExportedFile Resource.");

		if (constructionParameters == null) {
			ResourceCreationFaultType rcft =
				new ResourceCreationFaultType(null, null, null, null, new BaseFaultTypeDescription[] { new BaseFaultTypeDescription(
					"Could not create ExportedFile resource without cerationProperties") }, null);
			throw FaultManipulator.fillInFault(rcft);
		}

		ExportedFileUtils.ExportedFileInitInfo initInfo = null;
		initInfo = ExportedFileUtils.extractCreationProperties(constructionParameters);

		constructionParameters.put(IExportedFileResource.PATH_CONSTRUCTION_PARAM, initInfo.getPath());
		constructionParameters.put(IExportedFileResource.PARENT_IDS_CONSTRUCTION_PARAM, initInfo.getParentIds());
		constructionParameters.put(IExportedDirResource.REPLICATION_INDICATOR, initInfo.getReplicationState());

		return super.createResource(constructionParameters);
	}

	/*
	 * I think that this is now dead code -- mmm2a protected void fillIn(ResourceKey rKey, EndpointReferenceType newEPR,
	 * ConstructionParameters cParams, GenesisHashMap creationParameters, Collection<MessageElement> resolverCreationParams) throws
	 * ResourceException, BaseFaultType, RemoteException { super.postCreate(rKey, newEPR, cParams, creationParameters,
	 * resolverCreationParams);
	 * 
	 * Date d = new Date(); Calendar c = Calendar.getInstance(); c.setTime(d);
	 * 
	 * IExportedFileResource resource = (IExportedFileResource)rKey.dereference(); resource.setCreateTime(c); resource.setModTime(c);
	 * resource.setAccessTime(c); }
	 */

	@RWXMapping(RWXCategory.READ)
	public OpenStreamResponse openStream(Object openStreamRequest) throws RemoteException, ResourceCreationFaultType,
		ResourceUnknownFaultType
	{
		SByteIOFactory factory = null;
		InputStream inLocal = null;
		String primaryLocalPath = (String) openStreamRequest;

		try {
			factory = createStreamableByteIOResource();
			OutputStream outputStream = factory.getCreationStream();

			inLocal = new FileInputStream(primaryLocalPath);
			StreamUtils.copyStream(inLocal, outputStream);

			outputStream.flush();

			return new OpenStreamResponse(factory.create());
		} catch (IOException ioe) {
			throw FaultManipulator.fillInFault(new ResourceCreationFaultType(null, null, null, null,
				new BaseFaultTypeDescription[] { new BaseFaultTypeDescription(ioe.getLocalizedMessage()) }, null));
		} finally {
			StreamUtils.close(factory);
		}
	}
}