/*
 * Copyright 2006 University of Virginia
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
package org.morgan.util.launcher;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class JNILibraryLauncher
{
	private static boolean isLoaded = false;
	private static ClassLoader loader;
	private final static String JNI_IO_PACKAGE = "edu.virginia.vcgr.genii.client.jni.gIIlib.io";
	private final static String JNI_MISCELLANEOUS_PACKAGE = "edu.virginia.vcgr.genii.client.jni.gIIlib.miscellaneous";
	static private Log _logger = LogFactory.getLog(JNILibraryLauncher.class);

	/*
	 * Note:
	 * 
	 * You can change packages to point to edu...jni.giilibmirror.io(.miscellaneous) To get interaction with old library. Especially useful
	 * for testing out the driver without Internet connectivity
	 */

	private static final String BASE_DIR_SYSTEM_PROPERTY = "edu.virginia.vcgr.genii.install-base-dir";

	public static void initialize()
	{
		try {
			if (!isLoaded) {
				String basedir = System.getProperty(BASE_DIR_SYSTEM_PROPERTY);
				JarDescription description = new JarDescription(basedir + "/ifs-jar-desc.xml");
				loader = description.createClassLoader();
				isLoaded = true;
			}
		} catch (Exception e) {
			_logger.info("exception occurred in initialize", e);
		}
	}

	public static boolean changeDirectory(String targetDirectory)
	{
		String myClass = JNI_MISCELLANEOUS_PACKAGE + ".JNICdTool";
		String myMethod = "changeDirectory";
		Class<?>[] argTypes = new Class[] { String.class };
		Object[] args = new Object[] { targetDirectory };

		return (Boolean) invoke(myClass, myMethod, argTypes, args);
	}

	public static String getCurrentDirectory()
	{
		String myClass = JNI_MISCELLANEOUS_PACKAGE + ".JNIPwdTool";
		String myMethod = "getCurrentDirectory";
		Class<?>[] argTypes = null;
		Object[] args = null;

		return (String) invoke(myClass, myMethod, argTypes, args);
	}

	public static boolean login(String keystorePath, String password, String certPath)
	{
		String myClass = JNI_MISCELLANEOUS_PACKAGE + ".JNILoginTool";
		String myMethod = "login";
		Class<?>[] argTypes = new Class[] { String.class, String.class, String.class };
		Object[] args = new Object[] { keystorePath, password, certPath };

		return (Boolean) invoke(myClass, myMethod, argTypes, args);
	}

	public static void logout()
	{
		String myClass = JNI_MISCELLANEOUS_PACKAGE + ".JNILogoutTool";
		String myMethod = "logout";
		Class<?>[] argTypes = null;
		Object[] args = null;

		invoke(myClass, myMethod, argTypes, args);
	}

	/* ************************* IO Functions**************************** */

	@SuppressWarnings("unchecked")
	public static Object[] open(String fileName, int requestedDeposition, int DesiredAccess, boolean isDirectory)
	{
		String myClass = JNI_IO_PACKAGE + ".JNIOpen";
		String myMethod = "open";
		Class<?>[] argTypes = new Class<?>[] { String.class, Integer.class, Integer.class, Boolean.class };
		Object[] args = new Object[] { fileName, new Integer(requestedDeposition), new Integer(DesiredAccess), new Boolean(isDirectory) };

		ArrayList<String> toReturn = (ArrayList<String>) invoke(myClass, myMethod, argTypes, args);
		return ((toReturn != null) ? toReturn.toArray() : null);
	}

	public static byte[] read(int fileHandle, long offset, int length)
	{
		String myClass = JNI_IO_PACKAGE + ".JNIRead";
		String myMethod = "read";
		Class<?>[] argTypes = new Class[] { Integer.class, Long.class, Integer.class };
		Object[] args = new Object[] { new Integer(fileHandle), new Long(Math.abs(offset)), new Integer(Math.abs(length)) };

		return (byte[]) invoke(myClass, myMethod, argTypes, args);
	}

	public static int write(int fileHandle, byte[] data, long offset, int validLength)
	{
		String myClass = JNI_IO_PACKAGE + ".JNIWrite";
		String myMethod = "write";
		Class<?>[] argTypes = new Class[] { Integer.class, byte[].class, Long.class };

		/*
		 * Only not use default if not the same size (i.e. pool buffers may be larger than valid length
		 */

		// Bytes to actually use
		byte[] toUse = data;

		if (data.length != validLength) {
			toUse = Arrays.copyOf(data, validLength); // truncates to valid length
		}

		Object[] args = new Object[] { new Integer(fileHandle), toUse, new Long(offset) };
		return (Integer) invoke(myClass, myMethod, argTypes, args);
	}

	public static int truncateAppend(int fileHandle, byte[] data, long offset, int validLength)
	{
		String myClass = JNI_IO_PACKAGE + ".JNIWrite";
		String myMethod = "truncateAppend";
		Class<?>[] argTypes = new Class[] { Integer.class, byte[].class, Long.class };

		/*
		 * Only not use default if not the same size (i.e. pool buffers may be larger than valid length
		 */

		// Bytes to actually use
		byte[] toUse = data;

		if (data.length != validLength) {
			toUse = Arrays.copyOf(data, validLength); // truncates to valid length
		}

		Object[] args = new Object[] { new Integer(fileHandle), toUse, new Long(offset) };

		return (Integer) invoke(myClass, myMethod, argTypes, args);
	}

	public static boolean close(int fileHandle, boolean deleteOnClose)
	{
		String myClass = JNI_IO_PACKAGE + ".JNIClose";
		String myMethod = "close";
		Class<?>[] argTypes = new Class[] { Integer.class, Boolean.class };
		Object[] args = new Object[] { new Integer(fileHandle), new Boolean(deleteOnClose) };

		return (Boolean) invoke(myClass, myMethod, argTypes, args);
	}

	@SuppressWarnings("unchecked")
	public static Object[] getDirectoryListing(int handle, String target)
	{
		String myClass = JNI_IO_PACKAGE + ".JNIDirectoryListing";
		String myMethod = "getDirectoryListing";
		Class<?>[] argTypes = new Class[] { Integer.class, String.class };
		Object[] args = new Object[] { new Integer(handle), target };

		ArrayList<String> toReturn = (ArrayList<String>) invoke(myClass, myMethod, argTypes, args);
		return ((toReturn != null) ? toReturn.toArray() : null);
	}

	public static boolean rename(int handle, String destination)
	{
		String myClass = JNI_IO_PACKAGE + ".JNIRename";
		String myMethod = "rename";
		Class<?>[] argTypes = new Class[] { Integer.class, String.class };
		Object[] args = new Object[] { handle, destination };

		return (Boolean) invoke(myClass, myMethod, argTypes, args);
	}

	/* ************************* END IO Functions**************************** */

	private static Object invoke(String cls, String mtd, Class<?>[] argTypes, Object[] args)
	{
		if (!isLoaded)
			initialize();

		Thread.currentThread().setContextClassLoader(loader);

		try {
			Class<?> cl = loader.loadClass(cls);
			Method method = ((argTypes != null) ? cl.getMethod(mtd, argTypes) : cl.getMethod(mtd));
			return ((args != null) ? method.invoke(null, args) : method.invoke(null));
		} catch (Throwable t) {
			_logger.info("exception occurred; unable to invoke method", t);
			return null;
		}
	}
}
