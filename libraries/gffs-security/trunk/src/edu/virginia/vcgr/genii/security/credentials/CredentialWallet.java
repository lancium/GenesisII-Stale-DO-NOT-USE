package edu.virginia.vcgr.genii.security.credentials;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.virginia.vcgr.genii.algorithm.application.ProgramTools;
import edu.virginia.vcgr.genii.security.Describable;
import edu.virginia.vcgr.genii.security.RWXCategory;
import edu.virginia.vcgr.genii.security.VerbosityLevel;
import edu.virginia.vcgr.genii.security.X500PrincipalUtilities;
import edu.virginia.vcgr.genii.security.identity.IdentityType;
import eu.unicore.security.etd.TrustDelegation;

/**
 * This class holds the credentials wallet for a grid session. Individual trust delegations in the assertionChains member below
 * are either: (1) isolated SAML assertions or (2) the most recently delegated trust credential in a delegation chain. This is
 * always true unless the object is being deserialized or built from soap headers, in which case the reattachDelegations method
 * can put things right.
 * 
 * @author myanhaona
 * @author ckoeritz
 */
public class CredentialWallet implements Externalizable, Describable
{
	static public final long serialVersionUID = 2636486491170348968L;

	private static Log _logger = LogFactory.getLog(CredentialWallet.class);

	/**
	 * the full set of trust credentials and delegation chains.
	 */
	transient private Map<String, TrustCredential> assertionChains = new HashMap<String, TrustCredential>();

	/**
	 * constructor for creating a SAML credentials list manually: useful for the grid client.
	 */
	public CredentialWallet()
	{
	}

	public CredentialWallet(List<NuCredential> creds)
	{
		for (NuCredential cred : creds) {
			if (cred instanceof TrustCredential) {
				TrustCredential tc = (TrustCredential) cred;
				assertionChains.put(tc.getId(), tc);
			}
		}
		reattachDelegations(true);
		if (_logger.isDebugEnabled())
			_logger.debug("added " + assertionChains.size() + " credentials into list");
	}

	/**
	 * fairly dangerous accessor function provided for generators based on this object.
	 */
	public Map<String, TrustCredential> getAssertionChains()
	{
		return assertionChains;
	}

	/**
	 * returns true if there are no assertion chains held in this object.
	 */
	public boolean isEmpty()
	{
		return assertionChains.isEmpty();
	}

	/**
	 * This method delegates trust to a new delegatee using an existing trust credential, which increases the length of the
	 * delegation chain by one element.
	 */
	public void delegateTrust(X509Certificate[] delegatee, IdentityType delegateeType, X509Certificate[] issuer,
		PrivateKey issuerPrivateKey, BasicConstraints restrictions, EnumSet<RWXCategory> accessCategories,
		TrustCredential priorDelegation)
	{
		try {
			TrustCredential assertion =
				CredentialCache.getCachedDelegationChain(delegatee, delegateeType, issuer, issuerPrivateKey, restrictions,
					accessCategories, priorDelegation);
			if (!assertionChains.containsKey(priorDelegation.getId())) {
				throw new SecurityException("failure to delegate trust; assertion is not part of credential wallet.");
			}
			assertionChains.remove(priorDelegation.getId());
			assertionChains.put(assertion.getId(), assertion);
		} catch (Throwable e) {
			if (_logger.isDebugEnabled())
				_logger.debug("tossing inappropriate extension for delegatee " + delegatee[0].getSubjectDN() + " on chain of "
					+ priorDelegation.toString() + ", message was: " + e.getMessage());
			//hmmm: whoa!  who says we can whack this???  assertionChains.remove(priorDelegation.getId());
		}
	}

	/**
	 * Adds a new credential into the credentials wallet.
	 */
	public void addCredential(TrustCredential assertion)
	{
		if (assertionChains.containsKey(assertion.getId())) {
			_logger.error("attempt to add identical assertion to the wallet; ignoring.");
			return;
		}
		if (assertion.getPriorDelegationId() != null) {
			assertionChains.remove(assertion.getPriorDelegationId());
		}
		assertionChains.put(assertion.getId(), assertion);
	}

	/**
	 * adds a collection of credentials into the wallet.
	 */
	public void addCredentials(List<TrustCredential> assertions)
	{
		for (TrustCredential assertion : assertions) {
			this.addCredential(assertion);
		}
	}

	public List<TrustCredential> getCredentials()
	{
		if (assertionChains.isEmpty()) {
			return Collections.emptyList();
		}
		return new ArrayList<TrustCredential>(assertionChains.values());
	}

