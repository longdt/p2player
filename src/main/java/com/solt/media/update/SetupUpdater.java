package com.solt.media.update;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.eclipse.swt.program.Program;
import org.json.simple.JSONObject;

import com.solt.media.ui.MediaPlayer;
import com.solt.media.update.UpdateChecker.ErrorCode;
import com.solt.media.util.DownloadListener;
import com.solt.media.util.Downloader;
import com.solt.media.util.FileUtils;
import com.solt.media.util.MultipartDownloader;

public class SetupUpdater implements Updater {
	private URL updateUrl;
	private String hasher;
	private String link;
	private String checksum;
	private Downloader downloader;
	private UpdateListener listener;
	private MediaPlayer appPlayer;
	private File target = new File(UPDATE_FOLDER, "setup.exe");
	
	static {
		File updateFolder = new File(UPDATE_FOLDER);
		if (!updateFolder.isDirectory()) {
			updateFolder.mkdir();
		}
	}
	
	public SetupUpdater(MediaPlayer appPlayer, URL updateUrl, JSONObject content, UpdateListener listener) {
		this.appPlayer = appPlayer;
		hasher = (String) content.get(UpdateChecker.HASHER_FIELD);
		link = (String) content.get(UpdateChecker.LINK_FIELD);
		checksum =  (String) content.get(UpdateChecker.CHECKSUM_FIELD);
		this.updateUrl = updateUrl;
		this.listener = listener;
		downloader = new MultipartDownloader(4);
		downloader.setDownloadListener(new DownloadListener() {
			private String fileName;
			
			@Override
			public void onStart(URL url, File save) {
				fileName = save.getName();
			}
			
			@Override
			public void onProgress(long downloaded, long total) {
				SetupUpdater.this.listener.downloadProgress(fileName, (int)(downloaded * 1000 / total));
			}
			
			@Override
			public void onFailed(URL url, File save, Throwable e) {
				SetupUpdater.this.listener.downloadFailed(ErrorCode.NETWORK_ERROR);
			}
			
			@Override
			public void onCompleted(URL url, File save) {
				SetupUpdater.this.listener.downloadProgress(fileName, 1000);
			}
		});
	}

	@Override
	public boolean update() throws InterruptedException, IOException {
		try {
			if (target.isFile() && UpdateChecker.verify(target, hasher, checksum)) {
				if(listener.downloadCompleted()) {
					performUpdate();
				};
				return true;
			}
			URL file = new URL(updateUrl, link);
			File temp = new File(UPDATE_FOLDER, "setup.exe.part");
			if (!downloader.download(file, temp)) {
				listener.downloadFailed(ErrorCode.NETWORK_ERROR);
				return false;
			}
			if (UpdateChecker.verify(temp, hasher, checksum)) {
				Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
				if(listener.downloadCompleted()) {
					performUpdate();
				};
				return true;
			}
			listener.downloadFailed(ErrorCode.CORRUPT_DATA);
			return false;
		} finally {
			if (downloader != null) {
				downloader.shutdown();
			}
		}
	}
	
	private void performUpdate() {
		Program.launch(target.getAbsolutePath());
		appPlayer.requestShutdown();
	}
}
