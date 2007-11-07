package edu.virginia.vcgr.genii.client.cmd.tools;

import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.informationService.InformationServicePortType;
import edu.virginia.vcgr.genii.informationService.QueryRequestType;
import edu.virginia.vcgr.genii.informationService.QueryResponseType;

public class InfoSQueryTool extends BaseGridTool {

	
	static private final String _DESCRIPTION =
		"allows you to query the XML database";
	static private final String _USAGE1 =
		"query-dbxml <Berkley DB query> <informationService-service-path>";

	public InfoSQueryTool() {
		super(_DESCRIPTION, _USAGE1, false);
		// TODO Auto-generated constructor stub
	}

	
	@Override
	protected int runCommand() throws Throwable {
		query(new String(getArgument(0)),
				getArgument(1));
		return 0;
	}
	@Override
	protected void verify() throws ToolException {
		if (numArguments() != 2)
			throw new InvalidToolUsageException();

	}

	private void query(String str1, String servicePath) throws Throwable
	{
		RNSPath path = RNSPath.getCurrent().lookup(servicePath, RNSPathQueryFlags.MUST_EXIST);
		InformationServicePortType informationService = ClientUtils.createProxy(
				InformationServicePortType.class, path.getEndpoint());
		QueryResponseType rs =informationService.queryForProperties(new QueryRequestType(str1));
		
		System.err.println("Your query: " + str1 + "\n" + "The results are:" + "\n" + rs.getResult());
	}

}
