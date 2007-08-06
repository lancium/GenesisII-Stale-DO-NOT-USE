package edu.virginia.vcgr.genii.container.resource;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.IOException;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;

import org.apache.axis.AxisFault;
import org.apache.axis.message.MessageElement;
import org.apache.ws.security.message.token.BinarySecurity;
import org.apache.ws.security.message.token.PKIPathSecurity;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.CredentialException;

import org.morgan.util.GUID;
import org.ws.addressing.AttributedURIType;
import org.ws.addressing.EndpointReferenceType;
import org.ws.addressing.MetadataType;
import org.ws.addressing.ReferenceParametersType;

import edu.virginia.vcgr.genii.client.GenesisIIConstants;
import edu.virginia.vcgr.genii.client.naming.EPRUtils;
import edu.virginia.vcgr.genii.client.naming.WSName;
import edu.virginia.vcgr.genii.client.resource.AttributedURITypeSmart;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.common.resource.ResourceUnknownFaultType;
import edu.virginia.vcgr.genii.container.context.WorkingContext;
import edu.virginia.vcgr.genii.common.security.*;
import edu.virginia.vcgr.genii.container.security.authz.handlers.*;
import edu.virginia.vcgr.genii.client.security.*;


public class ResourceManager
{
	static public ResourceKey getTargetResource(EndpointReferenceType epr)
		throws ResourceException, ResourceUnknownFaultType
	{
		try
		{
			WorkingContext ctxt = WorkingContext.getCurrentWorkingContext();
			String serviceName = EPRUtils.extractServiceName(epr);
			ResourceKey rKey = new ResourceKey(serviceName, epr.getReferenceParameters());
			ctxt.setProperty(new GUID().toString(), rKey);
			return rKey;
		}
		catch (AxisFault af)
		{
			throw new ResourceException(af.getLocalizedMessage(), af);
		}
	}
	
	static public ResourceKey getCurrentResource()
		throws ResourceUnknownFaultType, ResourceException
	{
		WorkingContext ctxt = WorkingContext.getCurrentWorkingContext();
		ResourceKey key = (ResourceKey)ctxt.getProperty(WorkingContext.CURRENT_RESOURCE_KEY);
		if (key == null)
		{
			EndpointReferenceType epr = (EndpointReferenceType)ctxt.getProperty(
				WorkingContext.EPR_PROPERTY_NAME);
			if (epr == null)
				throw new ResourceException(
					"Couldn't locate target EPR in current working context.");
			
			String serviceName = (String)ctxt.getProperty(
				WorkingContext.TARGETED_SERVICE_NAME);
			if (serviceName == null)
				throw new ResourceException(
					"Couldn't locate target service name in current working context.");
			
			key = new ResourceKey(serviceName, epr.getReferenceParameters());
			ctxt.setProperty(WorkingContext.CURRENT_RESOURCE_KEY, key);
		}
		
		return key;
	}
	
	static public ResourceKey getServiceResource(String serviceName)
		throws ResourceException, ResourceUnknownFaultType
	{
		WorkingContext ctxt = WorkingContext.getCurrentWorkingContext();
		ResourceKey rKey = new ResourceKey(serviceName, (ReferenceParametersType)null);
		ctxt.setProperty(new GUID().toString(), rKey);
		return rKey;
	}
	
	static public ResourceKey createServiceResource(String serviceName, 
		HashMap<QName, Object> constructionParameters)
		throws ResourceException
	{
		WorkingContext ctxt = WorkingContext.getCurrentWorkingContext();
		
		constructionParameters.put(IResource.IS_SERVICE_CONSTRUCTION_PARAM,
			Boolean.TRUE);
		
		ResourceKey rKey = new ResourceKey(serviceName,
			constructionParameters);
		ctxt.setProperty(new GUID().toString(), rKey);
		return rKey;
	}
	
	static public ResourceKey createNewResource(String serviceName, 
		HashMap<QName, Object> constructionParameters)
		throws ResourceException
	{
		WorkingContext ctxt = WorkingContext.getCurrentWorkingContext();

		ResourceKey rKey = new ResourceKey(serviceName, constructionParameters);
		ctxt.setProperty(new GUID().toString(), rKey);

		return rKey;
	}
	
