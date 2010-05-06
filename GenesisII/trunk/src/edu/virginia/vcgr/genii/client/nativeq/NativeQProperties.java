package edu.virginia.vcgr.genii.client.nativeq;

import java.io.File;
import java.util.Properties;

import org.ggf.jsdl.OperatingSystemTypeEnumeration;
import org.ggf.jsdl.ProcessorArchitectureEnumeration;

import edu.virginia.vcgr.genii.client.bes.GeniiBESConstants;
import edu.virginia.vcgr.genii.container.bes.BESUtilities;

public class NativeQProperties
{
	private Properties _properties;
	
	public NativeQProperties(Properties properties)
	{
		_properties = properties;
		if (_properties == null)
			_properties = new Properties();
	}
	
	public OperatingSystemTypeEnumeration operatingSystemName()
	{
		String value = _properties.getProperty(
			NativeQConstants.OPERATING_SYSTEM_NAME_PROPERTY);
		if (value != null)
			return OperatingSystemTypeEnumeration.fromString(value);
		return null;
	}
	
	public String operatingSystemVersion()
	{
		return _properties.getProperty(
			NativeQConstants.OPERATING_SYSTEM_VERSION_PROPERTY);
	}
	
	public ProcessorArchitectureEnumeration cpuArchitecture()
	{
		String value = _properties.getProperty(
			NativeQConstants.CPU_ARCHITECTURE_NAME_PROPERTY);
		if (value != null)
			return ProcessorArchitectureEnumeration.fromString(value);
		return null;
	}
	
	public Integer cpuCount()
	{
		String value = _properties.getProperty(
			NativeQConstants.CPU_COUNT_PROPERTY);
		if (value != null)
			return Integer.valueOf(value);
		
		return null;
	}
	
	public Long cpuSpeed()
	{
		String value = _properties.getProperty(
			NativeQConstants.CPU_SPEED_PROPERTY);
		if (value != null)
			return Long.valueOf(value);
		
		return null;
	}
	
	public Long physicalMemory()
	{
		String value = _properties.getProperty(
			NativeQConstants.PHYSICAL_MEMORY_PROPERTY);
		if (value != null)
			return Long.valueOf(value);
		
		return null;
	}
	
	public Long virtualMemory()
	{
		String value = _properties.getProperty(
			NativeQConstants.VIRTUAL_MEMORY_PROPERTY);
		if (value != null)
			return Long.valueOf(value);
		
		return null;
	}
	
	public File commonDirectory()
	{
		File basedir = null;
		
		String dir = _properties.getProperty(
			GeniiBESConstants.SHARED_DIRECTORY_PROPERTY);
		if (dir != null)
			basedir = new File(dir);
			
		File configDir = null;
		if (basedir == null)
		{
			configDir = BESUtilities.getBESWorkerDir();
		} else
			configDir = basedir;
		
		return configDir;
	}
}