package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;

import org.apache.axis.message.MessageElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.security.message.token.BinarySecurity;
import org.apache.ws.security.message.token.X509Security;
import org.oasis_open.docs.ws_sx.ws_trust._200512.DelegateToType;
import org.oasis_open.docs.ws_sx.ws_trust._200512.LifetimeType;
import org.oasis_open.docs.ws_sx.ws_trust._200512.RequestSecurityTokenResponseType;
import org.oasis_open.docs.ws_sx.ws_trust._200512.RequestSecurityTokenType;
import org.oasis_open.docs.ws_sx.ws_trust._200512.RequestTypeEnum;
import org.oasis_open.docs.ws_sx.ws_trust._200512.RequestTypeOpenEnum;
import org.oasis_open.docs.ws_sx.ws_trust._200512.RequestedSecurityTokenType;
import org.oasis_open.docs.wss._2004._01.oasis_200401_wss_wssecurity_utility_1_0_xsd.AttributedDateTime;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.GenesisIIConstants;
import edu.virginia.vcgr.genii.client.cache.unified.CacheManager;
import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.comm.SecurityUpdateResults;
import edu.virginia.vcgr.genii.client.comm.axis.Elementals;
import edu.virginia.vcgr.genii.client.context.CallingContextImpl;
import edu.virginia.vcgr.genii.client.context.CallingContextUtilities;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.PreferredIdentity;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.client.utils.PathUtils;
import edu.virginia.vcgr.genii.client.utils.units.Duration;
import edu.virginia.vcgr.genii.client.utils.units.DurationUnits;
import edu.virginia.vcgr.genii.context.ContextType;
import edu.virginia.vcgr.genii.security.SecurityConstants;
import edu.virginia.vcgr.genii.security.TransientCredentials;
import edu.virginia.vcgr.genii.security.VerbosityLevel;
import edu.virginia.vcgr.genii.security.axis.AxisCredentialWallet;
import edu.virginia.vcgr.genii.security.credentials.CredentialWallet;
import edu.virginia.vcgr.genii.security.credentials.NuCredential;
import edu.virginia.vcgr.genii.security.credentials.TrustCredential;
import edu.virginia.vcgr.genii.security.x509.KeyAndCertMaterial;
import edu.virginia.vcgr.genii.x509authn.X509AuthnPortType;

public class IDPLoginTool extends BaseLoginTool
{
	static private Log _logger = LogFactory.getLog(IDPLoginTool.class);

	static private final String _DESCRIPTION = "config/tooldocs/description/dIDPLogin";
	static private final String _USAGE_RESOURCE = "config/tooldocs/usage/uIDPLogin";

	protected IDPLoginTool(String description, String usage, boolean isHidden)
	{
		super(description, usage, isHidden);
		overrideCategory(ToolCategory.SECURITY);
	}

	public IDPLoginTool()
	{
		super(_DESCRIPTION, _USAGE_RESOURCE, false);
		overrideCategory(ToolCategory.SECURITY);
	}

	public static ArrayList<NuCredential> extractAssertions(RequestSecurityTokenResponseType reponseMessage) throws ToolException
	{
		ArrayList<NuCredential> toReturn = new ArrayList<NuCredential>();

		for (MessageElement element : reponseMessage.get_any()) {

			if (element.getName().equals("RequestedSecurityToken")) {
				// process RequestedSecurityToken element
				RequestedSecurityTokenType rstt = null;
				try {
					rstt = (RequestedSecurityTokenType) element.getObjectValue(RequestedSecurityTokenType.class);
				} catch (Exception e) {
				}
				if (rstt != null) {
					for (MessageElement subElement : rstt.get_any()) {
						try {
							AxisCredentialWallet creds = new AxisCredentialWallet(subElement);
							toReturn.addAll(creds.getRealCreds().getCredentials());

							if (!creds.getRealCreds().isEmpty()) {
								StringBuffer sb = new StringBuffer();
								sb.append("Successfully retrieved trust delegations from IDP login:\n");
								for (TrustCredential cred : creds.getRealCreds().getCredentials()) {
									sb.append(cred.describe(VerbosityLevel.LOW) + "\n");
									CallingContextUtilities.updateCallingContext(cred);
								}
								_logger.info(sb);
							}

							return toReturn;
						} catch (Exception e) {
							_logger.error("failed to decode requested credentials", e);
						}
					}
				}
			}
		}

		throw new ToolException("Unknown response token type");
	}

