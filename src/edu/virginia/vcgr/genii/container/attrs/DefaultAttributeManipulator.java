/*
 * Copyright 2006 University of Virginia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package edu.virginia.vcgr.genii.container.attrs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.axis.message.MessageElement;

import org.oasis_open.docs.wsrf.r_2.ResourceUnknownFaultType;
import org.oasis_open.docs.wsrf.rp_2.UnableToModifyResourcePropertyFaultType;
import org.oasis_open.docs.wsrf.rp_2.UpdateResourcePropertiesRequestFailedFaultType;

import edu.virginia.vcgr.genii.client.wsrf.FaultManipulator;

public class DefaultAttributeManipulator implements IAttributeManipulator
{
	static private Log _logger = LogFactory.getLog(DefaultAttributeManipulator.class);

	private Object _target;
	private QName _attrQName;
	private Method _getMethod;
	private Method _setMethod;
	private boolean _isSingleSet;

	private DefaultAttributeManipulator(Object target, QName attrQName, Method getterMethod, Method setterMethod,
		boolean isSingleSet)
	{
		_target = target;
		_attrQName = attrQName;
		_getMethod = getterMethod;
		_setMethod = setterMethod;
		_isSingleSet = isSingleSet;
	}

	public QName getAttributeQName()
	{
		return _attrQName;
	}

	public boolean allowsSet()
	{
		return _setMethod != null;
	}

	@SuppressWarnings("unchecked")
	public Collection<MessageElement> getAttributeValues() throws ResourceUnknownFaultType, RemoteException
	{
		Object ret;

		try {
			ret = _getMethod.invoke(_target, new Object[0]);

			if (ret == null)
				return new ArrayList<MessageElement>();

			if (ret instanceof MessageElement) {
				ArrayList<MessageElement> tmp = new ArrayList<MessageElement>(1);
				tmp.add((MessageElement) ret);
				return tmp;
			} else {
				if (ret instanceof Collection<?>) {
					return (Collection<MessageElement>) ret;
				} else {
					String msg = "failure: unknown type in attribute";
					_logger.error(msg);
					throw new RuntimeException(msg);
				}
			}
		} catch (InvocationTargetException ite) {
			Throwable t = ite.getCause();
			if (t == null)
				t = ite;

			if (t instanceof RemoteException)
				throw (RemoteException) t;

			throw new RemoteException(t.getMessage(), t);
		} catch (IllegalAccessException iae) {
			throw new RemoteException(iae.getMessage(), iae);
		}
	}

	public void setAttributeValues(Collection<MessageElement> values) throws ResourceUnknownFaultType, RemoteException,
		UnableToModifyResourcePropertyFaultType
	{
		Object[] params = null;

		if (_setMethod == null)
			throw FaultManipulator.fillInFault(new UnableToModifyResourcePropertyFaultType());

		if (_isSingleSet) {
			if (values.size() == 0)
				params = new Object[] { null };
			else if (values.size() == 1)
				params = new Object[] { values.iterator().next() };
			else
				throw FaultManipulator.fillInFault(new UpdateResourcePropertiesRequestFailedFaultType());
		} else {
			params = new Object[] { values };
		}

		try {
			_setMethod.invoke(_target, params);
		} catch (InvocationTargetException ite) {
			Throwable t = ite.getCause();
			if (t == null)
				t = ite;

			if (t instanceof RemoteException)
				throw (RemoteException) t;

			throw new RemoteException(t.getMessage(), t);
		} catch (IllegalAccessException iae) {
			throw new RemoteException(iae.getMessage(), iae);
		}
	}

	static public IAttributeManipulator createManipulator(Object target, QName attributeName, String getMethodName)
		throws NoSuchMethodException
	{
		return createManipulator(target, attributeName, getMethodName, null);
	}

	@SuppressWarnings("unchecked")
	static public IAttributeManipulator createManipulator(Object target, QName attributeName, String getMethodName,
		String setMethodName) throws NoSuchMethodException
	{
		Class<? extends Object> clazz;

		if (target == null)
			throw new IllegalArgumentException("Parameter \"target\" cannot be null.");

		if (target instanceof Class) {
			clazz = (Class<? extends Object>) target;
			target = null;
		} else {
			clazz = target.getClass();
		}
		if (_logger.isTraceEnabled())
			_logger.trace("target class found: " + clazz.getCanonicalName());

		if (attributeName == null)
			throw new IllegalArgumentException("Parameter \"attributeName\" cannot be null.");
		if (getMethodName == null)
			throw new IllegalArgumentException("Parameter \"getMethodName\" cannot be null.");

		Method getter = clazz.getMethod(getMethodName, new Class[0]);
		Class<? extends Object> returnType = getter.getReturnType();
		if (_logger.isTraceEnabled())
			_logger.trace("return type decided on: " + returnType.getCanonicalName());

		if (!((MessageElement.class.isAssignableFrom(returnType)) || (Collection.class.isAssignableFrom(returnType))))
			throw new IllegalArgumentException("Method \"" + getMethodName + "\" does not appear to return the correct type.");

		Method setter;
		boolean isSingleSet = true;
		if (setMethodName == null)
			setter = null;
		else {
			try {
				setter = clazz.getMethod(setMethodName, new Class[] { Collection.class });
				isSingleSet = false;
			} catch (NoSuchMethodException nsme) {
				setter = clazz.getMethod(setMethodName, new Class[] { MessageElement.class });
			}
		}

		return new DefaultAttributeManipulator(target, attributeName, getter, setter, isSingleSet);
	}

	@Override
	final public String toString()
	{
		return String.format("[%s] %s/%s", _attrQName, _getMethod, _setMethod);
	}
}
