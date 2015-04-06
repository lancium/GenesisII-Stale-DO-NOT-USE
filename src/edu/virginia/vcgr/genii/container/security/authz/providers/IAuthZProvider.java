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

package edu.virginia.vcgr.genii.container.security.authz.providers;

import java.lang.reflect.Method;
import java.security.cert.X509Certificate;
import java.util.Collection;

import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.resource.IResource;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.client.wsrf.wsn.NotificationMessageContents;
import edu.virginia.vcgr.genii.common.security.AuthZConfig;
import edu.virginia.vcgr.genii.security.RWXCategory;
import edu.virginia.vcgr.genii.security.axis.MessageLevelSecurityRequirements;
import edu.virginia.vcgr.genii.security.credentials.NuCredential;

/**
 * Interface for Container-side authorization providers.
 * 
 * @author dgm4d
 * 
 */
public interface IAuthZProvider
{

	static public final String CALLING_CONTEXT_CALLER_CERT = "genii.container.security.authz.caller-cert";

	/**
	 * Configures the resource with default access control state. This configuration may be based upon information within the specified
	 * working context
	 */
	public void setDefaultAccess(ICallingContext callingContext, IResource resource, X509Certificate[] serviceCertChain)
		throws AuthZSecurityException, ResourceException;

	/**
	 * Checks whether or not an invocation of the specified method on the target resource is allowable with the given working context.
	 */
	public boolean checkAccess(Collection<NuCredential> authenticatedCallerCredentials, IResource resource, Class<?> serviceClass,
		Method operation);

	/**
	 * Checks whether or not the caller has read, write, or execute permission on the given resource.
	 */
	public boolean checkAccess(Collection<NuCredential> authenticatedCallerCredentials, IResource resource, RWXCategory category);

	/**
	 * Returns the minimum level of incoming message level security required for the specified resource
	 */
	public MessageLevelSecurityRequirements getMinIncomingMsgLevelSecurity(IResource resource) throws AuthZSecurityException,
		ResourceException;

	/**
	 * Returns the entire AuthZ configuration for the resource, by default sanitized
	 */
	public AuthZConfig getAuthZConfig(IResource resource) throws AuthZSecurityException, ResourceException;

	public AuthZConfig getAuthZConfig(IResource resource, boolean sanitize) throws AuthZSecurityException, ResourceException;

	/**
	 * Sets the entire AuthZ configuration for the resource
	 */
	public void setAuthZConfig(AuthZConfig config, IResource resource) throws AuthZSecurityException, ResourceException;

	/**
	 * Inform subscribers that the AuthZ configuration was changed.
	 */
	public void sendAuthZConfig(AuthZConfig oldConfig, AuthZConfig newConfig, IResource resource) throws AuthZSecurityException,
		ResourceException;

	/**
	 * Update the resource AuthZ configuration with the changes that were applied to a replica.
	 */
	public void receiveAuthZConfig(NotificationMessageContents message, IResource resource) throws ResourceException, AuthZSecurityException;
}
