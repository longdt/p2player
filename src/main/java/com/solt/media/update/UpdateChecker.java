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

import org.eclipse.swt.program.Program;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.solt.media.util.Constants;
import com.solt.media.util.FileUtils;
import com.solt.media.util.MultipartDownloader;

/**
 * @author ThienLong
 *
 */
public class UpdateChecker implements Runnable {
	private static final int INITIAL = 0;
	private static final int COMPLETE = 1;
	private static final int INTERVAL = 10 * 60000;
	private static final String VERSION_FIELD = "version";
	private static final String MD5_FIELD = "md5";
	private static final int NUM_PART = 4;
	private Thread checker;
	/**
	 * 
	 */
	public UpdateChecker() {
		checker = new Thread(this, "UpdateChecker");
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
		MultipartDownloader downloader = null;
		File target = new File("setup.exe.part");
		try {
			Thread.sleep(10000);
			URL updateUrl = new URL(Constants.UPDATE_URL);
			JSONObject content = null;
			String version = null;
			String hash = null;
			String hashFile = null;
			do {
				content = (JSONObject) parseJSON(updateUrl);
				version = (String) content.get(VERSION_FIELD);
				hash = (String) content.get(MD5_FIELD);
				if (version != null && Constants.compareVersions(version, Constants.VERSION) > 0) {
					if (downloader == null) {
						downloader = new MultipartDownloader(NUM_PART);
					}
					URL file = new URL(updateUrl, "setup.exe");
					
					downloader.download(file, target);
					if (hash == null || ((hashFile = FileUtils.getMD5Hash(target)) != null && hash.equalsIgnoreCase(hashFile))) {
						target.renameTo(new File(target.getParentFile(), "setup.exe"));
						state = COMPLETE;
						break;
					}
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
		if (state == COMPLETE) {
			Program.launch(target.getAbsolutePath());
		}
	}
	
	private Object parseJSON(URL url) {
		Reader reader = null;
		try {
			reader = new InputStreamReader(url.openStream());
			return JSONValue.parse(reader);
		} catch (IOException e) {
			
		}
		return null;
	}
	
	public void stop() throws InterruptedException {
		checker.interrupt();
		checker.join();
	}

}
