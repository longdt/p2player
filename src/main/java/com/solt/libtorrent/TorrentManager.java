package com.solt.libtorrent;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Set;

import org.apache.log4j.Logger;

import com.solt.libtorrent.policy.CachePolicy;
import com.solt.libtorrent.policy.CachePolicyFactory;
import com.solt.media.config.ConfigurationManager;
import com.solt.media.stream.HttpHandler;
import com.solt.media.stream.NanoHTTPD;
import com.solt.media.ui.MediaPlayer;
import com.solt.media.util.Constants;
import com.solt.media.util.FileUtils;
import com.solt.media.util.SystemProperties;

public class TorrentManager {
	public static MediaPlayer player;
	private static final Logger logger = Logger.getLogger(TorrentManager.class);
	private static final int HTTPD_PORT = 18080;
	private static final Boolean TORRENT_FILE = true;
	private static final Boolean MAGNET_FILE = false;
	private static String currentStream;
	private LibTorrent libTorrent;
	private NanoHTTPD httpd;
	private LinkedHashMap<String, Boolean> torrents;
	private File torrentsDir;
	private CachePolicy policy;
	private static TorrentManager instance;
	private boolean processAlerts;
	private Thread alertsService;

	private TorrentManager(int port, String wwwRoot) throws IOException {
		torrentsDir = SystemProperties.getTorrentsDir();
		torrents = new LinkedHashMap<String, Boolean>();
		File root = new File(wwwRoot);
		httpd = new NanoHTTPD(HTTPD_PORT, root);
		libTorrent = new LibTorrent();
		libTorrent.setSession(port, root, 0, 0);
		libTorrent.setSessionOptions(true, true, true, true);
		loadAsyncExistTorrents();
		httpd.setLibTorrent(libTorrent);
	}

