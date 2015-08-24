package edu.virginia.vcgr.genii.algorithm.compression;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class UnpackTar
{
	static private Log _logger = LogFactory.getLog(UnpackTar.class);

	/**
	 * takes a tar.gz file as the "tarFile" parameter, then decompresses and unpacks the file into the "dest" location.
	 */

	public static void uncompressArchive(ArchiveInputStream tarIn, File dest) throws IOException
	{

		if (dest.exists()) {
			// Don't unpack into an existing directory
			throw new IOException("Directory " + dest.getAbsolutePath() + " already exists. Unpacking exiting");
		}
		dest.mkdir();

		ArchiveEntry tarEntry = tarIn.getNextEntry();
		while (tarEntry != null) {
			// create a file with the same name as the tarEntry
			File destPath = new File(dest, tarEntry.getName());
			if (_logger.isTraceEnabled())
				_logger.debug("working on: " + destPath.getCanonicalPath());
			if (tarEntry.isDirectory()) {
				destPath.mkdirs();
			} else {
				destPath.createNewFile();
				// byte [] btoRead = new byte[(int)tarEntry.getSize()];
				byte[] btoRead = new byte[8192];
				BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(destPath));
				int len = 0;
				boolean wroteAnything = false;
				while ((len = tarIn.read(btoRead)) != -1) {
					if (_logger.isTraceEnabled())
						_logger.debug("read " + len + " bytes");
					wroteAnything = true;
					bout.write(btoRead, 0, len);
				}
				if (!wroteAnything) {
					_logger.error("zero bytes read from: " + destPath.getCanonicalPath());
				}

				bout.close();
			}
			tarEntry = tarIn.getNextEntry();
		}
		tarIn.close();

	}

	public synchronized static void uncompressTarGZ(File tarFile, File dest) throws IOException
	{
		TarArchiveInputStream tarIn = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(tarFile)));

		uncompressArchive(tarIn, dest);
	}

	public synchronized static void uncompressTar(File tarFile, File dest) throws IOException
	{
		TarArchiveInputStream tarIn = new TarArchiveInputStream(new FileInputStream(tarFile));

		uncompressArchive(tarIn, dest);
	}

	public synchronized static void uncompressZip(File zipFile, File dest) throws IOException
	{
		ZipArchiveInputStream tarIn = new ZipArchiveInputStream(new FileInputStream(zipFile));

		uncompressArchive(tarIn, dest);
	}

	static public void main(String[] args) throws Throwable
	{
		if (args.length != 2) {
			System.err.println("USAGE: UnpackTar {tar.gz file} {output location}");
			System.exit(1);
		}

		try {
			UnpackTar.uncompressTarGZ(new File(args[0]), new File(args[1]));
		} catch (Throwable t) {
			_logger.error("failed to uncompress tar file " + args[0] + " into " + args[1]);
			System.exit(1);
		}

		System.out.println("successfully uncompressed tar file " + args[0] + " into " + args[1]);
	}

}
