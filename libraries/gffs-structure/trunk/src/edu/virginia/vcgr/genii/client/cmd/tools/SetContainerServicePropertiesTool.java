package edu.virginia.vcgr.genii.client.cmd.tools;

import java.util.Collection;
import java.util.LinkedList;

import javax.xml.namespace.QName;

import org.apache.axis.message.MessageElement;
import org.oasis_open.docs.wsrf.rp_2.SetResourceProperties;
import org.oasis_open.docs.wsrf.rp_2.UpdateType;

import edu.virginia.vcgr.genii.client.cmd.ExceptionHandlerManager;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.container.ContainerConstants;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.container.VCGRContainerPortType;
import edu.virginia.vcgr.genii.client.gpath.*;
import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;

public class SetContainerServicePropertiesTool extends BaseGridTool
{
	static final private String _DESCRIPTION = "config/tooldocs/description/dset-container-service-properties";
	static final private LoadFileResource _USAGE = new LoadFileResource(
		"config/tooldocs/usage/uset-container-service-properties");
	static final private LoadFileResource _MANPAGE = new LoadFileResource(
		"config/tooldocs/man/set-container-service-properties");

	private String _downloadMgrTmpDir = null;
	private String _scratchSpaceDir = null;

	public SetContainerServicePropertiesTool()
	{
		super(new LoadFileResource(_DESCRIPTION), _USAGE, false, ToolCategory.ADMINISTRATION);
		addManPage(_MANPAGE);
	}

	@Option({ "download-mgr-tmpdir" })
	public void setDownload_mgr_tmpdir(String path)
	{
		_downloadMgrTmpDir = path;
	}

	@Option({ "scratch-space-dir" })
	public void setScratch_space_dir(String path)
	{
		_scratchSpaceDir = path;
	}

	private void addMessageElement(Collection<MessageElement> elements, QName attrName, String value)
	{
		if (value != null)
			elements.add(new MessageElement(attrName, value));
	}

	private SetResourceProperties generateSetRequest()
	{
		Collection<MessageElement> elements = new LinkedList<MessageElement>();

		addMessageElement(elements, ContainerConstants.PROPERTY_DOWNLOAD_TMPDIR, _downloadMgrTmpDir);
		addMessageElement(elements, ContainerConstants.PROPERTY_SCRATCH_SPACE_DIR, _scratchSpaceDir);

		SetResourceProperties ret =
			new SetResourceProperties(null, elements.size() == 0 ? null : new UpdateType(
				elements.toArray(new MessageElement[0])), null);

		return ret;
	}

	@Override
	protected int runCommand() throws Throwable
	{
		SetResourceProperties request = generateSetRequest();

		for (String arg : getArguments()) {
			try {
				GeniiPath gPath = new GeniiPath(arg);
				if (gPath.pathType() != GeniiPathType.Grid)
					throw new InvalidToolUsageException("<container> must be a grid path");
				RNSPath target = lookup(gPath, RNSPathQueryFlags.MUST_EXIST);
				VCGRContainerPortType stub = ClientUtils.createProxy(VCGRContainerPortType.class, target.getEndpoint());
				stub.setResourceProperties(request);
			} catch (Throwable cause) {
				stderr.format("Unable to set resource properties on \"%s\".", arg);
				ExceptionHandlerManager.getExceptionHandler().handleException(cause, stderr);
			}
		}

		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
	}
}