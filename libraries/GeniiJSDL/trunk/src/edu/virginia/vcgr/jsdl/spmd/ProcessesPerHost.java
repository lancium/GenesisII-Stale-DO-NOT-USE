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
package edu.virginia.vcgr.jsdl.spmd;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAnyAttribute;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.namespace.QName;

/**
 * @author Mark Morgan (mmm2a@virginia.edu)
 */
public class ProcessesPerHost implements Serializable
{
	static final long serialVersionUID = 0L;

	@XmlAnyAttribute
	private Map<QName, String> _anyAttributes = new HashMap<QName, String>();

	@XmlValue
	private long _value;

	/**
	 * For use by XML Unmarshalling only.
	 */
	@SuppressWarnings("unused")
	private ProcessesPerHost()
	{
	}

	public ProcessesPerHost(long value)
	{
		_value = value;
	}

	final public void value(long value)
	{
		_value = value;
	}

	final public long value()
	{
		return _value;
	}

	final public Map<QName, String> anyAttributes()
	{
		return _anyAttributes;
	}
}
