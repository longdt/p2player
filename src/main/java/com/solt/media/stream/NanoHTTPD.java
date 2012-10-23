package com.solt.media.stream;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.solt.libtorrent.FileEntry;
import com.solt.libtorrent.LibTorrent;
import com.solt.libtorrent.TorrentException;
import com.solt.libtorrent.TorrentManager;
import com.solt.media.util.Average;

public class NanoHTTPD {

	/**
	 * Some HTTP response status codes
	 */
	public static final String HTTP_OK = "200 OK",
			HTTP_PARTIALCONTENT = "206 Partial Content",
			HTTP_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable",
			HTTP_REDIRECT = "301 Moved Permanently",
			HTTP_NOTMODIFIED = "304 Not Modified",
			HTTP_FORBIDDEN = "403 Forbidden", HTTP_NOTFOUND = "404 Not Found",
			HTTP_BADREQUEST = "400 Bad Request",
			HTTP_INTERNALERROR = "500 Internal Server Error",
			HTTP_NOTIMPLEMENTED = "501 Not Implemented";

	/**
	 * Common mime types for dynamic content
	 */
	public static final String MIME_PLAINTEXT = "text/plain",
			MIME_HTML = "text/html",
			MIME_DEFAULT_BINARY = "application/octet-stream",
			MIME_XML = "text/xml";

	public static final String ACTION_VIEW = "/view";

	public static final String ACTION_ADD = "/add";

	public static final String PARAM_HASHCODE = "hashcode";

	private static final String PARAM_FILE = "file";
	
