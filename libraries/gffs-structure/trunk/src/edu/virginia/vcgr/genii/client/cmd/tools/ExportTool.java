package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.IOException;
import java.rmi.RemoteException;

import javax.xml.namespace.QName;

import org.apache.axis.message.MessageElement;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.GenesisIIConstants;
import edu.virginia.vcgr.genii.client.WellKnownPortTypes;
import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.configuration.DeploymentName;
import edu.virginia.vcgr.genii.client.configuration.Installation;
import edu.virginia.vcgr.genii.client.configuration.NamespaceDefinitions;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.exportdir.ExportedDirUtils;
import edu.virginia.vcgr.genii.client.gpath.GeniiPath;
import edu.virginia.vcgr.genii.client.gpath.GeniiPathType;
import edu.virginia.vcgr.genii.client.gui.GuiUtils;
import edu.virginia.vcgr.genii.client.gui.exportdir.ExportDirDialog;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.naming.EPRUtils;
import edu.virginia.vcgr.genii.client.rcreate.CreationException;
import edu.virginia.vcgr.genii.client.rcreate.ResourceCreator;
import edu.virginia.vcgr.genii.client.resource.PortType;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.rns.RNSConstants;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.client.rns.RNSUtilities;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.client.ser.ObjectSerializer;
import edu.virginia.vcgr.genii.client.utils.flock.FileLockException;
import edu.virginia.vcgr.genii.common.rfactory.ResourceCreationFaultType;
import edu.virginia.vcgr.genii.exportdir.ExportedRootPortType;
import edu.virginia.vcgr.genii.exportdir.QuitExport;
import edu.virginia.vcgr.genii.exportdir.QuitExportResponse;
import edu.virginia.vcgr.genii.client.rns.RNSPathAlreadyExistsException;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;

public class ExportTool extends BaseGridTool
{
	static final private String _DESCRIPTION = "config/tooldocs/description/dexport";
	static final private String _USAGE_RESOURCE = "config/tooldocs/usage/uexport";
	static final private String _MANPAGE = "config/tooldocs/man/export";

	private boolean _create = false;
	private boolean _quit = false;
	private boolean _url = false;
	private boolean _replicate = false;
	private String _svnUser = null;
	private String _svnPass = null;
	private Long _svnRevision = null;

