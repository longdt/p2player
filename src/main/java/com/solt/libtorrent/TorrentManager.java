package com.solt.libtorrent;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashSet;

import com.solt.libtorrent.policy.CachePolicy;
import com.solt.libtorrent.policy.CachePolicyFactory;
import com.solt.media.config.ConfigurationManager;
import com.solt.media.stream.NanoHTTPD;
import com.solt.media.util.FileUtils;
import com.solt.media.util.SystemProperties;

public class TorrentManager {
	private static final int HTTPD_PORT = 18080;
	private LibTorrent libTorrent;
	private NanoHTTPD httpd;
	private LinkedHashSet<String> torrents;
	private File torrentsDir;
	private CachePolicy policy;
	private static TorrentManager instance;
	private boolean processAlerts;
	private Thread alertsService;

	private TorrentManager(int port, String wwwRoot) throws IOException {
		torrentsDir = SystemProperties.getTorrentsDir();
		torrents = new LinkedHashSet<String>();
		libTorrent = new LibTorrent();
		httpd = new NanoHTTPD(HTTPD_PORT, wwwRoot, libTorrent);
		libTorrent.setSession(port, wwwRoot);
		loadAsyncExistTorrents();
	}

	/**
	 * 
	 */
	private void loadAsyncExistTorrents() {
		String hashCode = null;
		for (File torrent : torrentsDir.listFiles()) {
			if (torrent.isFile()) {
				hashCode = libTorrent.addAsyncTorrent(torrent.getAbsolutePath(), 0,
						false);
				if (hashCode != null) {
					torrents.add(hashCode);
//					try {
//						libTorrent.setUploadMode(hashCode, true);
//					} catch (TorrentException e) {
//						e.printStackTrace();
//					}
				}
			}
		}
	}

	public synchronized static TorrentManager getInstance() {
		if (instance == null) {
			ConfigurationManager conf = ConfigurationManager.getInstance();
			int port = conf.getInt(ConfigurationManager.TORRENT_LISTEN_PORT, 0);
			String wwwRoot = conf
					.get(ConfigurationManager.TORRENT_DOWNLOAD_DIR);
			try {
				instance = new TorrentManager(port, wwwRoot);
				instance.policy = CachePolicyFactory.getDefaultCachePolicy();
				instance.startAlertsProcessService(1000);
			} catch (IOException e) {
			}
		}
		return instance;
	}
	
	public synchronized void startAlertsProcessService(final long duration) {
		if (processAlerts) {
			return;
		}
		alertsService = new Thread("AlertsProcService") {
			@Override
			public void run() {
				try {
					while (true) {
						libTorrent.handleAlerts();
						Thread.sleep(duration);
					}
				} catch (InterruptedException e) {
				}
			}
		};
		alertsService.start();
		processAlerts = true;
	}
	
	public synchronized void stopAlertsProcessService() {
		if (!processAlerts) {
			return;
		}
		alertsService.interrupt();
		try {
			alertsService.join();
			processAlerts = false;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public synchronized String addTorrent(File torrentFile) {
		try {
			String hashCode = libTorrent.addTorrent(
					torrentFile.getAbsolutePath(), 0, false);
			if (hashCode != null) {

				libTorrent.setUploadMode(hashCode, false);

				if (torrents.add(hashCode)) {
					FileUtils.copyFile(torrentFile, new File(torrentsDir,
							hashCode));
					policy.prepare(torrentFile.getAbsolutePath());
				}
				return "http://127.0.0.1:" + HTTPD_PORT + NanoHTTPD.ACTION_VIEW
						+ "?" + NanoHTTPD.PARAM_HASHCODE + "=" + hashCode;
			}
		} catch (TorrentException e) {
			e.printStackTrace();
		}
		return null;
	}

	public synchronized String addTorrent(URL url) {
		File torrentFile = new File(torrentsDir, ".temp");
		try {
			FileUtils.copyFile(url.openStream(), torrentFile);
			String hashCode = libTorrent.addTorrent(
					torrentFile.getAbsolutePath(), 0, false);
			if (hashCode != null) {
				libTorrent.setUploadMode(hashCode, false);
				if (torrents.add(hashCode)) {
					torrentFile.renameTo(new File(torrentsDir, hashCode));
					policy.prepare(torrentFile.getAbsolutePath());
				}
				return "http://127.0.0.1:" + HTTPD_PORT + NanoHTTPD.ACTION_VIEW
						+ "?" + NanoHTTPD.PARAM_HASHCODE + "=" + hashCode;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public String getMediaUrl(String hashCode) {
		if (contains(hashCode)) {
			return "http://127.0.0.1:" + HTTPD_PORT + NanoHTTPD.ACTION_VIEW
					+ "?" + NanoHTTPD.PARAM_HASHCODE + "=" + hashCode;
		}
		return null;
	}

	public LinkedHashSet<String> getTorrents() {
		return torrents;
	}
	
	public synchronized boolean contains(String hashCode) {
		return torrents.contains(hashCode);
	}
	
	public void cancelStream() {
		httpd.cancelStream();
	}

	public void shutdown() {
		httpd.shutdown();
		stopAlertsProcessService();
		libTorrent.abortSession();
	}

	public synchronized void removeTorrent(String hashCode)
			throws TorrentException {
		if (libTorrent.removeTorrent(hashCode, true)) {
			torrents.remove(hashCode);
			(new File(torrentsDir, hashCode)).delete();
		}
	}

	/**
	 * @param string
	 * @throws MalformedURLException 
	 */
	public static void requestAddTorrent(String hashCode) throws MalformedURLException {
		URL url = new URL("http://127.0.0.1:" + HTTPD_PORT + NanoHTTPD.ACTION_ADD + "?" + NanoHTTPD.PARAM_HASHCODE + "=" + hashCode);
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader( url.openStream()));
			System.out.println(in.readLine());
		} catch (IOException e) {
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
