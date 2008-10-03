package edu.virginia.vcgr.genii.container.bes.execution.phases;

import java.io.File;
import java.io.Serializable;

import org.ggf.bes.factory.ActivityStateEnumeration;

import edu.virginia.vcgr.genii.client.bes.ActivityState;
import edu.virginia.vcgr.genii.container.bes.execution.ContinuableExecutionException;
import edu.virginia.vcgr.genii.container.bes.execution.ExecutionContext;

public class CleanupPhase extends AbstractExecutionPhase 
	implements Serializable
{
	static final long serialVersionUID = 0L;
	
	static final private String CLEANUP_STAGE = "cleanup";
	
	private File _fileToCleanup;
	
	public CleanupPhase(File fileToCleanup)
	{
		super(
			new ActivityState(
				ActivityStateEnumeration.Running, CLEANUP_STAGE, false));
		
		_fileToCleanup = fileToCleanup;
	}
	
	private void removeFile(File f)
	{
		if (f.exists())
		{
			if (f.isDirectory())
			{
				for (File ff : f.listFiles())
					removeFile(ff);
			}
			
			f.delete();
		}
	}
	
	@Override
	public void execute(ExecutionContext context) throws Throwable
	{
		try
		{
			removeFile(_fileToCleanup);
		}
		catch (Throwable cause)
		{
			throw new ContinuableExecutionException(
				"A continuable exception has occurred while " +
					"running a BES activity.", cause);
		}
	}
}