package edu.virginia.vcgr.genii.client.byteio.transfer.dime;

import java.rmi.RemoteException;

import org.apache.axis.message.MessageElement;
import org.ggf.byteio.TransferInformationType;
import org.ggf.rbyteio.Append;
import org.ggf.rbyteio.RandomByteIOPortType;
import org.ggf.rbyteio.Read;
import org.ggf.rbyteio.TruncAppend;
import org.ggf.rbyteio.Write;

import edu.virginia.vcgr.genii.client.byteio.ByteIOConstants;
import edu.virginia.vcgr.genii.client.byteio.transfer.AbstractByteIOTransferer;
import edu.virginia.vcgr.genii.client.byteio.transfer.RandomByteIOTransferer;
import edu.virginia.vcgr.genii.client.comm.attachments.AttachmentType;

/**
 * This class implements the DIME transfer protocol for remote
 * random ByteIOs.
 * 
 * @author mmm2a
 */
public class DimeRByteIOTransferer 
	extends AbstractByteIOTransferer<RandomByteIOPortType>
	implements RandomByteIOTransferer, DimeByteIOTransferer
{
	/**
	 * Construct a new DIMERByteIO transferer.
	 * 
	 * @param clientStub The client stub to use for outgoing calls.
	 */
	public DimeRByteIOTransferer(RandomByteIOPortType clientStub)
	{
		super(clientStub,
			TRANSFER_PROTOCOL, PREFERRED_READ_SIZE, PREFERRED_WRITE_SIZE);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void append(byte[] data) throws RemoteException
	{
		/* Attach the data to the client stub */
		sendRequestAttachmentData(_clientStub, data,
				AttachmentType.DIME);
			
		/* Create the SOAP body for the message */
		TransferInformationType transType = new TransferInformationType(
			new MessageElement[0], 
			ByteIOConstants.TRANSFER_TYPE_DIME_URI);
		
		_clientStub.append(new Append(transType));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] read(long startOffset, int bytesPerBlock, int numBlocks,
			long stride) throws RemoteException
	{
		/* Create a request SOAP message of the appropriate type */
		TransferInformationType holder =
			new TransferInformationType(null,
				ByteIOConstants.TRANSFER_TYPE_DIME_URI);
		
		_clientStub.read(
			new Read(startOffset, bytesPerBlock, numBlocks, stride, holder));
		
		/* Extract the attachment data from the message */
		return receiveResponseAttachmentData(_clientStub);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void truncAppend(long offset, byte[] data) throws RemoteException
	{
		/* Attach the data to the client stub */
		sendRequestAttachmentData(_clientStub, data,
			AttachmentType.DIME);
		
		/* Create the SOAP message content for the out call */
		TransferInformationType transType = new TransferInformationType(
			new MessageElement[0], 
			ByteIOConstants.TRANSFER_TYPE_DIME_URI);
		_clientStub.truncAppend(new TruncAppend(offset, transType));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(long startOffset, int bytesPerBlock, long stride,
			byte[] data) throws RemoteException
	{
		/* Attach the data to the client stub */
		sendRequestAttachmentData(_clientStub, data,
			AttachmentType.DIME);
		
		/* Create the SOAP message content for the out call */
		TransferInformationType transType = new TransferInformationType(
				new MessageElement[0], 
				ByteIOConstants.TRANSFER_TYPE_DIME_URI);
		_clientStub.write(new Write(startOffset, bytesPerBlock, stride, transType));
	}
}