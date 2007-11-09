package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.morgan.util.configuration.ConfigurationException;

import edu.virginia.vcgr.genii.client.cmd.*;
import edu.virginia.vcgr.genii.client.configuration.UserConfig;
import edu.virginia.vcgr.genii.client.configuration.UserConfigUtils;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.context.ContextStreamUtils;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.resource.ResourceException;

public class ConnectTool extends BaseGridTool
{
	static private final String _DESCRIPTION =
		"Connects to an existing net.";
	static private final String _USAGE =
		"connect <connect-url> [<deployment dir>]";
	
	public ConnectTool()
	{
		super(_DESCRIPTION, _USAGE, false);
	}
	
	@Override
	protected int runCommand() throws Throwable
	{
		String connectURL = getArgument(0);
		String deploymentDir = null;
		if (numArguments() > 1)
		{
			deploymentDir = getArgument(1);
			File testUserDir = new File(deploymentDir);
			if (!testUserDir.exists())
				throw new ConfigurationException("Deployment directory " + deploymentDir + " does not exist.");
			if (!testUserDir.isDirectory())
				throw new ConfigurationException("Deployment path " + deploymentDir + " is not a directory.");
			if (!testUserDir.canRead())
				throw new ConfigurationException("Deployment directory " + deploymentDir + " is not readable - check permissions.");
		}
//		else
//			userConfigDir = ConfigurationManager.getUserConfigDir();

		connect(connectURL, deploymentDir);
		
		throw new ReloadShellException();
	}

	@Override
	protected void verify() throws ToolException
	{
		if (numArguments() != 1 && numArguments() != 2)
			throw new InvalidToolUsageException();
	}
	

	public void connect(ICallingContext ctxt)
		throws ConfigurationException, ResourceException, IOException
	{
		ContextManager.storeCurrentContext(ctxt);
	}

	public void connect(String connectURL)
		throws ResourceException, MalformedURLException, IOException,
			ConfigurationException
	{
		URL url = new URL(connectURL);
		connect(ContextStreamUtils.load(url), null);
	}

	public void connect(ICallingContext ctxt, String deploymentDir)
		throws ConfigurationException, ResourceException, IOException
	{
		ContextManager.storeCurrentContext(ctxt);
		if (deploymentDir != null)
		{
			UserConfig userConfig = new UserConfig(deploymentDir);
			UserConfigUtils.setCurrentUserConfig(userConfig);
			
			// reload the configuration manager so that all
			// config options are loaded from the specified deployment dir
			// (instead of likely the "default" deployment)
			UserConfigUtils.reloadConfiguration();
/*			
			File sessionDir = ConfigurationManager.getCurrentConfiguration().getUserDirectory();
			boolean clientRole = ConfigurationManager.getCurrentConfiguration().isClientRole();
			ConfigurationManager.reloadConfiguration(sessionDir.getPath());
			if (clientRole) {
				ConfigurationManager.getCurrentConfiguration().setRoleClient();
			} else {
				ConfigurationManager.getCurrentConfiguration().setRoleServer();
			}
*/			
		}
	}
	
	public void connect(String connectURL, String deploymentDir)
		throws ResourceException, MalformedURLException, IOException,
			ConfigurationException
	{
		URL url = new URL(connectURL);
		connect(ContextStreamUtils.load(url), deploymentDir);
	}

}