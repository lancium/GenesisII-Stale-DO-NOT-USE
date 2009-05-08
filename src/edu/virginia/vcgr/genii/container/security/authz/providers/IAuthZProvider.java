/*
 * Copyright 2006 University of Virginia
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package edu.virginia.vcgr.genii.container.security.authz.providers;

import java.lang.reflect.Method;
import java.security.cert.X509Certificate;
import java.util.Collection;

import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.container.resource.IResource;
import edu.virginia.vcgr.genii.client.security.MessageLevelSecurityRequirements;
import edu.virginia.vcgr.genii.client.security.authz.AuthZSecurityException;
import edu.virginia.vcgr.genii.client.security.authz.PermissionDeniedException;
import edu.virginia.vcgr.genii.client.security.credentials.GIICredential;
import edu.virginia.vcgr.genii.common.security.AuthZConfig;

/**
 * Interface for Container-side authorization providers. 
 * 
 * @author dgm4d
 *
 */
public interface IAuthZProvider
{

	static public final String CALLING_CONTEXT_CALLER_CERT =
			"genii.container.security.authz.caller-cert";

	/**
	 * Configures the resource with default access control state. This
	 * configuration may be based upon informatin within the specified working
	 * context
	 */
	public void setDefaultAccess(ICallingContext callingContext,
			IResource resource, X509Certificate[] serviceCertChain)
			throws AuthZSecurityException, ResourceException;

	/**
	 * Checks whether or not an invocation of the specified method on the
	 * target resource is allowable with the given working context.
	 * 
	 * @throws PermissionDeniedException if not allowed 
	 */
	public void checkAccess(
			Collection<GIICredential> authenticatedCallerCredentials,
			IResource resource, 
			Class<?> serviceClass, 
			Method operation)
		throws PermissionDeniedException, AuthZSecurityException, ResourceException;

	/**
	 * Returns the minimum level of incoming message level security required for
	 * the specified resource
	 */
	public MessageLevelSecurityRequirements getMinIncomingMsgLevelSecurity(
			IResource resource) throws AuthZSecurityException,
			ResourceException;

	/**
	 * Returns the entire AuthZ configuration for the resource
	 */
	public AuthZConfig getAuthZConfig(IResource resource)
			throws AuthZSecurityException, ResourceException;

	/**
	 * Sets the entire AuthZ configuration for the resource
	 */
	public void setAuthZConfig(AuthZConfig config, IResource resource)
			throws AuthZSecurityException, ResourceException;

}