	private static final String DOWN_TORRENT_LINK = "http://localhost/";

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
		myTcpPort = port;
		if (!wwwroot.isDirectory() && !wwwroot.mkdirs()) {
			throw new IOException(wwwroot + ": cant create directory");
		}
		this.myRootDir = wwwroot;
		ServerSocketChannel ssc = ServerSocketChannel.open();
		myServerSocket = ssc.socket();
		myServerSocket.bind(new InetSocketAddress("127.0.0.1", myTcpPort));
		workers = Executors.newFixedThreadPool(8);
		sessions = new ArrayList<HTTPSession>();
		serving = true;
		myThread = new Thread(new Runnable() {
			public void run() {
				try {
					while (true) {
						HTTPSession httpsess = new HTTPSession(
								myServerSocket.accept());
						if (serving) {
							synchronized (sessions) {
								sessions.add(httpsess);
							}
							workers.execute(httpsess);
						} else {
							httpsess.stop();
						}
					}
				} catch (IOException ioe) {
				}
			}
		}, "NanoHttpd");
		myThread.setDaemon(true);
		myThread.start();
	}

	public NanoHTTPD(int port, String wwwRoot, LibTorrent torrent)
			throws IOException {
		this(port, new File(wwwRoot), torrent);
	}

	/**
	 * shutdowns the server.
	 */
	public void shutdown() {
		try {
			workers.shutdownNow();
			workers.awaitTermination(200000, TimeUnit.SECONDS);
			myServerSocket.close();
			myOut.println("httpd was stopped");
		} catch (IOException ioe) {
		} catch (InterruptedException e) {
		}
	}

	public void cancelStream() {
		synchronized (sessions) {
			for (HTTPSession ses : sessions) {
				ses.stop();
			}
			sessions.clear();
		}
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

	/**
	 * Handles one session, i.e. parses the HTTP request and returns the
	 * response.
	 */
	private class HTTPSession implements Runnable {
		public HTTPSession(Socket s) {
			mySocket = s;
			streaming = true;
		}

		public void run() {
			try {
				HttpRequest request = parseRequest(mySocket.getInputStream());
				if (request != null) {
					serveRequest(request);
				}
			} catch (IOException ioe) {
				try {
					sendMessage(
							HTTP_INTERNALERROR,
							"SERVER INTERNAL ERROR: IOException: "
									+ ioe.getMessage());
				} catch (Throwable t) {
				}
			} catch (InterruptedException ie) {
				// Thrown by sendError, ignore and exit the thread.
			} finally {
				try {
					mySocket.close();
				} catch (IOException e) {
					e.printStackTrace(myOut);
				}
				synchronized (sessions) {
					sessions.remove(this);
				}
			}
		}

		private void serveRequest(HttpRequest request)
				throws InterruptedException, MalformedURLException {
			String uri = request.getUri();
			String hashCode = request.getParam(NanoHTTPD.PARAM_HASHCODE);
			if (uri.equals(NanoHTTPD.ACTION_VIEW)) {
				if (hashCode != null) {
					TorrentRequest r = serve(request);
					sendResponse(r);
				} else {
					String torrentList = listTorrents();
					sendMessage(HTTP_OK, torrentList);
				}
			} else if (uri.equals(NanoHTTPD.ACTION_ADD)) {
				URL url = new URL(DOWN_TORRENT_LINK + hashCode + ".torrent");
				String mediaUrl = TorrentManager.getInstance().addTorrent(url);
				if (mediaUrl != null) {
					sendMessage(HTTP_OK, mediaUrl);
				} else {
					sendMessage(HTTP_NOTFOUND, "false");
				}
			} else {
				sendMessage(HTTP_BADREQUEST, "invalid uri");
			}
		}

		private String listTorrents() {
			StringBuilder info = new StringBuilder();
			Set<String> torrents = TorrentManager.getInstance().getTorrents();
			try {
				for (String hashCode : torrents) {
					info.append(libTorrent.getTorrentProgress(hashCode))
							.append('\t')
							.append(hashCode)
							.append('\t')
							.append(libTorrent.getTorrentState(hashCode))
							.append('\t')
							.append(libTorrent.getTorrentDownloadRate(hashCode,
									true)).append('\t')
							.append(libTorrent.getTorrentName(hashCode)).append('\n');
				}
			} catch (TorrentException e) {

			}
			return info.toString();
		}

		/**
		 * Decodes the sent headers and loads the data into java Properties' key
		 * - value pairs
		 **/
		private HttpRequest parseRequest(InputStream is)
				throws InterruptedException {
			try {
				BufferedReader in = new BufferedReader(
						new InputStreamReader(is));
				// Read the request line
				String inLine = in.readLine();
				if (inLine == null)
					return null;
				StringTokenizer st = new StringTokenizer(inLine);
				if (!st.hasMoreTokens())
					sendMessage(HTTP_BADREQUEST,
							"BAD REQUEST: Syntax error. Usage: GET /example/file.html");

				String methodString = st.nextToken();
				int method = 0;
				if (methodString.equalsIgnoreCase("GET")) {
					method = HttpRequest.METHOD_GET;
				} else if (methodString.equalsIgnoreCase("HEAD")) {
					method = HttpRequest.METHOD_HEAD;
				} else {
					sendMessage(HTTP_NOTIMPLEMENTED,
							"SERVER DOES NOT IMPLEMENTS THIS METHOD ");
				}

				if (!st.hasMoreTokens())
					sendMessage(HTTP_BADREQUEST,
							"BAD REQUEST: Missing URI. Usage: GET /example/file.html");

				String uri = st.nextToken();
				HttpRequest request = new HttpRequest();
				request.setMethod(method);
				// Decode parameters from the URI
				int qmi = uri.indexOf('?');
				if (qmi >= 0) {
					decodeParms(uri.substring(qmi + 1), request.getParams());
					uri = decodePercent(uri.substring(0, qmi));
				} else
					uri = decodePercent(uri);
				request.setUri(uri);
				// If there's another token, it's protocol version,
				// followed by HTTP headers. Ignore version but parse headers.
				// NOTE: this now forces header names lowercase since they are
				// case insensitive and vary by client.
				if (st.hasMoreTokens()) {
					String line = in.readLine();
					while (line != null && line.trim().length() > 0) {
						int p = line.indexOf(':');
						if (p >= 0)
							request.setHeader(line.substring(0, p).trim()
									.toLowerCase(), line.substring(p + 1)
									.trim());
						line = in.readLine();
					}
				}
				return request;
			} catch (IOException ioe) {
				sendMessage(
						HTTP_INTERNALERROR,
						"SERVER INTERNAL ERROR: IOException: "
								+ ioe.getMessage());
			}
			return null;
		}

		/**
		 * Decodes the percent encoding scheme. <br/>
		 * For example: "an+example%20string" -> "an example string"
		 */
		private String decodePercent(String str) throws InterruptedException {
			try {
				StringBuffer sb = new StringBuffer();
				for (int i = 0; i < str.length(); i++) {
					char c = str.charAt(i);
					switch (c) {
					case '+':
						sb.append(' ');
						break;
					case '%':
						sb.append((char) Integer.parseInt(
								str.substring(i + 1, i + 3), 16));
						i += 2;
						break;
					default:
						sb.append(c);
						break;
					}
				}
				return sb.toString();
			} catch (Exception e) {
				sendMessage(HTTP_BADREQUEST,
						"BAD REQUEST: Bad percent-encoding.");
				return null;
			}
		}

		/**
		 * Decodes parameters in percent-encoded URI-format ( e.g.
		 * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
		 * Properties. NOTE: this doesn't support multiple identical keys due to
		 * the simplicity of Properties -- if you need multiples, you might want
		 * to replace the Properties with a Hashtable of Vectors or such.
		 */
		private void decodeParms(String parms, Map<String, String> map)
				throws InterruptedException {
			if (parms == null)
				return;

			StringTokenizer st = new StringTokenizer(parms, "&");
			while (st.hasMoreTokens()) {
				String e = st.nextToken();
				int sep = e.indexOf('=');
				if (sep >= 0)
					map.put(decodePercent(e.substring(0, sep)).trim(),
							decodePercent(e.substring(sep + 1)));
			}
		}

		/**
		 * Returns an error message as a HTTP response and throws
		 * InterruptedException to stop further request processing.
		 */
		private void sendMessage(String status, String msg)
				throws InterruptedException {
			try {
				if (status == null)
					throw new Error("sendResponse(): Status can't be null.");

				PrintWriter pw = new PrintWriter(mySocket.getOutputStream());
				pw.print("HTTP/1.0 " + status + " \r\n");
				pw.print("Content-Type: " + MIME_PLAINTEXT + "\r\n");
				pw.print("Date: " + gmtFrmt.format(new Date()) + "\r\n");
				pw.print("Accept-Ranges: bytes\r\n");
				pw.print("\r\n");
				pw.flush();

				if (msg != null) {
					pw.write(msg);
				}
				pw.flush();
				pw.close();
			} catch (IOException ioe) {
			}
			throw new InterruptedException();
		}

		/**
		 * Sends given response to the socket.
		 * 
		 * @throws InterruptedException
		 */
		private void sendResponse(TorrentRequest req)
				throws InterruptedException {
			String status = req.getStatus();
			String mime = req.getMimeType();
			Map<String, String> header = req.getHeaders();

			String hashCode = req.getHashCode();
			String msg = req.getMessage();
			PrintStream pw = null;
			try {
				if (status == null)
					throw new Error("sendResponse(): Status can't be null.");

				pw = new PrintStream(mySocket.getOutputStream());
				pw.print("HTTP/1.0 " + status + " \r\n");

				if (mime != null)
					pw.print("Content-Type: " + mime + "\r\n");

				if (header == null || header.get("Date") == null)
					pw.print("Date: " + gmtFrmt.format(new Date()) + "\r\n");

				if (header != null) {
					for (Entry<String, String> entry : header.entrySet()) {
						pw.print(entry.getKey() + ": " + entry.getValue()
								+ "\r\n");
					}
				}

				pw.print("\r\n");
				pw.flush();

				if (hashCode != null) {
					int state = libTorrent.getTorrentState(hashCode);
					if (state == -1) {
						return;
					} else if (state == 4 || state == 5) {
						FileEntry[] entries = libTorrent.getTorrentFiles(hashCode);
						File f = new File(myRootDir, entries[req.getIndex()].getPath());
						sendFileData(pw, f, req.getDataLength(), req.getTransferOffset());
					} else {
						sendTorrentData(hashCode, req.getIndex(), req.getDataLength(),
								req.getTransferOffset());
					}
				} else if (msg != null) {
					pw.print(msg);
				}
			} catch (Exception e) {
				// System.err.println("close stream: " +
				// response.getTransferOffset() + " due: " + e.getMessage());
				// e.printStackTrace();
			} finally {
				if (pw != null) {
					pw.close();
				}
			}
		}

		/**
		 * @param pw
		 * @param f
		 * @param dataLength
		 * @param transferOffset
		 * @throws IOException 
		 */
		private void sendFileData(PrintStream out, File f, long dataLength,
				long transferOffset) throws IOException {
			RandomAccessFile raf = null;	 
			try {
				raf = new RandomAccessFile(f, "rw");
				raf.seek(transferOffset);
				byte[] buf = new byte[1024];
				int len = 0;
				while (streaming && dataLength > 0 && !Thread.currentThread().isInterrupted()) {
					len = raf.read(buf);
					if (len == -1) {
						break;
					}
					out.write(buf, 0, len);
					dataLength = dataLength - len;
				}
			} finally {
				if (raf != null) {
					raf.close();
				}
			}
		}

		private void sendTorrentData(String hashCode, int index, long dataLength,
				long transferOffset) throws Exception {
			int lastSet = 0;
			Average streamRate = Average.getInstance(1000, 20);
			long pending = dataLength; // This is to support partial sends, see
										// serveFile()

			// TODO transfer data when downloading torrent
			int pieceSize = libTorrent.getPieceSize(hashCode, false);
			// int PIECE_BUFFER_SIZE = 300 * 1024 * 30 / pieceSize;
			long timeToWait = (pieceSize * 10000l) / (300 * 1024);
			transferOffset += libTorrent.getTorrentFiles(hashCode)[index].getOffset();
			int streamPiece = (int) (transferOffset / pieceSize);
			int setRead = -1;
			int transferPieceIdx = streamPiece;
			int transferPieceOffset = (int) (transferOffset - transferPieceIdx
					* pieceSize);
			if (transferOffset > 0) {
				// TODO clear piece deadline
				// libTorrent.clearPiecesDeadline(hashCode);
			}

			int pieceNum = libTorrent.getPieceNum(hashCode);
			lastSet = streamPiece;
			int incompleteIdx = lastSet;
			int numSet = 0;
			byte[] buff = new byte[pieceSize];
			ByteBuffer readBuffer = ByteBuffer.allocate(1024);
			mySocket.setSendBufferSize(pieceSize);
			SocketChannel sc = mySocket.getChannel();
			sc.configureBlocking(false);
			int cancelPiece = 0;
			long lastTime = System.currentTimeMillis();
			boolean cancelled = false;
			int state = 0;
			while (streaming && pending > 0
					&& !Thread.currentThread().isInterrupted()) {
				if (state != 4 && state != 5 && state != 3) {
					state = libTorrent.getTorrentState(hashCode);
				}
				int PIECE_BUFFER_SIZE = computePieceBufferSize(hashCode,
						pieceSize, streamRate);
				System.err.println("PIECE_BUFFER_SIZE = " + PIECE_BUFFER_SIZE);
				if (state != 4 && state != 5
						&& streamPiece + PIECE_BUFFER_SIZE > incompleteIdx) {
					incompleteIdx = libTorrent.getFirstPieceIncomplete(
							hashCode, transferOffset);
					long currentTime = System.currentTimeMillis();
					if (cancelPiece != incompleteIdx) {
						if (cancelled
								&& cancelPiece + PIECE_BUFFER_SIZE * 2 / 3 > incompleteIdx) {
							libTorrent.cancelTorrentPiece(hashCode,
									incompleteIdx);
						} else {
							cancelled = false;
						}
						cancelPiece = incompleteIdx;
						lastTime = currentTime;
					} else if (lastTime + timeToWait < currentTime) {
						libTorrent.cancelTorrentPiece(hashCode, cancelPiece);
						cancelled = true;
						lastTime = currentTime;
					}

					System.err.println(streamPiece);
					if (incompleteIdx > lastSet) {
						lastSet = incompleteIdx;
					}
					numSet = incompleteIdx + PIECE_BUFFER_SIZE - lastSet;
					if (numSet > 0) {
						// System.err.println("set deadline: [" + lastSet + ", "
						// + (lastSet + numSet) + ")");
						for (int i = 0; i < numSet && i + lastSet < pieceNum; ++i) {
							libTorrent.setPieceDeadline(hashCode, lastSet + i,
									i * 150 + 1500);
							if (lastSet + i + PIECE_BUFFER_SIZE < pieceNum) {
								libTorrent.setPiecePriority(hashCode, lastSet
										+ i + PIECE_BUFFER_SIZE, 7);
							}
						}
						lastSet += numSet;
					}
					if (streamPiece == incompleteIdx) {
						System.err
								.println("wait for libtorrent download data...");
						Thread.sleep(500);
						int len = sc.read(readBuffer);
						if (len == -1) {
							System.err.println("..........EOF........");
							break;
						} else if (len > 0) {
							System.err.println("Player send data to server");
						}
						continue;
					}
				}
				if (setRead != streamPiece) {
					setRead = streamPiece;
					libTorrent.setTorrentReadPiece(hashCode, setRead);
					Thread.sleep(50);
				}
				int len = libTorrent.readTorrentPiece(hashCode, streamPiece,
						buff);
				if (len == -1) {
					break;
				} else if (len == 0) {
					Thread.sleep(50);
					continue;
				}
				int offset = (streamPiece == transferPieceIdx) ? transferPieceOffset
						: 0;
				len = len - offset;
				if (len > pending) {
					len = (int) pending;
				}
				writeData(sc, buff, offset, len, streamRate);
				pending -= len;
				++streamPiece;
			}

		}

		private void writeData(SocketChannel sc, byte[] buff, int offset,
				int len, Average streamRate) throws IOException,
				InterruptedException {
			ByteBuffer buffer = ByteBuffer.wrap(buff, offset, len); // TODO Need
																	// tunning
			sc.write(buffer);
			int writeLen = 0;
			while (buffer.hasRemaining()) {
				Thread.sleep(50);
				writeLen = sc.write(buffer);
				streamRate.addValue(writeLen);
			}
		}

		private int computePieceBufferSize(String hashCode, int pieceSize,
				Average streamRate) {

			long rate = streamRate.getAverage();
			try {
				long downRate = libTorrent.getTorrentDownloadRate(hashCode,
						true);
				rate = rate > 0 ? (rate + downRate) / 2 : downRate;
			} catch (TorrentException e) {
				e.printStackTrace();
			}

			int buffer_secs = DEFAULT_BUFFER_SECS;

			long buffer_bytes = (buffer_secs * rate);

			int pieces_to_buffer = (int) (buffer_bytes / pieceSize);

			if (pieces_to_buffer < DEFAULT_MIN_PIECES_TO_BUFFER) {

				pieces_to_buffer = DEFAULT_MIN_PIECES_TO_BUFFER;
			}
			return pieces_to_buffer;
		}

		public void stop() {
			streaming = false;
			try {
				mySocket.close();
			} catch (IOException e) {
			}
		}

		// ==================================================
		// API parts
		// ==================================================

		/**
		 * Override this to customize the server.
		 * <p>
		 * 
		 * (By default, this delegates to serveFile() and allows directory
		 * listing.)
		 * 
		 * @param uri
		 *            Percent-decoded URI without parameters, for example
		 *            "/index.cgi"
		 * @param method
		 *            "GET", "POST" etc.
		 * @param parms
		 *            Parsed, percent decoded parameters from URI and, in case
		 *            of POST, data.
		 * @param header
		 *            Header entries, percent decoded
		 * @return HTTP response, see class Response for details
		 * @throws InterruptedException
		 */
		TorrentRequest serve(HttpRequest request) throws InterruptedException {
			TorrentRequest res = null;
			try {
				String hashCode = request.getParam(PARAM_HASHCODE);
				String file = request.getParam(PARAM_FILE);
				int index = 0;
				if (file != null) {
					index = Integer.parseInt(file);
				} else {
					index = libTorrent.getBestStreamableFile(hashCode);
				}
				FileEntry[] entries = libTorrent.getTorrentFiles(hashCode);
				File f = new File(myRootDir, entries[index].getPath());

				// Get MIME type from file name extension, if possible
				String mime = null;
				int dot = f.getCanonicalPath().lastIndexOf('.');
				if (dot >= 0)
					mime = (String) theMimeTypes.get(f.getCanonicalPath()
							.substring(dot + 1).toLowerCase());
				if (mime == null)
					mime = MIME_DEFAULT_BINARY;

				// Calculate etag
				String etag = Integer.toHexString((f.getAbsolutePath()
						+ f.lastModified() + "" + f.length()).hashCode());

				// Support (simple) skipping:
				long startFrom = 0;
				long endAt = -1;
				String range = request.getHeader("range");
				if (range != null) {
					if (range.startsWith("bytes=")) {
						range = range.substring("bytes=".length());
						int minus = range.indexOf('-');
						try {
							if (minus > 0) {
								startFrom = Long.parseLong(range.substring(0,
										minus));
								endAt = Long.parseLong(range
										.substring(minus + 1));
							}
						} catch (NumberFormatException nfe) {
						}
					}
				}

				// Change return code and add Content-Range header when skipping
				// is requested
				long fileLen = entries[index].getSize();
				if (range != null && startFrom >= 0) {
					if (startFrom >= fileLen) {
						res = new TorrentRequest(HTTP_RANGE_NOT_SATISFIABLE,
								MIME_PLAINTEXT, null);
						res.setHeader("Content-Range", "bytes 0-0/" + fileLen);
						res.setHeader("ETag", etag);
					} else {
						if (endAt < 0)
							endAt = fileLen - 1;
						long newLen = endAt - startFrom + 1;
						if (newLen < 0)
							newLen = 0;

						final long dataLen = newLen;
						if (request.getMethod() == HttpRequest.METHOD_HEAD) {
							res = new TorrentRequest(HTTP_PARTIALCONTENT, mime,
									null);
						} else {
							res = new TorrentRequest(HTTP_PARTIALCONTENT, mime,
									hashCode, index, startFrom, dataLen);
						}
						res.setHeader("Content-Length", "" + dataLen);
						res.setHeader("Content-Range", "bytes " + startFrom
								+ "-" + endAt + "/" + fileLen);
						res.setHeader("ETag", etag);
					}
				} else {
					if (etag.equals(request.getHeader("if-none-match")))
						res = new TorrentRequest(HTTP_NOTMODIFIED, mime, null);
					else {
						res = request.getMethod() == HttpRequest.METHOD_HEAD ? new TorrentRequest(
								HTTP_OK, mime, null) : new TorrentRequest(
								HTTP_OK, mime, hashCode, index, 0, fileLen);
						res.setHeader("Content-Length", "" + fileLen);
						res.setHeader("ETag", etag);
					}
				}

			} catch (NumberFormatException e) {
				sendMessage(HTTP_NOTFOUND, "Error 404, file not found.");
			} catch (IOException ioe) {
				sendMessage(HTTP_FORBIDDEN, "FORBIDDEN: Reading file failed.");
			} catch (TorrentException e) {
				sendMessage(HTTP_NOTFOUND, "Error 404, file not found.");
			}

			res.setHeader("Accept-Ranges", "bytes"); // Announce that the file
														// server accepts
														// partial content
														// requestes
			return res;
		}

		private Socket mySocket;
		private volatile boolean streaming;
	}

	private int myTcpPort;
	private final ServerSocket myServerSocket;
	private Thread myThread;
	private ExecutorService workers;
	private final File myRootDir;
	private LibTorrent libTorrent;
	private List<HTTPSession> sessions;
	private volatile boolean serving;
	private static int DEFAULT_BUFFER_SECS = 60;
	private static int DEFAULT_MIN_PIECES_TO_BUFFER = 5;
	// ==================================================
	// File server code
	// ==================================================

	/**
	 * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
	 */
	private static Map<String, String> theMimeTypes = new HashMap<String, String>();
	static {
		StringTokenizer st = new StringTokenizer("css		text/css "
				+ "htm		text/html " + "html		text/html " + "xml		text/xml "
				+ "txt		text/plain " + "asc		text/plain " + "gif		image/gif "
				+ "jpg		image/jpeg " + "jpeg		image/jpeg " + "png		image/png "
				+ "mp3		audio/mpeg " + "m3u		audio/mpeg-url "
				+ "mp4		video/mp4 " + "avi		video/avi " + "ogv		video/ogg "
				+ "flv		video/x-flv " + "wmv		video/x-ms-wmv "
				+ "mov		video/quicktime " + "asf		video/x-ms-asf "
				+ "swf		application/x-shockwave-flash "
				+ "js			application/javascript " + "pdf		application/pdf "
				+ "doc		application/msword " + "ogg		application/x-ogg "
				+ "zip		application/octet-stream "
				+ "exe		application/octet-stream "
				+ "class		application/octet-stream ");
		while (st.hasMoreTokens())
			theMimeTypes.put(st.nextToken(), st.nextToken());
	}

	// Change this if you want to log to somewhere else than stdout
	protected static PrintStream myOut = System.out;

	/**
	 * GMT date formatter
	 */
	private static java.text.SimpleDateFormat gmtFrmt;
	static {
		gmtFrmt = new java.text.SimpleDateFormat(
				"E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
		gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
	}
}
