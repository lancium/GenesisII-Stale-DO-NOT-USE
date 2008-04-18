package edu.virginia.vcgr.genii.client.gui.exportdir;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;

import org.morgan.util.configuration.ConfigurationException;

import edu.virginia.vcgr.genii.client.cmd.tools.ExportTool;
import edu.virginia.vcgr.genii.client.naming.EPRUtils;
import edu.virginia.vcgr.genii.client.rcreate.CreationException;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.common.rfactory.ResourceCreationFaultType;

public class ExportManipulator
{
	static public RNSPath createExport(
		URL containerURL, File localPath, String rnsPath)
			throws FileNotFoundException, ExportException,
				RNSException, ConfigurationException, CreationException,
				ResourceCreationFaultType, RemoteException, IOException
	{
		validate(localPath);
			
		RNSPath target = RNSPath.getCurrent().lookup(rnsPath, RNSPathQueryFlags.MUST_NOT_EXIST);
		validate(target);
		
		ExportTool.createExportedRoot(EPRUtils.makeEPR(containerURL.toString() 
			+ "/axis/services/ExportedRootPortType"),
			localPath.getAbsolutePath(), rnsPath, false);
		return RNSPath.getCurrent().lookup(rnsPath, RNSPathQueryFlags.MUST_EXIST);
	}
	
	static public void validate(File localPath)
		throws FileNotFoundException, ExportException
	{
		if (!localPath.exists())
			throw new FileNotFoundException("Couldn't find source directory \"" + 
				localPath.getAbsolutePath() + "\".");
		if (!localPath.isDirectory())
			throw new ExportException("Cannot export \"" + localPath.getAbsolutePath() 
				+ "\" because it is not a directory.");
	}
	
	static public void validate(RNSPath rnsPath)
		throws ExportException
	{
		RNSPath parent = rnsPath.getParent();
		if (!parent.exists())
			throw new ExportException("Cannot create export because target RNS path \"" +
				parent.pwd() + "\" does not exist.");
		if (!parent.isDirectory())
			throw new ExportException("RNS path \"" + parent.pwd() + "\" is not an RNS capable endpoint.");
	}
	
	static public void quitExport(
		RNSPath exportRoot) throws RNSException, IOException, ConfigurationException
	{
		ExportTool.quitExportedRoot(exportRoot.getEndpoint(), false);
		exportRoot.unlink();
	}
}