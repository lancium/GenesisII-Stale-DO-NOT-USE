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
package org.morgan.util.launcher;

import java.lang.reflect.Method;
import java.util.ArrayList;

public class JNILibraryLauncher
{
	private static boolean isLoaded = false;
	private static ClassLoader loader;
	private final static String JNI_PACKAGE = 
		"edu.virginia.vcgr.genii.client.jni.gIIlib";
	private final static String JNI_IO_PACKAGE = 
		"edu.virginia.vcgr.genii.client.jni.gIIlib.io";
	private static final String BASE_DIR_SYSTEM_PROPERTY = 
		"edu.virginia.vcgr.genii.client.configuration.base-dir";
	
	public static void initialize()
	{
		try{
			if(!isLoaded)
			{			
				String basedir = System.getProperty(BASE_DIR_SYSTEM_PROPERTY);
				JarDescription description = new JarDescription(basedir + "/jar-desc.xml");			
				loader = description.createClassLoader();				
				isLoaded = true;
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}	
	
	public static Object[] getDirectoryListing(){		
		String myClass = JNI_PACKAGE + ".JNILsTool";
		String myMethod = "getDirectoryListing";
		Class[] argTypes = null;
		Object[] args = null;
				
		ArrayList toReturn = (ArrayList)invoke(myClass, myMethod, argTypes, args);
		return ((toReturn != null) ? toReturn.toArray() : null);
	}
	
	public static boolean changeDirectory(String targetDirectory){				
		String myClass = JNI_PACKAGE + ".JNICdTool";
		String myMethod = "changeDirectory";
		Class[] argTypes = new Class[] {String.class };
		Object[] args = new Object[] { targetDirectory};
		
		return (Boolean)invoke(myClass, myMethod, argTypes, args);				
	}
	
	public static String getCurrentDirectory(){
		String myClass = JNI_PACKAGE + ".JNIPwdTool";
		String myMethod = "getCurrentDirectory";
		Class[] argTypes = null;
		Object[] args = null;
		
		return (String)invoke(myClass, myMethod, argTypes, args);				
	}
	
	public static boolean makeDirectory(String newDirectory){
		String myClass = JNI_PACKAGE + ".JNIMkDirTool";
		String myMethod = "makeDirectory";
		Class[] argTypes = new Class[] {String.class };
		Object[] args = new Object[] { newDirectory};
		
		return (Boolean)invoke(myClass, myMethod, argTypes, args);					
	}
	
	public static boolean login(String keystorePath, String password){
		String myClass = JNI_PACKAGE + ".JNILoginTool";
		String myMethod = "login";
		Class[] argTypes = new Class[] {String.class, String.class };
		Object[] args = new Object[] { keystorePath , password };
		
		return (Boolean)invoke(myClass, myMethod, argTypes, args);			
	}
	
	public static void logout(){
		String myClass = JNI_PACKAGE + ".JNILogoutTool";
		String myMethod = "logout";
		Class[] argTypes = null;
		Object[] args = null;
		
		invoke(myClass, myMethod, argTypes, args);				
	}
	
	public static boolean remove(String target, boolean recursive, boolean force){
		String myClass = JNI_PACKAGE + ".JNIRmTool";
		String myMethod = "remove";
		Class[] argTypes = new Class[] {String.class, 
				Boolean.class, Boolean.class};
		Object[] args = new Object[] {target, 
				new Boolean(recursive), new Boolean(force)};
		
		return (Boolean)invoke(myClass, myMethod, argTypes, args);				
	}
	
	public static boolean copy(String source, String destination,boolean srcLocal, boolean dstLocal){
		String myClass = JNI_PACKAGE + ".JNICpTool";
		String myMethod = "copy";
		Class[] argTypes = new Class[] {String.class, String.class, 
					Boolean.class, Boolean.class};
		Object[] args = new Object[] {source, destination, 
				new Boolean(srcLocal), new Boolean(dstLocal)};
		
		return (Boolean)invoke(myClass, myMethod, argTypes, args);				
	}
	
	public static boolean move(String source, String destination){
		String myClass = JNI_PACKAGE + ".JNIMvTool";
		String myMethod = "move";
		Class[] argTypes = new Class[] {String.class, String.class};
		Object[] args = new Object[] {source, destination};
		
		return (Boolean)invoke(myClass, myMethod, argTypes, args);				
	}
	
	/* ************************* IO Functions**************************** */
	
	public static int open(String fileName, boolean create, boolean read, 
			boolean write){
		String myClass = JNI_IO_PACKAGE + ".JNIOpen";
		String myMethod = "open";
		Class[] argTypes = new Class[] {String.class, Boolean.class, 
				Boolean.class, Boolean.class};
		Object[] args = new Object[] {fileName, new Boolean(create), 
				new Boolean(read), new Boolean(write)};
		
		return (Integer)invoke(myClass, myMethod, argTypes, args);				
	}
	
	
	public static String read(int fileHandle, int offset, int length){
		String myClass = JNI_IO_PACKAGE + ".JNIRead";
		String myMethod = "read";
		Class[] argTypes = new Class[] {Integer.class, Integer.class, Integer.class};
		Object[] args = new Object[] {new Integer(fileHandle), 
				new Integer(offset), new Integer(length)};
		
		return (String)invoke(myClass, myMethod, argTypes, args);				
	}
	
	public static int write(int fileHandle, String data, int offset){
		String myClass = JNI_IO_PACKAGE + ".JNIWrite";
		String myMethod = "write";
		Class[] argTypes = new Class[] {Integer.class, String.class, Integer.class};
		Object[] args = new Object[] {new Integer(fileHandle), 
				new String(data), new Integer(offset)};
		
		return (Integer)invoke(myClass, myMethod, argTypes, args);				
	}
	
	public static boolean close(int fileHandle){
		String myClass = JNI_IO_PACKAGE + ".JNIClose";
		String myMethod = "close";
		Class[] argTypes = new Class[] {Integer.class};
		Object[] args = new Object[] {new Integer(fileHandle)};
		
		return (Boolean)invoke(myClass, myMethod, argTypes, args);				
	}
	
	private static Object invoke(String cls, String mtd, Class[] argTypes, Object[] args){
		if(!isLoaded){
			initialize();
		}		
		Thread.currentThread().setContextClassLoader(loader);			
		
		try
		{				
			Class<?> cl = loader.loadClass(cls);		
			Method method = ((argTypes != null)? cl.getMethod(mtd, argTypes) :
				cl.getMethod(mtd));	
			return ((args != null) ? method.invoke(null, args) :
				method.invoke(null));			
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}			
	}
}
