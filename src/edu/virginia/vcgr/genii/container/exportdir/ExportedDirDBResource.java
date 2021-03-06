package edu.virginia.vcgr.genii.container.exportdir;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.axis.message.MessageElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ggf.rns.RNSEntryExistsFaultType;
import org.morgan.util.GUID;
import org.morgan.util.io.StreamUtils;
import org.oasis_open.docs.wsrf.r_2.ResourceUnknownFaultType;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.common.GenesisHashMap;
import edu.virginia.vcgr.genii.client.context.WorkingContext;
import edu.virginia.vcgr.genii.client.exportdir.ExportedDirUtils;
import edu.virginia.vcgr.genii.client.exportdir.ExportedFileUtils;
import edu.virginia.vcgr.genii.client.naming.EPRUtils;
import edu.virginia.vcgr.genii.client.resource.IResource;
import edu.virginia.vcgr.genii.client.resource.MessageElementUtils;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.security.PreferredIdentity;
import edu.virginia.vcgr.genii.client.wsrf.FaultManipulator;
import edu.virginia.vcgr.genii.container.Container;
import edu.virginia.vcgr.genii.container.attrs.AttributePreFetcher;
import edu.virginia.vcgr.genii.container.byteio.DefaultRandomByteIOAttributePreFetcher;
import edu.virginia.vcgr.genii.container.common.DefaultGenesisIIAttributesPreFetcher;
import edu.virginia.vcgr.genii.container.db.ServerDatabaseConnectionPool;
import edu.virginia.vcgr.genii.container.replicatedExport.resolver.RExportResolverUtils;
import edu.virginia.vcgr.genii.container.resource.ResourceKey;
import edu.virginia.vcgr.genii.container.resource.ResourceManager;
import edu.virginia.vcgr.genii.container.resource.db.BasicDBResource;

public class ExportedDirDBResource extends BasicDBResource implements IExportedDirResource
{
	static private Log _logger = LogFactory.getLog(ExportedDirDBResource.class);

	static private final String _RETRIEVE_DIR_INFO = "SELECT path, parentIds, isReplicated, lastModified FROM exporteddir WHERE dirid = ?";
	static private final String _CREATE_DIR_INFO = "INSERT INTO exporteddir VALUES(?, ?, ?, ?, ?)";
	static private final String _UPDATE_MODIFY_TIME = "UPDATE exporteddir SET lastModified = ? WHERE dirid = ?";
	static private final String _ADD_ENTRY_STMT = "INSERT INTO exporteddirentry VALUES(?, ?, ?, ?, ?)";
	static private final String _ADD_DIR_ATTR_STMT = "INSERT INTO exporteddirattr VALUES(?, ?)";
	static private final String _RETRIEVE_EXPORTED_ENTRIES_STMT =
		"SELECT dirid, name, endpoint, entryid, type " + "FROM exporteddirentry WHERE dirid = ?";
	static private final String _RETRIEVE_EXPORTED_ENTRY_ATTRS_FOR_DIR_STMT = "SELECT attrtab.entryid, attrtab.attr "
		+ "FROM exportedentryattr attrtab, exporteddirentry entrytab " + "WHERE attrtab.entryid = entrytab.entryid AND entrytab.dirid = ?";
	static private final String _DELETE_EXPORTED_ENTRY_STMT = "DELETE FROM exporteddirentry WHERE entryid = ?";
	static private final String _DELETE_EXPORTED_ENTRY_ATTRS_STMT = "DELETE FROM exportedentryattr WHERE entryid = ?";
	static private final String _DELETE_EXPORTED_DIR_STMT = "DELETE FROM exporteddir WHERE dirid = ?";
	static private final String _DELETE_EXPORTED_DIR_ENTRY_ATTR =
		"DELETE FROM exportedentryattr WHERE entryid in " + "(SELECT entryid FROM exporteddirentry WHERE dirid = ?)";
	static private final String _DELETE_EXPORTED_DIR_ENTRIES_STMT = "DELETE FROM exporteddirentry WHERE dirid = ?";
	static private final String _DESTROY_ALL_ATTRS_FOR_PARENT_STMT = "DELETE FROM exportedentryattr "
		+ "WHERE entryid in (SELECT entryid FROM exporteddir WHERE dirid in " + "(SELECT dirid FROM exporteddir WHERE parentIds LIKE ?))";
	static private final String _DESTROY_ALL_ENTRIES_FOR_PARENT_STMT =
		"DELETE FROM exporteddirentry " + "WHERE dirid in " + "(SELECT dirid FROM exporteddir WHERE parentIds LIKE ?)";
	static private final String _DESTROY_ALL_DIRS_FOR_PARENT_STMT = "DELETE FROM exporteddir WHERE parentIds LIKE ?";
	static private final String _RETRIEVE_ALL_DIR_IDS_FOR_PARENT_STMT = "SELECT dirid FROM exporteddir WHERE parentIds LIKE ?";

