package edu.virginia.vcgr.genii.ui.plugins.files;

import java.io.Closeable;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.morgan.util.io.StreamUtils;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.gui.GuiUtils;
import edu.virginia.vcgr.genii.client.resource.TypeInformation;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathDoesNotExistException;
import edu.virginia.vcgr.genii.client.utils.flock.FileLockException;
import edu.virginia.vcgr.genii.ui.plugins.AbstractCombinedUIMenusPlugin;
import edu.virginia.vcgr.genii.ui.plugins.EndpointDescription;
import edu.virginia.vcgr.genii.ui.plugins.MenuType;
import edu.virginia.vcgr.genii.ui.plugins.UIPluginContext;
import edu.virginia.vcgr.genii.ui.plugins.UIPluginException;

/*
 * 
 * String ContainerPath=null; String TargetPath=null; ExportDirDialog dialog = new
 * ExportDirDialog(ContainerPath,TargetPath); dialog.pack(); GuiUtils.centerComponent(dialog);
 * dialog.setVisible(true);
 */

public class CreateDirectoryPlugin extends AbstractCombinedUIMenusPlugin
{
	static private Log _logger = LogFactory.getLog(CreateDirectoryPlugin.class);

	@Override
	protected void performMenuAction(UIPluginContext context, MenuType menuType) throws UIPluginException
	{
		String ContainerPath = "DEFAULT";
		String TargetPath = "/";
		Closeable assumedContextToken = null;
		try {
			assumedContextToken = ContextManager.temporarilyAssumeContext(context.uiContext().callingContext());
			RNSPath path = context.endpointRetriever().getTargetEndpoints().iterator().next();
				EndpointReferenceType epr = path.getEndpoint();
				
			EndpointDescription ep = new EndpointDescription(epr);
			TypeInformation tp = ep.typeInformation();
			// First determine if we were called on a container, if not then it must be on an RNS.
			if (tp.isContainer()) {
				ContainerPath = path.toString();
			} else {
				TargetPath = path.toString() + "/";
			}

			CreateDirDialog dialog = new CreateDirDialog(context, ContainerPath, TargetPath);
			dialog.pack();
			GuiUtils.centerComponent(dialog);
			dialog.setVisible(true);

		} catch (RNSPathDoesNotExistException e) {
			e.printStackTrace();
		} catch (FileLockException e) {
			_logger.error("caught unexpected exception", e);
		} finally {
			StreamUtils.close(assumedContextToken);
		}
	}

	@Override
	public boolean isEnabled(Collection<EndpointDescription> selectedDescriptions)
	{
		if (selectedDescriptions == null || selectedDescriptions.size() != 1)
			return false;
		// ASG: 9-13-2013. Modified to be more selective. Not just is it an RNS, but is it an RNS
		// and NOT (isContainer, isBES ...
		// Perhaps should be even more selective,
		TypeInformation tp = selectedDescriptions.iterator().next().typeInformation();
		return ((tp.isRNS() || tp.isContainer()) && !(tp.isBESContainer() || tp.isQueue() || tp.isIDP()));
		// return selectedDescriptions.iterator().next().typeInformation().isRNS();
	}
}