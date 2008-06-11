package edu.virginia.vcgr.genii.container.bes.jsdl.personality.simpleexec;

import java.net.URI;

import org.ggf.jsdl.CreationFlagEnumeration;

import edu.virginia.vcgr.genii.client.security.gamlauthz.identity.UsernamePasswordIdentity;

public class DataStagingUnderstanding
{
	private CreationFlagEnumeration _creationFlag = null;
	private boolean _deleteOnTerminate = true;
	private String _filename = null;
	private URI _sourceURI = null;
	private URI _targetURI = null;
	private UsernamePasswordIdentity _credential;
	
	public CreationFlagEnumeration getCreationFlag()
	{
		return _creationFlag;
	}
	
	public void setCreationFlag(CreationFlagEnumeration flag)
	{
		_creationFlag = flag;
	}
	
	public boolean isDeleteOnTerminate()
	{
		return _deleteOnTerminate;
	}
	
	public void setDeleteOnTerminate(boolean onTerminate)
	{
		_deleteOnTerminate = onTerminate;
	}
	
	public String getFilename()
	{
		return _filename;
	}
	
	public void setFilename(String _filename)
	{
		this._filename = _filename;
	}
	
	public URI getSourceURI()
	{
		return _sourceURI;
	}
	
	public void setSourceURI(URI _sourceuri)
	{
		_sourceURI = _sourceuri;
	}
	
	public URI getTargetURI()
	{
		return _targetURI;
	}
	
	public void setTargetURI(URI _targeturi)
	{
		_targetURI = _targeturi;
	}
	
	public void setCredential(UsernamePasswordIdentity cred)
	{
		_credential = cred;
	}
	
	public UsernamePasswordIdentity getCredential()
	{
		return _credential;
	}
}