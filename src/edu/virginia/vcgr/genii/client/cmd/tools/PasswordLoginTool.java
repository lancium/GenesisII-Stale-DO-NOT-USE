package edu.virginia.vcgr.genii.client.cmd.tools;

import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.cmd.tools.login.AbstractLoginHandler;
import edu.virginia.vcgr.genii.client.cmd.tools.login.GuiLoginHandler;
import edu.virginia.vcgr.genii.client.cmd.tools.login.TextLoginHandler;
import edu.virginia.vcgr.genii.client.configuration.UserPreferences;
import edu.virginia.vcgr.genii.client.context.CallingContextImpl;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.gui.GuiUtils;
import edu.virginia.vcgr.genii.client.io.FileResource;
import edu.virginia.vcgr.genii.context.ContextType;
import edu.virginia.vcgr.genii.security.TransientCredentials;
import edu.virginia.vcgr.genii.security.credentials.identity.UsernamePasswordIdentity;

public class PasswordLoginTool extends BaseLoginTool
{

	static private final String _DESCRIPTION = "edu/virginia/vcgr/genii/client/cmd/tools/description/dpasswordLogin";
	static private final String _USAGE_RESOURCE = "edu/virginia/vcgr/genii/client/cmd/tools/usage/upasswordLogin";
	static final private String _MANPAGE = "edu/virginia/vcgr/genii/client/cmd/tools/man/passwordLogin";

	protected PasswordLoginTool(String description, String usage, boolean isHidden)
	{
		super(description, usage, isHidden);
		overrideCategory(ToolCategory.SECURITY);
		addManPage(new FileResource(_MANPAGE));
	}

	public PasswordLoginTool()
	{
		super(_DESCRIPTION, _USAGE_RESOURCE, false);
		overrideCategory(ToolCategory.SECURITY);
		addManPage(new FileResource(_MANPAGE));
	}

	public UsernamePasswordIdentity doPasswordLogin(String uname, String pass)
	{
		// handle username/token login
		UsernamePasswordIdentity utCredential = null;
		if (uname != null) {
			if (pass == null) {
				AbstractLoginHandler handler = null;
				if (!useGui() || !GuiUtils.supportsGraphics() || !UserPreferences.preferences().preferGUI()) {
					handler = new TextLoginHandler(stdout, stderr, stdin);
				} else {
					handler = new GuiLoginHandler(stdout, stderr, stdin);
				}
				char[] pword = handler.getPassword("Username/Password Login", String.format("Password for %s:  ", uname));
				if (pword == null)
					return null;
				pass = new String(pword);
			}
			utCredential = new UsernamePasswordIdentity(uname, pass);
			return utCredential;
		}

		return null;

	}

	@Override
	protected int runCommand() throws Throwable
	{
		// get the local identity's key material (or create one if necessary)
		ICallingContext callContext = ContextManager.getCurrentContext();
		if (callContext == null) {
			callContext = new CallingContextImpl(new ContextType());
		}

		// handle username/token login
		UsernamePasswordIdentity utCredential = doPasswordLogin(_username, _password);

		if (utCredential != null) {

			TransientCredentials transientCredentials = TransientCredentials.getTransientCredentials(callContext);
			transientCredentials.add(utCredential);
		}

		ContextManager.storeCurrentContext(callContext);

		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
		int numArgs = numArguments();
		if (numArgs > 1 || _username == null)
			throw new InvalidToolUsageException();

	}
}