	/**
	 * Calls requestSecurityToken2() on the specified idp. If delegateAttribute is non-null, the returned tokens are delegated to that
	 * identity (the common-case).
	 */
	public static ArrayList<NuCredential> doIdpLogin(EndpointReferenceType idpEpr, long validMillis, X509Certificate[] delegateeIdentity)
		throws AuthZSecurityException, IOException, ToolException
	{

		// get the calling context (or create one if necessary)
		ICallingContext callContext = ContextManager.getCurrentContext();
		if (callContext == null) {
			callContext = new CallingContextImpl(new ContextType());
			ContextManager.storeCurrentContext(callContext);
		}

		// assemble the request message
		RequestSecurityTokenType request = new RequestSecurityTokenType();
		ArrayList<MessageElement> elements = new ArrayList<MessageElement>();

		// Add RequestType element
		MessageElement element =
			new MessageElement(new QName("http://docs.oasis-open.org/ws-sx/ws-trust/200512/", "RequestType"), new RequestTypeOpenEnum(
				RequestTypeEnum._value1));
		element.setType(RequestTypeOpenEnum.getTypeDesc().getXmlType());
		elements.add(element);

		// Add Lifetime element
		SimpleDateFormat zulu = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		zulu.setTimeZone(TimeZone.getTimeZone("ZULU"));
		element =
			new MessageElement(new QName("http://docs.oasis-open.org/ws-sx/ws-trust/200512/", "Lifetime"), new LifetimeType(
				new AttributedDateTime(zulu.format(new Date(System.currentTimeMillis() - SecurityConstants.CredentialGoodFromOffset))),
				new AttributedDateTime(zulu.format(new Date(System.currentTimeMillis() + validMillis)))));
		element.setType(LifetimeType.getTypeDesc().getXmlType());
		elements.add(element);

		/*
		 * add the x509 session certificate here that the container should delegate to so we can get work done. this is our identity on the
		 * network.
		 */
		if (delegateeIdentity != null) {
			try {
				MessageElement binaryToken = new MessageElement(BinarySecurity.TOKEN_BST);
				binaryToken.setAttributeNS(null, "ValueType", edu.virginia.vcgr.genii.client.comm.CommConstants.X509_SECURITY_TYPE);
				binaryToken.addTextNode("");
				BinarySecurity bstToken = new X509Security(binaryToken);
				((X509Security) bstToken).setX509Certificate(delegateeIdentity[0]);

				MessageElement embedded = new MessageElement(new QName(org.apache.ws.security.WSConstants.WSSE11_NS, "Embedded"));
				embedded.addChild(binaryToken);

				MessageElement wseTokenRef = new MessageElement(GenesisIIConstants.WSSE11_NS_SECURITY_QNAME);
				wseTokenRef.addChild(embedded);

				element =
					new MessageElement(new QName("http://docs.oasis-open.org/ws-sx/ws-trust/200512/", "DelegateTo"), new DelegateToType(
						new MessageElement[] { wseTokenRef }));
				element.setType(DelegateToType.getTypeDesc().getXmlType());
				elements.add(element);
				if (_logger.isDebugEnabled())
					_logger.debug("added security token reference");
			} catch (SOAPException e) {
				throw new ToolException("IDP login error: " + e.getLocalizedMessage(), e);
			}
		}

		request.set_any(Elementals.toArray(elements));

		// create a proxy to the remote idp and invoke it
		X509AuthnPortType idp = ClientUtils.createProxy(X509AuthnPortType.class, idpEpr);
		RequestSecurityTokenResponseType[] responses = idp.requestSecurityToken2(request);

		ArrayList<NuCredential> retval = new ArrayList<NuCredential>();

		if (responses != null) {
			for (RequestSecurityTokenResponseType response : responses) {
				ArrayList<NuCredential> newAssertions = extractAssertions(response);
				for (NuCredential cred : newAssertions) {
					if (_logger.isDebugEnabled())
						_logger.debug("got response with credential: " + cred.toString());
				}
				retval.addAll(newAssertions);
			}

			/*
			 * set the preferred identity based on any new USER credential that was found, but only if there is no current preferred identity.
			 */
			if (!PreferredIdentity.existsInCurrent()) {
				CredentialWallet tempWallet = new CredentialWallet(retval);
				TrustCredential firstUser = tempWallet.getFirstUserCredential();
				if (firstUser != null) {
					PreferredIdentity newIdentity =
						new PreferredIdentity(PreferredIdentity.getDnString(firstUser.getOriginalAsserter()[0]), false);
					_logger.debug("setting preferred identity at login time with: " + newIdentity.toString());
					PreferredIdentity.setInContext(callContext, newIdentity);
				}
			} else {
				_logger.debug("already has preferred identity: " + PreferredIdentity.getCurrent().toString());
			}

		}

		return retval;
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException
	{
		_authnUri = getArgument(0);
		URI authnSource;
		try {
			authnSource = PathUtils.pathToURI(_authnUri);
		} catch (URISyntaxException e) {
			throw new ToolException("URI syntax issue: " + e.getLocalizedMessage(), e);
		}

		// get the local identity's key material (or create one if necessary)
		ICallingContext callContext = ContextManager.getCurrentContext();
		if (callContext == null) {
			callContext = new CallingContextImpl(new ContextType());
		}

		TransientCredentials transientCredentials = TransientCredentials.getTransientCredentials(callContext);

		/*
		 * we're going to use the WS-TRUST token-issue operation to log in to a security tokens service.
		 */
		KeyAndCertMaterial clientKeyMaterial =
			ClientUtils.checkAndRenewCredentials(callContext, BaseGridTool.credsValidUntil(), new SecurityUpdateResults());
		RNSPath authnPath = callContext.getCurrentPath().lookup(authnSource.getSchemeSpecificPart(), RNSPathQueryFlags.MUST_EXIST);
		EndpointReferenceType epr = authnPath.getEndpoint();

		// log in
		ArrayList<NuCredential> creds = doIdpLogin(epr, _credentialValidMillis, clientKeyMaterial._clientCertChain);

		// drop any notification brokers or other cached info after credential change.
		CacheManager.resetCachingSystem();

		if (creds == null) {
			return 0;
		}

		// insert the assertion into the calling context's transient creds
		transientCredentials.addAll(creds);
		ContextManager.storeCurrentContext(callContext);

		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
		int numArgs = numArguments();
		if (numArgs != 1)
			throw new InvalidToolUsageException();

		if (_durationString != null) {
			try {
				_credentialValidMillis = (long) new Duration(_durationString).as(DurationUnits.Milliseconds);
			} catch (IllegalArgumentException pe) {
				throw new ToolException("Invalid duration string given.", pe);
			}
		}
	}

}
