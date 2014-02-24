package edu.virginia.vcgr.genii.container.replicatedExport;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.rmi.RemoteException;

import javax.xml.namespace.QName;

import org.apache.axis.message.MessageElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ggf.rns.RNSEntryExistsFaultType;
import org.morgan.util.io.StreamUtils;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.GenesisIIConstants;
import edu.virginia.vcgr.genii.client.byteio.ByteIOConstants;
import edu.virginia.vcgr.genii.client.byteio.ByteIOStreamFactory;
import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.naming.EPRUtils;
import edu.virginia.vcgr.genii.client.resource.IResource;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.wsrf.wsn.subscribe.DefaultSubscriptionFactory;
import edu.virginia.vcgr.genii.client.wsrf.wsn.subscribe.SubscribeException;
import edu.virginia.vcgr.genii.client.wsrf.wsn.subscribe.SubscriptionFactory;
import edu.virginia.vcgr.genii.client.wsrf.wsn.topic.wellknown.ByteIOTopics;
import edu.virginia.vcgr.genii.client.wsrf.wsn.topic.wellknown.GenesisIIBaseTopics;
import edu.virginia.vcgr.genii.common.GeniiCommon;

import org.oasis_open.docs.wsrf.r_2.ResourceUnknownFaultType;

import edu.virginia.vcgr.genii.common.rfactory.VcgrCreate;
import edu.virginia.vcgr.genii.common.rfactory.VcgrCreateResponse;
import edu.virginia.vcgr.genii.container.Container;
import edu.virginia.vcgr.genii.container.replicatedExport.resolver.RExportResolverUtils;

public class RExportUtils
{
	static private Log _logger = LogFactory.getLog(RExportUtils.class);
	static final protected String _PRIMARY_LOCALPATH_NAME = "localpath-construction-param";

	static final protected String _REXPORT_SERVICE = "RExportFilePortType";
	static final protected String _REXPORT_DIR_SERVICE = "RExportDirPortType";

	/**
	 * Creates EPR for replica with EPI specified by primary. Creates RExport if file or RExportDir
	 * if dir
	 * 
	 * @param primaryEPR
	 *            : epr of new export entry on primary
	 * @param resolverEPR
	 *            : epr of resolver for new export entry
	 * @param primaryLocalPath
	 *            : localpath of new export entry on primary
	 * @param isDir
	 *            : true if new export entry is dir; false if file
	 */
	static public EndpointReferenceType createReplica(EndpointReferenceType primaryEPR, String commonEPI,
		EndpointReferenceType resolverEPR, String primaryLocalPath, String replicaName, String entryType,
		EndpointReferenceType dataStreamEPR) throws RemoteException, RNSEntryExistsFaultType, ResourceUnknownFaultType
	{

		/* create creation params for replica */
		MessageElement[] replicaCreationProperties = new MessageElement[2];

		// specify EPI
		replicaCreationProperties[0] = new MessageElement(IResource.ENDPOINT_IDENTIFIER_CONSTRUCTION_PARAM);
		replicaCreationProperties[0].setValue(commonEPI);

		// specify primary's local path
		// is this used in creation?
		replicaCreationProperties[1] =
			new MessageElement(new QName(GenesisIIConstants.GENESISII_NS, _PRIMARY_LOCALPATH_NAME), primaryLocalPath);

		/* set appropriate rexport service to be instantiated */
		String RExportService = _REXPORT_SERVICE;
		if (entryType.equals(RExportResolverUtils._DIR_TYPE))
			RExportService = _REXPORT_DIR_SERVICE;

		// get epr for replica service on resolver's container
		EndpointReferenceType replicaServiceEPR = EPRUtils.makeEPR(Container.getServiceURL(RExportService));

		// EPR for new replica
		EndpointReferenceType replicaEPR = null;

		/* create instance of correct (dir or file) replication service */
		GeniiCommon replicaCommon = ClientUtils.createProxy(GeniiCommon.class, replicaServiceEPR);
		VcgrCreateResponse replicaResp =
			replicaCommon.vcgrCreate(new VcgrCreate(replicaCreationProperties));
		// get EPR to created replica
		replicaEPR = replicaResp.getEndpoint();

		// copy data from primary to replica if file
		if (entryType.equals(RExportResolverUtils._FILE_TYPE)) {
			unpackDataStream(replicaEPR, dataStreamEPR);
		}

		// subscribe replica to termination and rbytio events
		createReplicaSubscriptions(primaryEPR, resolverEPR, replicaEPR, primaryLocalPath, replicaName, entryType);

		return replicaEPR;
	}

