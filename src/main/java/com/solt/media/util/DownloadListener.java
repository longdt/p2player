package com.solt.media.util;

import java.io.File;
import java.net.URL;

public interface DownloadListener {

	public void onStart(URL url, File save);
	
	public void onProgress(long downloaded, long total);
	
	public void onCompleted(URL url, File save);
	
	public void onFailed(URL url, File save, Throwable e);
}
