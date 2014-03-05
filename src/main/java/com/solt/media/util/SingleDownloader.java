package com.solt.media.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public class SingleDownloader implements Downloader {
	private DownloadListener listener;
	@Override
	public boolean download(URL file, File target) throws InterruptedException {
		if (listener != null) {
			listener.onStart(file, target);
		}
		BufferedInputStream in = null;
		BufferedOutputStream out = null;
		URLConnection conn = null;
		long total = 0;
		long downloaded = 0;
		try {
			conn = file.openConnection();
			total = conn.getContentLengthLong();
			in = new BufferedInputStream(conn.getInputStream());
			out = new BufferedOutputStream(new FileOutputStream(target));
			byte[] buffer = new byte[8192];
			int length = 0;
			if (listener != null) {
				listener.onProgress(downloaded, total);
			}
			while ((length = in.read(buffer)) != -1) {
				if (Thread.currentThread().isInterrupted()) {
					throw new InterruptedException();
				}
				out.write(buffer, 0, length);
				downloaded += length;
				if (listener != null) {
					listener.onProgress(downloaded, total);
				}
			}
			if (listener != null) {
				listener.onCompleted(file, target);
			}
			return true;
		} catch (IOException e) {
			if (listener != null) {
				listener.onFailed(file, target, e);
			}
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
		return false;
	}

	@Override
	public void shutdown() {

	}

	@Override
	public void setDownloadListener(DownloadListener listener) {
		this.listener = listener;
	}

}
