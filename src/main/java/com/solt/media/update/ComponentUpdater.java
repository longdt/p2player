package com.solt.media.update;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.solt.media.ui.MediaPlayer;
import com.solt.media.update.UpdateChecker.ErrorCode;
import com.solt.media.util.Constants;
import com.solt.media.util.DownloadListener;
import com.solt.media.util.Downloader;
import com.solt.media.util.SingleDownloader;

public class ComponentUpdater implements Updater {
	private static final boolean DEBUG_MODE = false;
	public static final String LIB_FOLDER = "mediaplayer_lib";
	private static final File updateFolder;
	private static final File updateLib;
	private MediaPlayer appPlayer;
	private URL updateUrl;
	private UpdateListener listener;
	private Downloader downloader;
	private String hasher;
	private JSONArray main;
	private JSONArray lib;
	private List<String> components;

	static {
		updateFolder = new File(UPDATE_FOLDER);
		if (!updateFolder.isDirectory()) {
			updateFolder.mkdir();
		}
		updateLib = new File(updateFolder, LIB_FOLDER);
		if (!updateLib.isDirectory()) {
			updateLib.mkdir();
		}
	}

	public ComponentUpdater(MediaPlayer appPlayer, URL updateUrl,
			JSONObject content, UpdateListener listener) {
		this.appPlayer = appPlayer;
		this.updateUrl = updateUrl;
		this.listener = listener;
		hasher = (String) content.get(UpdateChecker.HASHER_FIELD);
		JSONObject jsonComponents = (JSONObject) content
				.get(UpdateChecker.COMPONENTS_FIELD);
		main = (JSONArray) jsonComponents
				.get(UpdateChecker.MAIN_COMPONENTS_FIELD);
		lib = (JSONArray) jsonComponents
				.get(UpdateChecker.LIB_COMPONENTS_FIELD);
		components = new ArrayList<String>();
		downloader = new SingleDownloader();
		downloader.setDownloadListener(new DownloadListener() {
			private String fileName;

			@Override
			public void onStart(URL url, File save) {
				fileName = save.getName();
			}

			@Override
			public void onProgress(long downloaded, long total) {
				ComponentUpdater.this.listener.downloadProgress(fileName,
						(int) (downloaded * 1000 / total));
			}

			@Override
			public void onFailed(URL url, File save, Throwable e) {
				ComponentUpdater.this.listener
						.downloadFailed(ErrorCode.NETWORK_ERROR);
			}

			@Override
			public void onCompleted(URL url, File save) {
				ComponentUpdater.this.listener.downloadProgress(fileName, 1000);
			}
		});
	}

	@Override
	public boolean update() throws InterruptedException, IOException {
		try {
			boolean done = downloadComponents(main, updateFolder)
					&& downloadComponents(lib, updateLib);
			if (done && listener.downloadCompleted()) {
				performUpdate();
				return true;
			}
			return false;
		} finally {
			downloader.shutdown();
		}
	}

	private void performUpdate() throws IOException {
		String separator = System.getProperty("file.separator");
		String classpath = System.getProperty("java.class.path");
		String path = System.getProperty("java.home") + separator + "bin"
				+ separator + "java";
		if (Constants.isWindows) {
			path = path + ".exe";
		}
		List<String> cmdList = new ArrayList<String>();
		cmdList.add(path);
		if (DEBUG_MODE) {
			cmdList.add("-Xdebug");
			cmdList.add("-Xnoagent");
			cmdList.add("-Djava.compiler=NONE");
			cmdList.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8008");
		}
		cmdList.add("-jar");
		cmdList.add("updatetool.jar");
		for (String component : components) {
			cmdList.add(component);
		}
		ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
		processBuilder.start();
		appPlayer.requestShutdown();
	}

	private boolean downloadComponents(JSONArray components, File saveFolder)
			throws IOException, InterruptedException {
		JSONObject c = null;
		String url = null;
		String name = null;
		String hash = null;
		for (int i = 0, n = components.size(); i < n; ++i) {
			c = (JSONObject) components.get(i);
			url = (String) c.get(UpdateChecker.LINK_FIELD);
			name = (String) c.get(UpdateChecker.NAME_FIELD);
			hash = (String) c.get(UpdateChecker.CHECKSUM_FIELD);
			if (!downloadComponent(url, name, saveFolder, hash)) {
				return false;
			}
		}
		return true;
	}

	private boolean downloadComponent(String url, String name, File saveFolder,
			String hash) throws IOException, InterruptedException {
		File target = new File(saveFolder, name);
		if (target.isFile() && UpdateChecker.verify(target, hasher, hash)) {
			components.add(target.getPath());
			return true;
		}
		URL file = new URL(updateUrl, url);
		File temp = new File(saveFolder, name + ".part");
		if (!downloader.download(file, temp)) {
			listener.downloadFailed(ErrorCode.NETWORK_ERROR);
			return false;
		}
		if (UpdateChecker.verify(temp, hasher, hash)) {
			Files.move(temp.toPath(), target.toPath(),
					StandardCopyOption.REPLACE_EXISTING);
			components.add(target.getPath());
			return true;
		}
		listener.downloadFailed(ErrorCode.CORRUPT_DATA);
		return false;
	}
}
