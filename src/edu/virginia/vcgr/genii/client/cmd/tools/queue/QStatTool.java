package edu.virginia.vcgr.genii.client.cmd.tools.queue;

import java.util.ArrayList;
import java.util.Map;

import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.cmd.tools.BaseGridTool;
import edu.virginia.vcgr.genii.client.queue.JobInformation;
import edu.virginia.vcgr.genii.client.queue.JobTicket;
import edu.virginia.vcgr.genii.client.queue.QueueManipulator;

public class QStatTool extends BaseGridTool
{
	static private final String _DESCRIPTION = 
		"Shows the status of a given job or jobs in the queue.";
	static private final String _USAGE =
		"qstat <queue-path> [<job-ticket0>...<job-ticketn>]";
	
	public QStatTool()
	{
		super(_DESCRIPTION, _USAGE, false);
	}
	
	@Override
	protected int runCommand() throws Throwable
	{
		ArrayList<JobTicket> tickets;
		QueueManipulator manipulator = new QueueManipulator(getArgument(0));
		
		if (numArguments() > 1)
		{
			tickets = new ArrayList<JobTicket>(numArguments() - 1);
			for (String arg : getArguments().subList(1, numArguments()))
			{
				tickets.add(new JobTicket(arg));
			}
		} else
			tickets = null;
		
		Map<JobTicket, JobInformation> info = manipulator.status(tickets);
		printHeader();
		for (JobTicket ticket : info.keySet())
		{
			printJobInformation(info.get(ticket));
		}
		
		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
		if (numArguments() < 1)
			throw new InvalidToolUsageException("Must supply a queue path.");
	}
	
	private void printHeader()
	{
		stdout.println(String.format(
			"%1$-36s   %2$-17s   %3$-8s   %4$-8s", 
			"Ticket", "Submit Time", "State", "Attempts"));
	}
	
	static private final String _FORMAT =
		"%1$-36s   %2$tH:%2$tM %2$td %2$tb %2$tY   %3$-8s   %4$-8d";
	
	private void printJobInformation(JobInformation jobInfo)
	{
		stdout.println(String.format(
			_FORMAT, jobInfo.getTicket(), jobInfo.getSubmitTime(),
			jobInfo.getJobState(), jobInfo.getFailedAttempts() + 1));
	}
}