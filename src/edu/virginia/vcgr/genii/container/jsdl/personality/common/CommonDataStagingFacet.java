package edu.virginia.vcgr.genii.container.jsdl.personality.common;

import javax.xml.namespace.QName;

import org.ggf.jsdl.CreationFlagEnumeration;

import edu.virginia.vcgr.genii.client.jsdl.JSDLConstants;
import edu.virginia.vcgr.genii.client.jsdl.JSDLException;
import edu.virginia.vcgr.genii.client.jsdl.UnsupportedJSDLElement;
import edu.virginia.vcgr.genii.client.jsdl.personality.common.DataStagingUnderstanding;
import edu.virginia.vcgr.genii.client.jsdl.personality.def.DefaultDataStagingFacet;
import edu.virginia.vcgr.genii.security.credentials.identity.UsernamePasswordIdentity;

public class CommonDataStagingFacet extends DefaultDataStagingFacet
{
	@Override
	public Object createFacetUnderstanding(Object parentUnderstanding) throws JSDLException
	{
		return new DataStagingUnderstanding();
	}

	@Override
	public void consumeCreationFlag(Object currentUnderstanding, CreationFlagEnumeration creationFlag) throws JSDLException
	{
		if (creationFlag.equals(CreationFlagEnumeration.append))
			throw new UnsupportedJSDLElement("Creation flag \"append\" is not supported.", new QName(JSDLConstants.JSDL_NS, "CreationFlag"));

		((DataStagingUnderstanding) currentUnderstanding).setCreationFlag(creationFlag);
	}

	@Override
	public void consumeDeleteOnTerminateFlag(Object currentUnderstanding, boolean deleteOnTerminate) throws JSDLException
	{
		((DataStagingUnderstanding) currentUnderstanding).setDeleteOnTerminate(deleteOnTerminate);
	}

	@Override
	public void consumeHandleAsArchiveFlag(Object currentUnderstanding, boolean handleAsArchive) throws JSDLException
	{
		((DataStagingUnderstanding) currentUnderstanding).setHandleAsArchive(handleAsArchive);
	}

	@Override
	public void consumeFileSystemName(Object currentUnderstanding, String fileSystemName) throws JSDLException
	{
		((DataStagingUnderstanding) currentUnderstanding).setFileSystemName(fileSystemName);
	}

	@Override
	public void consumeFileName(Object currentUnderstanding, String fileName) throws JSDLException
	{
		((DataStagingUnderstanding) currentUnderstanding).setFilename(fileName);
	}

	@Override
	public void consumeUsernamePassword(Object currentUnderstanding, UsernamePasswordIdentity upi) throws JSDLException
	{
		((DataStagingUnderstanding) currentUnderstanding).setUsernamePassword(upi);
	}

	@Override
	public void completeFacet(Object parentUnderstanding, Object currentUnderstanding) throws JSDLException
	{
		CommonExecutionUnderstanding parent = (CommonExecutionUnderstanding) parentUnderstanding;
		DataStagingUnderstanding child = (DataStagingUnderstanding) currentUnderstanding;

		parent.addDataStaging(child);
	}
}