	/**
	 * 
	 */
	private void loadAsyncExistTorrents() {
		String hashCode = null;
		String magnet = null;
		int flags = LibTorrent.FLAG_UPLOAD_MODE;// | LibTorrent.FLAG_SHARE_MODE;
		for (File torrent : torrentsDir.listFiles()) {
			if (torrent.isDirectory()) {
				continue;
			}
			if (torrent.getName().endsWith(Constants.TORRENT_FILE_EXTENSION)) {
				hashCode = libTorrent.addAsyncTorrent(torrent.getAbsolutePath(), 0,
						flags);
				if (hashCode != null) {
					torrents.put(hashCode, TORRENT_FILE);
				}
			} else if (torrent.getName().endsWith(Constants.MAGNET_FILE_EXTENSION)) {
				magnet = FileUtils.getStringContent(torrent);
				hashCode = libTorrent.addAsyncMagnetUri(magnet, 0, flags);
				if (hashCode != null) {
					torrents.put(hashCode, MAGNET_FILE);
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

	private void initStream(String hashCode) {
		try {
			libTorrent.setAutoManaged(hashCode, false);
			libTorrent.setUploadMode(hashCode, false);
//			libTorrent.setShareMode(hashCode, false);
			libTorrent.resumeTorrent(hashCode);
			if (currentStream == null) {
				currentStream = hashCode;
			} else if (!hashCode.equals(currentStream)) {
				libTorrent.setUploadMode(currentStream, true);
//				libTorrent.setShareMode(currentStream, true);
				currentStream = hashCode;
			}
		} catch (TorrentException e) {
			e.printStackTrace();
		}
	}

	public synchronized String addTorrent(File torrentFile) {
		String hashCode = libTorrent.addTorrent(
				torrentFile.getAbsolutePath(), 0, 0);
		if (hashCode != null) {
			initStream(hashCode);
			Boolean existFile = torrents.put(hashCode, TORRENT_FILE);
			if (existFile == null) {
				FileUtils.copyFile(torrentFile, new File(torrentsDir,
						hashCode + Constants.TORRENT_FILE_EXTENSION));
				policy.prepare(torrentFile.getAbsolutePath());
			} else if (!existFile) {
				FileUtils.copyFile(torrentFile, new File(torrentsDir,
						hashCode + Constants.TORRENT_FILE_EXTENSION));
			}
			return "http://127.0.0.1:" + HTTPD_PORT + HttpHandler.ACTION_STREAM
					+ "?" + HttpHandler.PARAM_HASHCODE + "=" + hashCode;
		}
		return null;
	}

	public synchronized String addTorrent(URL url) {
		File torrentFile = new File(torrentsDir, ".temp");
		try {
			FileUtils.copyFile(url.openStream(), torrentFile);
			String hashCode = libTorrent.addTorrent(
					torrentFile.getAbsolutePath(), 0, 0);
			if (hashCode != null) {
				initStream(hashCode);
				Boolean existFile = torrents.put(hashCode, TORRENT_FILE);
				if (existFile == null) {
					torrentFile.renameTo(new File(torrentsDir, hashCode + Constants.TORRENT_FILE_EXTENSION));
					policy.prepare(torrentFile.getAbsolutePath());
				} else if (!existFile) {
					torrentFile.renameTo(new File(torrentsDir, hashCode + Constants.TORRENT_FILE_EXTENSION));
				}
				return "http://127.0.0.1:" + HTTPD_PORT + HttpHandler.ACTION_STREAM
						+ "?" + HttpHandler.PARAM_HASHCODE + "=" + hashCode;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public synchronized String addTorrent(URI magnetUri) {
		String hashCode = libTorrent.addMagnetUri(
				magnetUri.toString(), 0, 0);
		if (hashCode != null) {
			initStream(hashCode);
			Boolean existFile = torrents.put(hashCode, MAGNET_FILE);
			if (existFile == null) {
				//TODO save magnet link
				FileUtils.writeFile(new File(torrentsDir,
						hashCode + Constants.MAGNET_FILE_EXTENSION), magnetUri.toString());
				policy.prepare(hashCode);
			}
			return "http://127.0.0.1:" + HTTPD_PORT + HttpHandler.ACTION_STREAM
					+ "?" + HttpHandler.PARAM_HASHCODE + "=" + hashCode;
		}
		return null;
	}
	
	public String getMediaUrl(String hashCode) {
		if (contains(hashCode)) {
			return "http://127.0.0.1:" + HTTPD_PORT + HttpHandler.ACTION_STREAM
					+ "?" + HttpHandler.PARAM_HASHCODE + "=" + hashCode;
		}
		return null;
	}

	public Set<String> getTorrents() {
		return torrents.keySet();
	}
	
	public synchronized boolean contains(String hashCode) {
		return torrents.containsKey(hashCode);
	}
	
	public void cancelStream() {
		httpd.cancelStream();
	}

	public void shutdown() {
		long start = System.nanoTime();
		if (currentStream != null) {
			try {
				libTorrent.setUploadMode(currentStream, true);
//				libTorrent.setShareMode(currentStream, true);
			} catch (TorrentException e) {
				e.printStackTrace();
			}
		}
		httpd.shutdown();
		stopAlertsProcessService();
		libTorrent.abortSession();
		System.out.println(System.nanoTime() - start);
	}

	public synchronized void removeTorrent(String hashCode)
			throws TorrentException {
		if (libTorrent.removeTorrent(hashCode, true)) {
			Boolean existFile = torrents.remove(hashCode);
			if (existFile == null) {
				logger.error("error occur when remove torrent: no torrent in manager");
			} else {
				String extention = existFile ? Constants.TORRENT_FILE_EXTENSION : Constants.MAGNET_FILE_EXTENSION;
				(new File(torrentsDir, hashCode + extention)).delete();
			}
		}
	}

	/**
	 * @param string
	 * @throws MalformedURLException 
	 */
	public static void requestAddTorrent(String movieId, boolean file) throws MalformedURLException {
		URL url = new URL("http://127.0.0.1:" + HTTPD_PORT + HttpHandler.ACTION_ADD + "?" + HttpHandler.PARAM_MOVIEID + "=" + movieId + "&" + HttpHandler.PARAM_FILE +"=" + file);
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

	public void setMediaPlayer(MediaPlayer player) {
		TorrentManager.player = player;
	}
}
