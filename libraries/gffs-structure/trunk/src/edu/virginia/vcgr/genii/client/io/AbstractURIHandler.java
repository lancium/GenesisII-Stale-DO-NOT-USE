package edu.virginia.vcgr.genii.client.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.morgan.util.io.DataTransferStatistics;
import org.morgan.util.io.StreamUtils;

import edu.virginia.vcgr.genii.security.credentials.identity.UsernamePasswordIdentity;

public abstract class AbstractURIHandler implements IURIHandler
{
	static private Log _logger = LogFactory.getLog(AbstractURIHandler.class);

	static public final int NUM_RETRIES = 5;
	static public final long BACKOFF = 8000L;

	static private Random GENERATOR = new Random();

	public abstract InputStream openInputStream(URI source, UsernamePasswordIdentity credential) throws IOException;

	public abstract OutputStream openOutputStream(URI target, UsernamePasswordIdentity credential) throws IOException;
	
	@Override
	public abstract String getLocalPath(URI uri) throws IOException;

	static private long generateBackoff(int attempt)
	{
		long twitter = (long) ((GENERATOR.nextFloat() - 0.5) * (BACKOFF << attempt));
		return (BACKOFF << attempt) + twitter;
	}

	@Override
	final public DataTransferStatistics get(URI source, File target, UsernamePasswordIdentity credential) throws IOException
	{
		IOException lastException = null;

		// hmmm: support directories here also!!!!

		// hmmm: right here does seem like the place to fork;
		// the existing methods are all calling getinternal which goes directory to creating streams and expecting that to work.

		// hmmm: we need new methods on uri handler for copying a directory!

		int attempt = 0;

		for (attempt = 0; attempt < NUM_RETRIES; attempt++) {
			try {
				return getInternal(source, target, credential);
			} catch (FileNotFoundException fnfe) {
				lastException = fnfe;
				break;
			} catch (IOException ioe) {
				lastException = ioe;
			}

			try {
				Thread.sleep(generateBackoff(attempt));
			} catch (Throwable cause) {
			}
		}

		throw lastException;
	}

	@Override
	final public DataTransferStatistics put(File source, URI target, UsernamePasswordIdentity credential) throws IOException
	{
		IOException lastException = null;

		int attempt = 0;

		for (attempt = 0; attempt < NUM_RETRIES; attempt++) {
			try {
				return putInternal(source, target, credential);
			} catch (IOException ioe) {
				lastException = ioe;
			}

			try {
				Thread.sleep(generateBackoff(attempt));
			} catch (Throwable cause) {
			}
		}

		throw lastException;
	}

	protected DataTransferStatistics getInternal(URI source, File target, UsernamePasswordIdentity credential) throws IOException
	{
		if (_logger.isDebugEnabled())
			_logger.debug("getInternal: source='" +  source.getSchemeSpecificPart() + "' target='" + target.getAbsolutePath()+ "'");
		
		if (isDirectory(source)) {
			return copyDirectoryDown(source, target, credential);
		} else {

			FileOutputStream fos = null;
			InputStream in = null;

			try {
				fos = new FileOutputStream(target);
				in = openInputStream(source, credential);
				return StreamUtils.copyStream(in, fos);
			} finally {
				StreamUtils.close(fos);
				StreamUtils.close(in);
			}
		}
	}

	protected DataTransferStatistics putInternal(File source, URI target, UsernamePasswordIdentity credential) throws IOException
	{
		if (_logger.isDebugEnabled())
			_logger.debug("putInternal: source='" +  source.getAbsolutePath() + "' target='" + target.getSchemeSpecificPart()+ "'");

		if (source.isDirectory()) {
			return copyDirectoryUp(source, target, credential);
		} else {

			FileInputStream fin = null;
			OutputStream out = null;

			try {
				fin = new FileInputStream(source);
				out = openOutputStream(target, credential);

				DataTransferStatistics toReturn = StreamUtils.copyStream(fin, out);
				HttpURLConnection conn = JavaURIAsURLHandler.getActiveConns().get(target);
				if (conn != null) {
					String msg = conn.getResponseMessage();
					_logger.debug("web server response: " + msg);
					// disconnect so everything is flushed and completed.
					conn.disconnect();
					// clean out the connection info.
					JavaURIAsURLHandler.getActiveConns().remove(target);
				}
				return toReturn;
			} finally {
				StreamUtils.close(fin);
				StreamUtils.close(out);
			}
		}
	}
}
