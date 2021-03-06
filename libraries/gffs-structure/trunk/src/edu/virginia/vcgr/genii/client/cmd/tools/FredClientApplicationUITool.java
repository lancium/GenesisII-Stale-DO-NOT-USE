package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.virginia.vcgr.appmgr.os.OperatingSystemType;
import edu.virginia.vcgr.appmgr.os.OperatingSystemType.OperatingSystemTypes;
import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.ui.FredClientApplication;
import edu.virginia.vcgr.genii.ui.UIFrame;

public class FredClientApplicationUITool extends BaseGridTool
{
	static private Log _logger = LogFactory.getLog(FredClientApplicationUITool.class);

	static private final String DESCRIPTION = "config/tooldocs/description/dclient-ui";
	static private final String USAGE = "config/tooldocs/usage/uclient-ui";
	static private final String _MANPAGE = "config/tooldocs/man/client-ui";

	static private void setupMacOSProperties()
	{
		System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Genesis II Application");
		System.setProperty("com.apple.mrj.application.growbox.intrudes", "false");
		System.setProperty("com.apple.mrj.application.live-resize", "true");
		System.setProperty("com.apple.macos.smallTabs", "true");
		System.setProperty("apple.laf.useScreenMenuBar", "true");
	}

	private boolean _launchShell = false;

	@Option("shell")
	public void setShell()
	{
		_launchShell = true;
	}

	public FredClientApplicationUITool()
	{
		super(new LoadFileResource(DESCRIPTION), new LoadFileResource(USAGE), false, ToolCategory.TESTING);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException
	{
		_logger.debug("entry into FredClientApplicationUITool runCommand");

		if (OperatingSystemTypes.MACOS.equals(OperatingSystemType.getCurrent()))
			setupMacOSProperties();

		FredClientApplication ca = new FredClientApplication(_launchShell);
		if (!_launchShell) {
			ca.pack();
			ca.centerWindowAndMarch();
			ca.setVisible(true);
		} else {
			ca.dispose();
		}

		// get any pending events processed.
		UIFrame.pumpEventsToWindows();

		_logger.debug("exit from FredClientApplicationUITool runCommand");
		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
		if (numArguments() > 0)
			throw new InvalidToolUsageException();
	}
}
