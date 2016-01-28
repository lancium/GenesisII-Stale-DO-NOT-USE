package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.bes.BESRP;
import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.dialog.ComboBoxDialog;
import edu.virginia.vcgr.genii.client.dialog.DialogException;
import edu.virginia.vcgr.genii.client.dialog.DialogFactory;
import edu.virginia.vcgr.genii.client.dialog.DialogProvider;
import edu.virginia.vcgr.genii.client.dialog.SimpleMenuItem;
import edu.virginia.vcgr.genii.client.dialog.TextContent;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.gpath.GeniiPath;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyManager;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.client.bes.BESPolicy;
import edu.virginia.vcgr.genii.client.bes.BESPolicyActions;

public class BesPolicyTool extends BaseGridTool
{
	static private Log _logger = LogFactory.getLog(BesPolicyTool.class);

	static final private String _DESCRIPTION = "config/tooldocs/description/dbespolicy";
	static final private LoadFileResource _USAGE = new LoadFileResource("config/tooldocs/usage/ubes-policy");
	static final private String _MANPAGE = "config/tooldocs/man/bes-policy";

	private BESPolicyActions _userLoggedInAction = null;
	private BESPolicyActions _screenSaverInactiveAction = null;
	private boolean _query = false;

	public BesPolicyTool()
	{
		super(new LoadFileResource(_DESCRIPTION), _USAGE, false, ToolCategory.ADMINISTRATION);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	@Option({ "set-user-logged-in" })
	public void setSet_user_logged_in(String action)
	{
		_userLoggedInAction = BESPolicyActions.valueOf(action);
	}

	@Option({ "set-screensaver-inactive" })
	public void setSet_screensaver_inactive(String action)
	{
		_screenSaverInactiveAction = BESPolicyActions.valueOf(action);
	}

	@Option({ "query" })
	public void setQuery()
	{
		_query = true;
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException
	{
		RNSPath bes = lookup(new GeniiPath(getArgument(0)), RNSPathQueryFlags.MUST_EXIST);

		if (_query)
			query(bes.getEndpoint());
		else if (_userLoggedInAction != null && _screenSaverInactiveAction != null)
			setPolicy(bes.getEndpoint(), _userLoggedInAction, _screenSaverInactiveAction);
		else {
			try {
				DialogProvider provider = DialogFactory.getProvider(stdout, stderr, stdin, useGui());

				ComboBoxDialog dialog = provider.createComboBoxDialog("User Logged In Action", "User logged in action?", null,
					new SimpleMenuItem("N", BESPolicyActions.NOACTION), new SimpleMenuItem("S", BESPolicyActions.SUSPEND),
					new SimpleMenuItem("SK", BESPolicyActions.SUSPENDORKILL), new SimpleMenuItem("K", BESPolicyActions.KILL));
				dialog.setHelp(new TextContent("Please select the action to take when a user logs in to the target computer."));
				dialog.showDialog();
				_userLoggedInAction = (BESPolicyActions) dialog.getSelectedItem().getContent();

				dialog = provider.createComboBoxDialog("Screensaver Inactive Action", "Screensaver inactive action?", null,
					new SimpleMenuItem("N", BESPolicyActions.NOACTION), new SimpleMenuItem("S", BESPolicyActions.SUSPEND),
					new SimpleMenuItem("SK", BESPolicyActions.SUSPENDORKILL), new SimpleMenuItem("K", BESPolicyActions.KILL));
				dialog.setHelp(
					new TextContent("Please select the action to take when the screensaver is de-activated on the target computer."));
				dialog.showDialog();
				_screenSaverInactiveAction = (BESPolicyActions) dialog.getSelectedItem().getContent();

				setPolicy(bes.getEndpoint(), _userLoggedInAction, _screenSaverInactiveAction);
			} catch (DialogException e) {
				String msg = "dialog exception occurred: " + e.getLocalizedMessage();
				_logger.warn(msg);
				throw new ToolException(msg, e);
			} catch (UserCancelException uce) {
				return 0;
			}
		}

		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
		if (numArguments() != 1)
			throw new InvalidToolUsageException();

		if (_query && (_userLoggedInAction != null || _screenSaverInactiveAction != null))
			throw new InvalidToolUsageException();

		boolean uL = (_userLoggedInAction == null);
		boolean sL = (_screenSaverInactiveAction == null);

		if (uL != sL)
			throw new InvalidToolUsageException();
	}

	private void query(EndpointReferenceType bes) throws ResourcePropertyException
	{
		BESRP rp = (BESRP) ResourcePropertyManager.createRPInterface(bes, BESRP.class);

		BESPolicy policy = rp.getPolicy();
		stdout.println(policy.toString());
	}

	private void setPolicy(EndpointReferenceType bes, BESPolicyActions userLoggedInAction, BESPolicyActions screenSaverInactiveAction)
		throws ResourcePropertyException
	{
		BESRP rp = (BESRP) ResourcePropertyManager.createRPInterface(bes, BESRP.class);

		rp.setPolicy(new BESPolicy(userLoggedInAction, screenSaverInactiveAction));
	}
}