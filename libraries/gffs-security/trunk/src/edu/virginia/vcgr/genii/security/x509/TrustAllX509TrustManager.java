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
package edu.virginia.vcgr.genii.security.x509;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A simple trust manager that always trusts everything.
 * 
 * The trust manager is responsible for managing the trust material that is used when making trust decisions, and for deciding whether
 * credentials presented by a peer should be accepted.
 * 
 * @author dmerrill
 */
public class TrustAllX509TrustManager implements X509TrustManager
{
	static private Log _logger = LogFactory.getLog(TrustAllX509TrustManager.class);

	/**
	 * Constructor
	 */
	public TrustAllX509TrustManager()
	{
	}

	/**
	 * Return an array of certificate authority certificates which are trusted for authenticating peers.
	 */
	@Override
	public X509Certificate[] getAcceptedIssuers()
	{
		return new X509Certificate[0];
	}

	/**
	 * Given the partial or complete certificate chain provided by the peer, build a certificate path to a trusted root and return if it can
	 * be validated and is trusted for client SSL authentication based on the authentication type.
	 */
	@Override
	public void checkClientTrusted(X509Certificate chain[], String authType) throws CertificateException
	{
		_logger.debug("seeing client: " + chain[0].getSubjectDN());
	}

	/**
	 * Given the partial or complete certificate chain provided by the peer, build a certificate path to a trusted root and return if it can
	 * be validated and is trusted for server SSL authentication based on the authentication type
	 */
	@Override
	public void checkServerTrusted(X509Certificate chain[], String authType) throws CertificateException
	{
		_logger.debug("seeing server: " + chain[0].getSubjectDN());
	}
}
