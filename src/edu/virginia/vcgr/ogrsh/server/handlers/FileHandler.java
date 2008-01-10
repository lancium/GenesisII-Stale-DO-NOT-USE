package edu.virginia.vcgr.ogrsh.server.handlers;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.morgan.util.GUID;
import org.morgan.util.configuration.ConfigurationException;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.resource.TypeInformation;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.client.security.GenesisIISecurityException;
import edu.virginia.vcgr.genii.common.rfactory.ResourceCreationFaultType;
import edu.virginia.vcgr.genii.container.common.SByteIOFactory;
import edu.virginia.vcgr.ogrsh.server.comm.OGRSHOperation;
import edu.virginia.vcgr.ogrsh.server.dir.StatBuffer;
import edu.virginia.vcgr.ogrsh.server.exceptions.OGRSHException;
import edu.virginia.vcgr.ogrsh.server.file.IFileDescriptor;
import edu.virginia.vcgr.ogrsh.server.file.RandomByteIOFileDescriptor;
import edu.virginia.vcgr.ogrsh.server.file.StreamableByteIOFileDescriptor;

public class FileHandler
{
	static private Log _logger = LogFactory.getLog(FileHandler.class);
	
	static public final int RDONLY = 00;
	static public final int WRONLY = 01;
	static public final int RDWR = 02;
	static public final int CREAT = 0100;
	static public final int EXCL = 0200;
	static public final int NOCTTY = 0400;
	static public final int TRUNC = 01000;
	static public final int APPEND = 02000;
	static public final int NONBLOCK = 04000;
	static public final int SYNC = 010000;
	static public final int ASYNC = 020000;
	static public final int DIRECT = 040000;
	static public final int LARGEFILE = 0100000;
	static public final int DIRECTORY = 0200000;
	static public final int NOFOLLOW = 0400000;
	static public final int NOATIME = 01000000;
	
	static private EndpointReferenceType openSByteIOFromFactory(
		EndpointReferenceType factory)
		throws GenesisIISecurityException, ConfigurationException,
			ResourceException, ResourceCreationFaultType, RemoteException,
			IOException
	{
		SByteIOFactory f = ClientUtils.createProxy(
			SByteIOFactory.class, factory);
		return f.create();
	}
	
	private HashMap<String, IFileDescriptor> _openFiles =
		new HashMap<String, IFileDescriptor>();
	
	@OGRSHOperation
	public String open(String fullPath, int flags, int mode)
		throws OGRSHException
	{
		_logger.trace("File::open(" + fullPath + ", " + flags 
			+ ", " + mode + ") called.");
		
		int twobits = flags & 0x3;
		
		try
		{
			RNSPath path = RNSPath.getCurrent();
			path = path.lookup(fullPath, RNSPathQueryFlags.DONT_CARE);
	
			if (path.exists())
			{
				if ( ((flags & CREAT) > 0) && ((flags & EXCL) > 0) )
				{
					throw new OGRSHException(OGRSHException.EEXIST,
						"Path \"" + fullPath + "\" already exists.");
				} 
			} else 
			{
				if (!((flags & CREAT) > 0))
				{
					throw new OGRSHException(OGRSHException.ENOENT,
						"Path \"" + fullPath + "\" does not exist.");
				}
				
				path.createFile();
			}
				
			TypeInformation typeInfo = new TypeInformation(path.getEndpoint());
			if (typeInfo.isRByteIO())
			{
				String key;
				do
				{
					key = "F" + (new GUID()).toString();
				} while (_openFiles.containsKey(key));
				
				_openFiles.put(key, new RandomByteIOFileDescriptor(
					path.getEndpoint(),
					((twobits == 0) || (twobits == 2)),
					((twobits == 1) || (twobits == 2)),
					((flags & APPEND) > 0), ((flags & TRUNC) > 0)));
				
				return key;
			} else if (typeInfo.isSByteIO())
			{
				String key;
				do
				{
					key = "F" + (new GUID()).toString();
				} while (_openFiles.containsKey(key));
				
				_openFiles.put(key, new StreamableByteIOFileDescriptor(
					path.getEndpoint(),
					((twobits == 0) || (twobits == 2)),
					((twobits == 1) || (twobits == 2)),
					((flags & APPEND) > 0)));
				
				return key;
			} else if (typeInfo.isSByteIOFactory())
			{
				String key;
				do
				{
					key = "F" + (new GUID()).toString();
				} while (_openFiles.containsKey(key));
				
				EndpointReferenceType endpoint = openSByteIOFromFactory(
					typeInfo.getEndpoint());
				
				_openFiles.put(key, new StreamableByteIOFileDescriptor(
					endpoint,
					((twobits == 0) || (twobits == 2)),
					((twobits == 1) || (twobits == 2)),
					((flags & APPEND) > 0)));
				
				return key;
			} else if (typeInfo.isRNS())
			{
				return "D";
			} else
			{
				throw new OGRSHException(OGRSHException.EACCES,
					"The path \"" + fullPath + 
					"\" refers to an object that isn't a file.");
			}
		}
		catch (Throwable t)
		{
			throw new OGRSHException(t);
		}
	}
	
