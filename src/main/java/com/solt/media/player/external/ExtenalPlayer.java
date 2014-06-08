package com.solt.media.player.external;

import java.io.IOException;

import com.solt.libtorrent.TorrentManager;

public class ExtenalPlayer {
	private volatile PlayerInstance instance;
	private InstanceMonitor monitor;
	public ExtenalPlayer() {
		monitor = new InstanceMonitor();
		monitor.start();
	}
	
	public void open(String url, String... subFile) {
		instance = PlayerInstanceFactory.newPlayerInstance();
		try {
			instance.play(url, subFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void prepare() {
		if (instance != null) {
			instance.exit();
		}
	}
	
	public void stop() {
		monitor.shutdown();
	}
	
	class InstanceMonitor extends Thread {
		private volatile boolean done;
		public InstanceMonitor() {
			setDaemon(true);
		}
		
		public void shutdown() {
			done = true;
			interrupt();
		}

		@Override
		public void run() {
			try {
				while (!done) {
					if (instance == null || instance.isTerminated()) {
						Thread.sleep(1000);
						continue;
					}
					instance.waitForTerminate();
					//if use quit external player then torrentmanager cancelstream
					TorrentManager.getInstance().cancelStream();
				}
			} catch (InterruptedException e) {
			}
		}
	}
	
}
