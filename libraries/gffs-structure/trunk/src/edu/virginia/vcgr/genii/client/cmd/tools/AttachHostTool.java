package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.IOException;

import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.gpath.GeniiPath;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.naming.EPRUtils;
import edu.virginia.vcgr.genii.client.ogsa.OGSARP;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyManager;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;

public class AttachHostTool extends BaseGridTool
{
	static final private String _DESCRIPTION = "config/tooldocs/description/dattachhost";
	static final private String _USAGE = "config/tooldocs/usage/uattach-host";
	static final private String _MANPAGE = "config/tooldocs/man/attach-host";

	public AttachHostTool()
	{
		super(new LoadFileResource(_DESCRIPTION), new LoadFileResource(_USAGE), false, ToolCategory.ADMINISTRATION);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException
	{
		String containerURL = getArgument(0);
		RNSPath path = lookup(new GeniiPath(getArgument(1)), RNSPathQueryFlags.MUST_NOT_EXIST);

		containerURL = edu.virginia.vcgr.appmgr.net.Hostname.normalizeURL(containerURL);

		OGSARP rp = (OGSARP) ResourcePropertyManager.createRPInterface(EPRUtils.makeEPR(containerURL), OGSARP.class);

		EndpointReferenceType epr = rp.getResourceEndpoint();

		path.link(epr);
		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
		if (numArguments() != 2)
			throw new InvalidToolUsageException();
	}
}