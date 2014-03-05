package com.solt.media.util;

import java.io.File;
import java.net.URL;

public interface Downloader {

	public abstract boolean download(URL file, File target) throws InterruptedException;

	public abstract void shutdown();
	
	public void setDownloadListener(DownloadListener listener);

}