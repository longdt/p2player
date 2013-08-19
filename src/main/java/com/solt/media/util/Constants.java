/*
 * Created on 16 juin 2003
 * Copyright (C) 2003, 2004, 2005, 2006 Aelitis, All Rights Reserved.
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
 *
 */
package com.solt.media.util;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * 
 * @author Olivier
 * 
 */

public class Constants {

	public static String VERSION = "1.0.BETA3";
	public static long TRANSFER_BYTES_PAYED_THRESHOLD = 52428800;
	public static long DOWNLOAD_BYTES_PAYED_THRESHOLD = 62914560;
	public static String UPDATE_URL = "http://sharephim.vn/api/update.json";
	public static final String DOWN_TORRENT_LINK = "http://sharephim.vn/api/movie/";
	public static final String DOWN_SUB_LINK = "http://sharephim.vn/api/sub/";
	public static String APP_NAME = "MediaPlayer";
	public static String PROTOCOL = "mdp";
	
	public static final String TORRENT_FILE_EXTENSION = ".tor";
	
	public static final String MAGNET_FILE_EXTENSION = ".mag";

	public static final String OSName = System.getProperty("os.name");

	public static final boolean isOSX = OSName.toLowerCase().startsWith(
			"mac os");
	public static final boolean isLinux = OSName.equalsIgnoreCase("Linux");
	public static final boolean isSolaris = OSName.equalsIgnoreCase("SunOS");
	public static final boolean isFreeBSD = OSName.equalsIgnoreCase("FreeBSD");
	public static final boolean isWindowsXP = OSName
			.equalsIgnoreCase("Windows XP");
	public static final boolean isWindows95 = OSName
			.equalsIgnoreCase("Windows 95");
	public static final boolean isWindows98 = OSName
			.equalsIgnoreCase("Windows 98");
	public static final boolean isWindows2000 = OSName
			.equalsIgnoreCase("Windows 2000");
	public static final boolean isWindowsME = OSName
			.equalsIgnoreCase("Windows ME");
	public static final boolean isWindows9598ME = isWindows95 || isWindows98
			|| isWindowsME;

	public static final boolean isWindows = OSName.toLowerCase().startsWith(
			"windows");
	// If it isn't windows or osx, it's most likely an unix flavor
	public static final boolean isUnix = !isWindows && !isOSX;

	public static final boolean isWindowsVista;
	public static final boolean isWindowsVistaSP2OrHigher;
	public static final boolean isWindowsVistaOrHigher;
	public static final boolean isWindows7OrHigher;

	// Common Patterns
	public static Pattern PAT_SPLIT_COMMAWORDS = Pattern.compile("\\s*,\\s*");
	public static Pattern PAT_SPLIT_COMMA = Pattern.compile(",");
	public static Pattern PAT_SPLIT_DOT = Pattern.compile("\\.");
	public static Pattern PAT_SPLIT_SPACE = Pattern.compile(" ");
	public static Pattern PAT_SPLIT_SLASH_N = Pattern.compile("\n");

	static {

		if (isWindows) {

			Float ver = null;

			try {
				ver = new Float(System.getProperty("os.version"));

			} catch (Throwable e) {
			}

			boolean vista_sp2_or_higher = false;

			if (ver == null) {

				isWindowsVista = false;
				isWindowsVistaOrHigher = false;
				isWindows7OrHigher = false;

			} else {
				float f_ver = ver.floatValue();

				isWindowsVista = f_ver == 6;
				isWindowsVistaOrHigher = f_ver >= 6;
				isWindows7OrHigher = f_ver >= 6.1f;

				if (isWindowsVista) {

					LineNumberReader lnr = null;

					try {
						Process p = Runtime
								.getRuntime()
								.exec(new String[] {
										"reg",
										"query",
										"HKLM\\Software\\Microsoft\\Windows NT\\CurrentVersion",
										"/v", "CSDVersion" });

						lnr = new LineNumberReader(new InputStreamReader(
								p.getInputStream()));

						while (true) {

							String line = lnr.readLine();

							if (line == null) {

								break;
							}

							if (line.matches(".*CSDVersion.*")) {

								vista_sp2_or_higher = line
										.matches(".*Service Pack [2-9]");

								break;
							}
						}
					} catch (Throwable e) {

					} finally {

						if (lnr != null) {

							try {
								lnr.close();

							} catch (Throwable e) {
							}
						}
					}
				}
			}

			isWindowsVistaSP2OrHigher = vista_sp2_or_higher;
		} else {

			isWindowsVista = false;
			isWindowsVistaSP2OrHigher = false;
			isWindowsVistaOrHigher = false;
			isWindows7OrHigher = false;
		}
	}

	public static final boolean isOSX_10_5_OrHigher;
	public static final boolean isOSX_10_6_OrHigher;
	public static final boolean isOSX_10_7_OrHigher;

	static {
		if (isOSX) {

			int first_digit = 0;
			int second_digit = 0;

			try {
				String os_version = System.getProperty("os.version");

				String[] bits = os_version.split("\\.");

				first_digit = Integer.parseInt(bits[0]);

				if (bits.length > 1) {

					second_digit = Integer.parseInt(bits[1]);
				}
			} catch (Throwable e) {

			}

			isOSX_10_5_OrHigher = first_digit > 10
					|| (first_digit == 10 && second_digit >= 5);
			isOSX_10_6_OrHigher = first_digit > 10
					|| (first_digit == 10 && second_digit >= 6);
			isOSX_10_7_OrHigher = first_digit > 10
					|| (first_digit == 10 && second_digit >= 7);

		} else {

			isOSX_10_5_OrHigher = false;
			isOSX_10_6_OrHigher = false;
			isOSX_10_7_OrHigher = false;

		}
	}

	public static final String JAVA_VERSION = System
			.getProperty("java.version");

	public static final String FILE_WILDCARD = isWindows ? "*.*" : "*";

	public static final String DOWNLOAD_DIRECTORY = ".mediacache";

}
