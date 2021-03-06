package edu.virginia.vcgr.genii.client.byteio.transfer.mtom;

import java.nio.ByteBuffer;
import java.rmi.RemoteException;

import org.ggf.byteio.TransferInformationType;
import org.ggf.rbyteio.Append;
import org.ggf.rbyteio.RandomByteIOPortType;
import org.ggf.rbyteio.Read;
import org.ggf.rbyteio.TruncAppend;
import org.ggf.rbyteio.Write;

import edu.virginia.vcgr.genii.client.byteio.ByteIOConstants;
import edu.virginia.vcgr.genii.client.byteio.transfer.AbstractByteIOTransferer;
import edu.virginia.vcgr.genii.client.byteio.transfer.RandomByteIOTransferer;
import edu.virginia.vcgr.genii.client.byteio.transfer.TransfererResolver;
import edu.virginia.vcgr.genii.client.comm.attachments.AttachmentType;
import edu.virginia.vcgr.genii.client.comm.axis.Elementals;

/**
 * This class implements the MTOM transfer protocol for remote random ByteIOs.
 * 
 * @author mmm2a
 */
public class MTOMRByteIOTransferer extends AbstractByteIOTransferer<RandomByteIOPortType>
	implements RandomByteIOTransferer, MTOMByteIOTransferer
{
	/**
	 * Construct a new MTOMRByteIO transferer.
	 * 
	 * @param clientStub
	 *            The client stub to use for outgoing calls.
	 */
	public MTOMRByteIOTransferer(RandomByteIOPortType clientStub)
	{
		super(clientStub, TRANSFER_PROTOCOL, PREFERRED_READ_SIZE, MAXIMUM_READ_SIZE, PREFERRED_WRITE_SIZE, MAXIMUM_WRITE_SIZE);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void append(byte[] data) throws RemoteException
	{
		sendRequestAttachmentData(_clientStub, data, AttachmentType.MTOM);

		TransferInformationType transType = new TransferInformationType(Elementals.getEmptyArray(), ByteIOConstants.TRANSFER_TYPE_MTOM_URI);
		_clientStub.append(new Append(transType));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] read(long startOffset, int bytesPerBlock, int numBlocks, long stride) throws RemoteException
	{
		TransferInformationType holder;
		holder = new TransferInformationType(null, ByteIOConstants.TRANSFER_TYPE_MTOM_URI);
		_clientStub.read(new Read(startOffset, bytesPerBlock, numBlocks, stride, holder));
		try {
			return receiveResponseAttachmentData(_clientStub);
		} catch (RemoteException exception) {
			RandomByteIOPortType newClientStub = TransfererResolver.resolveClientStub(_clientStub);
			if (newClientStub == null)
				throw exception;
			_clientStub = newClientStub;
			holder = new TransferInformationType(null, ByteIOConstants.TRANSFER_TYPE_MTOM_URI);
			_clientStub.read(new Read(startOffset, bytesPerBlock, numBlocks, stride, holder));
			return receiveResponseAttachmentData(_clientStub);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void truncAppend(long offset, byte[] data) throws RemoteException
	{
		sendRequestAttachmentData(_clientStub, data, AttachmentType.MTOM);

		TransferInformationType transType = new TransferInformationType(Elementals.getEmptyArray(), ByteIOConstants.TRANSFER_TYPE_MTOM_URI);
		_clientStub.truncAppend(new TruncAppend(offset, transType));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(long startOffset, int bytesPerBlock, long stride, byte[] data) throws RemoteException
	{
		sendRequestAttachmentData(_clientStub, data, AttachmentType.MTOM);

		TransferInformationType transType = new TransferInformationType(Elementals.getEmptyArray(), ByteIOConstants.TRANSFER_TYPE_MTOM_URI);
		_clientStub.write(new Write(startOffset, bytesPerBlock, stride, transType));
	}

	@Override
	public void append(ByteBuffer source) throws RemoteException
	{
		byte[] data = new byte[source.remaining()];
		source.get(data);
		append(data);
	}

	@Override
	public void read(long startOffset, ByteBuffer destination) throws RemoteException
	{
		byte[] data = read(startOffset, destination.remaining(), 1, 0);
		if (data != null)
			destination.put(data);
	}

	@Override
	public void truncAppend(long offset, ByteBuffer source) throws RemoteException
	{
		byte[] data = new byte[source.remaining()];
		source.get(data);
		truncAppend(offset, data);
	}

	@Override
	public void write(long startOffset, ByteBuffer source) throws RemoteException
	{
		byte[] data = new byte[source.remaining()];
		source.get(data);
		write(startOffset, data.length, 0, data);
	}
}