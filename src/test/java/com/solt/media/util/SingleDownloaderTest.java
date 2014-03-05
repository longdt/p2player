package com.solt.media.util;

import static org.junit.Assert.*;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class SingleDownloaderTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void testDownload() throws MalformedURLException {
		Downloader downloader = new SingleDownloader();
		downloader.setDownloadListener(new DownloadListener() {
			
			@Override
			public void onStart(URL url, File save) {
				System.out.println("start download: " + url + " save in file: "  + save);
			}
			
			@Override
			public void onProgress(long downloaded, long total) {
				System.out.println("progress: " + downloaded + "/" + total);
			}
			
			@Override
			public void onFailed(URL url, File save, Throwable e) {
				System.out.println("failed: ");
				e.printStackTrace(System.out);
			}
			
			@Override
			public void onCompleted(URL url, File save) {
				System.out.println("Done");
			}
		});
		downloader.download(new URL("http://sharephim.vn/api/movie/259"), new File("abc.torrent"));
	}

}
