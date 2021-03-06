package edu.virginia.vcgr.genii.security.credentials;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.crypto.dsig.XMLSignature;

import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlbeans.XmlObject;
import org.morgan.util.GUID;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import xmlbeans.org.oasis.saml2.assertion.AssertionDocument;
import xmlbeans.org.oasis.saml2.assertion.AttributeStatementType;
import xmlbeans.org.oasis.saml2.assertion.AttributeType;
import edu.virginia.vcgr.genii.algorithm.application.ProgramTools;
import edu.virginia.vcgr.genii.security.RWXAccessible;
import edu.virginia.vcgr.genii.security.RWXCategory;
import edu.virginia.vcgr.genii.security.SAMLConstants;
import edu.virginia.vcgr.genii.security.VerbosityLevel;
import edu.virginia.vcgr.genii.security.faults.AttributeInvalidException;
import edu.virginia.vcgr.genii.security.identity.IdentityType;
import eu.unicore.samly2.elements.SAMLAttribute;
import eu.unicore.samly2.exceptions.SAMLValidationException;
import eu.unicore.samly2.trust.SimpleTrustChecker;
import eu.unicore.security.etd.DelegationRestrictions;
import eu.unicore.security.etd.ETDApi;
import eu.unicore.security.etd.ETDImpl;
import eu.unicore.security.etd.TrustDelegation;

/**
 * This is the main class GenesisII class for SAML based trust delegation mechanism. A single TrustCredential object can be the representative
 * of an isolated delegation or the holder of an entire delegation chain. This replaces our old GIICredential based delegation mechanism. The
 * previous delegation mechanism was recursive which is not supported by UNICORE SAML library. So we simulate the recursive credentials
 * transferring, which is essential for our business logic, with a linked list of trust delegation.
 * 
 * @author Muhammad Yanhaona
 * @author Chris Koeritz
 */
public class TrustCredential implements NuCredential, RWXAccessible
{
	static public final long serialVersionUID = 50L;

	private static Log _logger = LogFactory.getLog(TrustCredential.class);

	transient private X509Certificate[] delegatee; // who the trust is being granted to.
	transient private X509Certificate[] issuer; // the identity that trusts the delegatee.
	transient private BasicConstraints restrictions; // restrictions on trust duration, etc.
	transient private String id; // this credential's own identifier (as a guid).

	/*
	 * if this credential is a linked delegation, than the priorDelegation is what comes before it in the chain. for example, if this
	 * credential is B -> C and the prior delegation is A -> B, then the entire chain is A -> B -> C.
	 */
	// the credentials that support this credential, if any.
	transient private TrustCredential priorDelegation;
	// the identifier of that previous credential, if any.
	transient private String priorDelegationId;
	/*
	 * priorDsig is the signature from the previous credential, if this is not the start of a delegation chain. this is crucial for ensuring
	 * that some attacker doesn't get in and monkey with our chains, since the GUID by itself is not enough of an assurance that the prior
	 * delegation really was the one we were handed. but the signature is derived from the previous credential and is part of what's signed
	 * into that credential, so an attacker would have to exercise some extremely devious crafting of credentials to actually match the dsig
	 * value. it's not impossible probably, for some delegation chains, but we're okay with them needing a few million years to compute the
	 * exploitable prior delegation. and even if they successfully spoof the chain with the dsig value *somehow*, they still have to be a
	 * valid grid user that has access to the resource in question.
	 */
	transient private String priorDsig;

	// what kind of access the delegatee is granted.
	transient private EnumSet<RWXCategory> accessMask = RWXCategory.FULL_ACCESS;
	// what type of delegatee is it?
	transient private IdentityType _delegateeType = IdentityType.UNSPECIFIED;
	// what type of issuer is it?
	transient private IdentityType _issuerType = IdentityType.UNSPECIFIED;

	// is this credential already signed?
	transient private boolean signed;
	// tracks when we have already validated this particular credential.
	transient private boolean properlySigned = false;

	// the representation of the trust credential as a unicore6 trust delegation.
	transient private TrustDelegation delegation;

	/**
	 * constructor used when recreating a trust delegation from a serialized stream.
	 */
	public TrustCredential()
	{
	}

	/**
	 * the constructor for manually creating a trust delegation. isAssertion should be true if this is a basic assertion.
	 */
	public TrustCredential(X509Certificate[] delegatee, IdentityType delegateeType, X509Certificate[] issuer, IdentityType issuerType,
		BasicConstraints restrictions, EnumSet<RWXCategory> accessMask)
	{
		this.delegatee = delegatee;
		this._delegateeType = delegateeType;
		this.issuer = issuer;
		this._issuerType = issuerType;
		this.accessMask = accessMask;
		this.restrictions = restrictions;
		this.id = new GUID().toString();
		this.signed = false;
		if (_logger.isTraceEnabled())
			_logger.debug("created trust delegation: " + describe(VerbosityLevel.LOW));
	}

