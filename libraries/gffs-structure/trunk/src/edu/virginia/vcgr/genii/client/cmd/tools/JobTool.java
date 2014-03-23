package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.morgan.util.io.StreamUtils;

import edu.virginia.vcgr.externalapp.EditableFile;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.gpath.GeniiPath;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.gjt.BlockingJobToolListener;

public class JobTool extends BaseGridTool
{
	static final private String USAGE = "config/tooldocs/usage/ujob-tool";
	static final private String DESCRIPTION = "config/tooldocs/description/djob-tool";
	static final private String _MANPAGE = "config/tooldocs/man/job-tool";

	@Override
	protected void verify() throws ToolException
	{
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException,
		AuthZSecurityException, IOException, ResourcePropertyException
	{
		Collection<EditableFile> files = new ArrayList<EditableFile>(numArguments());
		for (String arg : getArguments())
			files.add(EditableFile.createEditableFile(new GeniiPath(arg)));

		Collection<File> tmpFiles = new ArrayList<File>(files.size());
		for (EditableFile file : files)
			tmpFiles.add(file.file());

		BlockingJobToolListener waiter = new BlockingJobToolListener();
		edu.virginia.vcgr.genii.gjt.JobTool.launch(tmpFiles, null, waiter);
		try {
			waiter.join();
		} catch (InterruptedException e) {
			// nothing.
		}

		for (EditableFile file : files)
			StreamUtils.close(file);
		return 0;
	}

	public JobTool()
	{

		super(new LoadFileResource(DESCRIPTION), new LoadFileResource(USAGE), false, ToolCategory.EXECUTION);
		addManPage(new LoadFileResource(_MANPAGE));
	}
}