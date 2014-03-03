/*
 * Copyright 2006 University of Virginia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.morgan.util.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.Socket;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * @author Mark Morgan (mark@mark-morgan.org)
 */
public class StreamUtils {
	static private final int _DEFAULT_BUFFER_SIZE = 1024 * 512;

	static public void copyStream(Reader reader, Writer writer)
			throws IOException {
		char[] data = new char[_DEFAULT_BUFFER_SIZE];
		int read;

		while ((read = reader.read(data)) > 0) {
			if (writer != null)
				writer.write(data, 0, read);
		}

		writer.flush();
	}

	static public DataTransferStatistics copyStream(InputStream in,
			OutputStream out, boolean autoflush) throws IOException {
		DataTransferStatistics stats = DataTransferStatistics.startTransfer();

		byte[] data = new byte[_DEFAULT_BUFFER_SIZE];
		int read;

		while ((read = in.read(data)) >= 0) {
			if (out != null) {
				out.write(data, 0, read);
				if (autoflush)
					out.flush();
				stats.transfer(read);
			}
		}

		if (out != null)
			out.flush();

		return stats.finishTransfer();
	}

	static public DataTransferStatistics copyStream(InputStream in,
			OutputStream out) throws IOException {
		return copyStream(in, out, false);
	}

	static public void close(Closeable item) {
		try {
			if (item != null)
				item.close();
		} catch (Throwable ioe) {
		}
	}

	static public void close(Statement stmt) {
		try {
			if (stmt != null)
				stmt.close();
		} catch (Throwable sqe) {
		}
	}

	static public void close(Connection conn) {
		try {
			if (conn != null)
				conn.close();
		} catch (Throwable sqe) {
		}
	}

	static public void close(ResultSet rs) {
		try {
			if (rs != null)
				rs.close();
		} catch (Throwable sqe) {
		}
	}

	static public void close(Socket socket) {
		try {
			if (socket != null)
				socket.close();
		} catch (Throwable cause) {
		}
	}
}
