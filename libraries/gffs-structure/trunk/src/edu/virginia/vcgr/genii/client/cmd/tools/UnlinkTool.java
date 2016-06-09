package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathDoesNotExistException;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.client.gpath.GeniiPath;
import edu.virginia.vcgr.genii.client.gpath.GeniiPathType;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;

public class UnlinkTool extends BaseGridTool
{
	static final private String _DESCRIPTION = "config/tooldocs/description/dunlink";
	static final private String _USAGE = "config/tooldocs/usage/uunlink";
	static final private String _MANPAGE = "config/tooldocs/man/unlink";

	public UnlinkTool()
	{
		super(new LoadFileResource(_DESCRIPTION), new LoadFileResource(_USAGE), false, ToolCategory.DATA);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException
	{
		RNSPath path = RNSPath.getCurrent();
		int toReturn = 0;
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
					if (gpath._rns.exists())
						gpath._rns.unlink();
				} else {
					File fPath = gpath._file;
					toReturn += unlink(fPath);
				}
			}
			// End ASG updates
		}
		/*
		 * ASG - old code - no wildcards GeniiPath gPath = new GeniiPath(getArgument(lcv)); if (gPath.pathType() == GeniiPathType.Local) {
		 * File fPath = new File(gPath.path()); toReturn += unlink(fPath); } else unlink(path, new GeniiPath(getArgument(lcv)).path()); }
		 */
		return toReturn;
	}

	@Override
	protected void verify() throws ToolException
	{
		if (numArguments() < 1)
			throw new InvalidToolUsageException();
	}

	static public void unlink(RNSPath currentPath, String filePath) throws RNSException, IOException
	{
		RNSPath file = currentPath.lookup(filePath, RNSPathQueryFlags.MUST_EXIST);

		file.unlink();
	}

	private int unlink(File path) throws RNSPathDoesNotExistException
	{
		if (!path.exists()) {
			throw new RNSPathDoesNotExistException(path.getName());
		}
		boolean success = path.delete();
		if (!success) {
			stderr.println(path.getName() + " failed to delete");
			return 1;
		}
		return 0;
	}
}