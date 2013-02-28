package com.solt.media.stream;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.solt.libtorrent.LibTorrent;

public class NanoHTTPD {


	/**
	 * Common mime types for dynamic content
	 */
	public static final String MIME_PLAINTEXT = "text/plain",
			MIME_HTML = "text/html",
			MIME_DEFAULT_BINARY = "application/octet-stream",
			MIME_XML = "text/xml";



	// ==================================================
	// Socket & server code
	// ==================================================

	/**
	 * Starts a HTTP server to given port.
	 * <p>
	 * Throws an IOException if the socket is already in use
	 */
	public NanoHTTPD(int port, File wwwroot, LibTorrent libTorrent)
			throws IOException {
		this.libTorrent = libTorrent;
		this.port = port;
		if (!wwwroot.isDirectory() && !wwwroot.mkdirs()) {
			throw new IOException(wwwroot + ": cant create directory");
		}
		this.rootDir = wwwroot;
		ServerSocketChannel ssc = ServerSocketChannel.open();
		ssocket = ssc.socket();
		ssocket.bind(new InetSocketAddress("127.0.0.1", port));
		workers = Executors.newFixedThreadPool(8);
		sessions = new ArrayList<HttpHandler>();
		serving = true;
		final NanoHTTPD httpd = this;
		acceptor = new Thread(new Runnable() {
			public void run() {
				try {
					while (true) {
						HttpHandler httpsess = new HttpHandler(
								ssocket.accept(), httpd);
						if (serving) {
							addHandler(httpsess);
							workers.execute(httpsess);
						} else {
							httpsess.stop();
						}
					}
				} catch (IOException ioe) {
				}
			}
		}, "NanoHttpd");
		acceptor.setDaemon(true);
		acceptor.start();
	}

	public NanoHTTPD(int port, String wwwRoot, LibTorrent torrent)
			throws IOException {
		this(port, new File(wwwRoot), torrent);
	}

	void addHandler(HttpHandler handler) {
		synchronized (sessions) {
			sessions.add(handler);
		}
	}
	
	void removeHandler(HttpHandler handler) {
		synchronized (sessions) {
			sessions.remove(handler);
		}
	}
	/**
	 * shutdowns the server.
	 */
	public void shutdown() {
		try {
			workers.shutdownNow();
			workers.awaitTermination(200000, TimeUnit.SECONDS);
			ssocket.close();
			System.out.println("httpd was stopped");
		} catch (IOException ioe) {
		} catch (InterruptedException e) {
		}
	}

	public void cancelStream() {
		synchronized (sessions) {
			for (HttpHandler ses : sessions) {
				ses.stop();
			}
			sessions.clear();
		}
	}

	
	LibTorrent getLibTorrent() {
		return libTorrent;
	}
	
	File getRootDir() {
		return rootDir;
	}
	/**
	 * start serving file. It normally was called when server stops serving file
	 */
	public void start() {
		serving = true;
	}

	/**
	 * stop serving file. Note that server still is running
	 */
	public void stop() {
		serving = false;
		cancelStream();
	}


	private int port;
	private final ServerSocket ssocket;
	private Thread acceptor;
	private ExecutorService workers;
	private final File rootDir;
	private LibTorrent libTorrent;
	private List<HttpHandler> sessions;
	private volatile boolean serving;


	// ==================================================
	// File server code
	// ==================================================



}
