/**
 * 
 */
package com.solt.media.util;

import java.io.File;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author ThienLong
 *
 */
public class MultipartDownloader {
	private int numPart;
	private ExecutorService executor;
	/**
	 * 
	 */
	public MultipartDownloader(int numPart) {
		this.numPart = numPart;
		executor = Executors.newFixedThreadPool(numPart);
	}
	
	public void download(URL file, File target) {
		
	}
	
	public void shutdown() {
		
	}
}
