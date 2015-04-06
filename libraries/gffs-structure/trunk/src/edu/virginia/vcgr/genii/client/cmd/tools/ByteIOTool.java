package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.IOException;

import org.ggf.rbyteio.RandomByteIOPortType;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.byteio.transfer.RandomByteIOTransferer;
import edu.virginia.vcgr.genii.client.byteio.transfer.RandomByteIOTransfererFactory;
import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;

public class ByteIOTool extends BaseGridTool
{
	static final private String _DESCRIPTION = "config/tooldocs/description/dbyteio";
	static final private String _USAGE = "config/tooldocs/usage/ubyteio";

	private boolean _read = false;
	private boolean _write = false;
	private boolean _append = false;

	public ByteIOTool()
	{
		super(new LoadFileResource(_DESCRIPTION), new LoadFileResource(_USAGE), false, ToolCategory.DATA);
	}

	@Option({ "read", "r" })
	public void setRead()
	{
		_read = true;
	}

	@Option({ "write", "w" })
	public void setWrite()
	{
		_write = true;
	}

	@Option({ "append", "a" })
	public void setAppend()
	{
		_append = true;
	}

	@Override
	protected void verify() throws ToolException
	{
		boolean ok1 = _read && !_write && !_append && (numArguments() == 3);
		boolean ok2 = !_read && _write && !_append && (numArguments() == 3);
		boolean ok3 = !_read && !_write && _append && (numArguments() == 2);
		if (!(ok1 || ok2 || ok3))
			throw new InvalidToolUsageException();
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException
	{
		RNSPath current = RNSPath.getCurrent();
		RNSPath rnsPath = current.lookup(getArgument(0), RNSPathQueryFlags.MUST_EXIST);
		EndpointReferenceType fileEPR = rnsPath.getEndpoint();
		if (_read) {
			long offset = Long.parseLong(getArgument(1));
			int size = Integer.parseInt(getArgument(2));
			doRead(fileEPR, offset, size);
		}
		if (_write) {
			long offset = Long.parseLong(getArgument(1));
			String data = getArgument(2);
			doWrite(fileEPR, offset, data);
		}
		if (_append) {
			String data = getArgument(1);
			doAppend(fileEPR, data);
		}
		return 0;
	}

	private void doRead(EndpointReferenceType fileEPR, long offset, int size) throws IOException
	{
		RandomByteIOPortType clientStub = ClientUtils.createProxy(RandomByteIOPortType.class, fileEPR);
		RandomByteIOTransfererFactory factory = new RandomByteIOTransfererFactory(clientStub);
		RandomByteIOTransferer transferer = factory.createRandomByteIOTransferer();
		byte[] data = transferer.read(offset, size, 1, 0);
		String text = new String(data);
		stdout.println(text);
	}

	private void doWrite(EndpointReferenceType fileEPR, long offset, String text) throws IOException
	{
		RandomByteIOPortType clientStub = ClientUtils.createProxy(RandomByteIOPortType.class, fileEPR);
		RandomByteIOTransfererFactory factory = new RandomByteIOTransfererFactory(clientStub);
		RandomByteIOTransferer transferer = factory.createRandomByteIOTransferer();
		byte[] data = text.getBytes();
		transferer.write(offset, data.length, 0, data);
	}

	private void doAppend(EndpointReferenceType fileEPR, String text) throws IOException
	{
		text = text + "\n";
		RandomByteIOPortType clientStub = ClientUtils.createProxy(RandomByteIOPortType.class, fileEPR);
		RandomByteIOTransfererFactory factory = new RandomByteIOTransfererFactory(clientStub);
		RandomByteIOTransferer transferer = factory.createRandomByteIOTransferer();
		byte[] data = text.getBytes();
		transferer.append(data);
	}
}
