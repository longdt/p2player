package com.solt.media.update;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.simple.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.solt.media.update.UpdateChecker.ErrorCode;

public class SetupUpdaterTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void testUpdate() throws InterruptedException, IOException {
		URL updateUrl = new URL("http://sharephim.vn/api/update.json");
		JSONObject content = (JSONObject) UpdateChecker.parseJSON(updateUrl);
		Updater updater = new SetupUpdater(null, updateUrl, content, new UpdateListener() {
			
			@Override
			public boolean newVersionAvairable() {
				System.out.println("new version");
				return true;
			}
			
			@Override
			public void downloadProgress(String fileName, int percent) {
				System.out.println(fileName + ": " + percent);
			}
			
			@Override
			public void downloadFailed(ErrorCode error) {
				System.out.println("download error");
			}
			
			@Override
			public boolean downloadCompleted() {
				System.out.println("download done");
				return false;
			}
		});
		updater.update();
	}

}
