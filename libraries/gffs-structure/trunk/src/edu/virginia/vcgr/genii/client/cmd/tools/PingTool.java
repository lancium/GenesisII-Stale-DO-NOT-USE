package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.IOException;

import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.common.GeniiCommon;
import edu.virginia.vcgr.genii.client.gpath.GeniiPath;
import edu.virginia.vcgr.genii.client.gpath.GeniiPathType;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;

public class PingTool extends BaseGridTool
{
	static final private String _DEFAULT_MESSAGE = "Hello, World!";

	static final private String _DESCRIPTION = "config/tooldocs/description/dping";
	static final private String _USAGE = "config/tooldocs/usage/uping";
	static final private String _MANPAGE = "config/tooldocs/man/ping";

	// if this is true, we will not allow a fault to escape runCommand and will instead just exit with a failure code from the app.
	boolean _eatFaults = false;

	public PingTool()
	{
		super(new LoadFileResource(_DESCRIPTION), new LoadFileResource(_USAGE), false, ToolCategory.GENERAL);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	private int _attempts = 1;

	@Option({ "attempts" })
	public void setAttempts(String attempts)
	{
		_attempts = Integer.parseInt(attempts);
	}

	@Option({ "eatfaults" })
	public void setEatfaults()
	{
		_eatFaults = true;
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException
	{
		try {
			GeniiPath gPath = new GeniiPath(getArgument(0));
			if (gPath.pathType() != GeniiPathType.Grid)
				throw new InvalidToolUsageException("<target> must be a grid path. ");
			RNSPath path = lookup(gPath, RNSPathQueryFlags.MUST_EXIST);

			EndpointReferenceType target = path.getEndpoint();
			/*
			 * target.getAddress().get_value().setQueryString("genii-container-id=" + new GUID());
			 */
			GeniiCommon common = ClientUtils.createProxy(GeniiCommon.class, target);

			String msg = _DEFAULT_MESSAGE;
			if (numArguments() == 2)
				msg = getArgument(1);

			for (int i = 0; i < _attempts; i++) {
				String response = common.ping(msg);
				stdout.format("Response %d:  %s\n", i, response);
				stdout.format("Endpoint Information:  %s\n", ClientUtils.getLastEndpointInformation(common));
			}
		} catch (Throwable t) {
			if (_eatFaults) {
				stderr.println("ping problem: " + t.getMessage());
				return 1;
			} else {
				throw t;
			}
		}

		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
		int numArgs = numArguments();
		if ((numArgs < 1) || (numArgs > 2))
			throw new InvalidToolUsageException();
	}
}