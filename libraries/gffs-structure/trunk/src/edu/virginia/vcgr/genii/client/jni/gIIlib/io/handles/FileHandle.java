package edu.virginia.vcgr.genii.client.jni.gIIlib.io.handles;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.virginia.vcgr.fsii.FSFilesystem;
import edu.virginia.vcgr.fsii.exceptions.FSException;
import edu.virginia.vcgr.fsii.path.UnixFilesystemPathRepresentation;

public class FileHandle extends AbstractFilesystemHandle
{
	static private Log _logger = LogFactory.getLog(FileHandle.class);

	private long _fileHandle;

	public FileHandle(FSFilesystem fs, String[] path, long fileHandle)
	{
		super(fs, path);

		if (_logger.isTraceEnabled())
			_logger.trace(String.format("Created a FileHandle with internal handle %d.", fileHandle));

		_fileHandle = fileHandle;
	}

	@Override
	public boolean isDirectoryHandle()
	{
		return false;
	}

	@Override
	public void close() throws IOException
	{
		if (_logger.isTraceEnabled())
			_logger.trace(String.format("FileHandle::close(%s[%d])", UnixFilesystemPathRepresentation.INSTANCE.toString(_path), _fileHandle));

		if (_fileHandle < 0)
			return;

		try {
			_fs.close(_fileHandle);
		} catch (FSException fse) {
			throw new IOException("Unable to close file handle.", fse);
		}
	}

	public byte[] read(long offset, int length) throws FSException
	{
		if (_logger.isTraceEnabled())
			_logger.trace(String.format("FileHandle::read(%s[%d], %d, %d)", UnixFilesystemPathRepresentation.INSTANCE.toString(_path),
				_fileHandle, offset, length));

		ByteBuffer target = ByteBuffer.allocate(length);
		_fs.read(_fileHandle, offset, target);
		target.flip();
		byte[] dst = new byte[target.remaining()];
		target.get(dst);
		return dst;
	}

	public int write(long offset, byte[] data) throws FSException
	{
		if (_logger.isTraceEnabled())
			_logger.trace(String.format("FileHandle::write(%s[%d], %d)", UnixFilesystemPathRepresentation.INSTANCE.toString(_path),
				_fileHandle, offset));

		ByteBuffer source = ByteBuffer.wrap(data);
		_fs.write(_fileHandle, offset, source);
		return source.position();
	}

	public int truncAppend(long offset, byte[] data) throws FSException
	{
		if (_logger.isTraceEnabled())
			_logger.trace(String.format("FileHandle::truncAppend(%s[%d], %d)", UnixFilesystemPathRepresentation.INSTANCE.toString(_path),
				_fileHandle, offset));

		ByteBuffer source = ByteBuffer.wrap(data);
		_fs.truncate(_path, offset);
		_fs.write(_fileHandle, offset, source);
		return source.position();
	}

	public void flush() throws FSException
	{
		if (_logger.isTraceEnabled())
			_logger.trace(String.format("FileHandle::flush(%s)", UnixFilesystemPathRepresentation.INSTANCE.toString(_path)));
		_fs.flush(_fileHandle);
	}
}