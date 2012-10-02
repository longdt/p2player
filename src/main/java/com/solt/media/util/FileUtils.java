package com.solt.media.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileUtils {

	public static File makeDownloadDir() {
		if (Constants.isLinux || Constants.isOSX) {
			File downDir = new File(SystemProperties.getMetaDataPath() + File.separator + Constants.DOWNLOAD_DIRECTORY);
			if (!downDir.exists()) {
				downDir.mkdir();
			}
			return downDir;
		}
		List<File> roots = Arrays.asList(File.listRoots());
		Collections.sort(roots, new Comparator<File>() {

			@Override
			public int compare(File o1, File o2) {
				long free1 = o1.getFreeSpace();
				long free2 = o2.getFreeSpace();
				if (free1 > free2) {
					return -1;
				} else if (free1 == free2) {
					return 0;
				} else {
					return 1;
				}
			}
		});
		File result = null;
		for (File f : roots) {
			if (f.canWrite() && (result = makeHiddenDir(f)) != null) {
				return result;
			}
		}
		return null;
	}

	private static File makeHiddenDir(File dir) {
		File downDir = new File(dir, Constants.DOWNLOAD_DIRECTORY);
		try {
			if (!downDir.exists()) {
				if (downDir.mkdir()) {
					Process p = Runtime.getRuntime().exec(
							"attrib +h " + downDir.getAbsolutePath());
					p.waitFor();
					return downDir;
				} else {
					return null;
				}
			} else if (downDir.isHidden()) {
				Process p = Runtime.getRuntime().exec(
						"attrib +h " + downDir.getAbsolutePath());
				p.waitFor();
				return downDir;
			}
		} catch (Exception e) {
		}
		return null;
	}

	/**
	 * Makes Directories as long as the directory isn't directly in Volumes (OSX)
	 * @param f
	 * @return
	 */
	public static boolean mkdirs(File f) {
		if (Constants.isOSX) {
			Pattern pat = Pattern.compile("^(/Volumes/[^/]+)");
			Matcher matcher = pat.matcher(f.getParent());
			if (matcher.find()) {
				String sVolume = matcher.group();
				File fVolume = new File(sVolume);
				if (!fVolume.isDirectory()) {
					return false;
				}
			}
		}
		return f.mkdirs();
	}
	
	public static boolean copy(File source, File target) {
		BufferedInputStream in = null;
		BufferedOutputStream out = null;
		try {
			in = new BufferedInputStream(new FileInputStream(source));
			out = new BufferedOutputStream(new FileOutputStream(target));
			byte[] buffer = new byte[1024];
			int length = 0;
			while ((length = in.read(buffer)) != -1) {
				out.write(buffer, 0, length);
			}
		} catch (IOException e) {
			return false;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return true;
	}
}
