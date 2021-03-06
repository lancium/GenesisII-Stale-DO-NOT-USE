package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.IOException;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import org.oasis_open.docs.wsrf.rl_2.SetTerminationTime;

import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.rcreate.CreationException;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathDoesNotExistException;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.client.utils.units.Duration;
import edu.virginia.vcgr.genii.client.utils.units.DurationUnits;
import edu.virginia.vcgr.genii.common.GeniiCommon;
import edu.virginia.vcgr.genii.client.gpath.*;

public class TerminationScheduleTool extends BaseGridTool
{
	static final private String _DESCRIPTION_RESOURCE = "config/tooldocs/description/dschedule-termination";
	static final private String _USAGE_RESOURCE = "config/tooldocs/usage/uschedule-termination";
	static final private String _MANPAGE = "config/tooldocs/man/schedule-termination";

	public TerminationScheduleTool()
	{
		super(new LoadFileResource(_DESCRIPTION_RESOURCE), new LoadFileResource(_USAGE_RESOURCE), false, ToolCategory.GENERAL);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException, CreationException
	{
		int numArgs = numArguments();
		Date targetTime = null;

		try {
			targetTime = parseTargetTime(getArgument(numArgs - 1));
		} catch (ParseException e) {
			throw new ToolException("parsing error: " + e.getLocalizedMessage(), e);
		}

		for (int lcv = 0; lcv < (numArgs - 1); lcv++) {
			RNSPath target = lookup(new GeniiPath(getArgument(lcv)), RNSPathQueryFlags.MUST_EXIST);
			schedTerm(target, targetTime);
		}

		return 0;
	}

	static private Date parseTargetTime(String targetTime) throws ParseException
	{
		if (targetTime.startsWith("+")) {
			Duration d = new Duration(targetTime.substring(1));
			return new Date(new Date().getTime() + (long) d.as(DurationUnits.Milliseconds));
		} else {
			DateFormat format = DateFormat.getDateTimeInstance();
			return format.parse(targetTime);
		}
	}

	static public void schedTerm(RNSPath target, Date targetTime) throws RemoteException, RNSPathDoesNotExistException
	{
		GeniiCommon common = ClientUtils.createProxy(GeniiCommon.class, target.getEndpoint());

		Calendar c = Calendar.getInstance();
		c.setTime(targetTime);

		common.setTerminationTime(new SetTerminationTime(c, null));
	}

	@Override
	protected void verify() throws ToolException
	{
		if (numArguments() != 2)
			throw new InvalidToolUsageException();
		int numArgs = numArguments();
		for (int lcv = 0; lcv < numArgs - 1; lcv++)
			if (new GeniiPath(getArgument(lcv)).pathType() != GeniiPathType.Grid)
				throw new InvalidToolUsageException("<target> must be a grid path. ");
	}
}