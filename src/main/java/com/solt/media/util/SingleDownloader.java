package com.solt.media.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class SingleDownloader implements Downloader {

	@Override
	public void download(URL file, File target) {
		try {
			FileUtils.copyFile(file.openStream(), target);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void shutdown() {

	}

}
