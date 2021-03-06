package edu.virginia.vcgr.genii.client.cmd;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.Writer;

import org.apache.axis.AxisFault;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.oasis_open.wsrf.basefaults.BaseFaultType;

import edu.virginia.vcgr.genii.client.fuse.exceptions.FuseNoSuchEntryException;
import edu.virginia.vcgr.genii.client.security.PermissionDeniedException;
import edu.virginia.vcgr.genii.client.utils.DetailedLogger;

public class SimpleExceptionHandler implements IExceptionHandler
{
	static private Log _logger = LogFactory.getLog(SimpleExceptionHandler.class);

	public static int performBaseExceptionHandling(Throwable cause, Writer eStream)
	{
		PrintWriter errorStream = new PrintWriter(eStream);

		String tab = "";
		StringBuilder builder = new StringBuilder();

		int toReturn = 0; // assume we can recover by default.

		// before we do anything else, add the whole thing to the detailed log.
		DetailedLogger.detailed().info("processing exception: " + cause.getLocalizedMessage() + "\n" + ExceptionUtils.getStackTrace(cause));

		/*
		 * remembers the last message we printed so we don't echo duplicates (which can happen when exceptions are wrapped repeatedly at
		 * different levels).
		 */
		String lastMessage = null;

		while (cause != null) {
			String nextToAdd = null;
			if (cause instanceof java.lang.OutOfMemoryError) {
				builder.append(tab + "The client has run out of memory.  This could be fixed by increasing\n"
					+ "the maximum memory allowed for the JVM.  On Linux, try changing -Xmx512M\n"
					+ "to -Xmx1G or more.  On Windows, pass the memory to the grid launcher with a\n"
					+ "-J flag first, e.g. grid.exe -J-Xmx1G\n");
				errorStream.print(builder);
				errorStream.flush();
				_logger.error(builder, cause);
				// re-throw to cause the grid client to exit.
				throw new java.lang.OutOfMemoryError();
			} else if (cause instanceof NullPointerException) {
				nextToAdd = "Internal Genesis II Error -- Null Pointer Exception\n";
			} else if (cause instanceof FileNotFoundException) {
				nextToAdd = "File Not Found:  " + cause.getLocalizedMessage() + "\n";
			} else if (cause instanceof BaseFaultType) {
				nextToAdd = ((BaseFaultType) cause).getDescription(0).get_value() + "\n";
			} else if (cause instanceof FuseNoSuchEntryException) {
				nextToAdd = "Fuse exception: " + cause.getLocalizedMessage() + "\n";
			} else if (cause instanceof AxisFault) {
				String message = cause.getLocalizedMessage();

				/* Check to see if it's a permission denied exception */
				String operation = null;
				String failedAsset = null;
				operation = PermissionDeniedException.extractMethodName(cause.getMessage());
				failedAsset = PermissionDeniedException.extractAssetDenied(cause.getMessage());
				if ((operation != null) && (failedAsset != null)) {
					nextToAdd = "Permission denied on \"" + failedAsset + "\" (in method \"" + operation + "\").\n";
				} else {
					nextToAdd = message + "\n";
				}
			} else {
				nextToAdd = cause.getLocalizedMessage() + "\n";
			}
			if ((lastMessage == null) || !lastMessage.equals(nextToAdd)) {
				// we won't be repeating ourselves, so add that line.
				builder.append(tab + nextToAdd);
				// remember what we just added.
				lastMessage = nextToAdd;
				// increase the indentation level.
				tab = tab + "    ";
			}
			cause = cause.getCause();
		}

		errorStream.print(builder);
		errorStream.flush();
		_logger.error(builder, cause);

		return toReturn;
	}

	public int handleException(Throwable cause, Writer eStream)
	{
		return performBaseExceptionHandling(cause, eStream);
	}
}