	public ExportTool()
	{
		super(new LoadFileResource(_DESCRIPTION), new LoadFileResource(_USAGE_RESOURCE), false, ToolCategory.DATA);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	@Option({ "create" })
	public void setCreate()
	{
		_create = true;
	}

	@Option({ "quit" })
	public void setQuit()
	{
		_quit = true;
	}

	@Option({ "url" })
	public void setUrl()
	{
		_url = true;
	}

	@Option({ "replicate" })
	public void setReplicate()
	{
		_replicate = true;
	}

	@Option("svn-user")
	public void setSVNUser(String user)
	{
		_svnUser = user;
	}

	@Option("svn-pass")
	public void setSVNPass(String pass)
	{
		_svnPass = pass;
	}

	@Option("svn-revision")
	public void setSVNRevision(String revision)
	{
		_svnRevision = Long.valueOf(revision);
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException,
		AuthZSecurityException, IOException, ResourcePropertyException, CreationException, InvalidToolUsageException,
		ClassNotFoundException
	{
		int numArgs = numArguments();
		if (_create) {
			/* get rns path for exported root and ensure does not exist */
			String targetRNSName = null;
			if (numArgs == 3) {
				GeniiPath gPath = new GeniiPath(getArgument(2));
				if (gPath.pathType() != GeniiPathType.Grid)
					throw new InvalidToolUsageException("[new-rns-path] must be a grid path. ");
				targetRNSName = gPath.path();

				// ensure location does not already exist
				ensureTargetDNE(targetRNSName);
			}

			EndpointReferenceType exportServiceEPR;
			String serviceLocation = getArgument(0);
			/* get EPR for target exported root service */
			if (_url) {
				exportServiceEPR = EPRUtils.makeEPR(serviceLocation);
			} else {
				NamespaceDefinitions nsd = Installation.getDeployment(new DeploymentName()).namespace();
				exportServiceEPR =
					RNSUtilities.findService(nsd.getRootContainer(), "ExportedRootPortType",
						new PortType[] { WellKnownPortTypes.EXPORTED_ROOT_SERVICE_PORT_TYPE() }, serviceLocation).getEndpoint();
			}

			/* get local directory path to be exported */
			String localPath = getArgument(1);

			EndpointReferenceType epr =
				createExportedRoot(targetRNSName, exportServiceEPR, localPath, _svnUser, _svnPass, _svnRevision, targetRNSName,
					_replicate);

			if (targetRNSName == null) {
				stdout.println(ObjectSerializer.toString(epr, new QName(GenesisIIConstants.GENESISII_NS, "endpoint")));
			}

			return 0;
		} else if (_replicate) {
			/* get rns path for exported root and ensure dne */
			String targetRNSName = null;
			if (numArgs == 4) {
				GeniiPath gPath = new GeniiPath(getArgument(3));
				if (gPath.pathType() != GeniiPathType.Grid)
					throw new InvalidToolUsageException("[new-rns-path] must be a grid path. ");
				targetRNSName = gPath.path();

				// ensure location does not already exist
				ensureTargetDNE(targetRNSName);
			}

			EndpointReferenceType exportServiceEPR = null;
			EndpointReferenceType replicationServiceEPR = null;

			String primaryLocation = getArgument(0);
			String replicationLocation = getArgument(1);

			NamespaceDefinitions nsd = Installation.getDeployment(new DeploymentName()).namespace();

			/* get EPRs for needed services */
			if (_url) {
				exportServiceEPR = EPRUtils.makeEPR(primaryLocation);
				replicationServiceEPR = EPRUtils.makeEPR(replicationLocation);
			} else {
				exportServiceEPR =
					RNSUtilities.findService(nsd.getRootContainer(), "ExportedRootPortType",
						new PortType[] { WellKnownPortTypes.EXPORTED_ROOT_SERVICE_PORT_TYPE() }, primaryLocation).getEndpoint();
				replicationServiceEPR =
					RNSUtilities.findService(nsd.getRootContainer(), "RExportResolverPortType",
						new PortType[] { WellKnownPortTypes.REXPORT_RESOLVER_PORT_TYPE() }, replicationLocation).getEndpoint();
			}

			/* get local directory path to be exported */
			GeniiPath gPath = new GeniiPath(getArgument(2));
			if (gPath.pathType() != GeniiPathType.Local)
				throw new InvalidToolUsageException("<primary-local-path> must be a local path beginning with 'local:' ");
			String localPath = gPath.path();

			EndpointReferenceType epr =
				createReplicatedExportedRoot(exportServiceEPR, localPath, targetRNSName, _replicate, replicationServiceEPR);

			if (targetRNSName == null) {
				stdout.println(ObjectSerializer.toString(epr, new QName(GenesisIIConstants.GENESISII_NS, "endpoint")));
			}

			return 0;
		} else if (_quit) {
			GeniiPath gPath = new GeniiPath(getArgument(0));
			if (gPath.pathType() != GeniiPathType.Grid)
				throw new InvalidToolUsageException("export-root must be a grid path. ");
			String exportedRootLocation = gPath.path();
			/* get EPR for target export service that will create exported root */
			if (_url)
				quitExportedRootFromURL(exportedRootLocation);
			else
				quitExportedRootFromRNS(exportedRootLocation);
			stdout.println("Exported root stopped successfully");
			return 0;
		} else {
			String ContainerPath = null;
			String TargetPath = null;
			ExportDirDialog dialog;
			try {
				dialog = new ExportDirDialog(ContainerPath, TargetPath);
			} catch (FileLockException e) {
				throw new ToolException("failure to lock files: " + e.getLocalizedMessage(), e);
			}
			dialog.pack();
			GuiUtils.centerComponent(dialog);
			dialog.setVisible(true);
			return 0;
		}
	}

	@Override
	protected void verify() throws ToolException
	{
		int numArgs = numArguments();

		if (_create) {
			if (_quit)
				throw new InvalidToolUsageException("Only one of the options create or " + "quit can be specified.");

			if (numArgs < 2 || numArgs > 3)
				throw new InvalidToolUsageException("Invalid number of arguments.");
		} else if (_replicate) {
			if (numArgs < 3 || numArgs > 4)
				throw new InvalidToolUsageException("Invalid number of arguments.");
		} else if (_quit) {
			if (_create)
				throw new InvalidToolUsageException("Only one of the options create or " + "quit can be specified.");

			if (numArgs != 1)
				throw new InvalidToolUsageException("Invalid number of arguments.");
		} else {
			if (numArgs != 0)
				throw new InvalidToolUsageException("Invalid arguments.");
		}
	}

	static public EndpointReferenceType createExportedRoot(String humanName, EndpointReferenceType exportServiceEPR,
		String localPath, String svnUser, String svnPass, Long svnRevision, String RNSPath, boolean isReplicated)
		throws ResourceException, ResourceCreationFaultType, RemoteException, RNSException, CreationException, IOException,
		InvalidToolUsageException
	{
		EndpointReferenceType newEPR = null;

		String replicationIndicator = "false";
		if (isReplicated)
			replicationIndicator = "true";

		if (localPath.startsWith("local:"))
			localPath = localPath.substring(6);
		else if (localPath.startsWith("file:"))
			localPath = localPath.substring(5);

		MessageElement[] createProps =
			ExportedDirUtils.createCreationProperties(humanName, localPath, svnUser, svnPass, svnRevision, "",
				replicationIndicator);

		ICallingContext origContext = ContextManager.getExistingContext();
		ICallingContext createContext = origContext.deriveNewContext();
		createContext.setSingleValueProperty(RNSConstants.RESOLVED_ENTRY_UNBOUND_PROPERTY, "FALSE");
		try {
			ContextManager.storeCurrentContext(createContext);
			newEPR = createInstance(exportServiceEPR, RNSPath, createProps);
		} finally {
			ContextManager.storeCurrentContext(origContext);
		}
		return newEPR;
	}

	/**
	 * local linking tool that on error uses quit export instead of resource delete
	 * 
	 * @param service
	 * @param optTargetName
	 * @param createProperties
	 * @return
	 * @throws ConfigurationException
	 * @throws ResourceException
	 * @throws ResourceCreationFaultType
	 * @throws RemoteException
	 * @throws RNSException
	 * @throws CreationException
	 * @throws IOException
	 * @throws InvalidToolUsageException
	 */
	static public EndpointReferenceType createInstance(EndpointReferenceType service, String optTargetName,
		MessageElement[] createProperties) throws ResourceException, ResourceCreationFaultType, RemoteException, RNSException,
		CreationException, IOException, InvalidToolUsageException
	{
		EndpointReferenceType epr = ResourceCreator.createNewResource(service, createProperties, null);

		if (optTargetName != null) {
			try {
				LnTool.link(epr, new GeniiPath(optTargetName));
			} catch (RNSException re) {
				quitExportedRoot(epr, false);
				throw re;
			}
		}

		return epr;
	}

	static public EndpointReferenceType createReplicatedExportedRoot(EndpointReferenceType exportServiceEPR, String localPath,
		String RNSPath, boolean isReplicated, EndpointReferenceType replicationService) throws ResourceException,
		ResourceCreationFaultType, RemoteException, RNSException, CreationException, IOException, InvalidToolUsageException
	{
		EndpointReferenceType newEPR = null;
		String replicationIndicator = "false";
		if (isReplicated)
			replicationIndicator = "true";

		MessageElement[] createProps =
			ExportedDirUtils.createReplicationCreationProperties(localPath, "", replicationIndicator, replicationService);

		ICallingContext origContext = ContextManager.getExistingContext();
		ICallingContext createContext = origContext.deriveNewContext();
		createContext.setSingleValueProperty(RNSConstants.RESOLVED_ENTRY_UNBOUND_PROPERTY, "FALSE");
		try {
			ContextManager.storeCurrentContext(createContext);
			newEPR = createInstance(exportServiceEPR, RNSPath, createProps);
		} finally {
			ContextManager.storeCurrentContext(origContext);
		}
		return newEPR;
	}

	static public boolean quitExportedRootFromRNS(String exportedRootRNSPath) throws IOException, RNSException
	{
		RNSPath path = RNSPath.getCurrent();
		path = path.lookup(exportedRootRNSPath, RNSPathQueryFlags.MUST_EXIST);
		boolean ret = quitExportedRoot(path.getEndpoint(), false);

		/* remove from RNS */
		path.unlink();

		return ret;
	}

	static public boolean quitExportedRootFromURL(String exportedRootURL) throws IOException, RNSException
	{
		return quitExportedRoot(EPRUtils.makeEPR(exportedRootURL), false);
	}

	static public boolean quitExportedRoot(EndpointReferenceType epr, boolean deleteDirectory) throws IOException, RNSException
	{
		ExportedRootPortType exportedRoot = ClientUtils.createProxy(ExportedRootPortType.class, epr);
		QuitExport request = new QuitExport();
		request.setDelete_directory(deleteDirectory);
		QuitExportResponse response = exportedRoot.quitExport(request);
		return response.isSuccess();
	}

	static public void ensureTargetDNE(String targetPath) throws ResourceException
	{
		try {
			RNSPath currentPath = RNSPath.getCurrent();
			currentPath.lookup(targetPath, RNSPathQueryFlags.MUST_NOT_EXIST);
		} catch (RNSPathAlreadyExistsException e) {
			throw new ResourceException("Target RNS path already exists.");
		} catch (Exception e) {
			throw new ResourceException("Problem with ensuring target RNS path does not already exist.");
		}
	}
}