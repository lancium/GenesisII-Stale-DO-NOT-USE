package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.IOException;

import edu.virginia.vcgr.appmgr.launcher.ApplicationLauncher;
import edu.virginia.vcgr.appmgr.version.Version;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;

public class VersionTool extends BaseGridTool
{
	static final private String _DESCRIPTION = "config/tooldocs/description/dversion";
	static final private String _USAGE = "config/tooldocs/usage/uversion";
	static final private String _MANPAGE = "config/tooldocs/man/version";

	public VersionTool()
	{
		super(new LoadFileResource(_DESCRIPTION), new LoadFileResource(_USAGE), false);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException
	{
		Version v = ApplicationLauncher.getAppVersion();
		String softwareName = ApplicationLauncher.getAppName();
		if ((v == null) || (softwareName == null)) {
			stdout.println("GenesisII version {unspecified}");
			stdout.flush();
			return 0;
		}
		String versionMsg;
		if (Version.EMPTY_VERSION.equals(v)) {
			versionMsg = String.format("%s version %s", softwareName, "<Unknown>");
		} else {
			versionMsg = String.format("%s version %s", softwareName, v);
		}
		stdout.println(versionMsg);
		stdout.flush();
		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
	}

	@Override
	public void addArgument(String argument)
	{
		_arguments.add(argument);
	}
}
