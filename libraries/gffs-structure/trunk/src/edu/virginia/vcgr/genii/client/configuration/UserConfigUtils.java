package edu.virginia.vcgr.genii.client.configuration;

import java.io.File;
import java.io.IOException;

import edu.virginia.vcgr.genii.client.cmd.tools.GetUserDir;

public class UserConfigUtils {
	static private final String _USER_CONFIG_FILE_NAME = "user-config.xml";

	static public String getUserConfigFilePath() throws IOException {
		return GetUserDir.getUserDir() + "/" + _USER_CONFIG_FILE_NAME;
	}

	/**
	 * Stores information about current user's config.
	 * 
	 * @param UserConfig
	 *            userConfig Current user config info to set. Null means none
	 *            (delete current value).
	 * @return
	 */
	static public void setCurrentUserConfig(UserConfig userConfig)
			throws IOException {
		String userConfigFilePath = getUserConfigFilePath();
		File userConfigFile = new File(userConfigFilePath);
		if (userConfigFile.exists())
			userConfigFile.delete();

		if (userConfig != null) {
			userConfig.store(userConfigFile);
		}
	}

	/**
	 * Retrieves user's current configuration info.
	 * 
	 * @return UserConfig Structure describing user's current config info.
	 */
	static public UserConfig getCurrentUserConfig() throws IOException {
		String userConfigFilePath = getUserConfigFilePath();
		File userConfigFile = new File(userConfigFilePath);
		if (!userConfigFile.exists())
			return null;
		return new UserConfig(new File(userConfigFilePath));
	}

	static public void reloadConfiguration() {
		Installation.reload();

		synchronized (ConfigurationManager.class) {
			ConfigurationManager manager = ConfigurationManager
					.getCurrentConfiguration();
			if (manager != null) {
				ConfigurationManager.reloadConfiguration();
			}
		}
	}

}