	@OGRSHOperation
	public byte[] read(String fileDesc, int length) throws OGRSHException
	{
		IFileDescriptor fd = _openFiles.get(fileDesc);
		if (fd == null)
			throw new OGRSHException(OGRSHException.EBADF,
				"Attempt to read from non-existant file descriptor.");
		return fd.read(length);
	}
	
	@OGRSHOperation
	public int write(String fileDesc, byte []data) throws OGRSHException
	{
		IFileDescriptor fd = _openFiles.get(fileDesc);
		if (fd == null)
			throw new OGRSHException(OGRSHException.EBADF,
				"Attempt to read from non-existant file descriptor.");
		fd.write(data);
		return data.length;
	}
	
	@OGRSHOperation
	public void close(String fileDesc) throws OGRSHException
	{
		try
		{
			IFileDescriptor desc = _openFiles.get(fileDesc);
			if (desc != null)
				desc.close();
			
			_openFiles.remove(fileDesc);
		}
		catch (IOException ioe)
		{
			throw new OGRSHException(ioe);
		}
	}
	
	@OGRSHOperation
	public int unlink(String fullPath) throws OGRSHException
	{
		_logger.trace("File::unlink(" + fullPath + ") called.");
		
		try
		{
			RNSPath path = RNSPath.getCurrent();
			path = path.lookup(fullPath, RNSPathQueryFlags.MUST_EXIST);
			
			TypeInformation ti = new TypeInformation(path.getEndpoint());
			if (ti.isRNS())
			{
				throw new OGRSHException(OGRSHException.EISDIR,
					"Path \"" + path + "\" refers to a directory.");
			}
			
			// TODO
			// Warning, this next line leaks resources (it only removes the
			// the entry from RNS space, it doesn't actually delete it).
			path.unlink();
			return 0;
		}
		catch (Throwable cause)
		{
			throw new OGRSHException(cause);
		}
	}
	
	@OGRSHOperation
	public StatBuffer fxstat(String fileDesc) throws OGRSHException
	{
		IFileDescriptor fd = _openFiles.get(fileDesc);
		if (fd == null)
			throw new OGRSHException(OGRSHException.EBADF,
				"Attempt to fxstat from non-existant file descriptor.");
		return fd.fxstat();
	}
	
	@OGRSHOperation
	public long lseek64(String fileDesc, long offset, int whence) throws OGRSHException
	{
		IFileDescriptor fd = _openFiles.get(fileDesc);
		if (fd == null)
			throw new OGRSHException(OGRSHException.EBADF,
				"Attempt to lseek64 from non-existant file descriptor.");
		return fd.lseek64(offset, whence);
	}
}