	/**
	 * the constructor for creating a trust delegation from XML content of a SOAP message.
	 */
	public TrustCredential(Node node)
	{
		TrustDelegation trustDelegation = null;
		try {
			AssertionDocument assertionDocument = AssertionDocument.Factory.parse(node);
			trustDelegation = new TrustDelegation(assertionDocument);
		} catch (Exception e) {
			throw new SecurityException("failure: could not parse XML SAML assertion!", e);
		}
		constructFromTrustDelegation(trustDelegation);
		if (_logger.isTraceEnabled())
			_logger.trace("built trust delegation from node: " + describe(VerbosityLevel.LOW));
	}

	/**
	 * the constructor for creating a trust delegation from a UNICORE TrustDelegation object.
	 */
	public TrustCredential(TrustDelegation delegation)
	{
		constructFromTrustDelegation(delegation);
		if (_logger.isTraceEnabled())
			_logger.trace("built trust delegation from u6 TD: " + describe(VerbosityLevel.LOW));
	}

	public boolean isSigned()
	{
		return signed;
	}

	public TrustDelegation getDelegation()
	{
		return delegation;
	}

	public BasicConstraints getConstraints()
	{
		return restrictions;
	}

	// converts from our basic constraints object to unicore's form.
	public static DelegationRestrictions convert(BasicConstraints toConvert)
	{
		return new DelegationRestrictions(toConvert.getNotValidBefore(), toConvert.getExpiration(), toConvert.getMaxDelegationDepth());
	}

	// converts from unicore's delegation restrictions object to our basic constraints form.
	public static BasicConstraints convert(DelegationRestrictions toConvert)
	{
		return new BasicConstraints(toConvert.getNotBefore().getTime(),
			toConvert.getNotOnOrAfter().getTime() - toConvert.getNotBefore().getTime(), toConvert.getMaxProxyCount());
	}

	public X509Certificate[] getDelegatee()
	{
		return delegatee;
	}

	public X509Certificate[] getIssuer()
	{
		return issuer;
	}

	public String getId()
	{
		return id;
	}

	public String getPriorDelegationId()
	{
		return priorDelegationId;
	}

	public String getPriorDsig()
	{
		return priorDsig;
	}

	public TrustCredential getPriorDelegation()
	{
		return priorDelegation;
	}

	@Override
	public EnumSet<RWXCategory> getMask()
	{
		return accessMask;
	}

	@Override
	public void setMask(EnumSet<RWXCategory> newMask)
	{
		if (signed) {
			throw new SecurityException("failure: attempt to change mask after signing.");
		}
		accessMask = newMask;
	}

	public IdentityType getDelegateeType()
	{
		return _delegateeType;
	}

	public IdentityType getIssuerType()
	{
		return _issuerType;
	}

	/**
	 * returns the current depth of delegation. a depth of zero implies there is no trust delegation. that should never be the case. the first
	 * trust credential (such as user -> tls connection) counts as a depth of one. a chain with N trust credentials thus will always return a
	 * length of N.
	 */
	public int getDelegationDepth()
	{
		int depth = 0;
		TrustCredential curr = this;
		while (curr != null) {
			depth++;
			curr = curr.getPriorDelegation();
		}
		return depth;
	}

	/**
	 * gets the x509 identity that started the trust credential. this will generally be a grid user or group of some sort.
	 */
	public X509Identity getRootIdentity()
	{
		if (priorDelegation != null)
			return priorDelegation.getRootIdentity();
		return new X509Identity(getIssuer(), getIssuerType());
	}

	/**
	 * returns the first trust credential in the chain.
	 */
	public TrustCredential getRootOfTrust()
	{
		TrustCredential earliest = this;
		while (earliest.getPriorDelegation() != null) {
			earliest = earliest.getPriorDelegation();
		}
		return earliest;
	}

	/**
	 * checks if the entire trust delegation chain has any expired delegations.
	 */
	public boolean isValid()
	{
		try {
			checkValidity(new Date());
		} catch (AttributeInvalidException cause) {
			_logger.error("caught invalid attribute in TrustCredential isValid check", cause);
			return false;
		}
		return true;
	}

