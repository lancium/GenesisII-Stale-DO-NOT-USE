package edu.virginia.vcgr.genii.client.cmd.tools.queue;

import java.io.IOException;
import java.util.ArrayList;

import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.cmd.tools.BaseGridTool;
import edu.virginia.vcgr.genii.client.cmd.tools.ToolCategory;
import edu.virginia.vcgr.genii.client.queue.JobTicket;
import edu.virginia.vcgr.genii.client.queue.QueueManipulator;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.client.gpath.*;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.cmd.tools.Option;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;

public class QCompleteTool extends BaseGridTool
{
	static final private String _DESCRIPTION = "config/tooldocs/description/dqcomplete";
	static final private String _USAGE = "config/tooldocs/usage/uqcomplete";
	static final private String _MANPAGE = "config/tooldocs/man/qcomplete";
	private boolean _all = false;

	public QCompleteTool()
	{
		super(new LoadFileResource(_DESCRIPTION), new LoadFileResource(_USAGE), false, ToolCategory.EXECUTION);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	@Option({ "all" })
	public void setAll()
	{
		_all = true;
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException
	{
		GeniiPath gPath = new GeniiPath(getArgument(0));
		if (gPath.pathType() != GeniiPathType.Grid)
			throw new InvalidToolUsageException("<queue-path must be a grid path. ");
		QueueManipulator manipulator = new QueueManipulator(gPath.path());

		if (_all) {
			manipulator.completeAll();
		} else {
			ArrayList<JobTicket> tickets = new ArrayList<JobTicket>(numArguments() - 1);

			for (String arg : getArguments().subList(1, numArguments())) {
				tickets.add(new JobTicket(arg));
			}

			manipulator.complete(tickets);
		}

		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
		int numArgs = numArguments();
		if (numArgs <= 1 && !_all)
			throw new InvalidToolUsageException("Must indicate 1 or more tickets, or the --all flag.");
		if (numArgs != 1 && _all)
			throw new InvalidToolUsageException();
	}
}