package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.*;

import javax.xml.soap.SOAPException;

import org.apache.axis.message.MessageElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.algorithm.time.TimeHelpers;
import edu.virginia.vcgr.genii.client.byteio.ByteIOStreamFactory;
import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ReloadShellException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.dialog.UserCancelException;
import edu.virginia.vcgr.genii.client.utils.units.Duration;
import edu.virginia.vcgr.genii.client.utils.units.DurationUnits;
import edu.virginia.vcgr.genii.client.naming.EPRUtils;
import edu.virginia.vcgr.genii.client.rcreate.CreationException;
import edu.virginia.vcgr.genii.client.resource.TypeInformation;
import edu.virginia.vcgr.genii.client.rns.*;
import edu.virginia.vcgr.genii.client.rp.ResourcePropertyException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.client.gpath.GeniiPath;
import edu.virginia.vcgr.genii.client.gpath.GeniiPathType;
import edu.virginia.vcgr.genii.security.SecurityConstants;
import edu.virginia.vcgr.genii.security.XMLCompatible;
import edu.virginia.vcgr.genii.security.axis.XMLConverter;
import edu.virginia.vcgr.genii.security.credentials.NuCredential;
import edu.virginia.vcgr.genii.security.credentials.identity.UsernamePasswordIdentity;
import edu.virginia.vcgr.genii.security.identity.IdentityType;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;

public class IdpTool extends BaseLoginTool
{

	static private final String _DESCRIPTION = "config/tooldocs/description/didp";
	static private final String _USAGE_RESOURCE = "config/tooldocs/usage/uidp";
	static final private String _MANPAGE = "config/tooldocs/man/idp";

	protected String _kerbRealm = null;
	protected String _kerbKdc = null;
	protected IdentityType _type = null;

	static private Log _logger = LogFactory.getLog(IdpTool.class);

	protected IdpTool(String description, String usage, boolean isHidden)
	{
		super(description, usage, isHidden);
	}

	public IdpTool()
	{
		super(_DESCRIPTION, _USAGE_RESOURCE, false);
		addManPage(new LoadFileResource(_MANPAGE));
		if (_logger.isTraceEnabled())
			_logger.trace(String.format("idptool--valid millis default: %.2f days / %.2f years.", TimeHelpers.millisToDays(getValidMillis()),
				TimeHelpers.millisToYears(getValidMillis())));
	}

