package com.solt.media.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class SingleDownloader implements Downloader {

	@Override
	public boolean download(URL file, File target) {
		try {
			return FileUtils.copyFile(file.openStream(), target);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public void shutdown() {

	}

}
