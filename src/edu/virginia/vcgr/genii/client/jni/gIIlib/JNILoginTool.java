package edu.virginia.vcgr.genii.client.jni.gIIlib;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import edu.virginia.vcgr.genii.client.cmd.tools.GamlLoginTool;
import edu.virginia.vcgr.genii.client.cmd.tools.gamllogin.AbstractGamlLoginHandler;
import edu.virginia.vcgr.genii.client.cmd.tools.gamllogin.CertEntry;
import edu.virginia.vcgr.genii.client.cmd.tools.gamllogin.GuiGamlLoginHandler;
import edu.virginia.vcgr.genii.client.cmd.tools.gamllogin.TextGamlLoginHandler;
import edu.virginia.vcgr.genii.client.context.CallingContextImpl;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.security.gamlauthz.TransientCredentials;
import edu.virginia.vcgr.genii.client.security.gamlauthz.assertions.BasicConstraints;
import edu.virginia.vcgr.genii.client.security.gamlauthz.assertions.RenewableAttributeAssertion;
import edu.virginia.vcgr.genii.client.security.gamlauthz.assertions.RenewableClientAssertion;
import edu.virginia.vcgr.genii.client.security.gamlauthz.assertions.RenewableClientAttribute;
import edu.virginia.vcgr.genii.client.security.gamlauthz.assertions.RenewableIdentityAttribute;
import edu.virginia.vcgr.genii.client.security.gamlauthz.identity.X509Identity;
import edu.virginia.vcgr.genii.context.ContextType;


public class JNILoginTool extends JNILibraryBase 
{

	private static boolean useGui = true; 
	
	public static Boolean login(String keystorePath, String password, String certPattern){
		tryToInitialize();
		
		
		try{
			if(keystorePath == null){			
				GamlLoginTool loginTool = new GamlLoginTool();				
				loginTool.run(System.out, System.err, 
					new BufferedReader(new InputStreamReader(System.in)));
			}
			else{								
				AbstractGamlLoginHandler handler = null;
						
				if (!useGui)
					handler = new TextGamlLoginHandler(System.out, System.err, 
							new BufferedReader(new InputStreamReader(System.in)));
				else
					handler = new GuiGamlLoginHandler(System.out, System.err, 
							new BufferedReader(new InputStreamReader(System.in)));
				
				CertEntry certEntry = handler.selectCert(new FileInputStream(keystorePath), null, 
						password, false, certPattern);
				
				if (certEntry == null)
					return false;
				
				// Create identity assertion
				RenewableIdentityAttribute identityAttr = new RenewableIdentityAttribute(
					new BasicConstraints(
							System.currentTimeMillis() - (1000L * 60 * 15), // 15 minutes ago
							1000 * 60 * 60 * 2,								// valid 2 hours
							10),
					new X509Identity(certEntry._certChain));
				RenewableAttributeAssertion identityAssertion =
					new RenewableAttributeAssertion(identityAttr, certEntry._privateKey);
				
				// get the calling context (or create one if necessary)
				ICallingContext callContext = ContextManager.getCurrentContext(false);
				if (callContext == null)
					callContext = new CallingContextImpl(new ContextType());
				
				// Delegate the identity assertion to the temporary client
				// identity
				RenewableClientAttribute delegatedAttr = new RenewableClientAttribute(
					null, identityAssertion, callContext);
				RenewableClientAssertion delegatedAssertion = new RenewableClientAssertion(
					delegatedAttr, certEntry._privateKey);
				
				// insert the assertion into the calling context's transient creds
				TransientCredentials transientCredentials =
					TransientCredentials.getTransientCredentials(callContext);
				transientCredentials._credentials.add(delegatedAssertion);
				
				ContextManager.storeCurrentContext(callContext);
			}
			return true;
		}catch(Throwable e){
			e.printStackTrace();
			return false;			
		}
	}	
	
}
