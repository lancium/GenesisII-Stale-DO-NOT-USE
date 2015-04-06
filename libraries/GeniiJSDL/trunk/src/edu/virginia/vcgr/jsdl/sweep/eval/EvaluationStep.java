/*
 * This code was developed by Mark Morgan (mmm2a@virginia.edu) at the University of Virginia and is an implementation of JSDL, JSDL
 * ParameterSweep and other JSDL related specifications from the OGF.
 * 
 * Copyright 2010 University of Virginia
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
package edu.virginia.vcgr.jsdl.sweep.eval;

import java.util.List;

import edu.virginia.vcgr.jsdl.sweep.SweepException;

/**
 * @author Mark Morgan (mmm2a@virginia.edu)
 */
public class EvaluationStep implements Evaluable
{
	private List<SweepTargetIdentifier> _identifiers;
	private Object _value;

	public EvaluationStep(List<SweepTargetIdentifier> identifiers, Object value)
	{
		_identifiers = identifiers;
		_value = value;
	}

	@Override
	final public void evaluate(EvaluationContext context) throws SweepException
	{
		for (SweepTargetIdentifier identifier : _identifiers)
			identifier.identify(context.document()).replace(_value);
	}
}
