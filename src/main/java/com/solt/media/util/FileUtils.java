package com.solt.media.util;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileUtils {

	public static File makeDownloadDir() {
		if (Constants.isLinux || Constants.isOSX) {
			File downDir = new File(SystemProperties.getUserPath() + File.separator + Constants.DOWNLOAD_DIRECTORY);
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

	public static void mkdirs(File dir) {
		// TODO Auto-generated method stub
		
	}
}
