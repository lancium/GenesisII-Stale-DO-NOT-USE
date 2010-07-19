package edu.virginia.vcgr.genii.client.filesystems;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class FilesystemImpl implements Filesystem
{
	private FilesystemManager _manager;
	private String _filesystemName;
	private File _filesystemRoot;
	private Map<String, FilesystemSandbox> _sandboxes =
		new HashMap<String, FilesystemSandbox>();
	private Set<FilesystemProperties> _properties =
		EnumSet.noneOf(FilesystemProperties.class);
	
	FilesystemImpl(FilesystemManager manager, String filesystemName,
		FilesystemConfiguration conf) throws FileNotFoundException
	{
		_manager = manager;
		_filesystemName = filesystemName;
		
		File root = new File(conf.path());
		if (!root.exists())
			root.mkdirs();

		if (!root.exists())
			throw new FileNotFoundException(String.format(
				"Unable to find directory \"%s\".", root));
		else if (!root.isDirectory())
			throw new FileNotFoundException(String.format(
				"Unable to find directory \"%s\".", root));
		
		_filesystemRoot = root;
		
		for (FilesystemSandboxConfiguration boxConf : conf.sandboxes())
		{
			FilesystemSandbox sandbox = new FilesystemSandboxImpl(
				new File(root, boxConf.relativePath()));
			_sandboxes.put(boxConf.name(), sandbox);
		}
		
		for (FilesystemProperties property : conf.properties())
			_properties.add(property);
	}
	
	@Override
	final public File filesystemRoot()
	{
		return _filesystemRoot;
	}
	
	@Override
	public FilesystemSandbox openSandbox(String sandboxName)
		throws FileNotFoundException
	{
		FilesystemSandbox ret = _sandboxes.get(sandboxName);
		if (ret == null)
			throw new FileNotFoundException(String.format(
				"Sandbox \"%s\" does not exist.", sandboxName));
		
		return ret;
	}
	
	@Override
	public FilesystemWatchRegistration addWatch(Integer callLimit, 
		long checkPeriod, TimeUnit checkPeriodUnits, 
		FilesystemWatchFilter filter, FilesystemWatchHandler handler)
	{
		return _manager.addWatch(_filesystemName, this,
			callLimit, checkPeriod, checkPeriodUnits,
			filter, handler);
	}
	
	@Override
	public Set<FilesystemProperties> properties()
	{
		return Collections.unmodifiableSet(_properties);
	}
}