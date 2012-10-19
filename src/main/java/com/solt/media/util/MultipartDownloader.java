/**
 * 
 */
package com.solt.media.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author ThienLong
 *
 */
public class MultipartDownloader implements Downloader {
	private int numPart;
	private ExecutorService executor;
	/**
	 * 
	 */
	public MultipartDownloader(int numPart) {
		this.numPart = numPart;
		executor = Executors.newFixedThreadPool(numPart);
	}
	
	/* (non-Javadoc)
	 * @see com.solt.media.util.Downloader#download(java.net.URL, java.io.File)
	 */
	@Override
	public void download(URL file, File target) {
		try {
			FileUtils.copyFile(file.openStream(), target);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/* (non-Javadoc)
	 * @see com.solt.media.util.Downloader#shutdown()
	 */
	@Override
	public void shutdown() {
		executor.shutdownNow();
	}
}
