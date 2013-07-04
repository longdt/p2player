package com.solt.libtorrent.policy;

import java.util.Set;

import com.solt.libtorrent.TorrentException;
import com.solt.libtorrent.TorrentManager;
import com.solt.media.config.ConfigurationManager;

public class NumberCachePolicy implements CachePolicy {
	private TorrentManager manager;
	private int maxNumTorrent;
	
	public NumberCachePolicy() {
		manager = TorrentManager.getInstance();
		maxNumTorrent = ConfigurationManager.getInstance().getInt(ConfigurationManager.TORRENT_CACHE_AMOUNT, 25);
	}

	@Override
	public void prepare(String torrentFile) {
		Set<String> torrents = manager.getTorrents();
		if (torrents.size() >= maxNumTorrent) {
			String hashCode = torrents.iterator().next();
			try {
				manager.removeTorrent(hashCode);
			} catch (TorrentException e) {
				e.printStackTrace();
			}
		}
	}
}
