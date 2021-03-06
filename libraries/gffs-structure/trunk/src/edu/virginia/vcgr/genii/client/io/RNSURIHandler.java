/*
 * Copyright 2006 University of Virginia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package edu.virginia.vcgr.genii.client.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.morgan.util.io.DataTransferStatistics;

import edu.virginia.vcgr.genii.client.byteio.ByteIOStreamFactory;
import edu.virginia.vcgr.genii.client.rns.CopyMachine;
import edu.virginia.vcgr.genii.client.rns.PathOutcome;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathAlreadyExistsException;
import edu.virginia.vcgr.genii.client.rns.RNSPathDoesNotExistException;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.security.credentials.identity.UsernamePasswordIdentity;

public class RNSURIHandler extends AbstractURIHandler
// implements IURIHandler
{
	static private Log _logger = LogFactory.getLog(RNSURIHandler.class);

	static private final String[] _HANDLED_PROTOCOLS = new String[] { "rns" };

	public boolean canRead(String uriScheme)
	{
		return uriScheme != null && uriScheme.equals(_HANDLED_PROTOCOLS[0]);
	}

	public boolean canWrite(String uriScheme)
	{
		return uriScheme != null && uriScheme.equals(_HANDLED_PROTOCOLS[0]);
	}

	public String[] getHandledProtocols()
	{
		return _HANDLED_PROTOCOLS;
	}

	public InputStream openInputStream(URI uri, UsernamePasswordIdentity credential) throws IOException
	{
		if (credential != null)
			throw new IOException("Don't know how to perform rns: with a credential.");

		try {
			RNSPath path = RNSPath.getCurrent();
			path = path.lookup(uri.getSchemeSpecificPart(), RNSPathQueryFlags.MUST_EXIST);

			if (_logger.isDebugEnabled())
				_logger.debug(String.format("Staging a file in from \"%s\".", path.pwd()));

			return ByteIOStreamFactory.createInputStream(path);
		} catch (RNSException re) {
			throw new IOException(re.getMessage());
		}
	}

	public OutputStream openOutputStream(URI uri, UsernamePasswordIdentity credential) throws IOException
	{
		if (credential != null)
			throw new IOException("Don't know how to perform rns: with a credential.");

		try {
			RNSPath path = RNSPath.getCurrent();
			path = path.lookup(uri.getSchemeSpecificPart(), RNSPathQueryFlags.DONT_CARE);

			if (_logger.isDebugEnabled())
				_logger.debug(String.format("Staging a file out to \"%s\".", path.pwd()));

			return ByteIOStreamFactory.createOutputStream(path);
		} catch (RNSException re) {
			throw new IOException(re.getMessage());
		}
	}

	@Override
	public boolean isDirectory(URI uri)
	{
		RNSPath path = RNSPath.getCurrent();
		try {
			path = path.lookup(uri.getSchemeSpecificPart(), RNSPathQueryFlags.MUST_EXIST);
		} catch (RNSPathDoesNotExistException | RNSPathAlreadyExistsException e) {
			// we return false, since we couldn't find the uri probably.
			return false;
		}
		if (_logger.isDebugEnabled())
			_logger.debug(String.format("Staging a file in from \"%s\".", path.pwd()));

		return path.isRNS();
	}

	@Override
	public String getLocalPath(URI uri) throws IOException
	{
		RNSPath path = RNSPath.getCurrent();
		try {
			path = path.lookup(uri.getSchemeSpecificPart(), RNSPathQueryFlags.MUST_EXIST);
		} catch (RNSPathDoesNotExistException | RNSPathAlreadyExistsException e) {
			throw new IOException("exception in getLocalPath", e);
		}
		return path.pwd();
	}

	@Override
	public DataTransferStatistics copyDirectoryDown(URI source, File target, UsernamePasswordIdentity credential) throws IOException
	{
		RNSPath path = RNSPath.getCurrent();
		try {
			path = path.lookup(source.getSchemeSpecificPart(), RNSPathQueryFlags.MUST_EXIST);
		} catch (RNSPathDoesNotExistException | RNSPathAlreadyExistsException e) {
			throw new IOException("exception in copyDirectoryDown", e);
		}
		if (_logger.isDebugEnabled())
			_logger.debug(String.format("copying directory down from \"%s\" to \"%s\".", path.pwd(), target.getAbsolutePath()));

		CopyMachine cm = new CopyMachine(path.pwd(), "local:" + target.getAbsolutePath(), null, false, null, null);
		DataTransferStatistics stats = DataTransferStatistics.startTransfer();

		PathOutcome outcome = cm.copyTree();
		if (outcome != PathOutcome.OUTCOME_SUCCESS) {
			throw new IOException("failure in tree copy operation: " + PathOutcome.outcomeText(outcome));
		}
		stats.finishTransfer();

		return stats;
	}

	@Override
	public DataTransferStatistics copyDirectoryUp(File source, URI target, UsernamePasswordIdentity credential) throws IOException
	{
		RNSPath path = RNSPath.getCurrent();
		try {
			path = path.lookup(target.getSchemeSpecificPart(), RNSPathQueryFlags.DONT_CARE);
		} catch (RNSPathDoesNotExistException e) {
			_logger.debug("directory does not exist yet, but that's okay: " + target.getSchemeSpecificPart());
		} catch (RNSPathAlreadyExistsException e) {
			// should not be allowed to happen.
			_logger.debug("unexpected already exists exception on: " + target.getSchemeSpecificPart());
		}
		if (_logger.isDebugEnabled())
			_logger.debug(String.format("copying directory up from \"%s\" to \"%s\".", source.getAbsolutePath(), path.pwd()));

		CopyMachine cm = new CopyMachine("local:" + source.getAbsolutePath(), path.pwd(), null, false, null, null);

		DataTransferStatistics stats = DataTransferStatistics.startTransfer();

		PathOutcome outcome = cm.copyTree();
		if (outcome != PathOutcome.OUTCOME_SUCCESS) {
			throw new IOException("failure in tree copy operation: " + PathOutcome.outcomeText(outcome));
		}
		stats.finishTransfer();
		return stats;
	}
}
