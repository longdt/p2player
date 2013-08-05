/**
 * 
 */
package com.solt.media.update;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.StringTokenizer;

import org.eclipse.swt.program.Program;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.solt.media.ui.Main;
import com.solt.media.util.Constants;
import com.solt.media.util.Downloader;
import com.solt.media.util.FileUtils;
import com.solt.media.util.MultipartDownloader;
import com.solt.media.util.SingleDownloader;

/**
 * @author ThienLong
 *
 */
public class UpdateChecker implements Runnable {
	private static final int INITIAL = 0;
	private static final int COMPLETE = 1;
	private static final int INTERVAL = 10 * 60000;
	private static final String VERSION_FIELD = "version";
	private static final String HASHER_FIELD = "hasher";
	private static final String LINK_FIELD = "link";
	private static final String CHECKSUM_FIELD = "checksum";
	private static final int NUM_PART = 4;
	private Thread checker;
	private UpdateListener listener;
	/**
	 * 
	 */
	public UpdateChecker(UpdateListener listener) {
		checker = new Thread(this, "UpdateChecker");
		this.listener = listener;
	}
	
	public void start() {
		checker.start();
	}
	
	
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		int state = INITIAL;
		Downloader downloader = null;
		File target = new File("setup.exe");
		File temp = new File ("setup.exe.part");
		try {
			Thread.sleep(10000);
			URL updateUrl = new URL(Constants.UPDATE_URL);
			JSONObject content = null;
			String version = null;
			String hasher = null;
			String hashFile = null;
			String link = null;
			String checksum = null;
			do {
				content = (JSONObject) parseJSON(updateUrl);
				if (content == null) {
					Thread.sleep(INTERVAL);
					continue;
				}
				version = (String) content.get(VERSION_FIELD);
				hasher = (String) content.get(HASHER_FIELD);
				boolean newVersion = version != null && compareVersions(version, Constants.VERSION) > 0;
				if (newVersion && listener != null && listener.newVersionAvairable()) {
					link = (String) content.get(LINK_FIELD);
					if (downloader == null) {
						downloader = new SingleDownloader();
					}
					URL file = new URL(updateUrl, link);
					
					if (!downloader.download(file, temp)) {
						listener.downloadFailed(ErrorCode.NETWORK_ERROR);
						Thread.sleep(INTERVAL);
						continue;
					}
					checksum =  (String) content.get(CHECKSUM_FIELD);
					if (checksum == null || ((hashFile = FileUtils.getHash(hasher, temp)) != null && checksum.equalsIgnoreCase(hashFile))) {
						temp.renameTo(target);
						state = COMPLETE;
						break;
					}
					listener.downloadFailed(ErrorCode.CORRUPT_DATA);
				}
				Thread.sleep(INTERVAL);
			} while (true);
		} catch (InterruptedException e) {
			
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} finally {
			if (downloader != null) {
				downloader.shutdown();
			}
		}
		if (state == COMPLETE && listener != null) {
			listener.downloadCompleted(target);
		}
	}
	
	private Object parseJSON(URL url) {
		Reader reader = null;
		try {
			reader = new InputStreamReader(url.openStream());
			return JSONValue.parse(reader);
		} catch (IOException e) {
			
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}
		return null;
	}
	
	public void stop() throws InterruptedException {
		checker.interrupt();
		checker.join();
	}
	
	/**
	 * Gets the current version, or if a CVS version, the one on which it is
	 * based
	 * 
	 * @return
	 */

	public static String getBaseVersion() {
		return (getBaseVersion(Constants.VERSION));
	}

	public static String getBaseVersion(String version) {
		int p1 = version.indexOf("_"); // _CVS or _Bnn

		if (p1 == -1) {

			return (version);
		}

		return (version.substring(0, p1));
	}

	/**
	 * compare two version strings of form n.n.n.n (e.g. 1.2.3.4)
	 * 
	 * @param version_1
	 * @param version_2
	 * @return -ve -> version_1 lower, 0 = same, +ve -> version_1 higher
	 */

	public static int compareVersions(String version_1, String version_2) {
		try {
			if (version_1.startsWith(".")) {
				version_1 = "0" + version_1;
			}
			if (version_2.startsWith(".")) {
				version_2 = "0" + version_2;
			}

			version_1 = version_1.replaceAll("[^0-9.]", ".");
			version_2 = version_2.replaceAll("[^0-9.]", ".");

			StringTokenizer tok1 = new StringTokenizer(version_1, ".");
			StringTokenizer tok2 = new StringTokenizer(version_2, ".");

			while (true) {
				if (tok1.hasMoreTokens() && tok2.hasMoreTokens()) {

					int i1 = Integer.parseInt(tok1.nextToken());
					int i2 = Integer.parseInt(tok2.nextToken());

					if (i1 != i2) {

						return (i1 - i2);
					}
				} else if (tok1.hasMoreTokens()) {
					int i1 = Integer.parseInt(tok1.nextToken());
					if (i1 != 0) {
						return (1);
					}
				} else if (tok2.hasMoreTokens()) {
					int i2 = Integer.parseInt(tok2.nextToken());
					if (i2 != 0) {
						return (-1);
					}
				} else {
					return (0);
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
			return (0);
		}
	}

	public static boolean isValidVersionFormat(String version) {
		if (version == null || version.length() == 0) {
			return (false);
		}
		for (int i = 0; i < version.length(); i++) {
			char c = version.charAt(i);
			if (!(Character.isDigit(c) || c == '.')) {
				return (false);
			}
		}

		if (version.startsWith(".") || version.endsWith(".")
				|| version.indexOf("..") != -1) {
			return (false);
		}
		return (true);
	}
	
	public static enum ErrorCode {
		NETWORK_ERROR, CORRUPT_DATA
	}

}