	public void removeInvalidCredentials()
	{
		if (assertionChains.isEmpty())
			return;

		Iterator<TrustCredential> iterator = assertionChains.values().iterator();
		while (iterator.hasNext()) {
			TrustCredential curr = iterator.next();
			if (!curr.isValid()) {
				if (_logger.isDebugEnabled())
					_logger.debug("actually found an invalid credential to remove: " + curr.toString());
				iterator.remove();
			}
		}
	}

	@Override
	public String describe(VerbosityLevel verbosity)
	{
		StringBuilder buffer = new StringBuilder();
		for (TrustCredential assertion : assertionChains.values()) {
			buffer.append(assertion.describe(verbosity)).append("\n");
		}
		return buffer.toString();
	}

	public String describeCredentialMap(Map<String, TrustCredential> toShow, VerbosityLevel verbosity)
	{
		StringBuilder buffer = new StringBuilder();
		for (TrustCredential assertion : toShow.values()) {
			buffer.append(assertion.describe(verbosity)).append("\n");
		}
		return buffer.toString();
	}

	public String showIdChains(Map<String, TrustCredential> toShow, VerbosityLevel verbosity)
	{
		StringBuilder buffer = new StringBuilder();
		for (TrustCredential assertion : toShow.values()) {
			buffer.append(assertion.showIdChain()).append("\n");
		}
		return buffer.toString();
	}

	public TrustCredential getFirstUserCredential()
	{
		for (TrustCredential assertion : assertionChains.values()) {
			// see if the credential is a user credential.

			// _logger.debug("looking at credential: " +
			// assertion.getRootOfTrust().describe(VerbosityLevel.HIGH));
			// _logger.debug("cred user type is: " + assertion.getRootOfTrust().getIssuerType());

			if (assertion.getRootOfTrust().getIssuerType() == IdentityType.USER) {
				return assertion;
			}

			// _logger.debug("decided that one was NOT a user credential.  is this erroneous?");
		}
		return null;
	}

	public String getFirstUserName()
	{
		TrustCredential assertion = getFirstUserCredential();
		if (assertion != null) {
			/*
			 * This is a USER assertion of the form A->B->... where A is a USER.
			 */
			String userName =
				X500PrincipalUtilities.getCN(assertion.getRootOfTrust().getOriginalAsserter()[0].getSubjectX500Principal());
			_logger.debug("calculated user name for byteio storage: '" + userName + "'");
			return userName;

			// old code disabled.
			// String temp = assertion.getRootOfTrust().describe(VerbosityLevel.LOW);
			// _logger.debug("got temp string from cred of: " + temp);
			// String t2[] = temp.split("\"");
			// if (t2.length < 3) {
			// _logger.debug("the split gave me this as the username: " + t2[1]);
			// } else {
			// return t2[1];
			// }
		}
		return null;
	}

	public static boolean chainsAreIntact(TrustCredential credential)
	{
		try {
			credential.checkValidityUber(new Date(), false);
		} catch (Exception e) {
			// looking bad.
			return false;
		}
		// seems fine.
		return true;
	}

