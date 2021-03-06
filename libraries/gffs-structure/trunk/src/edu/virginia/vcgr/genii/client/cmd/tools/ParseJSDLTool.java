package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import org.ggf.jsdl.JobDefinition_Type;
import org.xml.sax.InputSource;

import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.gpath.GeniiPath;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.jsdl.JSDLException;
import edu.virginia.vcgr.genii.client.jsdl.JSDLInterpreter;
import edu.virginia.vcgr.genii.client.jsdl.JobRequest;
import edu.virginia.vcgr.genii.client.jsdl.parser.ExecutionProvider;
import edu.virginia.vcgr.genii.client.jsdl.personality.PersonalityProvider;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.client.ser.ObjectDeserializer;

public class ParseJSDLTool extends BaseGridTool
{

	static private final String _DESCRIPTION = "config/tooldocs/description/dparseJSDL";
	static private final String _USAGE = "config/tooldocs/usage/uparseJSDL";
	static private final String _MANPAGE = "config/tooldocs/man/parseJSDL";

	public ParseJSDLTool()
	{
		super(new LoadFileResource(_DESCRIPTION), new LoadFileResource(_USAGE), false, ToolCategory.EXECUTION);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException
	{
		// get the local identity's key material (or create one if necessary)
		ContextManager.getCurrentOrMakeNewContext();

		OutputStream out = null;
		InputStream in = null;

		GeniiPath source = new GeniiPath(getArgument(0));
		GeniiPath dest = new GeniiPath(getArgument(1));
		if (!source.exists())
			throw new FileNotFoundException(String.format("Unable to find source file %s!", source));
		if (!source.isFile())
			throw new IOException(String.format("Source path %s is not a file!", source));

		in = source.openInputStream();
		out = dest.openOutputStream();

		// Parse jsdl
		JobDefinition_Type jsdl = (JobDefinition_Type) ObjectDeserializer.deserialize(new InputSource(in), JobDefinition_Type.class);
		PersonalityProvider provider = new ExecutionProvider();
		JobRequest tJob;
		try {
			tJob = (JobRequest) JSDLInterpreter.interpretJSDL(provider, jsdl);
		} catch (JSDLException e) {
			throw new ToolException("jsdl error: " + e.getLocalizedMessage(), e);
		}

		ObjectOutputStream oOut = new ObjectOutputStream(out);
		oOut.writeObject(tJob);

		oOut.close();
		out.close();
		in.close();

		stdout.println("Job written to file");
		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
		if (numArguments() != 2)
			throw new InvalidToolUsageException();
	}

}
