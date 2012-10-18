/**
 * 
 */
package com.solt.media.update;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.solt.media.util.Constants;

/**
 * @author ThienLong
 *
 */
public class UpdateChecker implements Runnable {
	public static final int INITIAL = 0;
	private static final int INTERVAL = 10 * 60000;
	private static final String VERSION = "version";
	private static final String HASH_MD5 = "md5";
	private volatile int state;
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
		try {
			Thread.sleep(10000);
			URL updateUrl = new URL(Constants.UPDATE_URL);
			JSONObject content = null;
			do {
				content = (JSONObject) parseJSON(updateUrl);
				content.get(VERSION);
				Thread.sleep(INTERVAL);
			} while (true);
		} catch (InterruptedException e) {
			
		} catch (MalformedURLException e) {
			e.printStackTrace();
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
