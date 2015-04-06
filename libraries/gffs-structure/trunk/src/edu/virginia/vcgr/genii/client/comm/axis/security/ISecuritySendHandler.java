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

package edu.virginia.vcgr.genii.client.comm.axis.security;

import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;

/**
 * Interface for GII message send-handlers
 * 
 * @author dgm4d
 * 
 */
public interface ISecuritySendHandler extends org.apache.axis.Handler
{

	/**
	 * Indicates that this handler is the final handler and should serialize the message context
	 */
	public void setToSerialize();

	/**
	 * Configures the Send handler. Returns whether or not this handler is to perform any actions
	 */
	public boolean configure(ICallingContext callContext, MessageSecurity msgSecData) throws AuthZSecurityException;

}
