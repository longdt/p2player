package com.solt.libtorrent;

import java.io.File;

import com.solt.media.stream.NanoHTTPD;

public class TorrentManager {
	private LibTorrent torrent;
	private NanoHTTPD httpd;
//	private 
	private static TorrentManager instance;
	
	private TorrentManager() {
		torrent = new LibTorrent();
	}
	
	public static TorrentManager listenOn(int port, String wwwRoot) {
		return instance;
	}

	public void addTorrent(File torrentFile) {
		
	}
}
