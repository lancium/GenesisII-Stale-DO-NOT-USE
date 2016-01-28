package edu.virginia.vcgr.genii.client.rns;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.virginia.vcgr.genii.client.gpath.GeniiPath;

/**
 * Support for recursively deleting paths of either GeniiPath or RNSPath type.
 * 
 * @author Chris Koeritz
 * @copyright Copyright (c) 2012-$now By University of Virginia
 * @license This file is free software; you can modify and redistribute it under the terms of the Apache License v2.0:
 *          http://www.apache.org/licenses/LICENSE-2.0
 */
public class PathDisposal
{
	static private Log _logger = LogFactory.getLog(PathDisposal.class);

	// we do not try to remove directory trees deeper than this.
	final static int MAX_DELETE_DEPTH = 20;

	// this bank of functions and classes is for handling recursive removal of RNS paths.

	private static class RemoveBouncerRNS implements TreeTraversalActionAlert<RNSPath>
	{
		@Override
		public PathOutcome respond(RNSPath path)
		{
			if (path == null)
				return PathOutcome.OUTCOME_NOTHING;
			try {
				String msg = "detected bounce point and unlinking at path: " + path.pwd();
				if (_logger.isDebugEnabled())
					_logger.debug(msg);
				path.unlink();
			} catch (Throwable cause) {
				_logger.warn("caught exception when eating directory.", cause);
				return PathOutcome.OUTCOME_NO_ACCESS;
			}
			return PathOutcome.OUTCOME_CONTINUABLE;
		}
	}

	private static class RemoveFileRNS implements TreeTraversalActionAlert<RNSPath>
	{
		private TreeTraversalPathQuery<RNSPath> querier;
		RemoveBouncerRNS bouncy;

		RemoveFileRNS(TreeTraversalPathQuery<RNSPath> q, RemoveBouncerRNS b)
		{
			querier = q;
			bouncy = b;
		}

		@Override
		public PathOutcome respond(RNSPath path)
		{
			if ((path == null) || (querier == null))
				return PathOutcome.OUTCOME_NOTHING;
			PathOutcome ret = removeAppropriately(path, querier, bouncy);
			if (ret.differs(PathOutcome.OUTCOME_SUCCESS)) {
				_logger.warn("could not remove RNS path appropriately for file at " + path.toString());
			}
			return ret;
		}
	}

	private static class RemoveDirectoryRNS implements TreeTraversalActionAlert<RNSPath>
	{
		private TreeTraversalPathQuery<RNSPath> querier;
		RemoveBouncerRNS bouncy;

		RemoveDirectoryRNS(TreeTraversalPathQuery<RNSPath> q, RemoveBouncerRNS b)
		{
			querier = q;
			bouncy = b;
		}

		@Override
		public PathOutcome respond(RNSPath path)
		{
			if ((path == null) || (querier == null))
				return PathOutcome.OUTCOME_NOTHING;
			PathOutcome ret = removeAppropriately(path, querier, bouncy);
			if (ret.differs(PathOutcome.OUTCOME_SUCCESS)) {
				_logger.warn("could not remove RNS path appropriately for directory at " + path.toString());
			}
			return ret;
		}
	}

