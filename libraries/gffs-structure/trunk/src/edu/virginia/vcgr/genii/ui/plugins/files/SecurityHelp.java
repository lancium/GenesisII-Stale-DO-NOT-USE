package edu.virginia.vcgr.genii.ui.plugins.files;

import java.util.Collection;

import edu.virginia.vcgr.genii.client.gui.GuiHelpAction;
import edu.virginia.vcgr.genii.client.gui.HelpLinkConfiguration;
import edu.virginia.vcgr.genii.ui.plugins.AbstractCombinedUIMenusPlugin;
import edu.virginia.vcgr.genii.ui.plugins.EndpointDescription;
import edu.virginia.vcgr.genii.ui.plugins.MenuType;
import edu.virginia.vcgr.genii.ui.plugins.UIPluginContext;
import edu.virginia.vcgr.genii.ui.plugins.UIPluginException;

public class SecurityHelp extends AbstractCombinedUIMenusPlugin
{

	@Override
	protected void performMenuAction(UIPluginContext context, MenuType menuType) throws UIPluginException
	{
		GuiHelpAction.DisplayUrlHelp(HelpLinkConfiguration.get_help_url(HelpLinkConfiguration.GENERAL_SECURITY_HELP));

	}

	@Override
	public boolean isEnabled(Collection<EndpointDescription> selectedDescriptions)
	{

		return true;
		// return (tp.isByteIO() && !(tp.isContainer() || tp.isBESContainer() || tp.isQueue() ||
		// tp.isIDP()));
	}
}