	static public EndpointReferenceType createEPR(
		ResourceKey resource,
		String targetServiceURL,
		QName []implementedPortTypes) throws ResourceException
	{
		ReferenceParametersType refParams = null;
		AttributedURIType address = new AttributedURITypeSmart(targetServiceURL);
		if (resource != null)
			refParams = resource.getResourceParameters();
		
		return new EndpointReferenceType(address, refParams, 
			createMetadata(implementedPortTypes, resource), null);
	}
	
	static private MetadataType createMetadata(QName []portTypes,
		ResourceKey resourceKey) throws ResourceException
	{
		if (portTypes == null)
			portTypes = new QName[0];
		
		ArrayList<MessageElement> any = new ArrayList<MessageElement>();
		
		// add implemented porttypes
		for (QName portType : portTypes)
		{
			any.add(new MessageElement(
				GenesisIIConstants.IMPLEMENTED_PORT_TYPES_ATTR_QNAME,
				portType.toString()));
		}
		
		if (resourceKey != null) 
		{
			IResource resource = resourceKey.dereference();

			// add epi
			any.add(new MessageElement(
				WSName.ENDPOINT_IDENTIFIER_QNAME,
				resource.getProperty(
					IResource.ENDPOINT_IDENTIFIER_PROPERTY_NAME).toString()));
			
			// add cert chain
			X509Certificate[] certChain = 
				(X509Certificate[])resource.getProperty(
						IResource.CERTIFICATE_CHAIN_PROPERTY_NAME);
			
			try {
			
				if (certChain != null)
				{
			
					MessageElement binaryToken = new MessageElement(BinarySecurity.TOKEN_BST);
					binaryToken.setAttributeNS(null, "ValueType", PKIPathSecurity.getType());
					binaryToken.addTextNode("");
					BinarySecurity bstToken = new PKIPathSecurity(binaryToken);
					((PKIPathSecurity) bstToken).setX509Certificates(certChain, false,
							new edu.virginia.vcgr.genii.client.comm.axis.security.FlexibleBouncyCrypto());

					MessageElement embedded = new MessageElement(
						new QName(org.apache.ws.security.WSConstants.WSSE11_NS, "Embedded"));
					embedded.addChild(binaryToken);

					MessageElement wseTokenRef = new MessageElement(
						new QName(org.apache.ws.security.WSConstants.WSSE11_NS, "SecurityTokenReference"));
					wseTokenRef.addChild(embedded);
					
					MessageElement keyInfo = new MessageElement(
						new QName(GenesisIIConstants.OGSA_BSP_NS, "EndpointKeyInfo"));
					keyInfo.addChild(wseTokenRef);
					
					any.add(keyInfo);
				}
				
				// add minimum level of message level security
				AuthZHandler handler = AuthZHandler.getAuthZHandler(resource);
				if (handler != null) {
					MessageLevelSecurity minMsgSec = handler.getMinIncomingMsgLevelSecurity(resource);

					RequiredMessageSecurityTypeMin minAttr = 
						RequiredMessageSecurityTypeMin.fromString(minMsgSec.toString());
					RequiredMessageSecurityType min = 
						new RequiredMessageSecurityType(
								handler.getClass().getName(),
								minAttr);
					
					MessageElement mel = new MessageElement(
							RequiredMessageSecurityType.getTypeDesc().getXmlType(), min); 
					
					any.add(mel);
				}
			} catch (GenesisIISecurityException e) {
				throw new ResourceException(e.getMessage(), e);
			} catch (WSSecurityException e) {
				throw new ResourceException(e.getMessage(), e);
			} catch (SOAPException e) {
				throw new ResourceException(e.getMessage(), e);
			} catch (IOException e) {
				throw new ResourceException(e.getMessage(), e);
			} catch (CredentialException e) {
				throw new ResourceException(e.getMessage(), e);
			}
				
		}
		
		// Add container key
		
		MessageElement []anyArray = new MessageElement[any.size()];
		any.toArray(anyArray);
		return new MetadataType(anyArray);
	}
}