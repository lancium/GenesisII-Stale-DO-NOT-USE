package edu.virginia.vcgr.genii.client.cmd.tools.queue;

import java.io.IOException;

import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.cmd.tools.BaseGridTool;
import edu.virginia.vcgr.genii.client.cmd.tools.ToolCategory;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.queue.QueueManipulator;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.client.gpath.*;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;

public class QConfigureTool extends BaseGridTool
{
	static private final String _DESCRIPTION = "config/tooldocs/description/dqconfigure";
	static private final String _USAGE = "config/tooldocs/usage/uqconfigure";
	static final private String _MANPAGE = "config/tooldocs/man/qconfigure";

	public QConfigureTool()
	{
		super(new LoadFileResource(_DESCRIPTION), new LoadFileResource(_USAGE), false, ToolCategory.EXECUTION);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException
	{
		GeniiPath gPath = new GeniiPath(getArgument(0));
		if (gPath.pathType() != GeniiPathType.Grid)
			throw new InvalidToolUsageException("<queue-path> must be a grid path. ");
		QueueManipulator manipulator = new QueueManipulator(gPath.path());
		if (_arguments.size() > 3) {
			manipulator.configure(getArgument(1), Integer.parseInt(getArgument(2)), Integer.parseInt(getArgument(3)));
		} else {
			manipulator.configure(getArgument(1), Integer.parseInt(getArgument(2)), 1);
		}

		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
		if (!(numArguments() == 3 || numArguments() == 4))
			throw new InvalidToolUsageException();
	}
}