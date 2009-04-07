package edu.virginia.vcgr.genii.container;

import java.text.ParseException;

import org.apache.commons.logging.Log;
import org.mortbay.log.LogFactory;

import edu.virginia.vcgr.genii.client.configuration.DeploymentName;
import edu.virginia.vcgr.genii.client.configuration.Installation;
import edu.virginia.vcgr.genii.client.configuration.RejuvenationConstants;
import edu.virginia.vcgr.genii.client.utils.units.Duration;

public class SoftwareRejuvenator
{
	static private Log _logger = LogFactory.getLog(SoftwareRejuvenator.class);
	
	static public void startRejuvenator()
	{
		String rejuvCycleString =
			Installation.getDeployment(new DeploymentName(
				)).softwareRejuvenationProperties().getProperty(
				RejuvenationConstants.SOFTWARE_REJUVENATION_CYCLE_PROP);
		if (rejuvCycleString == null)
		{
			_logger.info("No software rejuvenation cycle given -- " +
				"skipping rejuvenation.");
			return;
		}
		
		try
		{
			Duration d = Duration.parse(rejuvCycleString);
			Thread th = new Thread(new Rejuvenator(System.currentTimeMillis() 
				+ d.getMilliseconds()), "Software Rejuvenator Thread");
			th.setDaemon(true);
			th.start();
			_logger.info(String.format(
				"Started software rejuvenator with cycle of \"%s\".",
				rejuvCycleString));
		}
		catch (ParseException e)
		{
			_logger.warn(String.format(
				"Unable to parse softare rejuvenation cycle string \"%s\".",
				rejuvCycleString), e);
		}
	}
	
	static private class Rejuvenator implements Runnable
	{
		private long _endTime;
		
		public Rejuvenator(long endTime)
		{
			_endTime = endTime;
		}
		
		@Override
		public void run()
		{
			long now = System.currentTimeMillis();

			while (now <= _endTime)
			{
				try 
				{
					Thread.sleep(_endTime - now); 
				}
				catch (InterruptedException ie)
				{		
				}
				
				now = System.currentTimeMillis();
			}
			
			_logger.info(
				"Software rejuvenator exiting the container for a restart.");
			System.exit(42);
		}
	}
}