/*
 * Copyright 2006 University of Virginia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package edu.virginia.vcgr.genii.container.kerbauthn;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sun.security.auth.module.Krb5LoginModule;

import edu.virginia.vcgr.genii.client.configuration.ContainerConfiguration;
import edu.virginia.vcgr.genii.client.configuration.DeploymentName;
import edu.virginia.vcgr.genii.client.configuration.Installation;
import edu.virginia.vcgr.genii.client.configuration.SslInformation;
import edu.virginia.vcgr.genii.client.configuration.SslInformation.KerberosKeytabAndPrincipal;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.resource.IResource;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.container.security.authz.providers.AclAuthZProvider;
import edu.virginia.vcgr.genii.security.RWXCategory;
import edu.virginia.vcgr.genii.security.SAMLConstants;
import edu.virginia.vcgr.genii.security.SecurityConstants;
import edu.virginia.vcgr.genii.security.credentials.NuCredential;
import edu.virginia.vcgr.genii.security.credentials.identity.UsernamePasswordIdentity;

/**
 * Kerberos access control implementation, extends default ACL authz provider
 * 
 * @author dmerrill
 * 
 */
public class KerbAuthZProvider extends AclAuthZProvider
{
	static private Log _logger = LogFactory.getLog(KerbAuthZProvider.class);

	/**
	 * R/W lock for the system-wide KDC setting (shame on you Sun)
	 */
	static private ReentrantReadWriteLock kdc_lock = new ReentrantReadWriteLock();

	public KerbAuthZProvider() throws AuthZSecurityException, IOException
	{
	}

	@Override
	public boolean checkAccess(Collection<NuCredential> authenticatedCallerCredentials, IResource resource, Class<?> serviceClass,
		Method operation)
	{
		// Try regular ACLs for administrative access.
		try {
			/*
			 * we cannot let the credentials be used intact, because the myproxy identity could be in here, which would lead us to think we
			 * authenticated already when we have not yet. thus we strip out that credential if we see it and force the user/password
			 * authentication process to occur.
			 */
			ArrayList<NuCredential> prunedCredentials = new ArrayList<NuCredential>();
			X509Certificate[] resourceCertChain = null;
			try {
				resourceCertChain = (X509Certificate[]) resource.getProperty(IResource.CERTIFICATE_CHAIN_PROPERTY_NAME);
			} catch (ResourceException e) {
				_logger.error("failed to load resource certificate chain for kerberos auth.  resource is: " + resource.toString());
				// this seems really pretty bad. the resource is bogus.
				return false;
			}
			for (NuCredential cred : authenticatedCallerCredentials) {
				if (cred.getOriginalAsserter().equals(resourceCertChain)) {
					_logger.debug("dropping kerberos own identity from cred set so we can authorize it.");
					continue;
				}
				prunedCredentials.add(cred);
			}

			/*
			 * we must check that the resource is writable if we're going to skip authentication. this must only be true for the admin of the
			 * STS.
			 */
			boolean accessOkay = checkAccess(prunedCredentials, resource, RWXCategory.WRITE);
			if (accessOkay) {
				_logger.debug("skipping kerberos authentication due to administrative access to resource.");
				if (_logger.isTraceEnabled()) {
					blurtCredentials("credentials that enabled kerberos authz skip are: ", prunedCredentials);
				}
				return true;
			}
		} catch (Exception AclException) {
			/*
			 * we assume we will need the sequel of the function now, since admin ACLs didn't work.
			 */
		}

		/*
		 * just try a traditional access check now. we do this before kerberos in case we already have permission; otherwise we would
		 * re-authorize using username and password where we could avoid it.
		 */
		boolean accessOkay = super.checkAccess(authenticatedCallerCredentials, resource, serviceClass, operation);
		if (accessOkay) {
			if (_logger.isDebugEnabled())
				_logger.debug("allowing kerberos auth due to base class permission.");
			return true;
		}

		// Try kerberos back-end. This is the 'normal' case.
		boolean kerberosAuthOkay = testKerberosAuthorization(resource);
		if (kerberosAuthOkay) {
			return true;
		}

		// Nobody appreciates us.
		String assetName = resource.toString();
		try {
			String addIn = (String) resource.getProperty(SecurityConstants.NEW_IDP_NAME_QNAME.getLocalPart());
			if (addIn != null)
				assetName.concat(" -- " + addIn);
		} catch (ResourceException e) {
			// ignore.
		}
		_logger.error("failure to authorize " + operation.getName() + " for " + assetName);
		return false;
	}