	/**
	 * Attempts to do the right thing about a path that should be removed. This will not succeed on a directory that is not empty.
	 */
	static public PathOutcome removeAppropriately(RNSPath path, TreeTraversalPathQuery<RNSPath> querier, RemoveBouncerRNS b)
	{
		if ((path == null) || (querier == null))
			return PathOutcome.OUTCOME_NOTHING;
		if (_logger.isTraceEnabled())
			_logger.debug("trying to remove appropriately: " + path.toString());
		PathOutcome ret = querier.checkPathSanity(path, b);
		if (ret.differs(PathOutcome.OUTCOME_SUCCESS)) {
			// we still need to get rid of it if we can, so try to unlink it.
			if (querier.exists(path)) {
				if (_logger.isDebugEnabled())
					_logger.debug("unlinking problematic path: " + path.toString());
				try {
					path.unlink();
					return PathOutcome.OUTCOME_SUCCESS;
				} catch (Throwable cause) {
					_logger.warn("Could not unlink the path at " + path.toString(), cause);
				}
			}
			return ret;
		}
		GeniiPath gPath = new GeniiPath(path.pwd());
		if (_logger.isTraceEnabled())
			_logger.debug("operating on " + path.pwd() + " from: " + path.toString());
		if (gPath.isFile()) {
			// this is not a directory, so we don't have to check for children.
			if (_logger.isTraceEnabled())
				_logger.debug("removing normal rns file of: " + gPath.toString());
			try {
				path.delete();
			} catch (Throwable cause) {
				_logger.warn("Could not delete the file path at " + path.toString(), cause);
				return PathOutcome.OUTCOME_NO_ACCESS;
			}
		} else if (gPath.isDirectory()) {
			if (_logger.isTraceEnabled())
				_logger.debug("removing directory for " + gPath.toString());
			// this is a directory, so make sure it's empty.
			int size = 0;
			try {
				size = path.listContents().size();
			} catch (Throwable cause) {
				_logger.warn("Could not list contents for directory path: " + path.toString(), cause);
				return PathOutcome.OUTCOME_ERROR;
			}
			if (size != 0) {
				// throw a fault, this is not right.
				String msg = new String("Could not remove non-empty subdirectory under: " + path.toString());
				_logger.warn(msg);
				return PathOutcome.OUTCOME_NON_EMPTY;
			}
			if (_logger.isTraceEnabled())
				_logger.debug("whacking rns directory of: " + path.pwd());
			// presumably we are good to go now; the directory is empty.
			try {
				path.delete();
			} catch (RNSPathDoesNotExistException cause) {
				_logger.warn("Could not delete non-existent directory at " + path.toString(), cause);
				return PathOutcome.OUTCOME_NONEXISTENT;
			} catch (Throwable cause) {
				_logger.warn("Could not delete the directory path at " + path.toString(), cause);
				return PathOutcome.OUTCOME_NO_ACCESS;
			}
		} else {
			if (_logger.isDebugEnabled())
				_logger.debug("unknown type for: " + gPath.toString());
			return PathOutcome.OUTCOME_WRONG_TYPE;
		}

		return PathOutcome.OUTCOME_SUCCESS;
	}

	/**
	 * Removes a path recursively, if possible. All contents will be removed. The only way this should fail is due to a permissions problem.
	 */
	static public PathOutcome recursiveDelete(RNSPath path)
	{
		if (path == null)
			return PathOutcome.OUTCOME_NOTHING;
		RNSPathHierarchyHelper helping = new RNSPathHierarchyHelper();
		RemoveBouncerRNS ourBouncer = new RemoveBouncerRNS();
		RNSRecurser zapper =
			new RNSRecurser(null, new RemoveDirectoryRNS(helping, ourBouncer), new RemoveFileRNS(helping, ourBouncer), ourBouncer);
		zapper.setMaximumRecursionDepth(MAX_DELETE_DEPTH);
		PathOutcome ret = PathOutcome.OUTCOME_NO_ACCESS;
		try {
			ret = zapper.recursePath(path);
		} catch (Exception cause) {
			_logger.error("caught unhandled exception during path traversal", cause);
			return PathOutcome.OUTCOME_NO_ACCESS;
		}
		if (ret.differs(PathOutcome.OUTCOME_SUCCESS))
			return ret;
		if (new GeniiPath(path.pwd()).isFile()) {
			// now clean up the top-level asset they gave us. this is only needed if the
			// top-level guy was a file.
			return removeAppropriately(path, helping, ourBouncer);
		}
		return PathOutcome.OUTCOME_SUCCESS;
	}

	// ////////////

	// this bank of functions and classes is for handling recursive removal of GenesisII paths.

	private static class RemoveBouncerJavaFile implements TreeTraversalActionAlert<File>
	{
		@Override
		public PathOutcome respond(File path)
		{
			if (path == null)
				return PathOutcome.OUTCOME_NOTHING;
			String msg = "detected bounceable condition at path: " + path;
			if (_logger.isDebugEnabled())
				_logger.debug(msg);
			return PathOutcome.OUTCOME_CONTINUABLE;
		}
	}

