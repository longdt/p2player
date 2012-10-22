/**
 * 
 */
package com.solt.media.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
	public boolean download(URL file, File target) {
		long fileLen = getContentLength(file);
		long partSize = fileLen / numPart;
		long start = 0;
		long end = partSize - 1;
		List<PartDownloader> tasks = new ArrayList<PartDownloader>();
		for (int i = 0; i < numPart; ++i) {
			tasks.add(new PartDownloader(file, target, start, end));
			start = end + 1;
			end = (i != numPart - 2) ? end + partSize : fileLen - 1;
		}
		try {
			List<Future<Boolean>> results = executor.invokeAll(tasks);
			for (Future<Boolean> r : results) {
				if (!r.get()) {
					return false;
				}
			}
			return true;
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public long getContentLength(URL file) {
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) file.openConnection();
			conn.setRequestMethod("HEAD");
			return conn.getContentLength();
		} catch (Exception e) {
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
		return -1;
	}
	
	/* (non-Javadoc)
	 * @see com.solt.media.util.Downloader#shutdown()
	 */
	@Override
	public void shutdown() {
		executor.shutdownNow();
		try {
			executor.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

class PartDownloader implements Callable<Boolean> {
	private long startBytes;
	private long endBytes;
	private URL remoteFile;
	private File target;
	public PartDownloader(URL remoteFile, File target, long startBytes, long endBytes) {
		this.remoteFile = remoteFile;
		this.startBytes = startBytes;
		this.endBytes = endBytes;
		this.target = target;
	}

	/* (non-Javadoc)
	 * @see java.util.concurrent.Callable#call()
	 */
	@Override
	public Boolean call() throws Exception {
		HttpURLConnection conn = null;
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(target, "rw");
			raf.seek(startBytes);
			conn = (HttpURLConnection) remoteFile.openConnection();
			conn.setRequestProperty("Range", "bytes=" + startBytes + "-" + endBytes);
			conn.connect();
			return FileUtils.copyFile(conn.getInputStream(), raf);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
		return false;
	}
	
}
