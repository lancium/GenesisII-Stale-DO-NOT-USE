package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;

import org.morgan.util.io.StreamUtils;

import edu.virginia.vcgr.genii.client.byteio.ByteIOConstants;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.gpath.GeniiPath;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;

public class CatTool extends BaseGridTool
{
	static final private String _DESCRIPTION = "config/tooldocs/description/dcat";
	static final private String _USAGE = "config/tooldocs/usage/ucat";
	static final private String _MANPAGE = "config/tooldocs/man/cat";

	public CatTool()
	{
		super(new LoadFileResource(_DESCRIPTION), new LoadFileResource(_USAGE), false, ToolCategory.DATA);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException
	{
		for (int lcv = 0; lcv < numArguments(); lcv++) {
			/*
			 * 2016-06-03 by ASG Updated to allow wildcards in arguments. Got tired of not having the capability
			 */
			Collection<GeniiPath.PathMixIn> paths = GeniiPath.pathExpander(getArgument(lcv));
			if (paths == null) {
				String msg = "Path does not exist or is not accessible: " + getArgument(lcv);
				stdout.println(msg);
				continue;
			}
			for (GeniiPath.PathMixIn gpath : paths) {
				if (gpath._rns != null) {
					// Do what needs doing
					cat(gpath._rns.pwd());
				} else {
					// Do what needs doing
					cat("local:" + gpath._file.getCanonicalPath());
				}
			}
			// End ASG updates
			// cat(getArgument(lcv));
		}
		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
	}

	public void cat(String filePath) throws IOException
	{
		char[] data = new char[ByteIOConstants.PREFERRED_SIMPLE_XFER_BLOCK_SIZE];
		int read;
		InputStream in = null;
		InputStreamReader reader = null;

		try {
			GeniiPath path = new GeniiPath(filePath);
			in = path.openInputStream();
			reader = new InputStreamReader(in);

			while ((read = reader.read(data, 0, data.length)) > 0) {
				stdout.write(data, 0, read);
				stdout.flush();
			}
		} finally {
			StreamUtils.close(reader);
			StreamUtils.close(in);
		}
	}
}