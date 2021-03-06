package edu.virginia.vcgr.genii.container.q2;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.ggf.jsdl.JobDefinition_Type;
import org.ggf.jsdl.JobDescription_Type;
import org.ggf.jsdl.JobIdentification_Type;

/**
 * Just a collection (size 1 now) of useful utilities used by the queue.
 * 
 * @author mmm2a
 */
public class QueueUtils
{
	/**
	 * We occassionally need to convert a date object into a calendar object (SOAP uses calendars, everyone else uses dates).
	 * 
	 * @param d
	 *            THe date to convert
	 * @return The new calendar instance of the date.
	 */
	static public Calendar convert(Date d)
	{
		if (d == null)
			return null;

		Calendar c = Calendar.getInstance();
		c.setTime(d);
		c.setTimeZone(TimeZone.getTimeZone("GMT"));
		return c;
	}

	/*
	 * This returns the job name of the JobDefinition_Type object passed in. Since the job name should be universal across multiple Job
	 * Descriptions, it is easiest to return the first one.
	 * 
	 * @param jobDefinition_Type The JobDefinition_Type object
	 * 
	 * @return The name of the job as a String
	 */
	static public String getJobName(JobDefinition_Type jsdl)
	{
		if (jsdl != null) {

			JobDescription_Type desc = jsdl.getJobDescription(0);
			if (desc != null) {
				JobIdentification_Type id = desc.getJobIdentification();
				if (id != null)
					return id.getJobName();
			}
		}

		return null;
	}
}