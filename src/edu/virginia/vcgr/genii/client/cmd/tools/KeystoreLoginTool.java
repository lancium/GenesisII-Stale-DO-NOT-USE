package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import edu.virginia.vcgr.genii.client.GenesisIIConstants;
import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.cmd.tools.gamllogin.AbstractGamlLoginHandler;
import edu.virginia.vcgr.genii.client.cmd.tools.gamllogin.CertEntry;
import edu.virginia.vcgr.genii.client.cmd.tools.gamllogin.GuiGamlLoginHandler;
import edu.virginia.vcgr.genii.client.cmd.tools.gamllogin.TextGamlLoginHandler;
import edu.virginia.vcgr.genii.client.context.CallingContextImpl;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.gpath.GeniiPath;
import edu.virginia.vcgr.genii.client.gui.GuiUtils;
import edu.virginia.vcgr.genii.client.security.credentials.GIICredential;
import edu.virginia.vcgr.genii.client.security.credentials.TransientCredentials;
import edu.virginia.vcgr.genii.client.security.credentials.assertions.BasicConstraints;
import edu.virginia.vcgr.genii.client.security.credentials.assertions.DelegatedAssertion;
import edu.virginia.vcgr.genii.client.security.credentials.assertions.DelegatedAttribute;
import edu.virginia.vcgr.genii.client.security.credentials.assertions.RenewableClientAssertion;
import edu.virginia.vcgr.genii.client.security.credentials.assertions.RenewableClientAttribute;
import edu.virginia.vcgr.genii.client.security.credentials.identity.X509Identity;
import edu.virginia.vcgr.genii.client.security.x509.KeyAndCertMaterial;
import edu.virginia.vcgr.genii.client.utils.units.Duration;
import edu.virginia.vcgr.genii.client.utils.units.DurationUnits;
import edu.virginia.vcgr.genii.context.ContextType;


public class KeystoreLoginTool extends BaseLoginTool
{
	static private final String _DESCRIPTION = "edu/virginia/vcgr/genii/client/cmd/tools/description/dkeystoreLogin";
	static private final String _USAGE_RESOURCE = 
		"edu/virginia/vcgr/genii/client/cmd/tools/usage/ukeystoreLogin";


	protected KeystoreLoginTool(String description, String usage, boolean isHidden) {
		super(description, usage, isHidden);
	}

	public KeystoreLoginTool() {
		super(_DESCRIPTION, _USAGE_RESOURCE, false);
		overrideCategory(ToolCategory.SECURITY);
	}


	/**
	 * Prompts the user to select an identity from the specified
	 * keystore, delegating the selected credential to the delegatee.
	 * 
	 * If delegatee is null, the credential is delegated to the calling 
	 * context's current client key material, in which case it will
	 * be self-renewing.
	 * 
	 * @param keystoreInput
	 * @param callContext
	 * @param delegateeIdentity
	 * @return
	 * @throws Throwable
	 */
	protected ArrayList<GIICredential> doKeystoreLogin(
			InputStream keystoreInput, 
			ICallingContext callContext,
			X509Certificate[] delegateeIdentity) throws Throwable {

		ArrayList<GIICredential> retval = new ArrayList<GIICredential>();

		AbstractGamlLoginHandler handler = null;
		if (!useGui() || !GuiUtils.supportsGraphics()) {
			handler = new TextGamlLoginHandler(stdout, stderr, stdin);
		} else {
			handler = new GuiGamlLoginHandler(stdout, stderr, stdin);
		}

		CertEntry certEntry = handler.selectCert(keystoreInput, _storeType,
				_password, _aliasPatternFlag, _pattern);
		if (certEntry == null) {
			return null;
		}

		// If desired, replace the primary client identity (used for
		// SSL & message signing) with the  
		// one specified
		if (_replaceClientToolIdentityFlag)
		{
			TransientCredentials.globalLogout(callContext);

			stdout.println("Replacing client tool identity with credentials for \""
					+ certEntry._certChain[0].getSubjectDN().getName() + "\".");

			KeyAndCertMaterial clientKeyMaterial = 
				new KeyAndCertMaterial(certEntry._certChain, certEntry._privateKey);
			callContext.setActiveKeyAndCertMaterial(clientKeyMaterial);

			return null;
		}


		stdout.println("Acquiring credentials for \""
				+ certEntry._certChain[0].getSubjectDN().getName() + "\".");

		// Create identity assertion
		X509Identity identityAssertion = 
			new X509Identity(certEntry._certChain);

		// create the delegateeAttribute
		if (delegateeIdentity == null) {

			// create a renewable attribute delegated to the calling context
			RenewableClientAttribute delegateeAttribute = new RenewableClientAttribute(
					new BasicConstraints(
							System.currentTimeMillis() - GenesisIIConstants.CredentialGoodFromOffset,
							_validMillis, 										
							10),												 
							identityAssertion,
							callContext);

			// Delegate the identity assertion to the temporary client
			// identity
			retval.add(new RenewableClientAssertion(delegateeAttribute, certEntry._privateKey));

		} else {

			// create a static attribute delegated to the specified party 
			DelegatedAttribute delegateeAttribute = new DelegatedAttribute(
					new BasicConstraints(
							System.currentTimeMillis() - GenesisIIConstants.CredentialGoodFromOffset,
							_validMillis, 										
							10),												 
							identityAssertion,
							delegateeIdentity);

			retval.add(new DelegatedAssertion(delegateeAttribute, certEntry._privateKey));
		}		

		return retval;
	}




	@Override
	protected int runCommand() throws Throwable
	{
		if (_storeType == null)
			_storeType = "PKCS12";
		
		_authnUri = getArgument(0);
		GeniiPath gPath = null;
		
		if (_authnUri != null)
			gPath = new GeniiPath(_authnUri);

		// get the local identity's key material (or create one if necessary)
		ICallingContext callContext = ContextManager.getCurrentContext(false);
		if (callContext == null)
			callContext = new CallingContextImpl(new ContextType());

		TransientCredentials transientCredentials = 
			TransientCredentials.getTransientCredentials(callContext);
		ArrayList<GIICredential> signedAssertions = null;

		if (gPath == null)
			// login to keystore built into the user's OS
			signedAssertions = doKeystoreLogin(null, callContext, null);
		else
		{
			InputStream in = null;
			
			try
			{
				in = gPath.openInputStream();
				signedAssertions = doKeystoreLogin(in, callContext, null);
			}
			finally
			{
				if (in != null)
					in.close();
			}
		}

		if (signedAssertions == null)
			return 0;

		// insert the assertion into the calling context's transient creds
		transientCredentials._credentials.addAll(signedAssertions);
		ContextManager.storeCurrentContext(callContext);

		return 0;
	}

	@Override
	protected void verify() throws ToolException 
	{
		int numArgs = numArguments();
		if (numArgs > 1) 
			throw new InvalidToolUsageException();

		if (_durationString != null)
		{
			try
			{
				_validMillis = (long)new Duration(
						_durationString).as(DurationUnits.Milliseconds);
			}
			catch (IllegalArgumentException pe)
			{
				throw new ToolException("Invalid duration string given.", pe);
			}
		}
	}
}