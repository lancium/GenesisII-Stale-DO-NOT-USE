package edu.virginia.vcgr.genii.client.cmd.tools.login;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.security.cert.X509Certificate;
import java.util.Collection;

import edu.virginia.vcgr.genii.client.io.GetPassword2;
import edu.virginia.vcgr.genii.security.x509.CertEntry;

public class TextLoginHandler extends AbstractLoginHandler {
	public TextLoginHandler(PrintWriter out, PrintWriter err, BufferedReader in) {
		super(out, err, in);
	}

	@Override
	public char[] getPassword(String title, String prompt) {
		return GetPassword2.getPassword(prompt).toCharArray();
	}

	@Override
	protected CertEntry selectCert(Collection<CertEntry> entries) {
		CertEntry[] entriesA = new CertEntry[entries.size()];
		entries.toArray(entriesA);

		while (true) {
			_out.println("Please select a certificate to load:");
			for (int lcv = 0; lcv < entriesA.length; lcv++) {
				_out.println("\t["
						+ lcv
						+ "]:  "
						+ ((X509Certificate) (entriesA[lcv]._certChain[0]))
								.getSubjectDN().getName());
			}

			_out.println("\t[x]:  Cancel");
			_out.print("\nSelection?  ");
			try {
				String answer = _in.readLine();
				if (answer == null)
					return null;
				if (answer.equalsIgnoreCase("x"))
					return null;
				int which = Integer.parseInt(answer);
				if (which >= entriesA.length) {
					_err.println("Selection index must be between 0 and "
							+ (entriesA.length - 1));
				}
				return entriesA[which];
			} catch (Throwable t) {
				_err.println("Error getting login selection:  "
						+ t.getLocalizedMessage());
				return null;
			}
		}
	}
}
