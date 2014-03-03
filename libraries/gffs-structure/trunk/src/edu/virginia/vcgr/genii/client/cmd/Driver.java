package edu.virginia.vcgr.genii.client.cmd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.virginia.vcgr.genii.client.ApplicationBase;
import edu.virginia.vcgr.genii.client.ContainerProperties;
import edu.virginia.vcgr.genii.client.comm.axis.security.VcgrSslSocketFactory;
import edu.virginia.vcgr.genii.client.configuration.DeploymentName;
import edu.virginia.vcgr.genii.client.configuration.GridEnvironment;
import edu.virginia.vcgr.genii.client.configuration.ShellPrompt;
import edu.virginia.vcgr.genii.client.configuration.UserPreferences;
import edu.virginia.vcgr.genii.client.logging.DLogDatabase;
import edu.virginia.vcgr.genii.client.logging.LoggingContext;
import edu.virginia.vcgr.genii.client.mem.LowMemoryExitHandler;
import edu.virginia.vcgr.genii.client.mem.LowMemoryWarning;
import edu.virginia.vcgr.genii.client.security.KeystoreManager;
import edu.virginia.vcgr.genii.osgi.OSGiSupport;
import edu.virginia.vcgr.genii.security.CertificateValidatorFactory;
import edu.virginia.vcgr.genii.security.utils.SecurityUtilities;

public class Driver extends ApplicationBase
{
	static private Log _logger = LogFactory.getLog(Driver.class);

	static public void usage()
	{
		System.out.println("Driver");
	}

	static public void loadClientState()
	{
		LoggingContext.assumeNewLoggingContext();

		if (!OSGiSupport.setUpFramework()) {
			System.err.println("Exiting due to OSGi startup failure.");
			System.exit(1);
		}

		SecurityUtilities.initializeSecurity();

		try {
			CertificateValidatorFactory.setValidator(new SecurityUtilities(KeystoreManager.getResourceTrustStore()));
		} catch (Throwable t) {
			System.err.println("Security validation setup failure: " + t.getMessage());
			System.exit(1);
		}

		// hmmm: never even knew there was an environment variable override for the deployment name.
		// this should be unified with other ways of loading things; pretty sure we are not always
		// handling this uniformly.
		GridEnvironment.loadGridEnvironment();
		String deploymentName = System.getenv("GENII_DEPLOYMENT_NAME");
		if (deploymentName != null) {
			if (_logger.isDebugEnabled())
				_logger.debug("Using Deployment \"" + deploymentName + "\".");
			System.setProperty(DeploymentName.DEPLOYMENT_NAME_PROPERTY, deploymentName);
		} else {
			deploymentName = System.getProperty(DeploymentName.DEPLOYMENT_NAME_PROPERTY);
			if ((deploymentName == null) || deploymentName.isEmpty())
				deploymentName = "default";
			if (_logger.isDebugEnabled())
				_logger.debug("Using Deployment \"" + deploymentName + "\".");
		}

		prepareClientApplication();

		// Set Trust Store Provider
		java.security.Security.setProperty("ssl.SocketFactory.provider", VcgrSslSocketFactory.class.getName());
	}

	static public void main(String[] args)
	{
		// first run through, go ahead and try to load the state.
		loadClientState();

		// new for clients; try to handle memory better.
		LowMemoryWarning.INSTANCE.addLowMemoryListener(new LowMemoryExitHandler(7));

		int lastExit = 0; // track the grid commands and how they returned.

		if ((args.length > 1) && (args[0].equals("connect"))) {
			if (_logger.isDebugEnabled())
				_logger.debug("not trying auto-connect as this is a connect command.");
		} else {
			ApplicationBase.GridStates gridOkay =
				establishGridConnection(new PrintWriter(System.out, true), new PrintWriter(System.err, true),
					new InputStreamReader(System.in));
			switch (gridOkay) {
				case CONNECTION_FAILED: {
					// this means we are not on the grid when we think we should have been able to
					// get on.
					System.err.println("Failed to build a connection to the grid.");
					System.err.println("You can try to connect manually using the configured command, e.g.:");
					String connectCmd = ContainerProperties.getContainerProperties().getConnectionCommand();
					System.err.println("  grid connect " + connectCmd);
					break;
				}
				case CONNECTION_ALREADY_GOOD: {
					// nothing was wrong, just proceed normally.
					if (_logger.isDebugEnabled())
						_logger.debug("grid connection was already present.");
					break;
				}
				default: // default should never be seen, but must be included as a case.
				case CONNECTION_MEANS_UNKNOWN: {
					// we are not going to get connected at this point. we don't know how. this had
					// better be a bootstrap.
					if (_logger.isDebugEnabled())
						_logger.debug("steps for grid connection were not present; assuming bootstrap.");
					break;
				}
				case CONNECTION_GOOD_NOW: {
					// so we were not connected before, but we are now.
					_logger.info("grid connection automatically created with: grid connect "
						+ ContainerProperties.getContainerProperties().getConnectionCommand());
					break;
				}
			}
		}

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

		if (args.length == 0 || (args.length == 1 && args[0].equals("shell")))
			while (true) {
				try {
					lastExit = doShell(in);
					break;
				} catch (ReloadShellException e) {
				}
			}
		else {
			try {
				doNonShell(in, args);
			} catch (ReloadShellException re) {
			}
		}
		OSGiSupport.shutDownFramework();
		System.exit(lastExit);
	}

