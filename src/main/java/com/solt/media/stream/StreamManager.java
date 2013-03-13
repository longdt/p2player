package com.solt.media.stream;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.solt.media.util.StripedLockProvider;


public class StreamManager {
	private StripedLockProvider locks;
	private ConcurrentMap<String, Set<TorrentStreamer>> connections;
	
	public StreamManager() {
		this(16, 64);
	}
	
	public StreamManager(int concurrencyLevel, int initialCapacity) {
		locks = new StripedLockProvider();
		connections = new ConcurrentHashMap<String, Set<TorrentStreamer>>(initialCapacity, 0.75f, concurrencyLevel);
	}
	
	public void putSync(String hashCode, TorrentStreamer streamer) {
		synchronized (getLock(hashCode)) {
			Set<TorrentStreamer> set = connections.get(hashCode);
			if (set == null) {
				set = new HashSet<TorrentStreamer>(8);
				connections.put(hashCode, set);
			}
			set.add(streamer);
		}
	}
	
	public Set<TorrentStreamer> getStreamers(String hashCode) {
		return connections.get(hashCode);
	}
	
	public void removeSync(String hashCode, TorrentStreamer streamer) {
		synchronized (getLock(hashCode)) {
			Set<TorrentStreamer> set = connections.get(hashCode);
			if (set != null) {
				set.remove(streamer);
				if (set.isEmpty()) {
					connections.remove(hashCode);
				}
			}
		}
	}
	
	public Object getLock(String email) {
		return locks.getLock(email);
	}

}