	public boolean isTerminalDelegation()
	{
		if (!signed) {
			throw new SecurityException("failure: an attempt to deduce unsecured properties of a delegation!");
		}
		return priorDelegationId == null;
	}

	@Override
	public String toString()
	{
		StringBuilder toReturn = new StringBuilder();
		toReturn.append(describe(VerbosityLevel.HIGH));

		toReturn.append(" <id " + id.toString() + ">");
		if (priorDelegationId != null) {
			toReturn.append(" (prior-id " + priorDelegationId.toString() + ")");
		}

		toReturn.append(" {mask ");
		toReturn.append(accessMask.toString());
		toReturn.append("}");
		return toReturn.toString();
	}

	/**
	 * This method recursively traverses the trust delegation chain and construct a list of AssertionDocuments in appropriate order. This list
	 * of AssertionDocuments can be converted to XML DOM nodes to send over the wire.
	 */
	public void getXMLChain(List<AssertionDocument> chain, List<String> references, List<String> newlySent, String containerGUID)
	{
		if (!signed)
			throw new SecurityException("failure: an attempt to create a trust delegation chain without signed content.");

		boolean sawCred = false;
		boolean streamliningOkay = false;
		// check if we can skip adding this trust delegation.
		if (CredentialCache.CLIENT_CREDENTIAL_STREAMLINING_ENABLED && (containerGUID != null)) {
			streamliningOkay = ClientCredentialTracker.doesContainerSupportStreamlining(containerGUID);
			if (streamliningOkay) {
				// lookup this credential under this container's EPI.
				sawCred = ClientCredentialTracker.hasContainerSeenCred(containerGUID, getId());
				if ((references != null) && sawCred) {
					/*
					 * we only add a reference here if we think the container had already seen this. otherwise we are referring to things
					 * we're actually shipping, which is bogus.
					 */
					references.add(getId());
				}
				if ((newlySent != null) && !sawCred) {
					/*
					 * we have to record that we are going to actually send this, so the tracking can be updated. it is not a reference now,
					 * but can be in the future for a new sending.
					 */
					newlySent.add(getId());
				}
			}
		}
		if (!sawCred) {
			// had not seen this one yet, or credential streamlining is disabled.
			synchronized (delegation) {
				chain.add(delegation.getXMLBeanDoc());
			}
		}
		// include the prior delegations for this trust credential also.
		if (priorDelegation != null) {
			priorDelegation.getXMLChain(chain, references, newlySent, containerGUID);
		}
	}

