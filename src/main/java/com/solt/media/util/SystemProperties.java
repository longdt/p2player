/*
 * Created on Feb 27, 2004
 * Created by Alon Rohter
 * Copyright (C) 2004, 2005, 2006 Alon Rohter, All Rights Reserved.
 * 
 * Copyright (C) 2004, 2005, 2006 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */
package com.solt.media.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * Utility class to manage system-dependant information.
 */
public class SystemProperties {
	private static final Logger logger = Logger
			.getLogger(SystemProperties.class);

	// note this is also used in the restart code....

	/**
	 * Path separator charactor.
	 */
	public static final String SEP = System.getProperty("file.separator");


	// TODO: fix for non-SWT entry points one day
	private static String APPLICATION_ENTRY_POINT = "com.solt.media.ui.Main";

	private static final String WIN_DEFAULT = "Application Data";
	private static final String OSX_DEFAULT = "Library" + SEP
			+ "Application Support";
	private static final String TORRENTS_DIRECTORY = "torrents";
	private static volatile String metadataPath;
	private static volatile String app_path;
	private static volatile File torrentsDir;

	public static void setApplicationEntryPoint(String entry_point) {
		if (entry_point != null && entry_point.trim().length() > 0) {

			APPLICATION_ENTRY_POINT = entry_point.trim();
		}
	}

	public static String getApplicationEntryPoint() {
		return (APPLICATION_ENTRY_POINT);
	}

	/**
	 * This is used by third-party apps that want explicit control over the
	 * user-path
	 * 
	 * @param _path
	 */

	public static void setMetaDataPath(String _path) {
		metadataPath = _path;
	}

	/**
	 * Returns the full path to the user's home MediaPlayer directory. Under
	 * unix, this is usually ~/.mediaplayer/ Under Windows, this is usually
	 * .../Documents and Settings/username/Application Data/MediaPlayer/ Under
	 * OSX, this is usually /Users/username/Library/Application
	 * Support/MediaPlayer/
	 */
	public static String getMetaDataPath() {
		if (metadataPath != null) {
			return metadataPath;
		}

		// WATCH OUT!!!! possible recursion here if logging is changed so that
		// it messes with
		// config initialisation - that's why we don't assign the user_path
		// variable until it
		// is complete - an earlier bug resulted in us half-assigning it and
		// using it due to
		// recursion. At least with this approach we'll get (worst case) stack
		// overflow if
		// a similar change is made, and we'll spot it!!!!

		// Super Override -- no AZ_DIR or xxx_DEFAULT added at all.

		String tempPath = null;

		try {
			// If platform failed, try some hackery
			String userhome = System.getProperty("user.home");

			if (Constants.isWindows) {
				tempPath = getEnvironmentalVariable("APPDATA");

				if (tempPath != null && tempPath.length() > 0) {
					logger.debug("Using user config path from APPDATA env var instead: "
							+ tempPath);
				} else {
					tempPath = userhome + SEP + WIN_DEFAULT;
					logger.debug("Using user config path from java user.home var instead: "
							+ tempPath);
				}

				tempPath = tempPath + SEP + Constants.APP_NAME + SEP;

				logger.debug("SystemProperties::getUserPath(Win): user_path = "
						+ tempPath);

			} else if (Constants.isOSX) {
				tempPath = userhome + SEP + OSX_DEFAULT + SEP
						+ Constants.APP_NAME + SEP;

				logger.debug("SystemProperties::getUserPath(Mac): user_path = "
						+ tempPath);

			} else {
				// unix type
				tempPath = userhome + SEP + "."
						+ Constants.APP_NAME.toLowerCase() + SEP;

				logger.debug("SystemProperties::getUserPath(Unix): user_path = "
						+ tempPath);
			}

			// if the directory doesn't already exist, create it
			File dir = new File(tempPath);
			if (!dir.exists()) {
				FileUtils.mkdirs(dir);
			}

			return tempPath;
		} finally {

			metadataPath = tempPath;
		}
	}

	public static File getTorrentsDir() {
		if (torrentsDir == null) {
			torrentsDir = new File(getMetaDataPath() + TORRENTS_DIRECTORY);
			if (!torrentsDir.isDirectory()) {
				torrentsDir.mkdir();
			}
		}
		return torrentsDir;
	}

	/**
	 * Returns the full path to the directory where Azureus is installed and
	 * running from.
	 */
	public static String getApplicationPath() {
		if (app_path != null) {

			return (app_path);
		}

		String temp_app_path = System.getProperty("azureus.install.path",
				System.getProperty("user.dir"));

		if (!temp_app_path.endsWith(SEP)) {

			temp_app_path += SEP;
		}

		app_path = temp_app_path;

		return (app_path);
	}

	/**
	 * Will attempt to retrieve an OS-specific environmental var.
	 */

	public static String getEnvironmentalVariable(final String _var) {

		// this approach doesn't work at all on Windows 95/98/ME - it just hangs
		// so get the hell outta here!

		if (Constants.isWindows9598ME) {

			return ("");
		}

		// getenv reinstated in 1.5 - try using it

		String res = System.getenv(_var);

		if (res != null) {

			return (res);
		}

		Properties envVars = new Properties();
		BufferedReader br = null;

		try {

			Process p = null;
			Runtime r = Runtime.getRuntime();

			if (Constants.isWindows) {
				p = r.exec("cmd.exe /c set");
			} else { // we assume unix
				p = r.exec("env");
			}

			String system_encoding = System.getProperty("file.encoding");

			logger.debug("SystemProperties::getEnvironmentalVariable - " + _var
					+ ", system encoding = " + system_encoding);

			br = new BufferedReader(new InputStreamReader(p.getInputStream(),
					system_encoding), 8192);
			String line;
			while ((line = br.readLine()) != null) {
				int idx = line.indexOf('=');
				if (idx >= 0) {
					String key = line.substring(0, idx);
					String value = line.substring(idx + 1);
					envVars.setProperty(key, value);
				}
			}
			br.close();
		} catch (Throwable t) {
			if (br != null)
				try {
					br.close();
				} catch (Exception ingore) {
				}
		}

		return envVars.getProperty(_var, "");
	}

	public static String getAzureusJarPath() {
		String str = getApplicationPath();

		if (Constants.isOSX) {

			str += Constants.APP_NAME + ".app/Contents/Resources/Java/";
		}

		return (str + "Azureus2.jar");
	}
}
