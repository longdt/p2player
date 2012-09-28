package com.solt.libtorrent;

import java.io.File;
import java.io.IOException;

import com.solt.media.stream.NanoHTTPD;

public class TorrentManager {
	private static final int HTTPD_PORT = 18080;
	private LibTorrent torrent;
	private NanoHTTPD httpd;
	private static TorrentManager instance;
	
	private TorrentManager(int port, String wwwRoot) {
		torrent = new LibTorrent();
		torrent.setSession(port, wwwRoot);
		try {
			httpd = new NanoHTTPD(HTTPD_PORT, wwwRoot, torrent);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public synchronized static TorrentManager listenOn(int port, String wwwRoot) {
		if (instance == null) {
			instance = new TorrentManager(port, wwwRoot);
		}
		return instance;
	}

	public String addTorrent(File torrentFile) {
		String hashCode = torrent.addTorrent(torrentFile.getAbsolutePath(), 1, false);
		if (hashCode != null) {
			return "http://127.0.0.1:" + HTTPD_PORT + "/" + hashCode;
		}
		return null;
	}

	public void shutdown() {
		httpd.shutdown();
		torrent.abortSession();
	}
}
