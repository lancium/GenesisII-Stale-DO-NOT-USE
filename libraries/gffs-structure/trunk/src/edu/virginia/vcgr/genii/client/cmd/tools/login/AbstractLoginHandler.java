package edu.virginia.vcgr.genii.client.cmd.tools.login;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.security.AccessController;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;

import edu.virginia.vcgr.appmgr.os.OperatingSystemType;
import edu.virginia.vcgr.genii.client.cmd.tools.KeystoreLoginTool;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.security.wincrypto.WinX509KeyManager;
import edu.virginia.vcgr.genii.security.x509.CertEntry;
import edu.virginia.vcgr.genii.security.x509.InputStreamBuilder;

public abstract class AbstractLoginHandler implements CallbackHandler
{
	static private final int _PASSWORD_TRIES = 5;

	protected PrintWriter _out;
	protected PrintWriter _err;
	protected BufferedReader _in;

	private String _password = null;

	protected AbstractLoginHandler(PrintWriter out, PrintWriter err, BufferedReader in)
	{
		_out = out;
		_err = err;
		_in = in;
	}

	private void addEntriesFromFile(Collection<CertEntry> entries, InputStream storeInput, String storeType, String password)
		throws AuthZSecurityException, IOException
	{
		KeyStore specifiedKs = null;

		if (storeType == null) {
			// try PKCS12
			storeType = KeystoreLoginTool.PKCS12;
		}

		if (password != null) {
			_password = password;
		}

		KeyStore.Builder builder =
			new InputStreamBuilder(storeType, null, storeInput, new KeyStore.CallbackHandlerProtection(this),
				AccessController.getContext());
		try {
			specifiedKs = builder.getKeyStore();
		} catch (KeyStoreException e) {
			throw new AuthZSecurityException("keystore issue: " + e.getLocalizedMessage(), e);
		}

		if (specifiedKs != null) {
			try {
				Enumeration<String> aliases = specifiedKs.aliases();
				while (aliases.hasMoreElements()) {
					String alias = aliases.nextElement();
					Certificate[] aliasCertChain = specifiedKs.getCertificateChain(alias);
					if (aliasCertChain == null)
						continue;

					entries.add(new CertEntry(aliasCertChain, alias, specifiedKs));
				}
			} catch (KeyStoreException e) {
				throw new AuthZSecurityException("keystore issue: " + e.getLocalizedMessage(), e);
			}
		}
	}

	private void addEntriesFromWindows(Collection<CertEntry> entries)
	{
		if (OperatingSystemType.isWindows()) {
			WinX509KeyManager km = new WinX509KeyManager();
			String[] aliases = km.getClientAliases(null, null);
			for (String alias : aliases) {
				PrivateKey privateKey = km.getPrivateKey(alias);
				String friendlyName = km.getFriendlyName(alias);
				if (privateKey != null) {
					X509Certificate[] aliasCertChain = km.getCertificateChain(alias);
					if (aliasCertChain != null) {
						entries.add(new CertEntry(aliasCertChain, alias, privateKey, friendlyName));
					}
				}
			}
		}
	}

	protected Collection<CertEntry> retrieveCertEntries(InputStream storeInput, String storeType, String password)
		throws AuthZSecurityException, IOException
	{
		ArrayList<CertEntry> list = new ArrayList<CertEntry>();

		if ((storeType != null) && storeType.equals(KeystoreLoginTool.WINDOWS)) {
			addEntriesFromWindows(list);
		} else {
			addEntriesFromFile(list, storeInput, storeType, password);
		}

		return list;
	}

	public CertEntry selectCert(InputStream storeInput, String storeType, String password, boolean isAliasPattern,
		String entryPattern) throws AuthZSecurityException, IOException
	{

		Collection<CertEntry> entries = retrieveCertEntries(storeInput, storeType, password);
		CertEntry entry = null;

		if (entryPattern != null) {
			int flags = 0;
			if (isAliasPattern)
				flags = Pattern.CASE_INSENSITIVE;
			Pattern p = Pattern.compile("^.*" + Pattern.quote(entryPattern) + ".*$", flags);

			int numMatched = 0;
			CertEntry selectedEntry = null;
			for (CertEntry iter : entries) {
				String toMatch = null;
				if (!isAliasPattern)
					toMatch = ((X509Certificate) (iter._certChain[0])).getSubjectDN().getName();
				else
					toMatch = iter._alias;

				Matcher matcher = p.matcher(toMatch);
				if (matcher.matches()) {
					selectedEntry = iter;
					numMatched++;
				}
			}

			if (numMatched == 0) {
				throw new IOException("No certificates matched the pattern \"" + entryPattern + "\".");
			} else if (numMatched > 1) {
				throw new IOException("Multiple certificates matched the pattern \"" + entryPattern + "\".");
			} else {
				entry = selectedEntry;
			}
		} else {
			if (entries.size() == 1)
				entry = entries.iterator().next();
			else
				entry = selectCert(entries);
		}

		if (entry == null)
			return null;

		if (entry._privateKey == null) {
			String passwordChars = "";

			for (int tryNumber = 0; tryNumber < _PASSWORD_TRIES; tryNumber++) {
				try {
					entry._privateKey = (PrivateKey) entry._keyStore.getKey(entry._alias, passwordChars.toCharArray());
					break;
				} catch (KeyStoreException e) {
					throw new AuthZSecurityException("keystore issue: " + e.getLocalizedMessage(), e);
				} catch (NoSuchAlgorithmException e) {
					throw new AuthZSecurityException("missing keystore algorithm: " + e.getLocalizedMessage(), e);
				} catch (UnrecoverableKeyException uke) {
					if (_password != null) {
						passwordChars = _password;
						_password = null;
					} else {
						passwordChars =
							new String(getPassword("Key Password", "Enter key password for \""
								+ entry._certChain[0].getSubjectDN().getName() + "\"."));
					}
				}
			}
		}

		_password = null;
		return entry;
	}

	private void handle(PasswordCallback callback)
	{
		if (_password != null) {
			callback.setPassword(_password.toCharArray());
		} else {
			_password = new String(getPassword("Password Entry", callback.getPrompt() + ": "));
			callback.setPassword(_password.toCharArray());
		}
	}

	@Override
	public void handle(Callback[] callbacks)
	{
		for (Callback cb : callbacks) {
			if (cb instanceof PasswordCallback) {
				handle((PasswordCallback) cb);
			} else {
				_err.println("Can't handle security callback of type \"" + cb.getClass().getName() + "\".");
			}
		}
	}

	public abstract char[] getPassword(String title, String prompt);

	protected abstract CertEntry selectCert(Collection<CertEntry> entries);
}