	static private final String _RETRIEVE_ALL_EPRS_FOR_PARENT_STMT =
		"SELECT endpoint " + "FROM exporteddirentry " + "WHERE dirid in " + "(SELECT dirid FROM exporteddir WHERE parentIds LIKE ?)";

	static final private String _CREATE_TIME_PROP_NAME = "create-time";
	static final private String _MOD_TIME_PROP_NAME = "mod-time";
	static final private String _ACCESS_TIME_PROP_NAME = "access-time";

	private String _myLocalPath = null;
	private String _myParentIds = null;
	private String _isReplicated = null;
	private Long _lastModified = null;

	protected static EndpointReferenceType _fileServiceEPR;
	protected static EndpointReferenceType _dirServiceEPR;

	public ExportedDirDBResource(ResourceKey parentKey, ServerDatabaseConnectionPool connectionPool) throws SQLException
	{
		super(parentKey, connectionPool);

	}

	@Override
	public void initialize(GenesisHashMap constructionParams) throws ResourceException
	{
		_myParentIds = (String) constructionParams.get(IExportedFileResource.PARENT_IDS_CONSTRUCTION_PARAM);
		_myLocalPath = (String) constructionParams.get(IExportedFileResource.PATH_CONSTRUCTION_PARAM);
		_isReplicated = (String) constructionParams.get(IExportedFileResource.REPLICATION_INDICATOR);
		_lastModified = (Long) constructionParams.get(IExportedDirResource.LAST_MODIFIED_TIME);
		if (_logger.isDebugEnabled())
			_logger.debug("Initializing exportDir " + _myLocalPath + " with modifed time: " + _lastModified);

		super.initialize(constructionParams);

		Boolean isService = (Boolean) constructionParams.get(IResource.IS_SERVICE_CONSTRUCTION_PARAM);
		if (isService == null || !isService.booleanValue())
			insertDirInfo();
	}

	@Override
	public void load(String resourceKey) throws ResourceUnknownFaultType, ResourceException
	{
		super.load(resourceKey);

		if (isServiceResource())
			return;

		loadDirInfo();
		if (!dirExists()) {
			_logger.error("Local dir does not exist for ExportedDirResource.");

			destroy(_connection, false);
			throw FaultManipulator.fillInFault(new ResourceUnknownFaultType());
		}
	}

	public void addEntry(ExportedDirEntry entry, boolean createOnDisk) throws ResourceException, RNSEntryExistsFaultType
	{
		/* if createFile is true, create underlying file */
		if (createOnDisk)
			createOnDisk(entry.getName(), entry.getType());

		addEntry(entry.getName(), entry.getEntryReference(), entry.getId(), entry.getType());
		addAttributes(entry.getName(), entry.getAttributes(), entry.getId());
	}

	public Collection<String> listEntries() throws ResourceException
	{
		/*
		 * Sanity checks - make sure we have a local directory, that it exists, and is a directory
		 */
		String dirPath = getLocalPath();
		if (dirPath == null)
			throw new ResourceException("ExportedDir has no local path.  Cannot list entries.");
		File dir = new File(dirPath);
		if (!dir.exists())
			throw new ResourceException("Local path for ExportedDir does not exist.");
		if (!dir.isDirectory())
			throw new ResourceException("Local path for exported dir is not a directory.");

		/* Get listting */
		String[] localEntries = dir.list();
		ArrayList<String> ret = new ArrayList<String>();

		for (String nextEntry : localEntries)
			ret.add(nextEntry);

		return ret;
	}

	private boolean dirNotModified() throws ResourceException
	{
		Long latestModifyTime = ExportedDirUtils.getLastModifiedTime(_myLocalPath);
		if (_lastModified.equals(latestModifyTime)) {
			if (_logger.isDebugEnabled())
				_logger.debug("ExportDir's last modify time has not changed; proceeding without sync.");
			return true;
		} else {
			// set new modify time
			_lastModified = latestModifyTime;
			updateModifyTime();
			if (_logger.isDebugEnabled())
				_logger.debug("ExportDir's last modify time has changed; initiating sync.");
			return false;
		}
	}

