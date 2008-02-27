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
package edu.virginia.vcgr.genii.container.bes.activity.resource;

import edu.virginia.vcgr.genii.client.bes.ActivityState;
import edu.virginia.vcgr.genii.client.resource.ResourceException;

import org.oasis_open.docs.wsrf.r_2.ResourceUnknownFaultType;

public interface IStateSaveCallback
{
	public void saveState(ActivityState state)
		throws ResourceUnknownFaultType, ResourceException;
	public ActivityState getSavedStatus() 
		throws ResourceException, ResourceUnknownFaultType;
	
	public void noteError(Throwable cause)
		throws ResourceUnknownFaultType, ResourceException;
	public Throwable getError(Throwable cause)
		throws ResourceUnknownFaultType, ResourceException;
}