	static private int doShell(BufferedReader in) throws ReloadShellException
	{
		CommandLineRunner runner = new CommandLineRunner();
		ShellPrompt prompt = UserPreferences.preferences().shellPrompt();

		int lastExit = 0;

		while (true) {
			// Acquire a new context for this command
			LoggingContext.adoptNewContext();

			try {
				System.out.format("%s ", prompt);
				System.out.flush();

				String line = null;
				try {
					line = in.readLine();
					if (line == null)
						break;
				} catch (IOException ioe) {
					ExceptionHandlerManager.getExceptionHandler().handleException(ioe, new OutputStreamWriter(System.err));
					break;
				}

				if (DLogDatabase.getLocalConnector() != null)
					DLogDatabase.getLocalConnector().recordCommand(line);

				String[] args = CommandLineFormer.formCommandLine(line);
				if (args.length == 0)
					continue;

				int firstArg = 0;
				String toolName = args[0];

				// shell commands
				boolean displayElapsed = false;
				if ((toolName.compareToIgnoreCase("quit") == 0) || (toolName.compareToIgnoreCase("exit") == 0))
					break;
				else if (toolName.compareToIgnoreCase("time") == 0) {
					displayElapsed = true;
					firstArg = 1;
					if (args.length > 1)
						toolName = args[1];
					else
						continue;
				}

				long startTime = System.currentTimeMillis();
				String[] passArgs = new String[args.length - firstArg];
				System.arraycopy(args, firstArg, passArgs, 0, passArgs.length);

				lastExit =
					runner.runCommand(passArgs, new PrintWriter(System.out, true), new PrintWriter(System.err, true), in);

				long elapsed = System.currentTimeMillis() - startTime;

				if (displayElapsed) {
					long hours = elapsed / (60 * 60 * 1000);
					if (hours != 0)
						elapsed = elapsed % (hours * 60 * 60 * 1000);
					long minutes = elapsed / (60 * 1000);
					if (minutes != 0)
						elapsed = elapsed % (minutes * 60 * 1000);
					long seconds = elapsed / (1000);
					if (seconds != 0)
						elapsed = elapsed % (seconds * 1000);
					System.out.println("Elapsed time: " + hours + "h:" + minutes + "m:" + seconds + "s." + elapsed + "ms");
				}
			} catch (ReloadShellException e) {
				throw e;
			} catch (Throwable cause) {
				int toReturn =
					ExceptionHandlerManager.getExceptionHandler().handleException(cause, new OutputStreamWriter(System.err));
				if (toReturn != 0)
					return toReturn;
				lastExit = 1; // at least let them know we were displeased, if this is the last
								// command in the loop.
			} finally {
				// Get ready for the next time through
				LoggingContext.releaseCurrentLoggingContext();
			}
		}
		return lastExit;
	}

	static private void doNonShell(BufferedReader in, String[] args) throws ReloadShellException
	{
		CommandLineRunner runner = new CommandLineRunner();

		try {
			System.exit(runner.runCommand(args, new PrintWriter(System.out, true), new PrintWriter(System.err, true), in));
		} catch (ReloadShellException re) {
			throw re;
		} catch (Throwable cause) {
			ExceptionHandlerManager.getExceptionHandler().handleException(cause, new OutputStreamWriter(System.err));
			System.exit(1);
		}
	}
}