	@Override
	protected int runCommand() throws ReloadShellException, ToolException, UserCancelException, RNSException, AuthZSecurityException,
		IOException, ResourcePropertyException, CreationException, InvalidToolUsageException, ClassNotFoundException
	{
		String idpServiceRelPath = null;
		String newIdpName = null;

		switch (numArguments()) {
			case 2:
				idpServiceRelPath = this.getArgument(0);
				newIdpName = this.getArgument(1);
				break;
			case 3:
				_authnUri = getArgument(0);
				idpServiceRelPath = this.getArgument(1);
				newIdpName = this.getArgument(2);
				break;
		}

		// get rns path to idp service
		GeniiPath gPath = new GeniiPath(idpServiceRelPath);
		if (gPath.pathType() != GeniiPathType.Grid)
			throw new InvalidToolUsageException("idpServicePath must be a grid path. ");
		RNSPath idpService = lookup(gPath, RNSPathQueryFlags.MUST_EXIST);

		// get the identity of the idp service
		X509Certificate[] idpCertChain = EPRUtils.extractCertChain(idpService.getEndpoint());
		if (idpCertChain == null) {
			throw new RNSException("Entry \"" + idpServiceRelPath + "\" is not an IDP service.");
		}

		ArrayList<MessageElement> constructionParms = new ArrayList<MessageElement>();
		constructionParms.add(new MessageElement(SecurityConstants.NEW_IDP_NAME_QNAME, newIdpName));

		if (_type == null)
			_type = IdentityType.GROUP;

		constructionParms.add(new MessageElement(SecurityConstants.NEW_IDP_TYPE_QNAME, _type.toString()));

		if ((_authnUri == null) && (_storeType == null)) {

			constructionParms.add(new MessageElement(SecurityConstants.IDP_VALID_MILLIS_QNAME, _credentialValidMillis));

			if ((_username != null) && (_password != null)) {

				// actually create a new idp that delegates a username/password token.
				UsernamePasswordIdentity ut = new UsernamePasswordIdentity(_username, _password);

				MessageElement delegatedIdentParm = new MessageElement(SecurityConstants.IDP_STORED_CREDENTIAL_QNAME);
				try {
					delegatedIdentParm.addChild((MessageElement) ut.convertToMessageElement());
				} catch (SOAPException e) {
					throw new ToolException("failure processing SOAP: " + e.getLocalizedMessage(), e);
				} catch (GeneralSecurityException e) {
					throw new AuthZSecurityException(e.getLocalizedMessage(), e);
				}

				constructionParms.add(delegatedIdentParm);
			}

			if (_kerbRealm != null) {
				constructionParms.add(new MessageElement(SecurityConstants.NEW_KERB_IDP_REALM_QNAME, _kerbRealm));
			}
			if (_kerbKdc != null) {
				constructionParms.add(new MessageElement(SecurityConstants.NEW_KERB_IDP_KDC_QNAME, _kerbKdc));
			}

		} else {

			// create a new IDP that further delegates a delegated token

			// log in
			ArrayList<NuCredential> assertions = null;

			try {
				URI authnSource = (_authnUri == null) ? null : new URI(_authnUri);

				KeystoreLoginTool tool = new KeystoreLoginTool();
				BaseLoginTool.copyCreds(this, tool);

				GeniiPath tPath = new GeniiPath(_authnUri);

				if (_authnUri == null) {
					// Keystore login
					assertions = tool.doKeystoreLogin(null, ContextManager.getExistingContext(), idpCertChain);
				} else {
					if (tPath.pathType() == GeniiPathType.Local) {
						// Keystore Login
						BufferedInputStream fis = new BufferedInputStream(new FileInputStream(tPath.path()));
						try {
							assertions = tool.doKeystoreLogin(fis, ContextManager.getExistingContext(), idpCertChain);
						} finally {
							fis.close();
						}

					} else {
						RNSPath authnPath =
							ContextManager.getExistingContext().getCurrentPath()
								.lookup(authnSource.getSchemeSpecificPart(), RNSPathQueryFlags.MUST_EXIST);
						EndpointReferenceType epr = authnPath.getEndpoint();
						TypeInformation type = new TypeInformation(epr);
						if (type.isIDP()) {
							// IDP Login
							assertions = IDPLoginTool.doIdpLogin(epr, BaseGridTool._credentialValidMillis, idpCertChain);
						} else if (type.isByteIO()) {
							// log into keystore from rns path to keystore file
							InputStream in = ByteIOStreamFactory.createInputStream(epr);
							try {
								assertions = tool.doKeystoreLogin(in, ContextManager.getExistingContext(), idpCertChain);
							} finally {
								in.close();
							}
						}
					}
				}
			} catch (URISyntaxException e) {
				throw new ToolException("failure in URI: " + e.getLocalizedMessage(), e);
			}

			if ((assertions == null) || (assertions.size() == 0)) {
				return 0;
			}

			stdout.println("Creating idp for attribute for \"" + assertions.get(0) + "\".");

			// serialize the assertion and put into construction params.
			MessageElement delegatedIdentParm = new MessageElement(SecurityConstants.IDP_STORED_CREDENTIAL_QNAME);

			XMLCompatible xup = XMLConverter.upscaleCredential(assertions.get(0));
			if (xup == null) {
				String msg = "unknown type of credential; cannot upscale to XMLCompatible: " + assertions.get(0).toString();
				_logger.error(msg);
				throw new AuthZSecurityException(msg);
			}

			try {
				delegatedIdentParm.addChild((MessageElement) xup.convertToMessageElement());
			} catch (SOAPException e) {
				throw new AuthZSecurityException(e.getLocalizedMessage(), e);
			} catch (GeneralSecurityException e) {
				throw new AuthZSecurityException(e.getLocalizedMessage(), e);
			}

			constructionParms.add(delegatedIdentParm);
		}

		// create the new idp resource and link it into context space
		MessageElement parmsArray[] = new MessageElement[0];
		CreateResourceTool.createInstance(idpService.getEndpoint(), null, // no link needed
			constructionParms.toArray(parmsArray));

		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
		int numArgs = numArguments();
		if ((numArgs < 2) || (numArgs > 3))
			throw new InvalidToolUsageException();

		if (_durationString != null) {
			try {
				_credentialValidMillis = (long) new Duration(_durationString).as(DurationUnits.Milliseconds);
			} catch (IllegalArgumentException pe) {
				throw new ToolException("Invalid duration string given.", pe);
			}
		}

		if (((_kerbRealm != null) || (_kerbKdc != null)) && ((_kerbRealm == null) || (_kerbKdc == null))) {
			throw new ToolException("Insufficient Kerberos information given.");
		}
	}

	@Option({ "kerbRealm" })
	public void setKerbRealm(String kerbRealm)
	{
		_kerbRealm = kerbRealm;
	}

	@Option({ "kerbKdc" })
	public void setKdc(String kerbKdc)
	{
		_kerbKdc = kerbKdc;
	}

	@Option({ "type" })
	public void setCredentialType(IdentityType type)
	{
		_type = type;
	}

	/**
	 * Set the valid duration of the certificate for the new IDP instance.
	 * 
	 * @param validDuration
	 *            The valid duration for this tool. This string is a formatted duration string. See Genesis II wiki page for a description.
	 */
	@Option({ "validDuration" })
	public void setValidDuration(String validDuration)
	{
		_durationString = validDuration;
	}
}