	public void getAndSetModifyTime() throws ResourceException
	{
		Long latestModifyTime = ExportedDirUtils.getLastModifiedTime(_myLocalPath);
		_lastModified = latestModifyTime;
		updateModifyTime();
	}

	private void updateModifyTime() throws ResourceException
	{
		if (_logger.isDebugEnabled())
			_logger.debug("Updating ExportDir modify time with: " + _lastModified);
		PreparedStatement stmt = null;

		try {
			stmt = _connection.prepareStatement(_UPDATE_MODIFY_TIME);
			stmt.setLong(1, _lastModified);
			stmt.setString(2, _resourceKey);
			stmt.executeUpdate();
		} catch (SQLException sqe) {
			throw new ResourceException("Could not update modify time for exportDir resource entry", sqe);
		} finally {
			StreamUtils.close(stmt);
		}
	}

	public Collection<ExportedDirEntry> retrieveEntries(String entryName) throws ResourceException
	{
		// get all known entries from db
		Collection<ExportedDirEntry> allKnownEntries = retrieveKnownEntries();
		Collection<ExportedDirEntry> syncedEntries = null;

		// if dir not modified and not empty, known entries are synced
		if (dirNotModified() && (!allKnownEntries.isEmpty()))
			syncedEntries = allKnownEntries;
		// else force sync for modified or empty dirs
		else {
			// get all entries on local file system
			Collection<File> allLocalEntries = listEntriesAsFiles();

			// sync entries
			syncedEntries = syncEntries(allKnownEntries, allLocalEntries);
		}

		Collection<ExportedDirEntry> ret = new ArrayList<ExportedDirEntry>();
		for (ExportedDirEntry nextEntry : syncedEntries) {
			if (entryName == null || entryName.equals(nextEntry.getName())) {
				// We are going to pre-fill in the attributes document for this entry
				// so that we can send it back for pre-fetching.
				fillInAttributes(nextEntry);
				ret.add(nextEntry);
			}
		}

		return ret;
	}

	public Collection<String> removeEntries(String entryName, boolean hardDestroy) throws ResourceException
	{
		ArrayList<String> ret = new ArrayList<String>();

		Collection<ExportedDirEntry> entries = retrieveEntries(entryName);
		for (ExportedDirEntry nextEntry : entries) {
			try {
				removeEntry(nextEntry, hardDestroy);
				ret.add(nextEntry.getName());
			} catch (ResourceUnknownFaultType ruft) {
				if (_logger.isDebugEnabled())
					_logger.debug(ruft);
			}
		}

		return ret;
	}

	public void destroy() throws ResourceException
	{
		try {
			destroy(_connection, true);
		} catch (ResourceUnknownFaultType e) {
			throw new ResourceException(e.getMessage(), e);
		}
	}

	public void destroy(boolean hardDestroy) throws ResourceException, ResourceUnknownFaultType
	{
		destroy(_connection, hardDestroy);
	}

	public void destroy(Connection connection, boolean hardDestroy) throws ResourceException, ResourceUnknownFaultType
	{
		dirDestroyAllForParentDir(connection, getId(), false, getReplicationState());
		ExportedFileDBResource.fileDestroyAllForParentDir(connection, getId(), false, getReplicationState());

		/* Delete information related directly to parent exported dir */
		PreparedStatement stmt = null;
		try {
			stmt = connection.prepareStatement(_DELETE_EXPORTED_DIR_ENTRY_ATTR);
			stmt.setString(1, getId());
			stmt.executeUpdate();

			StreamUtils.close(stmt);
			stmt = null;

			stmt = connection.prepareStatement(_DELETE_EXPORTED_DIR_ENTRIES_STMT);
			stmt.setString(1, getId());
			stmt.executeUpdate();

			StreamUtils.close(stmt);
			stmt = null;

			stmt = connection.prepareStatement(_DELETE_EXPORTED_DIR_STMT);
			stmt.setString(1, getId());
			stmt.executeUpdate();

			super.destroy();

			/* Delete underlying file system resources if necessary */
			if (hardDestroy)
				destroyDirectory(getLocalPath());
		} catch (SQLException sqe) {
			throw new ResourceException(sqe.getLocalizedMessage(), sqe);
		} finally {
			StreamUtils.close(stmt);
		}

		// if replicated, delete resolver mapping and notify resolver of termination
		if (getReplicationState().equals("true")) {
			try {
				if (_logger.isDebugEnabled())
					_logger.debug("Notifying resolver of exportedDir termination.");
				RExportResolverUtils.destroyResolverByEPI(getResourceEPIasString(), null);
			} catch (Exception e) {
				_logger.error("No resolver for exportedDir could be found to destory.");
			}
		}

	}