	/**
	 * if credentials have been added willy nilly, possibly without their being linked together, this will find and relink all
	 * of them properly. the only thing remaining in the wallet will be isolated assertions or an assertion chain's most recent
	 * element. if removeInvalid is true, then any expired or invalid delegations will be trashed. it is important not to clear
	 * those out during deserialization though or one will not get back any credential, which leads to unanticipated exceptions.
	 */
	public void reattachDelegations(boolean removeInvalid)
	{
		Map<String, TrustCredential> allCreds = new HashMap<String, TrustCredential>();
		allCreds.putAll(assertionChains);
		assertionChains.clear();

		if (_logger.isTraceEnabled()) {
			_logger.debug("this is list before reattach:");
			_logger.debug(describeCredentialMap(allCreds, VerbosityLevel.HIGH));

			_logger.debug("these are ids involved before reattach:");
			_logger.debug(showIdChains(allCreds, VerbosityLevel.HIGH));
		}
		
		// we postpone cleaning of the prior delegations until after we've gotten everyone reattached.
		ArrayList<String> idsToWhack = new ArrayList<String> ();
		
		// construct delegation chain from the map of detached delegations collected above
		while (!allCreds.isEmpty()) {
			/*
			 * if we don't actually change the remaining detached set, then this stays false, and we know we will not make more
			 * progress.
			 */
			boolean progressMade = false;
			Iterator<TrustCredential> delegationIterator = allCreds.values().iterator();

			while (delegationIterator.hasNext()) {
				TrustCredential delegation = delegationIterator.next();
				if (delegation.isTerminalDelegation()) {
					if (_logger.isTraceEnabled())
						_logger.trace("storing terminal delegation: " + delegation.describe(VerbosityLevel.HIGH));
					assertionChains.put(delegation.getId(), delegation);
					delegationIterator.remove();
					progressMade = true;
					break;
				} else {
					String priorDelegationId = delegation.getPriorDelegationId();
					// make sure we're not operating on a wallet that's already been fully reattached.
					if ((priorDelegationId != null) && (delegation.getPriorDelegation() != null) && chainsAreIntact(delegation)) {
						// this one looks okay already, so we'll just add it.
						if (_logger.isTraceEnabled()) {
							_logger.debug("found complete credential during reassembly; adding directly: "
								+ delegation.describe(VerbosityLevel.HIGH));
						}
						assertionChains.put(delegation.getId(), delegation);
						delegationIterator.remove();
						progressMade = true;
						break;
					} else {
						/*
						 * we pull in the stranded prior delegation if we can find it. otherwise we haven't gotten it off the
						 * wire or from serialization yet.
						 */
						if (assertionChains.containsKey(priorDelegationId)) {
							TrustCredential priorDelegation = assertionChains.get(priorDelegationId);
							/* we used to remove that prior guy right away, but that breaks some credential wallets.
							instead we choose to wait until the end before removing the consumed prior delegations.*/
							idsToWhack.add(delegation.getId());
							try {
								delegation.extendTrustChain(priorDelegation);
								if (_logger.isTraceEnabled()) {
									_logger.debug("extended trust chain for prior delegation: "
										+ delegation.describe(VerbosityLevel.HIGH));
								}
								assertionChains.put(delegation.getId(), delegation);
							} catch (Throwable e) {
								_logger.info("problem with credential; discarding it");
							}
							delegationIterator.remove();							
							progressMade = true;
							break;
						}
					}
				}
			}
			if (!progressMade) {
				if (_logger.isDebugEnabled()) {
					_logger.debug("this is what remains in FAILURE case:");
					_logger.debug(describeCredentialMap(allCreds, VerbosityLevel.HIGH));
					_logger.debug("these are ids involved for FAILURE case:");
					_logger.debug(showIdChains(allCreds, VerbosityLevel.HIGH));
				}

				String msg = "failure; there are missing links in the encoded SAML credentials!";
				_logger.error(msg);
				throw new SecurityException(msg);
			}
		}

		if (removeInvalid) {
			// finally, remove all credentials that are invalid/expired.
			removeInvalidCredentials();
		}
		
		for (String toToss : idsToWhack) {
			if (_logger.isTraceEnabled())
				_logger.debug("removing consumed prior credential: " + toToss);
			assertionChains.remove(toToss);
		}
		
	}

	/*
	 * This leverages the unicore6 security library's serialization.
	 */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException
	{
		int howManyTotal = 0;
		for (TrustCredential trustDelegation : assertionChains.values())
			howManyTotal += trustDelegation.getDelegationDepth();
		if (howManyTotal == 0) {
			// ASG Jul4, 2013, changed from an error to a warn
			_logger.warn("failed to encode any credentials for soap header.");
			return;
		}
		try {
			out.writeInt(howManyTotal);
		} catch (Throwable e) {
			_logger.error("failed to serialize an int.  an int!");
		}

		int addedAny = 0;
		for (TrustCredential trustDelegation : assertionChains.values()) {

			TrustCredential curr = trustDelegation;
			while (curr != null) {
				addedAny++;

				try {
					out.writeObject(curr.getDelegation());
					if (_logger.isTraceEnabled())
						_logger.trace("serializing: " + curr.toString());
				} catch (IOException e) {
					_logger.error("failed to serialize assertion: " + e.getMessage());
				}

				curr = curr.getPriorDelegation();
			}
		}
		if (_logger.isTraceEnabled())
			_logger.trace("serialized " + addedAny + " credentials.");
		if (_logger.isTraceEnabled())
			_logger.trace("serialization track: " + ProgramTools.showLastFewOnStack(6));
		if (addedAny != howManyTotal) {
			String msg = "failure to count number of assertions properly";
			_logger.error(msg);
		}
	}

	/*
	 * This method is called when java recreates the SOAPCredentials wallet from a serialized stream. First the no-argument
	 * constructor is been called. Then this method is invoked to properly restore the properties of the wallet.
	 */
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
	{
		if (assertionChains.size() > 0)
			throw new SecurityException("ERROR: somehow an external read op already has assertions");
		int assertionsToRead = 0;
		try {
			assertionsToRead = in.readInt();
		} catch (Throwable e) {
			_logger.info("not loading any credentials as there was no count stored.");
		}
		for (int i = 0; i < assertionsToRead; i++) {
			TrustDelegation td = (TrustDelegation) in.readObject();
			TrustCredential newCred = new TrustCredential(td);
			this.addCredential(newCred);
		}
		reattachDelegations(false);
	}
}