	static protected String createReplicaLinkName(String localPath)
	{
		// back or forward slashes?
		int index = localPath.lastIndexOf("\\");

		if (index == -1)
			return localPath;

		return localPath.substring(index + 1);
	}

	/**
	 * Copy data from newly exported local file on primary to replica
	 * 
	 * @param replicaEPR
	 *            : used to connect to replica
	 * @param primaryLocalPath
	 *            : used to connect to newly exported local file
	 */
	static protected void updateReplicaData(EndpointReferenceType replicaEPR, String primaryLocalPath) throws ResourceException
	{
		InputStream inLocal = null;
		OutputStream outReplica = null;

		try {
			inLocal = new FileInputStream(primaryLocalPath);
			outReplica = ByteIOStreamFactory.createOutputStream(replicaEPR);
			copy(inLocal, outReplica);
		} catch (Exception e) {
			_logger.error("Exception on streaming data to rexport replica.");
			throw new ResourceException("Exception on streaming data to rexport replica.", e);
		} finally {
			StreamUtils.close(inLocal);
			StreamUtils.close(outReplica);
		}
	}

	/**
	 * Copy data from newly exported local file on primary to replica
	 * 
	 * @param replicaEPR
	 *            : used to connect to replica
	 * @param primaryLocalPath
	 *            : used to connect to newly exported local file
	 */
	static public void unpackDataStream(EndpointReferenceType replicaEPR, EndpointReferenceType primaryDataStream)
		throws ResourceException
	{

		InputStream primaryDataStreamIN = null;
		OutputStream replicaDataOUT = null;

		try {
			primaryDataStreamIN = ByteIOStreamFactory.createInputStream(primaryDataStream);
			replicaDataOUT = ByteIOStreamFactory.createOutputStream(replicaEPR);
			copy(primaryDataStreamIN, replicaDataOUT);
		} catch (Exception e) {
			_logger.error("Exception on streaming data to rexport replica:" + e.getMessage());
			throw new ResourceException("Exception on streaming data to rexport replica:", e);
		} finally {
			StreamUtils.close(primaryDataStreamIN);
			StreamUtils.close(replicaDataOUT);
		}
	}

	/**
	 * Subscribe replica to be notified of resolver termination and (if file replica) of
	 * RandomByteIO operations performed on primary
	 */
	static protected void createReplicaSubscriptions(EndpointReferenceType primaryEPR, EndpointReferenceType resolverEPR,
		EndpointReferenceType replicaEPR, String primaryLocalPath, String replicaName, String replicaType)
		throws ResourceException
	{
		PrimaryExportLocalpathUserData userData = new PrimaryExportLocalpathUserData(primaryLocalPath);
		SubscriptionFactory factory = new DefaultSubscriptionFactory(replicaEPR);

		try {
			factory.subscribe(resolverEPR, GenesisIIBaseTopics.RESOURCE_TERMINATION_TOPIC.asConcreteQueryExpression(), null,
				userData);
		} catch (SubscribeException e1) {
			_logger.error("Could not create replica subscription to rexport resolver termination.", e1);
			throw new ResourceException("Could not create replica subscription to rexport resolver termination.", e1);
		}

		// subscribe resolver of file replica to RandomeByteIO notifications from export
		if (replicaType.equals(RExportResolverUtils._FILE_TYPE)) {
			factory = new DefaultSubscriptionFactory(resolverEPR);

			try {
				factory.subscribe(primaryEPR, ByteIOTopics.BYTEIO_CONTENTS_CHANGED_TOPIC.asConcreteQueryExpression(), null,
					userData);
			} catch (SubscribeException e) {
				if (_logger.isDebugEnabled())
					_logger.debug("Could not subscribe resolver to export randomByteIO ops.");
				throw new ResourceException("Could not subscribe resolver to export randomByteIO ops.", e);
			}
		}
	}

	static private final int _BLOCK_SIZE = ByteIOConstants.PREFERRED_SIMPLE_XFER_BLOCK_SIZE;

	static private void copy(InputStream in, OutputStream out) throws IOException
	{
		byte[] data = new byte[_BLOCK_SIZE];
		int r;

		while ((r = in.read(data)) >= 0) {
			out.write(data, 0, r);
		}
	}

}