	public String getId() throws ResourceException
	{
		return _resourceKey;
	}

	public String getLocalPath() throws ResourceException
	{
		return _myLocalPath;
	}

	public String getParentIds() throws ResourceException
	{
		return _myParentIds;
	}

	public String getReplicationState() throws ResourceException
	{
		return _isReplicated;
	}

	public void setAccessTime(Calendar c) throws ResourceException
	{
		setProperty(_ACCESS_TIME_PROP_NAME, c);
	}

	public void setCreateTime(Calendar c) throws ResourceException
	{
		setProperty(_CREATE_TIME_PROP_NAME, c);
	}

	public void setModTime(Calendar c) throws ResourceException
	{
		setProperty(_MOD_TIME_PROP_NAME, c);
	}

	protected boolean dirExists() throws ResourceException
	{
		String path = getLocalPath();
		if (path == null)
			throw new ResourceException("No path name set for ExportedDirResource");

		File myFile = new File(path);

		if (myFile.exists() && myFile.isDirectory())
			return true;

		return false;
	}

	protected void loadDirInfo() throws ResourceException
	{
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			stmt = _connection.prepareStatement(_RETRIEVE_DIR_INFO);
			stmt.setString(1, getId());
			rs = stmt.executeQuery();

			if (rs.next()) {
				_myLocalPath = rs.getString(1);
				_myParentIds = rs.getString(2);
				_isReplicated = rs.getString(3);
				_lastModified = rs.getLong(4);
			} else {
				_myLocalPath = null;
				_myParentIds = null;
				_isReplicated = null;
				_lastModified = null;
			}
		} catch (SQLException sqe) {
			throw new ResourceException(sqe.getLocalizedMessage(), sqe);
		} finally {
			StreamUtils.close(rs);
			StreamUtils.close(stmt);
		}
	}

	protected void insertDirInfo() throws ResourceException
	{
		if (_myLocalPath == null || _myParentIds == null)
			throw new ResourceException("Cannot add ExportedDir without valid parent IDs or path.");

		PreparedStatement stmt = null;

		try {
			stmt = _connection.prepareStatement(_CREATE_DIR_INFO);
			stmt.setString(1, _resourceKey);
			stmt.setString(2, _myLocalPath);
			stmt.setString(3, _myParentIds);
			stmt.setString(4, _isReplicated);
			stmt.setLong(5, _lastModified);
			if (stmt.executeUpdate() != 1)
				throw new ResourceException("Unable to insert ExportedDir resource information.");
		} catch (SQLException sqe) {
			throw new ResourceException(sqe.getLocalizedMessage(), sqe);
		} finally {
			StreamUtils.close(stmt);
		}
	}

	protected void createOnDisk(String entryName, String entryType) throws ResourceException
	{
		String fullPath = ExportedFileUtils.createFullPath(getLocalPath(), entryName);

		if (_logger.isDebugEnabled())
			_logger.debug("Export Dir asked to create \"[" + entryType + "] " + fullPath + "\" on disk");

		try {
			if (entryType.equals(ExportedDirEntry._FILE_TYPE)) {
				if (!ExportedFileUtils.createLocalFile(fullPath))
					throw FaultManipulator.fillInFault(new RNSEntryExistsFaultType());
			} else if (entryType.equals(ExportedDirEntry._DIR_TYPE)) {
				if (!ExportedDirUtils.createLocalDir(fullPath))
					throw FaultManipulator.fillInFault(new RNSEntryExistsFaultType());
			} else {
				throw new ResourceException("Improper type for exported dir entry.");
			}
		} catch (IOException ioe) {
			_logger.error("Unable to create file/directory at path " + fullPath, ioe);
			throw new ResourceException("Unable to create file/directory at path " + fullPath, ioe);
		}
	}

	protected void addEntry(String entryName, EndpointReferenceType entryReference, String entryID, String entryType)
		throws ResourceException, RNSEntryExistsFaultType
	{
		PreparedStatement stmt = null;

		try {
			stmt = _connection.prepareStatement(_ADD_ENTRY_STMT);
			stmt.setString(1, getId());
			stmt.setString(2, entryName);
			stmt.setBlob(3, EPRUtils.toBlob(entryReference, "exporteddirentry", "endpoint"));
			stmt.setString(4, entryID);
			stmt.setString(5, entryType);
			if (stmt.executeUpdate() != 1)
				throw new ResourceException("Unable to update resource.");
		} catch (SQLException sqe) {
			if (sqe.getErrorCode() == -104) {
				// Uniqueness problem
				RNSEntryExistsFaultType fault = new RNSEntryExistsFaultType();
				fault.setEntryName(entryName);
				throw FaultManipulator.fillInFault(fault);
			} else
				throw new ResourceException(sqe.getLocalizedMessage(), sqe);
		} finally {
			StreamUtils.close(stmt);
		}
	}

	protected void addAttributes(String entryName, MessageElement[] attrs, String entryID) throws ResourceException
	{
		PreparedStatement stmt = null;

		try {
			if (attrs != null && attrs.length > 0) {
				stmt = _connection.prepareStatement(_ADD_DIR_ATTR_STMT);
				boolean failed = false;
				for (MessageElement nextAttr : attrs) {
					stmt.setString(1, entryID);
					stmt.setBytes(2, MessageElementUtils.toBytes(nextAttr));
					if (stmt.executeUpdate() != 1) {
						failed = true;
						break;
					}
				}
				if (failed) {
					throw new ResourceException("Unable to update attributes for RNS resource.");
				}
			}
		} catch (SQLException sqe) {
			throw new ResourceException(sqe.getLocalizedMessage(), sqe);
		} finally {
			StreamUtils.close(stmt);
		}
	}

	protected Collection<File> listEntriesAsFiles() throws ResourceException
	{
		/*
		 * Sanity checks - make sure we have a local directory, that it exists, and is a directory
		 */
		String dirPath = getLocalPath();
		if (dirPath == null)
			throw new ResourceException("ExportedDir has no local path.  Cannot list entries.");
		File dir = new File(dirPath);
		if (!dir.exists())
			throw new ResourceException("Local path for ExportedDir does not exist.");
		if (!dir.isDirectory())
			throw new ResourceException("Local path for exported dir is not a directory.");

		/* Get listing */
		File[] localEntries = dir.listFiles();
		ArrayList<File> ret = new ArrayList<File>();

		for (File nextEntry : localEntries)
			ret.add(nextEntry);

		return ret;
	}

	protected Collection<ExportedDirEntry> syncEntries(Collection<ExportedDirEntry> knownEntries, Collection<File> realEntries)
		throws ResourceException
	{
		Collection<ExportedDirEntry> results = new ArrayList<ExportedDirEntry>();

		/* Create HashMap (names --> entry) for known entries */
		HashMap<String, ExportedDirEntry> knownEntriesHash = new HashMap<String, ExportedDirEntry>(knownEntries.size());
		for (ExportedDirEntry nextKnown : knownEntries)
			knownEntriesHash.put(nextKnown.getName(), nextKnown);

		/* Create HashMap (names --> File) for real directory entries */
		HashMap<String, File> realEntriesHash = new HashMap<String, File>(realEntries.size());
		for (File nextReal : realEntries)
			realEntriesHash.put(nextReal.getName(), nextReal);

		/* eliminate old entries */
		Iterator<ExportedDirEntry> nextKnownIter = knownEntries.iterator();
		while (nextKnownIter.hasNext()) {
			ExportedDirEntry nextKnown = nextKnownIter.next();
			File matchingReal = realEntriesHash.get(nextKnown.getName());
			if (matchingReal != null) {
				/* name matches. Check if dir/file matches */
				if ((matchingReal.isDirectory() && !nextKnown.isDirectory()) || (matchingReal.isFile() && !nextKnown.isFile())) {
					/* remove entry from directory data */
					try {
						removeEntry(nextKnown, false);
					} catch (ResourceUnknownFaultType ruft) {
						if (_logger.isDebugEnabled())
							_logger.debug("ResourceUnknownFaultType encountered while cleaning "
								+ "up entry in ExportedDirDBResource.syncEntries " + "-- probably normal");
					}
					nextKnownIter.remove();
				} else {
					results.add(nextKnown);
				}
			} else {
				/* remove entry from directory data */
				try {
					removeEntry(nextKnown, false);
				} catch (ResourceUnknownFaultType ruft) {
					if (_logger.isDebugEnabled())
						_logger.debug("ResourceUnknownFaultType encountered while cleaning "
							+ "up entry in ExportedDirDBResource.syncEntries " + "-- probably normal");
				}
				nextKnownIter.remove();
			}
		}

		/* make new entries if necessary */
		String childrenParentIds = ExportedDirUtils.createParentIdsString(getParentIds(), getId());
		Iterator<File> realIter = realEntries.iterator();
		/*
		 * ASG, August 10, 2008. Modified to do more initialization here rather than constantly checking to see if things have been
		 * initialized later.
		 */
		if ((_fileServiceEPR == null) || (_dirServiceEPR == null))
			synchronized (this.getClass()) {
				if (_fileServiceEPR == null) {
					_fileServiceEPR = EPRUtils.makeEPR(Container.getServiceURL("ExportedFilePortType"));
				}
				if (_dirServiceEPR == null) {
					_dirServiceEPR = EPRUtils.makeEPR(Container.getServiceURL("ExportedDirPortType"));
				}
			}
		while (realIter.hasNext()) {
			File nextReal = realIter.next();
			ExportedDirEntry matchingKnown = knownEntriesHash.get(nextReal.getName());
			if (matchingKnown == null) {
				String newPath = ExportedFileUtils.createFullPath(getLocalPath(), nextReal.getName());

				ExportedDirEntry newEntry;
				EndpointReferenceType serviceEPR;
				String entryType;
				MessageElement[] creationProperties;

				if (nextReal.isFile()) {
					/*
					 * Moved to constructor by ASG, 8/10/08 synchronized(this.getClass()) { if (_fileServiceEPR == null) { _fileServiceEPR =
					 * EPRUtils.makeEPR( Container.getServiceURL("ExportedFilePortType")); } }
					 */
					serviceEPR = _fileServiceEPR;
					entryType = ExportedDirEntry._FILE_TYPE;
					creationProperties = ExportedFileUtils.createCreationProperties(newPath, childrenParentIds, getReplicationState());

				} else if (nextReal.isDirectory()) {
					try {
						/* moved code to check if _dirServiceEPR set to constructor */
						serviceEPR = _dirServiceEPR;
						entryType = ExportedDirEntry._DIR_TYPE;
						PreferredIdentity current = PreferredIdentity.getCurrent();
						String owner = current != null ? current.getIdentityString() : null;
						if (_logger.isDebugEnabled())
							_logger.debug("got preferred identity for new export: '" + owner + "'");
						creationProperties = ExportedDirUtils.createCreationProperties(null, newPath, null, null, null, childrenParentIds,
							getReplicationState(), owner);
					} catch (RemoteException re) {
						throw new ResourceException("Unable to create construction parameters.", re);
					}
				} else {
					throw new ResourceException("Local directory " + getLocalPath() + " has an entry (" + nextReal.getName()
						+ ") that is neither a directory nor a file.");
				}

				try {
					newEntry = createEntryForRealFile(nextReal.getName(), serviceEPR, entryType, creationProperties, nextReal);
					results.add(newEntry);
				} catch (RemoteException re) {
					throw new ResourceException(re.getLocalizedMessage(), re);
				}
			}
		}

		commit();
		return results;
	}

	protected ExportedDirEntry createEntryForRealFile(String nextRealName, EndpointReferenceType serviceEPR, String entryType,
		MessageElement[] creationProperties, File nextReal) throws ResourceException, RemoteException
	{
		if (_logger.isDebugEnabled())
			_logger.debug("Creating new export entries");

		/* create new Export resource */

		/*
		 * ASG changes on August 9, 2008 to make a direct call to create an EPR rather than going through the whole WS stack.
		 */

		EndpointReferenceType entryReference = null;

		if (nextReal.isFile()) {
			try {
				ExportedFileServiceImpl tmp = new ExportedFileServiceImpl();

				entryReference = tmp.CreateEPR(creationProperties, serviceEPR.getAddress().get_value().toString());
				// entryReference = tmp.vcgrCreate(new
				// VcgrCreate(creationProperties)).getEndpoint();
			} catch (RemoteException re) {
				throw new ResourceException(re.getLocalizedMessage(), re);
			}
		}
		if (nextReal.isDirectory()) {
			try {
				ExportedDirServiceImpl tmp = new ExportedDirServiceImpl();
				entryReference = tmp.CreateEPR(creationProperties, serviceEPR.getAddress().get_value().toString());
				// entryReference = tmp.vcgrCreate(new
				// VcgrCreate(creationProperties)).getEndpoint();

			} catch (RemoteException re) {
				throw new ResourceException(re.getLocalizedMessage(), re);
			}
		}

		/*
		 * GeniiCommon common = ClientUtils.createProxy(GeniiCommon.class, serviceEPR); VcgrCreateResponse resp = common.vcgrCreate( new
		 * VcgrCreate(creationProperties));
		 * 
		 * EndpointReferenceType entryReference = resp.getEndpoint();
		 */

		// replicate if flag is set
		if (getReplicationState().equals("true")) {

			// creates resolver and replica for new export entry
			// returns augmented EPR with resolver to replica
			try {
				entryReference = RExportResolverUtils.setupRExport(entryReference, // primary epr
					entryType, // file or dir primary type
					creationProperties[0].getValue(), // primary localpath of export
					getResourceEPIasString(), // EPI of dir resource containing new entry
					nextRealName); // name of file/dir
			} catch (Exception e) {
				_logger.error("Unable to create/replicate/fill exportdir's rexport resolver: " + e.getLocalizedMessage());
				throw new ResourceException("Unable to create/replicate/fill exportdir's  rexport resolver: " + e.getLocalizedMessage());
			}
		}

		// create entry for new export resource in export DB
		String newId = (new GUID()).toString();
		ExportedDirEntry newEntry = new ExportedDirEntry(getId(), nextRealName, entryReference, newId, entryType, null);
		addEntry(newEntry, false);

		return newEntry;
	}

	/*
	 * returns current resource's EPI as String from working context
	 */
	protected String getResourceEPIasString() throws RuntimeException, ResourceException, ResourceUnknownFaultType
	{
		String resourceEPI = null;

		resourceEPI = (String) WorkingContext.getCurrentWorkingContext().getProperty(WorkingContext.EPI_KEY);

		return resourceEPI;
	}

	protected Collection<ExportedDirEntry> retrieveKnownEntries() throws ResourceException
	{
		try {
			Collection<ExportedDirEntry> ret = retrieveBareEntries(getId());

			if (ret.size() > 0)
				addAttributes(ret, getId());

			return ret;
		} catch (SQLException sqe) {
			throw new ResourceException(sqe.getLocalizedMessage(), sqe);
		}
	}

	protected Collection<ExportedDirEntry> retrieveBareEntries(String id) throws SQLException, ResourceException
	{
		PreparedStatement stmt = null;
		ResultSet rs = null;
		ArrayList<ExportedDirEntry> ret = new ArrayList<ExportedDirEntry>();

		try {
			stmt = _connection.prepareStatement(_RETRIEVE_EXPORTED_ENTRIES_STMT);
			stmt.setString(1, getId());
			rs = stmt.executeQuery();

			while (rs.next()) {
				ExportedDirEntry entry = new ExportedDirEntry(rs.getString(1), rs.getString(2), EPRUtils.fromBlob(rs.getBlob(3)),
					rs.getString(4), rs.getString(5), null);
				ret.add(entry);
			}

			return ret;
		} finally {
			StreamUtils.close(rs);
			StreamUtils.close(stmt);
		}
	}

	protected void addAttributes(Collection<ExportedDirEntry> entries, String id) throws SQLException, ResourceException
	{
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			stmt = _connection.prepareStatement(_RETRIEVE_EXPORTED_ENTRY_ATTRS_FOR_DIR_STMT);
			stmt.setString(1, id);
			rs = stmt.executeQuery();

			while (rs.next()) {
				String entryid = rs.getString(1);
				MessageElement elem = MessageElementUtils.fromBytes(rs.getBytes(2));

				for (ExportedDirEntry nextEntry : entries) {
					if (nextEntry.getId().equals(entryid)) {
						nextEntry.addAttribute(elem);
						break;
					}
				}
			}
		} finally {
			StreamUtils.close(rs);
			StreamUtils.close(stmt);
		}
	}

	protected void removeEntry(ExportedDirEntry entry, boolean hardDestroy) throws ResourceException, ResourceUnknownFaultType
	{
		try {
			ResourceKey rKey = ResourceManager.getTargetResource(entry.getEntryReference());
			IExportedEntryResource resource = (IExportedEntryResource) rKey.dereference();

			// destory everything underneath/relating to that export resource
			resource.destroy(_connection, hardDestroy);
		} catch (ResourceException ruft) {
			// Ignore so we can keep cleaning up.
			_logger.error("(EXPECTED) Unable to destroy resource.  " + "If file/dir no longer exists, then it was already cleaned up.");
		}

		/* remove entry information */
		PreparedStatement stmt = null;

		try {
			stmt = _connection.prepareStatement(_DELETE_EXPORTED_ENTRY_ATTRS_STMT);
			stmt.setString(1, entry.getId());
			stmt.executeUpdate();

			stmt.close();
			stmt = null;

			stmt = _connection.prepareStatement(_DELETE_EXPORTED_ENTRY_STMT);
			stmt.setString(1, entry.getId());
			stmt.executeUpdate();

			stmt.close();
			stmt = null;
		} catch (SQLException sqe) {
			throw new ResourceException(sqe.getLocalizedMessage(), sqe);
		} finally {
			StreamUtils.close(stmt);
		}
	}

	protected void destroyDirectory(File rootDir)
	{
		if (!rootDir.exists() || !rootDir.isDirectory())
			return;

		File[] children = rootDir.listFiles();
		for (File child : children) {
			if (child.isDirectory())
				destroyDirectory(child);
			else
				child.delete();
		}

		rootDir.delete();
	}

	protected void destroyDirectory(String path)
	{
		destroyDirectory(new File(path));
	}

	static void dirDestroyAllForParentDir(Connection connection, String parentId, boolean hardDestroy, String isReplicated)
		throws ResourceException
	{
		String parentIdSearch =
			"%" + ExportedDirUtils._PARENT_ID_BEGIN_DELIMITER + parentId + ExportedDirUtils._PARENT_ID_END_DELIMITER + "%";

		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			stmt = connection.prepareStatement(_RETRIEVE_ALL_DIR_IDS_FOR_PARENT_STMT);
			stmt.setString(1, parentIdSearch);
			rs = stmt.executeQuery();
			Collection<String> dirids = new ArrayList<String>();
			while (rs.next())
				dirids.add(rs.getString(1));

			rs.close();
			rs = null;
			stmt.close();
			stmt = null;

			// if replicated, perform required actions
			if (isReplicated.equals("true")) {

				// retrieve all EPRs associated with dirs
				stmt = connection.prepareStatement(_RETRIEVE_ALL_EPRS_FOR_PARENT_STMT);
				stmt.setString(1, parentIdSearch);
				rs = stmt.executeQuery();
				Collection<EndpointReferenceType> dirEPRs = new ArrayList<EndpointReferenceType>();
				while (rs.next())
					dirEPRs.add(EPRUtils.fromBlob(rs.getBlob(1)));
				rs.close();
				rs = null;
				stmt.close();
				stmt = null;

				for (EndpointReferenceType exportEPR : dirEPRs) {

					// notify exportDirs resolver of termination
					try {
						if (_logger.isDebugEnabled())
							_logger.debug("Notifying resolver of (contained) exportedDir termination");

						RExportResolverUtils.destroyResolverByEPR(exportEPR);
					} catch (Exception ce) {
						_logger.error("Unable to notify resolver of export termination.", ce);
					}

				}
			}

			stmt = connection.prepareStatement(_DESTROY_ALL_ATTRS_FOR_PARENT_STMT);
			stmt.setString(1, parentIdSearch);
			stmt.executeUpdate();
			stmt.close();
			stmt = null;

			stmt = connection.prepareStatement(_DESTROY_ALL_ENTRIES_FOR_PARENT_STMT);
			stmt.setString(1, parentIdSearch);
			stmt.executeUpdate();
			stmt.close();
			stmt = null;

			stmt = connection.prepareStatement(_DESTROY_ALL_DIRS_FOR_PARENT_STMT);
			stmt.setString(1, parentIdSearch);
			stmt.executeUpdate();
			stmt.close();
			stmt = null;

			BasicDBResource.destroyAll(connection, dirids);
		} catch (SQLException sqe) {
			throw new ResourceException(sqe.getLocalizedMessage(), sqe);
		} finally {
			StreamUtils.close(rs);
			StreamUtils.close(stmt);
		}
	}

	private void fillInAttributes(ExportedDirEntry entry)
	{
		AttributePreFetcher preFetcher = null;

		try {
			File entryFile = new File(getLocalPath(), entry.getName());
			if (!entryFile.exists())
				return;
			if (entryFile.isFile())
				preFetcher = new DefaultRandomByteIOAttributePreFetcher(entry.getEntryReference());
			else
				preFetcher = new DefaultGenesisIIAttributesPreFetcher<IResource>(entry.getEntryReference());
		} catch (Throwable cause) {
			_logger.warn("Unable to pre-fetch attributes for entry.", cause);
		}

		if (preFetcher == null)
			return;

		Collection<MessageElement> attrs = preFetcher.preFetch();
		MessageElement[] oldAttrs = entry.getAttributes();
		if (oldAttrs != null) {
			for (MessageElement oldAttr : oldAttrs) {
				attrs.add(oldAttr);
			}
		}

		if (attrs.size() > 0)
			entry.setAttributes(attrs.toArray(new MessageElement[0]));
	}
}
