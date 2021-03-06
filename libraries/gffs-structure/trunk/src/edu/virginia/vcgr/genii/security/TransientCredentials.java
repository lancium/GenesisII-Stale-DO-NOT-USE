package edu.virginia.vcgr.genii.security;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.virginia.vcgr.genii.algorithm.application.ProgramTools;
import edu.virginia.vcgr.genii.client.cache.unified.CacheManager;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.security.PreferredIdentity;
import edu.virginia.vcgr.genii.security.credentials.CredentialWallet;
import edu.virginia.vcgr.genii.security.credentials.NuCredential;
import edu.virginia.vcgr.genii.security.credentials.TrustCredential;
import edu.virginia.vcgr.genii.security.credentials.X509Identity;
import edu.virginia.vcgr.genii.security.identity.Identity;

/**
 * Class for holding and managing a set of "outgoing" credentials within the current calling context. This is a combination of all
 * authenticated certificate chains, caller credentials and credentials for the target.
 * 
 * @author dmerrill
 * @author ckoeritz
 */
public class TransientCredentials implements Serializable
{
	static final long serialVersionUID = 0L;

	static private Log _logger = LogFactory.getLog(TransientCredentials.class);

	private static final String TRANSIENT_CRED_PROP_NAME = "genii.client.security.transient-cred";

	private ArrayList<NuCredential> _credentials = new ArrayList<NuCredential>();

	public ArrayList<NuCredential> getCredentials()
	{
		return _credentials;
	}

	public void add(NuCredential cred)
	{
		if (cred == null) {
			_logger.error("credential being passed in is null!  ignoring it.");
			return;
		}
		if (!(cred instanceof TrustCredential)) {
			if (cred instanceof Identity) {
				if (cred instanceof X509Identity) {
					if (_logger.isTraceEnabled())
						_logger.debug("ignoring bare x509 identity: " + cred.describe(VerbosityLevel.HIGH));
					return;
				}
				// this is probably okay; may be username/password identity or x509 identity.
			} else {
				if (_logger.isDebugEnabled())
					_logger.debug("skipping unknown type of credential!: " + cred.toString());
				return;
			}
		}
		if (_logger.isTraceEnabled())
			_logger.debug("storing cred into transient set: " + cred.toString() + " via " + ProgramTools.showLastFewOnStack(6));
		_credentials.add(cred);
	}

	/**
	 * returns a credential wallet object with all of the trust credentials from this object in it. note that this strips out any X509Identity
	 * and other non-trust-credentials, so there will be no connection credential left either.
	 */
	public CredentialWallet generateWallet()
	{
		CredentialWallet toReturn = new CredentialWallet();
		for (NuCredential cred : _credentials) {
			if (cred instanceof TrustCredential) {
				toReturn.addCredential((TrustCredential) cred);
			}
		}
		return toReturn;
	}

	public void addAll(Collection<NuCredential> newCreds)
	{
		/*
		 * we intentionally invoke our own function, rather than addAll on the list, in order to check on things.
		 */
		for (NuCredential cred : newCreds)
			add(cred);
	}

	public void remove(NuCredential cred)
	{
		_credentials.remove(cred);
	}

	public boolean isEmpty()
	{
		return _credentials.isEmpty();
	}

	/**
	 * Retrieves the credentials from the calling context. Guaranteed to not be null (may be empty of any credentials, however)
	 */
	public static synchronized TransientCredentials getTransientCredentials(ICallingContext callingContext)
	{
		TransientCredentials retval =
			(TransientCredentials) callingContext.getTransientProperty(TransientCredentials.TRANSIENT_CRED_PROP_NAME);
		if (retval == null) {
			// we need to make up a new empty set of transient credentials.
			retval = new TransientCredentials();
			setTransientCredentials(callingContext, retval);
		}
		return retval;
	}

	public static synchronized void setTransientCredentials(ICallingContext callingContext, TransientCredentials newCreds)
	{
		callingContext.setTransientProperty(TransientCredentials.TRANSIENT_CRED_PROP_NAME, newCreds);
	}

	public static synchronized void globalLogout(ICallingContext callingContext)
	{
		// clear out the preferred identity unless it's fixated.
		PreferredIdentity current = PreferredIdentity.getFromContext(callingContext);
		if (current != null) {
			if (!current.getFixateIdentity()) {
				PreferredIdentity.removeFromContext(callingContext);
				if (_logger.isDebugEnabled())
					_logger.debug("Removed preferred identity from context.");
			}
		}
		// toss the transient credentials.
		callingContext.removeTransientProperty(TransientCredentials.TRANSIENT_CRED_PROP_NAME);
		if (_logger.isDebugEnabled())
			_logger.debug("Clearing current calling context credentials.");
		// drop any notification brokers or other cached info after credential change.
		CacheManager.resetCachingSystem();
	}

	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();

		int index = 1;
		for (NuCredential cred : _credentials) {
			if (builder.length() != 0)
				builder.append("\n");
			builder.append(String.format("#%d -- ", index));
			builder.append(cred.toString());
			index++;
		}

		return builder.toString();
	}
}
