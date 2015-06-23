package edu.virginia.vcgr.smb.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.rmi.RemoteException;
import java.util.Calendar;

import org.ggf.rbyteio.RandomByteIOPortType;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.byteio.RandomByteIORP;
import edu.virginia.vcgr.genii.client.byteio.transfer.RandomByteIOTransferer;
import edu.virginia.vcgr.genii.client.byteio.transfer.RandomByteIOTransfererFactory;
import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.resource.TypeInformation;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyManager;
import edu.virginia.vcgr.genii.client.security.GenesisIISecurityException;

public class SMBRByteIOFile extends SMBFile {
	private RandomByteIORP rp;
	private SMBIOCache cache;
	
	private class IO implements SMBFile.IO {
		@Override
		public int read(ByteBuffer read, long off) throws SMBException {
			return SMBRByteIOFile.this.cache.read(read, off);
		}

		@Override
		public void write(ByteBuffer write, long off) throws SMBException {
			SMBRByteIOFile.this.cache.write(write, off);
		}

		@Override
		public void truncate(long size) throws SMBException {
			SMBRByteIOFile.this.cache.truncate(size);
		}
	}
	
	public SMBRByteIOFile(RNSPath path, EndpointReferenceType fileEPR,
			RandomByteIOTransferer transferer, RandomByteIORP rp) {
		super(path, fileEPR);
		this.rp = rp;
		this.cache = new SMBIOCache(transferer);
	}
	
	@Override
	public IO getIO() {
		return new IO();
	}
	
	public long getSize() {
		return rp.getSize();
	}
	
	@Override
	public void setSize(long fileSize) {
		try {
			truncate(fileSize);
		} catch (SMBException e) {
			// Oh well
		}
	}
	
	public long getCreateTime() {
		Calendar create = rp.getCreateTime();
		return create.getTimeInMillis();
	}
	
	public void setCreateTime(long millis) {
		// Not supported
	}
	
	public long getWriteTime() {
		Calendar create = rp.getModificationTime();
		return create.getTimeInMillis();
	}
	
	public void setWriteTime(long millis) {
		Calendar write = Calendar.getInstance();
		write.setTimeInMillis(millis);
		rp.setModificationTime(write);
	}
	
	public long getAccessTime() {
		Calendar create = rp.getAccessTime();
		return create.getTimeInMillis();
	}
	
	public void setAccessTime(long millis) {
		Calendar write = Calendar.getInstance();
		write.setTimeInMillis(millis);
		rp.setAccessTime(write);
	}
	
	public int getAttr() {
		return SMBFileAttributes.fromTypeInfo(new TypeInformation(getEPR()));
	}
	
	public void setAttr(int attr) {
		// Not supported
	}

	public static SMBFile fromRNS(RNSPath path, EndpointReferenceType fileEPR) throws SMBException {
		try {
			RandomByteIOPortType clientStub = ClientUtils.createProxy(RandomByteIOPortType.class, fileEPR);
			RandomByteIOTransfererFactory factory = new RandomByteIOTransfererFactory(clientStub);
			RandomByteIOTransferer transferer = factory.createRandomByteIOTransferer();
			RandomByteIORP rp = (RandomByteIORP) ResourcePropertyManager.createRPInterface(fileEPR, RandomByteIORP.class);
			return new SMBRByteIOFile(path, fileEPR, transferer, rp);
		} catch (ResourceException e) {
			// FIXME
			throw new SMBException(NTStatus.DATA_ERROR);
		} catch (GenesisIISecurityException e) {
			throw new SMBException(NTStatus.ACCESS_DENIED);
		} catch (RemoteException e) {
			// FIXME
			throw new SMBException(NTStatus.DATA_ERROR);
		} catch (IOException e) {
			// FIXME
			throw new SMBException(NTStatus.DATA_ERROR);
		} catch (ResourcePropertyException e) {
			// FIXME
			throw new SMBException(NTStatus.DATA_ERROR);
		}
	}

	@Override
	public int getExtAttr() {
		return SMBExtFileAttributes.fromTypeInfo(new TypeInformation(getEPR()));
	}

	@Override
	public void setExtAttr(int fileAttr) {
		// Not supported
	}
	
	public void close() {
		super.close();
		try {
			cache.close();
		} catch (SMBException e) {
			
		}
	}
}