	/**
	 * seeks out the signature in the lower-level trust delegation object.
	 */
	public String locateDsigValue()
	{
		TrustDelegation deleg = getDelegation();
		AssertionDocument ad = deleg.getXMLBeanDoc();
		org.w3c.dom.Document doc = (Document) ad.getDomNode();

		// Find Signature element.
		NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "SignatureValue");
		if (nl.getLength() == 0) {
			_logger.error("cannot find 'SignatureValue' element in assertion.");
			return null;
		}
		// get the first child node of signature value.
		Node n = nl.item(0);
		// then grab the first child of that, which should be the text contents.
		String sig = n.getChildNodes().item(0).getNodeValue();
		if (sig == null) {
			_logger.error("failed to retrieve contents from the supposed sig value node.");
			return null;
		}
		// process the signature string to take out any line breaks.
		sig = sig.replace("\n", "");
		sig = sig.replace("\r", "");
		if (_logger.isTraceEnabled())
			_logger.trace("found dsig signature: " + sig);
		return sig;
	}

	/**
	 * This extends the length of the delegation chain by placing 'this' credential at the end, as the newest delegation of trust. The obvious
	 * use case is the delegation of credentials from the grid-client when making an RPC over a grid resource. Hence, the
	 * 'priorPortionOfChain' parameter can be a single existing delegation or a chain. Note that linking must be done before signing "this"
	 * newly created trust credential or an exception is thrown. There is just one exception to this rule: when linking trust delegation whose
	 * ID matches the priorDelegationId and whose dsig value matches the priorDsig reference, this is permitted on signed trust credentials to
	 * allow reconstruction of delegation chains from XML.
	 */
	public void extendTrustChain(TrustCredential priorPortionOfChain)
	{
		if (priorPortionOfChain == null)
			throw new SecurityException("failure: an attempt to link a null credential.");

		if (_logger.isTraceEnabled()) {
			_logger.debug(
				"linking trust delegations:\nthis one is:\n" + this.toString() + "\nand we will link to:\n" + priorPortionOfChain.toString());
		}

		// check the length of chain to catch any attempt to over-extend it.
		if (getDelegationDepth() + 1 > restrictions.getMaxDelegationDepth()) {
			String msg = "failure: cannot extend chain length past " + restrictions.getMaxDelegationDepth() + " elements.";
			_logger.error(msg);
			throw new SecurityException(msg);
		}

		// fill in the prior delegation values so we can include them.
		String priorChainId = priorPortionOfChain.getId();
		String priorChainDsig = priorPortionOfChain.locateDsigValue();
		if (_logger.isTraceEnabled())
			_logger.trace("prior chain portion's signature is " + priorChainDsig);

		if (signed) {
			/*
			 * perform simple integrity checks on the thing they're claiming is the right prior delegation. if the prior dsig value is all
			 * crazy, we will dump these when they hit the wire or the credential wallet.
			 */
			if (!priorChainId.equals(priorDelegationId)) {
				throw new SecurityException("failure: cannot extend trust while signed if prior id does not match!");
			}
		}
		X509Certificate[] delegateeInpriorAssertion = priorPortionOfChain.getDelegatee();
		if (!delegateeInpriorAssertion[0].equals(this.issuer[0])) {
			throw new SecurityException("failure: prior assertion delegatee is not the same as this credential's issuer!");
		}
		this.priorDelegation = priorPortionOfChain;
		this.priorDelegationId = priorChainId;
		this.priorDsig = priorChainDsig;

		if (_logger.isTraceEnabled()) {
			String creationWord = "successful extension of trust";
			if (signed)
				creationWord = "successful reconstitution of chain";
			_logger.debug(creationWord + ":\n" + this.describe(VerbosityLevel.HIGH));
		}
	}

	/**
	 * This method creates an UNICORE TrustDelegation from the information available in the trust delegation assertion a caller has created.
	 * This method is used when the assertion is created anew. Not when the assertion is derived from an existing TrustDelegation in the
	 * database or that is received with a SOAP message. Note that, once signed no further modification of an assertion is permitted.
	 */
	public void signAssertion(PrivateKey privateKey)
	{
		if (signed) {
			throw new SecurityException("failure: this assertion was already signed.");
		}

		List<SAMLAttribute> attributes = new ArrayList<SAMLAttribute>();
		SAMLAttribute idAttribute = new SAMLAttribute(SAMLConstants.ASSERTION_ID_ATTRIBUTE_NAME, SAMLConstants.PLACEHOLDER_FOR_NAME_FORMAT);
		idAttribute.addStringAttributeValue(id);
		attributes.add(idAttribute);

		if (priorDelegationId != null) {
			// add the prior delegation info.
			SAMLAttribute priorDelegationIdAttribute =
				new SAMLAttribute(SAMLConstants.PRIOR_ASSERTION_ID_ATTRIBUTE_NAME, SAMLConstants.PLACEHOLDER_FOR_NAME_FORMAT);
			priorDelegationIdAttribute.addStringAttributeValue(priorDelegationId);
			attributes.add(priorDelegationIdAttribute);

			SAMLAttribute priorDsigAttribute =
				new SAMLAttribute(SAMLConstants.PRIOR_DSIG_ATTRIBUTE_NAME, SAMLConstants.PLACEHOLDER_FOR_NAME_FORMAT);
			priorDsigAttribute.addStringAttributeValue(priorDsig);
			attributes.add(priorDsigAttribute);
		}

		if (!accessMask.isEmpty()) {
			SAMLAttribute accessMaskAttribute =
				new SAMLAttribute(SAMLConstants.ACCESS_RIGHT_MASK_ATTRIBUTE_NAME, SAMLConstants.PLACEHOLDER_FOR_NAME_FORMAT);
			for (RWXCategory category : accessMask) {
				accessMaskAttribute.addStringAttributeValue(category.name());
			}
			attributes.add(accessMaskAttribute);
		}

		SAMLAttribute typeAttrib =
			new SAMLAttribute(SAMLConstants.DELEGATEE_IDENTITY_TYPE_ATTRIBUTE_NAME, SAMLConstants.PLACEHOLDER_FOR_NAME_FORMAT);
		typeAttrib.addStringAttributeValue(_delegateeType.toString());
		attributes.add(typeAttrib);

		typeAttrib = new SAMLAttribute(SAMLConstants.ISSUER_IDENTITY_TYPE_ATTRIBUTE_NAME, SAMLConstants.PLACEHOLDER_FOR_NAME_FORMAT);
		typeAttrib.addStringAttributeValue(_issuerType.toString());
		attributes.add(typeAttrib);

		// note: this creation phase did not need synchronization 2013-02-09.
		ETDApi etdApi = new ETDImpl();
		try {
			delegation = etdApi.generateTD(issuer[0], issuer, privateKey, delegatee, convert(restrictions), attributes);
			signed = true;
		} catch (Exception e) {
			throw new SecurityException("failure: could not delegate trust properly!", e);
		}

		boolean superNoisyDebug = false;
		if (superNoisyDebug) {
			// extra checking--test the signature we just made.
			try {
				SimpleTrustChecker stc = new SimpleTrustChecker(delegation.getIssuerFromSignature()[0], true);
				stc.checkTrust(delegation.getXMLBeanDoc());
				_logger.debug("SUCCESS checking trust delegation just made.");
			} catch (Exception e) {
				_logger.error("exception checking signature just made for cred: " + toString() + " and last few frames are: "
					+ ProgramTools.showLastFewOnStack(28), e);
			}

			AssertionDocument doc = delegation.getXMLBeanDoc();
			try {
				TrustCredential newcred = new TrustCredential(doc.getDomNode());
				_logger.info("SUCCESS again in creating trust cred from dom node: " + newcred.describe(VerbosityLevel.LOW));
			} catch (Exception e) {
				_logger.error("failed to create new trust cred from domnode although original was good");
			}
		}

	}

	@Override
	public String describe(VerbosityLevel verbosity)
	{
		StringBuilder toReturn = new StringBuilder();
		if (priorDelegation != null) {
			// prepend the original part of the chain.
			toReturn.append(priorDelegation.describe(verbosity));
		} else {
			// this is terminal delegation.
			toReturn.append(new X509Identity(issuer, _issuerType).describe(verbosity));
		}

		if (verbosity == VerbosityLevel.HIGH) {
			toReturn.append(" {delegation " + restrictions.describe(verbosity) + "}");
		}

		toReturn.append(" -> " + new X509Identity(delegatee, _delegateeType).describe(verbosity));

		return toReturn.toString();
	}

	public String showIdChain()
	{
		StringBuilder toReturn = new StringBuilder();
		if (priorDelegation != null) {
			// prepend the original part of the chain.
			toReturn.append(priorDelegation.showIdChain() + " ==> ");
		} else {
			// this is terminal delegation.
			toReturn.append(id);
		}
		return toReturn.toString();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException
	{
		if (!signed)
			throw new SecurityException("failure: attempting to write an unsigned credential");
		CredentialWallet singleAssertionChainWallet = new CredentialWallet();
		singleAssertionChainWallet.addCredential(this);
		singleAssertionChainWallet.writeExternal(out);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
	{
		CredentialWallet singleAssertionChainWallet = new CredentialWallet();
		try {
			singleAssertionChainWallet.readExternal(in);
		} catch (Throwable e) {
			String msg = "failed to read credential via wallet";
			_logger.error(msg, e);
			throw new IOException(msg, e);
		}
		if (singleAssertionChainWallet.getCredentials().size() == 0) {
			String msg = "failed to deserialize any credentials; returning an empty credential.";
			_logger.error(msg);
			if (_logger.isDebugEnabled())
				_logger.debug("stack is:" + ProgramTools.showLastFewOnStack(20));
			return;
		}
		TrustCredential singular = singleAssertionChainWallet.getCredentials().get(0);

		this.delegatee = singular.delegatee;
		this.issuer = singular.issuer;
		this.restrictions = singular.restrictions;
		this.id = singular.id;
		this.accessMask = singular.accessMask;
		this._delegateeType = singular._delegateeType;
		this._issuerType = singular._issuerType;
		this.delegation = singular.delegation;
		this.priorDelegationId = singular.priorDelegationId;
		this.priorDsig = singular.priorDsig;
		this.priorDelegation = singular.priorDelegation;
		this.signed = singular.signed;

		if (_logger.isTraceEnabled())
			_logger.trace("serialization rebuilt trust delegation: " + toString());
	}

	/**
	 * regenerates a trust credential from the Unicore form.
	 */
	@SuppressWarnings("deprecation")
	private void constructFromTrustDelegation(TrustDelegation delegation)
	{
		boolean signatureWasVerified = false;
		try {
			synchronized (delegation) {
				SimpleTrustChecker stc = new SimpleTrustChecker(delegation.getIssuerFromSignature()[0], false);
				stc.checkTrust(delegation.getXMLBeanDoc());
			}
			signatureWasVerified = true;
		} catch (Exception e) {
			throw new SecurityException("failure: an invalid trust delegation was found!  delegation has subject " + delegation.getSubjectDN()
				+ ", custodian " + delegation.getCustodianDN() + ", and issuer " + delegation.getIssuerDN(), e);
		}
		this.delegation = delegation;
		this.signed = true;
		this.properlySigned = signatureWasVerified;
		this.delegatee = delegation.getSubjectFromConfirmation();
		this.issuer = delegation.getIssuerFromSignature();
		this.restrictions =
			convert(new DelegationRestrictions(delegation.getNotBefore(), delegation.getNotOnOrAfter(), delegation.getProxyRestriction()));
		retrieveAttributesFromDelegation();
	}

	/**
	 * pulls out the attributes where we have stored the parts of the trust credential that unicore doesn't directly support.
	 */
	private void retrieveAttributesFromDelegation()
	{
		synchronized (delegation) {
			AttributeStatementType[] attributes = delegation.getXMLBeanDoc().getAssertion().getAttributeStatementArray();
			if (attributes != null) {
				for (AttributeStatementType attributeStmt : attributes) {
					for (AttributeType attribute : attributeStmt.getAttributeArray()) {
						String name = attribute.getName();
						if (SAMLConstants.ASSERTION_ID_ATTRIBUTE_NAME.equals(name)) {
							XmlObject xml = attribute.getAttributeValueArray(0);
							this.id = parseAttributeValue(xml);
						} else if (SAMLConstants.PRIOR_ASSERTION_ID_ATTRIBUTE_NAME.equals(name)) {
							XmlObject xml = attribute.getAttributeValueArray(0);
							this.priorDelegationId = parseAttributeValue(xml);
						} else if (SAMLConstants.PRIOR_DSIG_ATTRIBUTE_NAME.equals(name)) {
							XmlObject xml = attribute.getAttributeValueArray(0);
							this.priorDsig = parseAttributeValue(xml);
						} else if (SAMLConstants.ACCESS_RIGHT_MASK_ATTRIBUTE_NAME.equals(name)) {
							Set<RWXCategory> categorySet = new HashSet<RWXCategory>();
							for (XmlObject xml : attribute.getAttributeValueArray()) {
								RWXCategory category = RWXCategory.getMatchingCategory(parseAttributeValue(xml));
								if (category != null)
									categorySet.add(category);
							}
							this.accessMask = EnumSet.copyOf(categorySet);
						} else if (SAMLConstants.DELEGATEE_IDENTITY_TYPE_ATTRIBUTE_NAME.equals(name)) {
							XmlObject xml = attribute.getAttributeValueArray(0);
							this._delegateeType = IdentityType.valueOf(parseAttributeValue(xml));
						} else if (SAMLConstants.ISSUER_IDENTITY_TYPE_ATTRIBUTE_NAME.equals(name)) {
							XmlObject xml = attribute.getAttributeValueArray(0);
							this._issuerType = IdentityType.valueOf(parseAttributeValue(xml));
						}
					}
				}
			}
			if (this.id == null) {
				throw new SecurityException("failure: an attempt to use an assertion having no ID!");
			}
		}
	}

	/**
	 * A convenient method for extracting an attribute value from an XMLFragment. This works for only simple attributes, e.g., that can be
	 * represented as a string. For reason unknown, direct parsing of attributes using UNICORE library is not working and throwing an strange
	 * XMLBean XmlValueDisconnectedException. If we need to use any compound attribute in our trust delegations then we have to construct a
	 * more generic solution as the remedy of the parsing problem.
	 */
	private String parseAttributeValue(XmlObject attributeValueXML)
	{
		String attributeDescription = attributeValueXML.toString();
		int valueStart = attributeDescription.indexOf('>');
		int valueEnd = attributeDescription.indexOf('<', valueStart);
		return attributeDescription.substring(valueStart + 1, valueEnd);
	}

	public void checkValidityUber(Date date, boolean performLogging) throws AttributeInvalidException
	{
		if (!signed) {
			String msg = "this assertion is not signed yet, and so therefore not valid.";
			if (performLogging)
				_logger.error(msg);
			throw new AttributeInvalidException(msg);
		}
		// test that the delegation chain appears sound.
		if (priorDelegation != null) {
			if (!priorDelegation.getId().equals(priorDelegationId)) {
				String msg = "the attached prior delegation has the wrong ID!: " + priorDelegation.toString();
				if (performLogging)
					_logger.error(msg);
				throw new AttributeInvalidException(msg);
			} else if (!priorDelegation.locateDsigValue().equals(priorDsig)) {
				String msg = "the attached prior delegation has the wrong dsig!: " + priorDelegation.toString();
				if (performLogging)
					_logger.error(msg);
				throw new AttributeInvalidException(msg);
			}
			priorDelegation.checkValidity(date);
		} else {
			if (priorDelegationId != null) {
				String msg = "there is an id for the prior delegation but it is null!: " + priorDelegationId;
				if (performLogging) {
					_logger.error(msg);
				}
				throw new AttributeInvalidException(msg);
			}
		}

		try {
			// test our constraints.
			getConstraints().checkValidity(getDelegationDepth(), date);
		} catch (AttributeInvalidException t) {
			if (performLogging) {
				_logger.error("failed to validate basic date validity or delegation depth", t);
			}
			throw t;
		}

		if (properlySigned) {
			// we have already checked this particular delegation.
			return;
		}

		try {
			synchronized (delegation) {
				if (!delegation.getIssuerFromSignature()[0].equals(getIssuer()[0])) {
					String msg = "the signature in TD and signature here do not match! deleg issuer="
						+ delegation.getIssuerFromSignature()[0].getSubjectDN() + " vs. issuer here=" + getIssuer()[0].getSubjectDN();
					if (performLogging)
						_logger.error(msg + " ...came in via: " + ProgramTools.showLastFewOnStack(5));
					throw new AttributeInvalidException(msg);
				}
				SimpleTrustChecker stc = new SimpleTrustChecker(delegation.getIssuerFromSignature()[0], true);
				stc.checkTrust(delegation.getXMLBeanDoc());
			}
		} catch (SAMLValidationException e) {
			String msg = "failed to validate signature on our TrustDelegation: " + e.getMessage() + "\n...testing on: " + toString()
				+ " signed by " + getIssuer()[0].getSubjectDN();
			if (performLogging)
				_logger.error(msg + " ...came in via: " + ProgramTools.showLastFewOnStack(5));
			throw new AttributeInvalidException(msg);
		}
		if (_logger.isTraceEnabled() && performLogging)
			_logger.trace("successfully validated trust delegation assertion.");
		this.properlySigned = true;
	}

	/**
	 * tests that the credential is valid. this method is used by unicore gffs interop.
	 */
	@Override
	public void checkValidity(Date date) throws AttributeInvalidException
	{
		checkValidityUber(date, true);
	}

	/**
	 * Note that the end-entity of a trust delegation chain is the issuer of the innermost delegation, and is not the delegatee. This is a
	 * shift from our old system of GIICredentials. In the old system, the ultimate end-entity such as an IDP resource would create an
	 * IdentityCredential having its certificate chains in it. Then it would sign the attribute with the requester as the delegatee and itself
	 * as the issuer of the resulting assertion. In our new system, we are not creating the IdentityCredential; rather creating a trust
	 * delegation directly. This is equivalent of the statement that IDP resource has given permissions to the requester to act on its behalf.
	 * Therefore, when checking for access right on any other resource, we have to use the issuer's (in the example case, IDP resource's)
	 * certificate.
	 */
	@Override
	public X509Certificate[] getOriginalAsserter()
	{
		if ((priorDelegationId != null) && (priorDelegation == null)) {
			throw new SecurityException("failure: could not retrieve end entity identity from trust delegation chain!");
		}
		if (priorDelegation != null)
			return priorDelegation.getOriginalAsserter();
		return getIssuer();
	}

	@Override
	public boolean checkRWXAccess(RWXCategory category)
	{
		if (!accessMask.contains(category)) {
			if (_logger.isTraceEnabled())
				_logger.trace("Credential does not have " + category.toString() + " access.");
			return false;
		}
		if (priorDelegation != null)
			return priorDelegation.checkRWXAccess(category);
		return true;
	}

	/**
	 * luckily we have a unique id that can be checked for equality. this class does not support any notions of equivalency aside from having
	 * identical guids.
	 */
	@Override
	public boolean equals(Object other)
	{
		if (other == null)
			return false; // no instance equals null.
		if (!(other instanceof TrustCredential))
			return false; // wrong type.
		TrustCredential otherCast = (TrustCredential) other;
		if (!signed || !otherCast.signed) {
			_logger.error("attempt to compare equality on unsigned object");
			return false;
		}
		return otherCast.id == id;
	}

	@Override
	public int hashCode()
	{
		// feed the hash code builder a couple of random-ish prime numbers.
		return new HashCodeBuilder(281, 31).append(id).toHashCode();
	}

	// sentinel value returned when findDelegateeInChain sees the certificate as the chain's issuer.
	static public final int TO_FIND_WAS_ISSUER = 10000;

	/**
	 * returns the position of the certificate in the list of delegatees in the trust credential. 0 is the initial delegation. if the
	 * certificate is not found as a delegatee, then -1 is returned. note that this will also return TO_FIND_WAS_ISSUER if the "toFind"
	 * certificate is the actual issuer of the chain, meaning that the identity sought actually created the chain; in this case it is crucial
	 * one does not take that returned index seriously, since one cannot find it in a valid chain. this is in line with proving that the tls
	 * session cert for the caller actually signed the message, although we only issue credentials like that in the pass through case for
	 * authenticating based on the original tls session cert of the caller.
	 */
	public int findDelegateeInChain(X509Certificate toFind)
	{
		if (getOriginalAsserter()[0].equals(toFind)) {
			// special case where the identity in question started the whole chain.
			return TO_FIND_WAS_ISSUER;
		}
		int chainLength = this.getDelegationDepth();
		TrustCredential curr = this;
		while (curr != null) {
			chainLength -= 1;
			if (toFind.equals(curr.getDelegatee()[0])) {
				return chainLength;
			}
			curr = curr.getPriorDelegation();
		}
		// not found in there.
		return -1;
	}

	/**
	 * formats a list of credentials in a nice manner.
	 */
	public static String showCredentialList(List<NuCredential> toShow, VerbosityLevel verbosity)
	{
		StringBuilder buffer = new StringBuilder();
		for (NuCredential assertion : toShow) {
			buffer.append(assertion.describe(verbosity)).append("\n");
		}
		return buffer.toString();
	}

	public static String showCredentialList(Collection<NuCredential> toShow, VerbosityLevel verbosity)
	{
		StringBuilder buffer = new StringBuilder();
		for (NuCredential assertion : toShow) {
			buffer.append(assertion.describe(verbosity)).append("\n");
		}
		return buffer.toString();
	}

	/**
	 * tests whether the trust credential can be serialized, then deserialized, and actually recovered.
	 */
	static public boolean paranoidSerializationCheck(TrustCredential toCheck)
	{
		try {
			toCheck.checkValidity(new Date());
		} catch (Exception e) {
			_logger.error("credential is not even valid in serialization check!");
			return false;
		}

		// testing whether we get back full creds from write/read external.
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oo = null;
		try {
			oo = new ObjectOutputStream(baos);
			CredentialWallet cw = new CredentialWallet();
			cw.addCredential(toCheck);
			cw.writeExternal(oo);
			baos.flush();
		} catch (IOException e) {
			_logger.error("failure to serialize newly extended credential!", e);
			return false;
		}
		if (oo != null) {
			byte[] chunk = baos.toByteArray();
			ByteArrayInputStream bais = new ByteArrayInputStream(chunk);
			ObjectInputStream ois;
			try {
				ois = new ObjectInputStream(bais);
				CredentialWallet reconstituted = new CredentialWallet();
				reconstituted.readExternal(ois);
				if (reconstituted.isEmpty())
					throw new IOException("credential wallet is empty after reconstitution!");
				if (reconstituted.getCredentials().size() > 1)
					throw new IOException("credential wallet has too many credentials after reconstitution!");

				if (_logger.isTraceEnabled()) {
					_logger.debug("reconst wallet is:\n" + reconstituted.describe(VerbosityLevel.HIGH));
				}

				TrustCredential tc = reconstituted.getCredentials().get(0);

				if (tc.getDelegationDepth() != toCheck.getDelegationDepth()) {
					throw new IOException("reconstituted credential has wrong delegation depth!  reconst=" + tc.getDelegationDepth()
						+ ", orig=" + toCheck.getDelegationDepth());
				}

				if (!tc.getIssuer()[0].getSubjectX500Principal().toString()
					.equals(toCheck.getIssuer()[0].getSubjectX500Principal().toString())) {
					throw new IOException("issuer disagrees in reconstituted credential: original="
						+ toCheck.getIssuer()[0].getSubjectX500Principal() + " recons=" + tc.getIssuer()[0].getSubjectX500Principal());
				}

				if (!tc.getDelegatee()[0].getSubjectX500Principal().toString()
					.equals(toCheck.getDelegatee()[0].getSubjectX500Principal().toString())) {
					throw new IOException("delegatee disagrees in reconstituted credential: original="
						+ toCheck.getDelegatee()[0].getSubjectX500Principal() + " recons=" + tc.getDelegatee()[0].getSubjectX500Principal());
				}

				if (_logger.isDebugEnabled())
					_logger.debug("reconstituted as: " + tc.describe(VerbosityLevel.HIGH));

			} catch (Exception e) {
				_logger.error("failure to deserialize newly extended credential!", e);
				return false;
			}

		}
		return true;
	}

}