	private static class RemoveFileJavaFile implements TreeTraversalActionAlert<File>
	{
		private TreeTraversalPathQuery<File> querier;
		RemoveBouncerJavaFile bouncy;

		RemoveFileJavaFile(TreeTraversalPathQuery<File> q, RemoveBouncerJavaFile b)
		{
			querier = q;
			bouncy = b;
		}

		@Override
		public PathOutcome respond(File path)
		{
			return removeAppropriately(path, querier, bouncy);
		}
	}

	private static class RemoveDirectoryJavaFile implements TreeTraversalActionAlert<File>
	{
		private TreeTraversalPathQuery<File> querier;
		RemoveBouncerJavaFile bouncy;

		RemoveDirectoryJavaFile(TreeTraversalPathQuery<File> q, RemoveBouncerJavaFile b)
		{
			querier = q;
			bouncy = b;
		}

		@Override
		public PathOutcome respond(File path)
		{
			return removeAppropriately(path, querier, bouncy);
		}
	}

	/**
	 * Attempts to do the right thing about a path that should be removed. This will not succeed on a directory that is not empty.
	 */
	static public PathOutcome removeAppropriately(File path, TreeTraversalPathQuery<File> querier, RemoveBouncerJavaFile b)
	{
		if ((path == null) || (querier == null))
			return PathOutcome.OUTCOME_NOTHING;
		if (_logger.isTraceEnabled())
			_logger.debug("trying to remove appropriately: " + path);
		PathOutcome ret = querier.checkPathSanity(path, b);
		if (ret.differs(PathOutcome.OUTCOME_SUCCESS)) {
			if (_logger.isTraceEnabled())
				_logger.debug("operating on " + path);
			return ret;
		}

		// / File gPath = new File(path);
		if (_logger.isTraceEnabled())
			_logger.debug("operating on " + path);
		if (path.isFile()) {
			// this is not a directory, so we don't have to check for children.
			if (_logger.isTraceEnabled())
				_logger.debug("removing normal file of: " + path);
			path.delete();
		} else {
			if (_logger.isTraceEnabled())
				_logger.debug("removing directory for " + path);
			// this is a directory, so make sure it's empty.
			if (querier.getContents(path).size() != 0) {
				// throw a fault, this is not right.
				String msg = new String("Could not remove non-empty subdirectory under: " + path);
				_logger.warn(msg);
				return PathOutcome.OUTCOME_NON_EMPTY;
			}
			if (_logger.isTraceEnabled())
				_logger.debug("whacking directory of: " + path);
			// presumably we are good to go now; the directory is empty.
			path.delete();
		}
		return PathOutcome.OUTCOME_SUCCESS;
	}

	/**
	 * Removes a path recursively, if possible. All contents will be removed. The only way this should fail is due to a permissions problem.
	 */
	static public PathOutcome recursiveDelete(File path)
	{
		if (path == null)
			return PathOutcome.OUTCOME_NOTHING;
		JavaFileHierarchyHelper helping = new JavaFileHierarchyHelper();
		RemoveBouncerJavaFile ourBouncer = new RemoveBouncerJavaFile();
		JavaFileRecurser zapper = new JavaFileRecurser(null, new RemoveDirectoryJavaFile(helping, ourBouncer),
			new RemoveFileJavaFile(helping, ourBouncer), ourBouncer);
		zapper.setMaximumRecursionDepth(MAX_DELETE_DEPTH);
		PathOutcome ret = PathOutcome.OUTCOME_NO_ACCESS;
		ret = zapper.recursePath(path);
		if (ret.differs(PathOutcome.OUTCOME_SUCCESS))
			return ret;
		if (path.isFile()) {
			// now clean up the top-level asset they gave us. this is only needed if the
			// top-level guy was a file.
			ret = removeAppropriately(path, helping, new RemoveBouncerJavaFile());
		}
		return ret;
	}
}
