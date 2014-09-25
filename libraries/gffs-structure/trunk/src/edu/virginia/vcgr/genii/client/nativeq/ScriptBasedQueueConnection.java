package edu.virginia.vcgr.genii.client.nativeq;

import java.net.URI;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.morgan.util.io.StreamUtils;
import org.morgan.util.Pair;

import edu.virginia.vcgr.genii.client.cmdLineManipulator.CmdLineManipulatorUtils;
import edu.virginia.vcgr.genii.client.bes.ResourceOverrides;
import edu.virginia.vcgr.genii.client.pwrapper.ProcessWrapper;
import edu.virginia.vcgr.genii.client.pwrapper.ProcessWrapperException;
import edu.virginia.vcgr.genii.client.pwrapper.ProcessWrapperFactory;
import edu.virginia.vcgr.genii.cmdLineManipulator.CmdLineManipulatorException;
import edu.virginia.vcgr.genii.cmdLineManipulator.config.CmdLineManipulatorConfiguration;
import edu.virginia.vcgr.jsdl.OperatingSystemNames;

public abstract class ScriptBasedQueueConnection<ProviderConfigType extends ScriptBasedQueueConfiguration> extends
	AbstractNativeQueueConnection<ProviderConfigType>
{
	static private Log _logger = LogFactory.getLog(ScriptBasedQueueConnection.class);

	static public final String PATH_TO_DEV_NULL = "/dev/null";

	static public final String QUEUE_SCRIPT_RESULT_FILENAME = "queue.script.result";

	protected ScriptBasedQueueConnection(File workingDirectory, ResourceOverrides resourceOverrides,
		CmdLineManipulatorConfiguration cmdLineManipulatorConf, NativeQueueConfiguration queueConfig,
		ProviderConfigType providerConfig) throws NativeQueueException
	{
		super(workingDirectory, resourceOverrides, cmdLineManipulatorConf, queueConfig, providerConfig);
	}

	@Override
	protected void initialize() throws NativeQueueException
	{
		super.initialize();

		String submitScriptName = providerConfiguration().submitScriptName();

		if (submitScriptName != null && submitScriptName.contains("/"))
			throw new IllegalArgumentException("SubmitScriptName must not contain any " + "path characters (such as '/').");

		File bashBinary = providerConfiguration().bashBinary(new File("/bin/bash"));
		checkBinary(bashBinary);
	}

	final protected File getSubmitScript(File workingDirectory) throws IOException
	{
		String submitScriptName = providerConfiguration().submitScriptName();

		if (submitScriptName == null)
			return File.createTempFile("qsub", ".sh", workingDirectory);

		return new File(workingDirectory, submitScriptName);
	}

	final protected File getBashBinary()
	{
		return providerConfiguration().bashBinary(new File("/bin/bash"));
	}

	final protected Pair<File, List<String>> generateSubmitScript(File workingDirectory, ApplicationDescription application)
		throws NativeQueueException
	{
		PrintStream ps = null;
		List<String> finalCmdLine;
		try {
			File submitScript = getSubmitScript(workingDirectory);

			ps = new PrintStream(submitScript);
			generateScriptHeader(ps, workingDirectory, application);
			generateQueueHeaders(ps, workingDirectory, application);
			generateQueueApplicationHeader(ps, workingDirectory, application);
			finalCmdLine = generateApplicationBody(ps, workingDirectory, application);
			generateQueueApplicationFooter(ps, workingDirectory, application);
			generateScriptFooter(ps, workingDirectory, application);

			submitScript.setExecutable(true, true);
			return new Pair<File, List<String>>(submitScript, finalCmdLine);
		} catch (IOException ioe) {
			throw new NativeQueueException("Unable to generate submit script.", ioe);
		} finally {
			StreamUtils.close(ps);
		}
	}

	protected void generateScriptHeader(PrintStream script, File workingDirectory, ApplicationDescription application)
		throws NativeQueueException, IOException
	{
		script.format("#!%s\n\n", getBashBinary().getAbsolutePath());
	}

	protected void generateQueueHeaders(PrintStream script, File workingDirectory, ApplicationDescription application)
		throws NativeQueueException, IOException
	{
	}

	protected void
		generateQueueApplicationHeader(PrintStream script, File workingDirectory, ApplicationDescription application)
			throws NativeQueueException, IOException
	{
		Set<UnixSignals> signals = queueConfiguration().trapSignals();

		if (signals.size() > 0) {
			script.println("function signalTrap()");
			script.println("{");
			script.println("\techo \"Caught a signal -- killing process group.\" >&2");
			script.println("\tQUEUE_SCRIPT_RESULT=257");
			script.format("echo $QUEUE_SCRIPT_RESULT > %s.tmp\n", QUEUE_SCRIPT_RESULT_FILENAME);
			script.format("mv %1$s.tmp %1$s\n", QUEUE_SCRIPT_RESULT_FILENAME);
			script.println("\tkill -9 0");
			script.println("}");
			script.println();
			for (UnixSignals signal : signals)
				script.format("trap signalTrap %s\n", signal);
		}

		script.format("export QUEUE_SCRIPT_RESULT=0\n");
	}

	protected void
		generateQueueApplicationFooter(PrintStream script, File workingDirectory, ApplicationDescription application)
			throws NativeQueueException, IOException
	{
		Set<UnixSignals> signals = queueConfiguration().trapSignals();

		if (signals.size() > 0) {
			script.println("QUEUE_JOB_ID=%%");
			script.println("wait %$QUEUE_JOB_ID");
			script.println("export QUEUE_SCRIPT_RESULT=$?");
		} else
			script.format("\nexport QUEUE_SCRIPT_RESULT=$?\n");
	}

	protected List<String>
		generateApplicationBody(PrintStream script, File workingDirectory, ApplicationDescription application)
			throws NativeQueueException, IOException
	{
		Set<UnixSignals> signals = queueConfiguration().trapSignals();
		List<String> newCmdLine = new Vector<String>();

		script.format("cd \"%s\"\n", workingDirectory.getAbsolutePath());

		String execName = application.getExecutableName();
		if (!execName.contains("/"))
			execName = String.format("./%s", execName);

		try {
			ResourceOverrides overrides = resourceOverrides();
			if (overrides == null)
				overrides = new ResourceOverrides();

			ProcessWrapper wrapper =
				ProcessWrapperFactory.createWrapper(getCommonDirectory(), overrides.operatingSystemName(),
					overrides.cpuArchitecture());

			// assemble job properties for cmdLineManipulators
			Map<String, Object> jobProperties = new HashMap<String, Object>();
			CmdLineManipulatorUtils.addBasicJobProperties(jobProperties, execName, application.getArguments());

			OperatingSystemNames desiredOperatingSystemType = overrides.operatingSystemName();
			OperatingSystemNames operatingSystemType =
				(desiredOperatingSystemType != null) ? desiredOperatingSystemType : OperatingSystemNames
					.getCurrentOperatingSystem();

			File stdoutRedirect = application.getStdoutRedirect(workingDirectory);
			if (stdoutRedirect == null && operatingSystemType == OperatingSystemNames.LINUX) {
				stdoutRedirect = new File(PATH_TO_DEV_NULL);
			}
			File stderrRedirect = application.getStderrRedirect(workingDirectory);
			if (stderrRedirect == null && operatingSystemType == OperatingSystemNames.LINUX) {
				stderrRedirect = new File(PATH_TO_DEV_NULL);
			}

			//VANA

			URI variation = application.getSPMDVariation();
			if (variation != null) {
				stdoutRedirect = null;
				stderrRedirect = null;
			}

			//VANA

			CmdLineManipulatorUtils.addEnvProperties(jobProperties, application.getFuseMountPoint(),
				application.getEnvironment(), workingDirectory, application.getStdinRedirect(workingDirectory), stdoutRedirect,
				stderrRedirect, application.getResourceUsagePath(), wrapper.getPathToWrapper());
			CmdLineManipulatorUtils.addSPMDJobProperties(jobProperties, application.getSPMDVariation(),
				application.getNumProcesses(), application.getNumProcessesPerHost());

			if (_logger.isDebugEnabled())
				_logger.debug("Trying to call cmdLine manipulators.");
			try {
				newCmdLine = CmdLineManipulatorUtils.callCmdLineManipulators(jobProperties, cmdLineManipulatorConf());
			} catch (CmdLineManipulatorException execption) {
				throw new NativeQueueException(String.format("CmdLine Manipulators failed: %s", execption.getMessage()));
			}

			// for testing only - default cmdLine format to compare to transform
			Vector<String> testCmdLine =
				wrapper.formCommandLine(application.getFuseMountPoint(), application.getEnvironment(), workingDirectory,
					application.getStdinRedirect(workingDirectory), stdoutRedirect, stderrRedirect,
					application.getResourceUsagePath(), execName,
					application.getArguments().toArray(new String[application.getArguments().size()]));
			if (_logger.isDebugEnabled())
				_logger.debug(String.format("Pervious cmdLine format with pwrapper only:\n %s", testCmdLine.toString()));

			boolean first = true;
			for (String element : newCmdLine) {
				if (!first)
					script.format(" ");
				first = false;
				script.format("\"%s\"", element);
			}
		} catch (ProcessWrapperException e) {
			throw new NativeQueueException("Unable to generate submission script.", e);
		}

		if (signals.size() > 0)
			script.print(" &");

		script.println();
		return newCmdLine;
	}

	protected void generateScriptFooter(PrintStream script, File workingDirectory, ApplicationDescription application)
		throws NativeQueueException, IOException
	{
		script.format("echo $QUEUE_SCRIPT_RESULT > %s.tmp\n", QUEUE_SCRIPT_RESULT_FILENAME);
		script.format("mv %1$s.tmp %1$s\n", QUEUE_SCRIPT_RESULT_FILENAME);
		script.println("exit $QUEUE_SCRIPT_RESULT");
	}

	protected String execute(ProcessBuilder builder) throws NativeQueueException
	{
		try {
			Collection<String> commandLine = builder.command();
			logProcess(commandLine);

			Process proc = builder.start();
			proc.getOutputStream().close();
			StreamCopier stdoutCopy = new StreamCopier(proc.getInputStream());
			StreamCopier stderrCopy = new StreamCopier(proc.getErrorStream());
			int result = proc.waitFor();

			logProcessResult(result, stdoutCopy, stderrCopy);

			if (result == 0)
				return stdoutCopy.getResult();

			throw new ScriptExecutionException(commandLine, result, stderrCopy.getResult());
		} catch (InterruptedException ie) {
			throw new NativeQueueException("Unable to execute command.", ie);
		} catch (IOException ioe) {
			throw new NativeQueueException("Unable to execute command.", ioe);
		}
	}

	static private class StreamCopier implements Runnable
	{
		private Thread _myThread;
		private InputStream _source;
		private ByteArrayOutputStream _sink;
		private IOException _exception = null;

		public StreamCopier(InputStream source)
		{
			_source = source;
			_sink = new ByteArrayOutputStream();

			_myThread = new Thread(this);
			_myThread.setDaemon(true);
			_myThread.setName("Stream Copier");

			_myThread.start();
		}

		public void run()
		{
			try {
				StreamUtils.copyStream(_source, _sink);
			} catch (IOException ioe) {
				_exception = ioe;
			}
		}

		public String getResult() throws IOException
		{
			try {
				_myThread.join();
				if (_exception != null)
					throw _exception;

				return new String(_sink.toByteArray());
			} catch (InterruptedException ie) {
				return null;
			}
		}
	}

	protected void parseResult(String result, ScriptLineParser... parsers) throws NativeQueueException
	{
		BufferedReader reader = new BufferedReader(new StringReader(result));
		String line;

		try {
			while ((line = reader.readLine()) != null) {
				for (ScriptLineParser parser : parsers) {
					for (Pattern pattern : parser.getHandledPatterns()) {
						Matcher matcher = pattern.matcher(line);
						if (matcher.matches()) {
							parser.parseLine(matcher);
							break;
						}
					}
				}
			}
		} catch (IOException ioe) {
			// This won't happen.
		}
	}

	static private void logProcess(Collection<String> commandLine)
	{
		if (_logger.isDebugEnabled())
			_logger.debug(new LazyEvaluatorList(commandLine));
	}

	static private void logProcessResult(int result, StreamCopier stdout, StreamCopier stderr)
	{
		if (_logger.isDebugEnabled())
			_logger.debug(new LazyEvaluatorResult(result, stdout, stderr));
	}

	static private class LazyEvaluatorResult
	{
		private int _result;
		private StreamCopier _stdout;
		private StreamCopier _stderr;

		public LazyEvaluatorResult(int result, StreamCopier stdout, StreamCopier stderr)
		{
			_result = result;
			_stdout = stdout;
			_stderr = stderr;
		}

		public String toString()
		{
			try {
				StringBuilder builder = new StringBuilder("Result:  " + _result + "\n");
				builder.append("Standard Out:\n");
				builder.append(_stdout.getResult());
				builder.append("\nStandard Err:\n");
				builder.append(_stderr.getResult());

				return builder.toString();
			} catch (IOException ioe) {
				return "Unable to get result from stream:  " + ioe.getLocalizedMessage();
			}
		}
	}

	static private class LazyEvaluatorList
	{
		private Collection<String> _list;

		public LazyEvaluatorList(Collection<String> list)
		{
			_list = list;
		}

		public String toString()
		{
			StringBuilder builder = new StringBuilder("Executing:  ");

			boolean first = true;
			for (String entry : _list) {
				if (!first)
					builder.append(" ");
				first = false;
				builder.append("\"" + entry + "\"");
			}

			return builder.toString();
		}
	}
}