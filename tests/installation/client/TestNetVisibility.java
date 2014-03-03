package installation.client;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.Assert;
import org.junit.Test;

import edu.virginia.vcgr.genii.client.cmd.tools.PingTool;

public class TestNetVisibility
{

	@Test
	public void testNothing()
	{
		// does nothing; this test needs help with ConfigurationManager.
	}

	// @Test(timeout = 8000L)
	public void testRootVisible() throws Throwable
	{
		PingTool tool = new PingTool();
		tool.addArgument("/");
		Assert.assertEquals("Ping worked", 0, tool.run(new StringWriter(), new StringWriter(), new StringReader("")));
	}
}