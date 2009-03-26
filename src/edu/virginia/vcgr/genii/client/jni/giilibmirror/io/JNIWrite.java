package edu.virginia.vcgr.genii.client.jni.giilibmirror.io;

import edu.virginia.vcgr.genii.client.jni.giilibmirror.cache.CacheManager;
import edu.virginia.vcgr.genii.client.jni.giilibmirror.io.handles.WindowsDirHandle;
import edu.virginia.vcgr.genii.client.jni.giilibmirror.io.handles.WindowsFileHandle;
import edu.virginia.vcgr.genii.client.jni.giilibmirror.io.handles.WindowsResourceHandle;


public class JNIWrite{
	public static Integer write(Integer fileHandle, byte[] data, Long offset){
		CacheManager manager = CacheManager.getInstance();		
		WindowsResourceHandle resourceHandle = manager.getHandle(fileHandle);							
		
		if(resourceHandle == null || resourceHandle instanceof WindowsDirHandle){
			System.out.println("G-ICING:  Invalid handle received for file write");			
			return null;
		}		
		
		WindowsFileHandle fh = (WindowsFileHandle) resourceHandle;		
		return fh.write(data, offset);						
	}
	
	public static Integer truncateAppend(Integer fileHandle, byte[] data, Long offset){
		CacheManager manager = CacheManager.getInstance();		
		WindowsResourceHandle resourceHandle = manager.getHandle(fileHandle);							
		
		if(resourceHandle == null || resourceHandle instanceof WindowsDirHandle){
			System.out.println("G-ICING:  Invalid handle received for file truncateAppend");			
			return null;
		}		
		
		WindowsFileHandle fh = (WindowsFileHandle) resourceHandle;
		return fh.truncateAppend(data, offset);			
	}
}