package com.solt.libtorrent;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.solt.media.config.ConfigurationManager;
import com.solt.media.stream.NanoHTTPD;
import com.solt.media.util.FileUtils;
import com.solt.media.util.SystemProperties;

public class TorrentManager {
	private static final int HTTPD_PORT = 18080;
	private LibTorrent libTorrent;
	private NanoHTTPD httpd;
	private Set<String> torrents;
	private File torrentsDir;
	private static TorrentManager instance;
	
	private TorrentManager(int port, String wwwRoot) {
		try {
			torrentsDir = SystemProperties.getTorrentsDir();
			torrents = new HashSet<String>();
			libTorrent = new LibTorrent();
			httpd = new NanoHTTPD(HTTPD_PORT, wwwRoot, libTorrent);
			libTorrent.setSession(port, wwwRoot);
			loadExistTorrents();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	/**
	 * 
	 */
	private void loadExistTorrents() {
		String hashCode = null;
		for (File torrent : torrentsDir.listFiles()) {
			if (torrent.isFile()) {
				hashCode = libTorrent.addTorrent(torrent.getAbsolutePath(), 0, false);
				if (hashCode != null) {
					torrents.add(hashCode);
				}
				try {
					libTorrent.setUploadMode(hashCode, true);
				} catch (TorrentException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public synchronized static TorrentManager getInstance() {
		if (instance == null) {
			ConfigurationManager conf = ConfigurationManager.getInstance();
			int port = conf.getInt(ConfigurationManager.TORRENT_LISTEN_PORT, 0);
			String wwwRoot = conf.get(ConfigurationManager.TORRENT_DOWNLOAD_DIR);
			instance = new TorrentManager(port, wwwRoot);
		}
		return instance;
	}

	public String addTorrent(File torrentFile) {
		String hashCode = libTorrent.addTorrent(torrentFile.getAbsolutePath(), 0, false);
		if (hashCode != null) {
			if (torrents.add(hashCode)) {
				FileUtils.copy(torrentFile, new File(torrentsDir, hashCode));
			}
			return "http://127.0.0.1:" + HTTPD_PORT + "/" + hashCode;
		}
		return null;
	}

	public void shutdown() {
		httpd.shutdown();
		libTorrent.abortSession();
	}
}
