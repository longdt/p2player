/**
 * 
 */
package com.solt.media.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ThienLong
 *
 */
public class MultipartDownloader implements Downloader {
	private DownloadListener listener;
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
		if (listener != null) {
			listener.onStart(file, target);
		}
		if (target.isFile() && !target.delete()) {
			if (listener != null) {
				listener.onFailed(file, target, new IOException("can't modify/delete " + target.getPath()));
			}
		}
		long fileLen = getContentLength(file);
		long partSize = fileLen / numPart;
		long start = 0;
		long end = partSize - 1;
		AtomicLong downloaded = new AtomicLong();
		List<Future<Void>> results = new ArrayList<Future<Void>>();
		if(listener != null) {
			listener.onProgress(0, fileLen);
		}
		for (int i = 0; i < numPart; ++i) {
			results.add(executor.submit(new PartDownloader(file, target, start, end, downloaded)));
			start = end + 1;
			end = (i != numPart - 2) ? end + partSize : fileLen - 1;
		}
		try {
			Iterator<Future<Void>> iter = results.iterator();
			Future<Void> r = null;
			while (iter.hasNext()) {
				if (r == null) {
					r = iter.next();
				}
				try {
					r.get(100, TimeUnit.MILLISECONDS);
					r = null;
				} catch (TimeoutException e) {
					if (listener != null) {
						listener.onProgress(downloaded.get(), fileLen);
					}
				}
			}
			if (listener != null) {
				listener.onCompleted(file, target);	
			}
			return true;
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			if (listener != null) {
				listener.onFailed(file, target, e);
			}
		}
		return false;
	}
	
	private long getContentLength(URL file) {
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) file.openConnection();
//			conn.setRequestMethod("HEAD");
			return conn.getContentLengthLong();
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

	@Override
	public void setDownloadListener(DownloadListener listener) {
		this.listener = listener;
	}
}

class PartDownloader implements Callable<Void> {
	private long startBytes;
	private long endBytes;
	private URL remoteFile;
	private File target;
	private AtomicLong downloaded;
	public PartDownloader(URL remoteFile, File target, long startBytes, long endBytes, AtomicLong downloaded) {
		this.remoteFile = remoteFile;
		this.startBytes = startBytes;
		this.endBytes = endBytes;
		this.target = target;
		this.downloaded = downloaded;
	}

	/* (non-Javadoc)
	 * @see java.util.concurrent.Callable#call()
	 */
	@Override
	public Void call() throws Exception {
		HttpURLConnection conn = null;
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(target, "rw");
			raf.seek(startBytes);
			conn = (HttpURLConnection) remoteFile.openConnection();
			conn.setRequestProperty("Range", "bytes=" + startBytes + "-" + endBytes);
			conn.connect();
			BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
			byte[] buffer = new byte[1024];
			int length = 0;
			while ((length = in.read(buffer)) != -1) {
				raf.write(buffer, 0, length);
				downloaded.addAndGet(length);
			}
			return null;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
			if (raf != null) {
				try {
					raf.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
}