	/**
	 * does the heavy lifting of authenticating the user against kerberos. for xsede kdc, this also requires that the sts container have a
	 * secret that authorizes the app against the kdc.
	 */
	static public boolean testKerberosAuthorization(IResource resource)
	{
		String username = "";
		String realm = "";
		String kdc = "";
		try {
			username = (String) resource.getProperty(SecurityConstants.NEW_IDP_NAME_QNAME.getLocalPart());
			realm = (String) resource.getProperty(SecurityConstants.NEW_KERB_IDP_REALM_QNAME.getLocalPart());
			kdc = (String) resource.getProperty(SecurityConstants.NEW_KERB_IDP_KDC_QNAME.getLocalPart());
		} catch (ResourceException e) {
			_logger.error("failed to retrieve kerberos properties: " + e.getMessage());
			return false;
		}

		if ((realm == null) || (kdc == null)) {
			_logger.error("Insufficient Kerberos realm/kdc configuration.");
			return false;
		}

		// load the keytable and principal from our file based properties.
		SslInformation sslinfo = ContainerConfiguration.getTheContainerConfig().getSslInformation();
		KerberosKeytabAndPrincipal keypr = sslinfo.loadKerberosKeytable(realm);

		if (keypr == null) {
			_logger.warn("INSECURE Kerberos authentication in realm " + realm + " due to missing keytab or principal!");
			if (realm.equals("TERAGRID.ORG")) {
				String msg = "TERAGRID.ORG realm requires authorization to a service principal.  Please ensure "
					+ "keytab and principal are defined in deployment's configuration/security.properties file.";
				_logger.error(msg);
				throw new SecurityException(msg);
			}
		}

		_logger.info("kerberos STS will authenticate against: realm=" + realm + " kdc=" + kdc);

		ICallingContext callingContext;
		try {
			callingContext = ContextManager.getExistingContext();
		} catch (IOException e) {
			_logger.error("Calling context exception", e);
			return false;
		}

		// try each identity in the caller's credentials
		@SuppressWarnings("unchecked")
		ArrayList<NuCredential> callerCredentials =
			(ArrayList<NuCredential>) callingContext.getTransientProperty(SAMLConstants.CALLER_CREDENTIALS_PROPERTY);
		for (NuCredential cred : callerCredentials) {
			if (cred instanceof UsernamePasswordIdentity) {
				// Grab password from usernametoken (but use the username that is our resource name)
				UsernamePasswordIdentity utIdentity = (UsernamePasswordIdentity) cred;
				String password = utIdentity.getPassword();

				try {
					// Acquire kdc-settings read lock.
					kdc_lock.readLock().lock();

					if (!realm.equals(System.getProperty("java.security.krb5.realm"))
						|| !kdc.equals(System.getProperty("java.security.krb5.kdc"))) {
						_logger.debug("switching kerberos realm for auth attempt");
						// Wants different KDC/realm. Upgrade lock
						kdc_lock.readLock().unlock();
						kdc_lock.writeLock().lock();

						// The only way to set realm/KDC is through these system properties
						// (or the krb5.ini/conf global config file). Shame on you, Sun.
						System.setProperty("java.security.krb5.realm", realm);
						System.setProperty("java.security.krb5.kdc", kdc);

						// Downgrade lock, acquiring read before giving up write
						kdc_lock.readLock().lock();
						kdc_lock.writeLock().unlock();
					}

					// fill in the keytab and principal if we have them and authenticate the
					// service.
					if (keypr != null) {
						// KDC config.
						Map<String, String> state = new HashMap<String, String>();
						Map<String, String> serverOptions = new HashMap<String, String>();
						serverOptions.put("useTicketCache", "false");
						serverOptions.put("refreshKrb5Config", "true");

						_logger.info("Kerberos: authenticating with service principal '" + keypr._principal + "' for realm '" + realm + "'");
						serverOptions.put("useKeyTab", "true");
						File fullKeytabPath = Installation.getDeployment(new DeploymentName()).security().getSecurityFile(keypr._keytab);
						if (!(new File(fullKeytabPath.getAbsolutePath())).exists()) {
							_logger.error(
								"Failing authentication on kerberos because keytab file does not exist: " + fullKeytabPath.getAbsolutePath());
							return false;
						}
						if (_logger.isDebugEnabled())
							_logger.debug("Kerberos keytab for realm " + realm + " is at path: " + fullKeytabPath.getAbsolutePath());
						serverOptions.put("keyTab", fullKeytabPath.getAbsolutePath());
						serverOptions.put("principal", keypr._principal);
						serverOptions.put("doNotPrompt", "true");

						try {
							Krb5LoginModule serverLoginCtx = new Krb5LoginModule();
							Subject serverSubject = new Subject();
							// user and password apparently do not matter for server login.
							serverLoginCtx.initialize(serverSubject, new LoginCallbackHandler("boink", "doink"), state, serverOptions);
							/*
							 * ignoring always "true" return from login; will catch exception if login failure.
							 */
							serverLoginCtx.login();
							_logger.info("Success authenticating service principal '" + keypr._principal + "' into realm '" + realm + "'");
						} catch (LoginException e) {
							_logger.error("Failure authenticating service principal '" + keypr._principal + "' into realm '" + realm + "'");
							return false;
						}
					}

					Map<String, String> state = new HashMap<String, String>();
					Map<String, String> userOptions = new HashMap<String, String>();
					userOptions.put("useTicketCache", "false");
					userOptions.put("refreshKrb5Config", "true");

					// must always attempt to login as the user, regardless of existence of service
					// principal.
					_logger.info("authenticating user '" + username + "' against realm '" + realm + "'");
					Krb5LoginModule userLoginCtx = new Krb5LoginModule();
					Subject userSubject = new Subject();
					userLoginCtx.initialize(userSubject, new LoginCallbackHandler(username, password), state, userOptions);
					/*
					 * ignoring always "true" return from login; will catch exception if login failure.
					 */
					userLoginCtx.login();

					_logger.info("Success logging kerberos user '" + username + "' into realm '" + realm + "'");
					return true;
				} catch (LoginException e) {
					_logger.error("Failure logging kerberos user '" + username + "' into realm '" + realm + "'", e);
					return false;
				} finally {
					// Release read lock
					kdc_lock.readLock().unlock();
				}
			}
		}
		// ultimately, a failure, since we would have returned true before here if things were good.
		return false;
	}
}
