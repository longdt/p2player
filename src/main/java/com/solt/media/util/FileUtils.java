package com.solt.media.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.solt.libtorrent.FileEntry;

public class FileUtils {
	private static final Set<String> mediaExts = new HashSet<String>();

	static {
		String[] extensions = new String[] {"mp3", "mp4", "ogv", "flv", "mov", "mkv", "avi", "asf", "wmv", "divx"};
		mediaExts.addAll(Arrays.asList(extensions));
	}
	
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
	
	public static boolean copyFile(File source, File target) {
		try {
			return copyFile(new FileInputStream(source), new FileOutputStream(target));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean copyFile(InputStream is, File target) {
		try {
			return copyFile(is, new FileOutputStream(target));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean copyFile(URL file, File target) {
		try {
			return FileUtils.copyFile(file.openStream(), target);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean copyFile(InputStream is, OutputStream os) {
		BufferedInputStream in = null;
		BufferedOutputStream out = null;
		try {
			in = new BufferedInputStream(is);
			out = new BufferedOutputStream(os);
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
	
	public static String getMD5Hash(File file) {
		try {
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			return hash(md5, file);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static String getSHA1Hash(File file) {
		try {
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			return hash(sha1, file);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String getHash(String hasher, File file) {
		try {
			MessageDigest digest = MessageDigest.getInstance(hasher);
			return hash(digest, file);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static String hash(MessageDigest digest, File file) {
		BufferedInputStream in = null;
		try {
			in = new BufferedInputStream(new FileInputStream(file));
			byte[] buffer = new byte[1024];
			int length = 0;
			while ((length = in.read(buffer)) != -1) {
				digest.update(buffer, 0, length);
			}
		} catch (IOException e) {
			return null;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return StringUtils.byteToHexString(digest.digest());
	}

	/**
	 * @param inputStream
	 * @param raf
	 */
	public static boolean copyFile(InputStream is, RandomAccessFile raf) {
		BufferedInputStream in = null;
		try {
			in = new BufferedInputStream(is);
			byte[] buffer = new byte[1024];
			int length = 0;
			while ((length = in.read(buffer)) != -1) {
				raf.write(buffer, 0, length);
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
			if (raf != null) {
				try {
					raf.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return true;
	}

	public static void writeFile(File file, String content) {
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(file);
			writer.print(content);
		} catch (IOException e) {
		} finally {
			if (writer != null) {
				writer.close();
			}
		}
	}

	public static String getStringContent(File file) {
		try {
			return getStringContent(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static String getStringContent(InputStream is) {
		BufferedReader in = null;
		StringBuilder result = new StringBuilder();
		try {
			in = new BufferedReader(new InputStreamReader(is));
			String line = null;
			while ((line = in.readLine()) != null) {
				result.append(line).append('\n');
			}
			if (result.length() > 0) {
				result.deleteCharAt(result.length() - 1);
			}
			return result.toString();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}
	
	public static boolean isStreamable(FileEntry entry) {
		int index = entry.getPath().lastIndexOf('.');
		if (index != -1) {
			String extension = entry.getPath().substring(index + 1).toLowerCase();
			return mediaExts.contains(extension);
		}
		return false;
	}
}
