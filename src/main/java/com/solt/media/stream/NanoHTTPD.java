package com.solt.media.stream;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.solt.libtorrent.FileEntry;
import com.solt.libtorrent.LibTorrent;
import com.solt.libtorrent.TorrentException;
import com.solt.media.util.Average;

/**
 * A simple, tiny, nicely embeddable HTTP 1.0 (partially 1.1) server in Java
 *
 * <p> NanoHTTPD version 1.25,
 * Copyright &copy; 2001,2005-2012 Jarno Elonen (elonen@iki.fi, http://iki.fi/elonen/)
 * and Copyright &copy; 2010 Konstantinos Togias (info@ktogias.gr, http://ktogias.gr)
 *
 * <p><b>Features + limitations: </b><ul>
 *
 *    <li> Only one Java file </li>
 *    <li> Java 1.1 compatible </li>
 *    <li> Released as open source, Modified BSD licence </li>
 *    <li> No fixed config files, logging, authorization etc. (Implement yourself if you need them.) </li>
 *    <li> Supports parameter parsing of GET and POST methods (+ rudimentary PUT support in 1.25) </li>
 *    <li> Supports both dynamic content and file serving </li>
 *    <li> Supports file upload (since version 1.2, 2010) </li>
 *    <li> Supports partial content (streaming)</li>
 *    <li> Supports ETags</li>
 *    <li> Never caches anything </li>
 *    <li> Doesn't limit bandwidth, request time or simultaneous connections </li>
 *    <li> Default code serves files and shows all HTTP parameters and headers</li>
 *    <li> File server supports directory listing, index.html and index.htm</li>
 *    <li> File server supports partial content (streaming)</li>
 *    <li> File server supports ETags</li>
 *    <li> File server does the 301 redirection trick for directories without '/'</li>
 *    <li> File server supports simple skipping for files (continue download) </li>
 *    <li> File server serves also very long files without memory overhead </li>
 *    <li> Contains a built-in list of most common mime types </li>
 *    <li> All header names are converted lowercase so they don't vary between browsers/clients </li>
 *
 * </ul>
 *
 * <p><b>Ways to use: </b><ul>
 *
 *    <li> Run as a standalone app, serves files and shows requests</li>
 *    <li> Subclass serve() and embed to your own program </li>
 *    <li> Call serveFile() from serve() with your own base directory </li>
 *
 * </ul>
 *
 * See the end of the source file for distribution license
 * (Modified BSD licence)
 */
public class NanoHTTPD
{
	private int getBestStreamableFile(String hashCode) throws TorrentException {
		FileEntry[] entries = libTorrent.getTorrentFiles(hashCode);
		long maxSize = 0;
		int index = -1;
		for (int i = 0; i < entries.length; ++i) {
			if (isStreamable(entries[i]) && entries[i].getSize() > maxSize) {
				maxSize = entries[i].getSize();
				index = i;
			}
		}
		return index;
	}

	private boolean isStreamable(FileEntry entry) {
		int index = entry.getPath().lastIndexOf('.');
		if (index != -1) {
			String extension = entry.getPath().substring(index + 1).toLowerCase();
			return mediaExts.contains(extension);
		}
		return false;
	}

	/**
	 * HTTP response.
	 * Return one of these from serve().
	 */
	public class Response
	{

		/**
		 * Basic constructor.
		 */
		public Response(String status, String mimeType, String hashCode, long transferOffset, long dataLength )
		{
			this.status = status;
			this.mimeType = mimeType;
			this.hashCode = hashCode;
			this.transferOffset = transferOffset;
			this.dataLength = dataLength;
		}
		
		public Response(String status, String mimeType)
		{
			this(status, mimeType, null, 0, 0);
		}

		/**
		 * Adds given line to the header.
		 */
		public void addHeader( String name, String value )
		{
			headers.put( name, value );
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getMimeType() {
			return mimeType;
		}

		public void setMimeType(String mimeType) {
			this.mimeType = mimeType;
		}

		public String getHashCode() {
			return hashCode;
		}

		public void setHashCode(String hashCode) {
			this.hashCode = hashCode;
		}

		public long getDataLength() {
			return dataLength;
		}

		public void setDataLength(long dataLength) {
			this.dataLength = dataLength;
		}

		public long getTransferOffset() {
			return transferOffset;
		}

		public Properties getHeaders() {
			return headers;
		}

		public void setHeaders(Properties headers) {
			this.headers = headers;
		}

		/**
		 * HTTP status code after processing, e.g. "200 OK", HTTP_OK
		 */
		private String status;

		/**
		 * MIME type of content, e.g. "text/html"
		 */
		private String mimeType;
		
		private long dataLength;
		
		private String hashCode;
		
		private long transferOffset;

		/**
		 * Headers for the HTTP response. Use addHeader()
		 * to add lines.
		 */
		private Properties headers = new Properties();
	}

	/**
	 * Some HTTP response status codes
	 */
	public static final String
		HTTP_OK = "200 OK",
		HTTP_PARTIALCONTENT = "206 Partial Content",
		HTTP_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable",
		HTTP_REDIRECT = "301 Moved Permanently",
		HTTP_NOTMODIFIED = "304 Not Modified",
		HTTP_FORBIDDEN = "403 Forbidden",
		HTTP_NOTFOUND = "404 Not Found",
		HTTP_BADREQUEST = "400 Bad Request",
		HTTP_INTERNALERROR = "500 Internal Server Error",
		HTTP_NOTIMPLEMENTED = "501 Not Implemented";

	/**
	 * Common mime types for dynamic content
	 */
	public static final String
		MIME_PLAINTEXT = "text/plain",
		MIME_HTML = "text/html",
		MIME_DEFAULT_BINARY = "application/octet-stream",
		MIME_XML = "text/xml";

	// ==================================================
	// Socket & server code
	// ==================================================

	/**
	 * Starts a HTTP server to given port.<p>
	 * Throws an IOException if the socket is already in use
	 */
	public NanoHTTPD( int port, File wwwroot , LibTorrent libTorrent) throws IOException
	{
		this.libTorrent = libTorrent;
		myTcpPort = port;
		if (!wwwroot.isDirectory() && !wwwroot.mkdirs()) {
			throw  new IOException(wwwroot + ": cant create directory");
		}
		this.myRootDir = wwwroot;
		ServerSocketChannel ssc = ServerSocketChannel.open();
		myServerSocket = ssc.socket();
		myServerSocket.bind(new InetSocketAddress("127.0.0.1", myTcpPort ));
		workers = Executors.newFixedThreadPool(8);
		sessions = new ArrayList<HTTPSession>();
		serving = true;
		myThread = new Thread( new Runnable()
			{
				public void run()
				{
					try
					{
						while( true ) {
							HTTPSession httpsess = new HTTPSession( myServerSocket.accept());
							if (serving) {
								synchronized (sessions) {
									sessions.add(httpsess);
								}
								workers.execute(httpsess);
							} else {
								httpsess.stop();
							}
						}
					}
					catch ( IOException ioe )
					{}
				}
			}, "NanoHttpd");
		myThread.setDaemon( true );
		myThread.start();
	}

	public NanoHTTPD(int port, String wwwRoot, LibTorrent torrent) throws IOException {
		this(port, new File(wwwRoot), torrent);
	}

	/**
	 * shutdowns the server.
	 */
	public void shutdown()
	{
		try
		{
			workers.shutdownNow();
			workers.awaitTermination(200000, TimeUnit.SECONDS);
			myServerSocket.close();
			myOut.println("httpd was stopped");
		}
		catch ( IOException ioe ) {}
		catch ( InterruptedException e ) {}
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
	 * Handles one session, i.e. parses the HTTP request
	 * and returns the response.
	 */
	private class HTTPSession implements Runnable
	{
		public HTTPSession( Socket s )
		{
			mySocket = s;
			streaming = true;
		}

		public void run()
		{
			try
			{
				InputStream is = mySocket.getInputStream();
				if ( is == null) return;
				BufferedReader hin = new BufferedReader( new InputStreamReader( is ));
				Properties pre = new Properties();
				Properties parms = new Properties();
				Properties header = new Properties();

				// Decode the header into parms and header java properties
				decodeHeader(hin, pre, parms, header);
				String method = pre.getProperty("method");
				String uri = pre.getProperty("uri");


				if (! method.equalsIgnoreCase( "GET" )) {
					sendError( HTTP_NOTIMPLEMENTED, "SERVER DOES NOT IMPLEMENTS THIS METHOD ");
				}

				// Ok, now do the serve()
				Response r = serve( uri, method, header, parms);
				if ( r == null )
					sendError( HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: Serve() returned a null response." );
				else
					sendResponse( r );
			}
			catch ( IOException ioe )
			{
				try
				{
					sendError( HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
				}
				catch ( Throwable t ) {}
			}
			catch ( InterruptedException ie )
			{
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

		/**
		 * Decodes the sent headers and loads the data into
		 * java Properties' key - value pairs
		**/
		private  void decodeHeader(BufferedReader in, Properties pre, Properties parms, Properties header)
			throws InterruptedException
		{
			try {
				// Read the request line
				String inLine = in.readLine();
				if (inLine == null) return;
				StringTokenizer st = new StringTokenizer( inLine );
				if ( !st.hasMoreTokens())
					sendError( HTTP_BADREQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html" );

				String method = st.nextToken();
				pre.put("method", method);

				if ( !st.hasMoreTokens())
					sendError( HTTP_BADREQUEST, "BAD REQUEST: Missing URI. Usage: GET /example/file.html" );

				String uri = st.nextToken();

				// Decode parameters from the URI
				int qmi = uri.indexOf( '?' );
				if ( qmi >= 0 )
				{
					decodeParms( uri.substring( qmi+1 ), parms );
					uri = decodePercent( uri.substring( 0, qmi ));
				}
				else uri = decodePercent(uri);

				// If there's another token, it's protocol version,
				// followed by HTTP headers. Ignore version but parse headers.
				// NOTE: this now forces header names lowercase since they are
				// case insensitive and vary by client.
				if ( st.hasMoreTokens())
				{
					String line = in.readLine();
					while ( line != null && line.trim().length() > 0 )
					{
						int p = line.indexOf( ':' );
						if ( p >= 0 )
							header.put( line.substring(0,p).trim().toLowerCase(), line.substring(p+1).trim());
						line = in.readLine();
					}
				}

				pre.put("uri", uri);
			}
			catch ( IOException ioe )
			{
				sendError( HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
			}
		}

		/**
		 * Decodes the percent encoding scheme. <br/>
		 * For example: "an+example%20string" -> "an example string"
		 */
		private String decodePercent( String str ) throws InterruptedException
		{
			try
			{
				StringBuffer sb = new StringBuffer();
				for( int i=0; i<str.length(); i++ )
				{
					char c = str.charAt( i );
					switch ( c )
					{
						case '+':
							sb.append( ' ' );
							break;
						case '%':
							sb.append((char)Integer.parseInt( str.substring(i+1,i+3), 16 ));
							i += 2;
							break;
						default:
							sb.append( c );
							break;
					}
				}
				return sb.toString();
			}
			catch( Exception e )
			{
				sendError( HTTP_BADREQUEST, "BAD REQUEST: Bad percent-encoding." );
				return null;
			}
		}

		/**
		 * Decodes parameters in percent-encoded URI-format
		 * ( e.g. "name=Jack%20Daniels&pass=Single%20Malt" ) and
		 * adds them to given Properties. NOTE: this doesn't support multiple
		 * identical keys due to the simplicity of Properties -- if you need multiples,
		 * you might want to replace the Properties with a Hashtable of Vectors or such.
		 */
		private void decodeParms( String parms, Properties p )
			throws InterruptedException
		{
			if ( parms == null )
				return;

			StringTokenizer st = new StringTokenizer( parms, "&" );
			while ( st.hasMoreTokens())
			{
				String e = st.nextToken();
				int sep = e.indexOf( '=' );
				if ( sep >= 0 )
					p.put( decodePercent( e.substring( 0, sep )).trim(),
						   decodePercent( e.substring( sep+1 )));
			}
		}

		/**
		 * Returns an error message as a HTTP response and
		 * throws InterruptedException to stop further request processing.
		 */
		private void sendError( String status, String msg ) throws InterruptedException {
			try
			{
				if ( status == null )
					throw new Error( "sendResponse(): Status can't be null." );

				PrintWriter pw = new PrintWriter( mySocket.getOutputStream() );
				pw.print("HTTP/1.0 " + status + " \r\n");
				pw.print("Content-Type: " + MIME_PLAINTEXT + "\r\n");
				pw.print("Date: " + gmtFrmt.format( new Date()) + "\r\n");
				pw.print("Accept-Ranges: bytes\r\n");
				pw.print("\r\n");
				pw.flush();

				if ( msg != null )
				{
					pw.write( msg);
				}
				pw.flush();
				pw.close();
			}
			catch( IOException ioe )
			{
			}
			throw new InterruptedException();
		}

		/**
		 * Sends given response to the socket.
		 * @throws InterruptedException 
		 */
		private void sendResponse( Response response ) throws InterruptedException
		{
			String status = response.getStatus();
			String mime = response.getMimeType();
			Properties header = response.getHeaders();
			int lastSet = 0;
			String hashCode = response.getHashCode();
			try
			{
				if ( status == null )
					throw new Error( "sendResponse(): Status can't be null." );

				PrintWriter pw = new PrintWriter( mySocket.getOutputStream() );
				pw.print("HTTP/1.0 " + status + " \r\n");

				if ( mime != null )
					pw.print("Content-Type: " + mime + "\r\n");

				if ( header == null || header.getProperty( "Date" ) == null )
					pw.print( "Date: " + gmtFrmt.format( new Date()) + "\r\n");

				if ( header != null )
				{
					Enumeration e = header.keys();
					while ( e.hasMoreElements())
					{
						String key = (String)e.nextElement();
						String value = header.getProperty( key );
						pw.print( key + ": " + value + "\r\n");
					}
				}

				pw.print("\r\n");
				pw.flush();

				if ( hashCode != null )
				{
					int state = libTorrent.getTorrentState(hashCode);
					if (state == -1) {
						return;
					}
					Average streamRate = Average.getInstance( 1000, 20 );
					long pending = response.getDataLength();	// This is to support partial sends, see serveFile()

					//TODO transfer data when downloading torrent
					int pieceSize = libTorrent.getPieceSize(hashCode, false);
				//	int PIECE_BUFFER_SIZE = 300 * 1024 * 30 / pieceSize;
					long timeToWait = (pieceSize * 10000l) / (300 * 1024);
					long transferOffset = response.getTransferOffset();
					int streamPiece = (int) (transferOffset / pieceSize);
					int setRead = -1;
					int transferPieceIdx = streamPiece;
					int transferPieceOffset = (int) (transferOffset - transferPieceIdx * pieceSize);
					if (transferOffset > 0) {
						//TODO clear piece deadline
//						libTorrent.clearPiecesDeadline(hashCode);
					}
					
					
					int pieceNum = libTorrent.getPieceNum(hashCode);
					lastSet = streamPiece;
					int incompleteIdx = lastSet;
					int numSet =  0;
					byte[] buff = new byte[pieceSize];
					ByteBuffer readBuffer = ByteBuffer.allocate(1024);
					mySocket.setSendBufferSize(pieceSize);
					SocketChannel sc = mySocket.getChannel();
					sc.configureBlocking(false);
					int cancelPiece = 0;
					long lastTime = System.currentTimeMillis();
					boolean cancelled = false;
					while (streaming && pending>0 && !Thread.currentThread().isInterrupted())
					{
						if (state != 4 && state != 5 && state != 3) {
							state = libTorrent.getTorrentState(hashCode);
						}
						int PIECE_BUFFER_SIZE = computePieceBufferSize(hashCode, pieceSize, streamRate);
						System.err.println("PIECE_BUFFER_SIZE = " + PIECE_BUFFER_SIZE);
						if (state != 4 && state != 5 && streamPiece + PIECE_BUFFER_SIZE > incompleteIdx) {
							incompleteIdx = libTorrent.getFirstPieceIncomplete(hashCode, transferOffset);
							long currentTime = System.currentTimeMillis();
							if (cancelPiece != incompleteIdx) {
								if (cancelled && cancelPiece + PIECE_BUFFER_SIZE * 2 / 3 > incompleteIdx) {
									libTorrent.cancelTorrentPiece(hashCode, incompleteIdx);
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
							numSet =  incompleteIdx + PIECE_BUFFER_SIZE - lastSet;
							if (numSet > 0) {
		//						System.err.println("set deadline: [" + lastSet + ", " + (lastSet + numSet) + ")");
								for (int i = 0; i < numSet && i + lastSet < pieceNum; ++i) {
									libTorrent.setPieceDeadline(hashCode, lastSet + i, i *150 + 1500 );
									if (lastSet + i + PIECE_BUFFER_SIZE < pieceNum) {
										libTorrent.setPiecePriority(hashCode, lastSet + i + PIECE_BUFFER_SIZE, 7);
									}
								}
								lastSet += numSet;
							}
							if (streamPiece == incompleteIdx) {
								System.err.println("wait for libtorrent download data...");
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
						int len = libTorrent.readTorrentPiece(hashCode, streamPiece, buff);
						if (len == -1) {
							break;
						} else if (len == 0) {
							Thread.sleep(50);
							continue;
						}
						int offset = (streamPiece == transferPieceIdx)? transferPieceOffset : 0;
						len = len - offset;
						if (len > pending) {
							len = (int) pending;
						}
						writeData(sc, buff, offset, len, streamRate);
						pending -= len;
						++streamPiece;
					}
				}
				pw.flush();
				pw.close();
			}
			catch( Exception e )
			{
//				System.err.println("close stream: " + response.getTransferOffset() + " due: " + e.getMessage());
//				e.printStackTrace();
			} finally {
				if (lastSet != 0) {
//					for (int i = 0; i < RANGE; ++i) {
//						libTorrent.resetPieceDeadline(hashCode, lastSet - i -1);
//						libTorrent.setPiecePriority(hashCode, lastSet + i, 3);
//					}
				}
			}
		}
		
		private void writeData(SocketChannel sc, byte[] buff, int offset, int len, Average streamRate) throws IOException, InterruptedException {
			ByteBuffer buffer = ByteBuffer.wrap(buff, offset, len); //TODO Need tunning
			sc.write(buffer);
			int writeLen = 0;
			while (buffer.hasRemaining()) {
				Thread.sleep(50);
				writeLen = sc.write(buffer);
				streamRate.addValue(writeLen);
			}
		}
		
		private int computePieceBufferSize(String hashCode, int pieceSize, Average streamRate) {
	   		
	   		long	rate = streamRate.getAverage();
   			try {
   				long downRate = libTorrent.getTorrentDownloadRate(hashCode, true);
				rate = rate > 0 ? (rate + downRate) / 2 : downRate;
			} catch (TorrentException e) {
				e.printStackTrace();
			}
	   		
	   		int	buffer_secs = DEFAULT_BUFFER_SECS;
	   		
	   		long	buffer_bytes = ( buffer_secs * rate );
	   		
	   		int	pieces_to_buffer = (int)( buffer_bytes / pieceSize );
	   		
	   		if ( pieces_to_buffer < DEFAULT_MIN_PIECES_TO_BUFFER ){
	   			
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
		 * Override this to customize the server.<p>
		 *
		 * (By default, this delegates to serveFile() and allows directory listing.)
		 *
		 * @param uri	Percent-decoded URI without parameters, for example "/index.cgi"
		 * @param method	"GET", "POST" etc.
		 * @param parms	Parsed, percent decoded parameters from URI and, in case of POST, data.
		 * @param header	Header entries, percent decoded
		 * @return HTTP response, see class Response for details
		 * @throws InterruptedException 
		 */
		Response serve( String uri, String method, Properties header, Properties parms ) throws InterruptedException
		{
			Response res = null;
			// Remove URL arguments
			uri = uri.trim().replace( File.separatorChar, '/' );
			if ( uri.indexOf( '?' ) >= 0 )
				uri = uri.substring(0, uri.indexOf( '?' ));
	
			// Prohibit getting out of current directory
			if ( uri.startsWith( ".." ) || uri.endsWith( ".." ) || uri.indexOf( "../" ) >= 0 )
				sendError(HTTP_FORBIDDEN, "FORBIDDEN: Won't serve ../ for security reasons.");
					
		
			try
			{
				String hashCode = uri.substring(1);
				String file = parms.getProperty(FILE_PARAM);
				int index = 0;
				if (file != null) {
					index = Integer.parseInt(file);
				} else {
					index = getBestStreamableFile(hashCode);
				}
				FileEntry[] entries = libTorrent.getTorrentFiles(hashCode);
				File f = new File(myRootDir, entries[index].getPath());
				long fileOffset = entries[index].getOffset();
				

				// Get MIME type from file name extension, if possible
				String mime = null;
				int dot = f.getCanonicalPath().lastIndexOf( '.' );
				if ( dot >= 0 )
					mime = (String)theMimeTypes.get( f.getCanonicalPath().substring( dot + 1 ).toLowerCase());
				if ( mime == null )
					mime = MIME_DEFAULT_BINARY;
	
				// Calculate etag
				String etag = Integer.toHexString((f.getAbsolutePath() + f.lastModified() + "" + f.length()).hashCode());
	
				// Support (simple) skipping:
				long startFrom = 0;
				long endAt = -1;
				String range = header.getProperty( "range" );
				if ( range != null )
				{
					if ( range.startsWith( "bytes=" ))
					{
						range = range.substring( "bytes=".length());
						int minus = range.indexOf( '-' );
						try {
							if ( minus > 0 )
							{
								startFrom = Long.parseLong( range.substring( 0, minus ));
								endAt = Long.parseLong( range.substring( minus+1 ));
							}
						}
						catch ( NumberFormatException nfe ) {}
					}
				}
	
				// Change return code and add Content-Range header when skipping is requested
				long fileLen = entries[index].getSize();
				if (range != null && startFrom >= 0)
				{
					if ( startFrom >= fileLen)
					{
						res = new Response(HTTP_RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT);
						res.addHeader( "Content-Range", "bytes 0-0/" + fileLen);
						res.addHeader( "ETag", etag);
					}
					 else {
						if ( endAt < 0 )
							endAt = fileLen-1;
						long newLen = endAt - startFrom + 1;
						if ( newLen < 0 ) newLen = 0;
	
						final long dataLen = newLen;
						if (method.equalsIgnoreCase("HEAD")) {
							res = new Response(HTTP_PARTIALCONTENT, mime );
						} else {
							res = new Response(HTTP_PARTIALCONTENT, mime, hashCode, fileOffset + startFrom, dataLen);
						}
						res.addHeader( "Content-Length", "" + dataLen);
						res.addHeader( "Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
						res.addHeader( "ETag", etag);
					}
				}
				else
				{
					if (etag.equals(header.getProperty("if-none-match")))
						res = new Response(HTTP_NOTMODIFIED, mime);
					else
					{
						res = method.equalsIgnoreCase("HEAD") ? new Response(HTTP_OK, mime) : new Response(HTTP_OK, mime, hashCode, fileOffset, fileLen);
						res.addHeader( "Content-Length", "" + fileLen);
						res.addHeader( "ETag", etag);
					}
				}
			
			} catch (NumberFormatException e) {
				sendError(HTTP_NOTFOUND, "Error 404, file not found.");
			}
			catch( IOException ioe )
			{
				sendError(HTTP_FORBIDDEN, "FORBIDDEN: Reading file failed.");
			} catch (TorrentException e) {
				sendError(HTTP_NOTFOUND, "Error 404, file not found.");
			}
		
			res.addHeader( "Accept-Ranges", "bytes"); // Announce that the file server accepts partial content requestes
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
	private static int		DEFAULT_BUFFER_SECS = 60;
	private static int		DEFAULT_MIN_PIECES_TO_BUFFER = 5;
	private static String FILE_PARAM = "file";
	// ==================================================
	// File server code
	// ==================================================


	/**
	 * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
	 */
	private static Map<String, String> theMimeTypes = new HashMap<String, String>();
	private static Set<String> mediaExts = new HashSet<String>();
	static
	{
		String[] extensions = new String[] {"mp3", "mp4", "ogv", "flv", "mov", "mkv", "avi", "asf", "wmv"};
		mediaExts.addAll(Arrays.asList(extensions));
		StringTokenizer st = new StringTokenizer(
			"css		text/css "+
			"htm		text/html "+
			"html		text/html "+
			"xml		text/xml "+
			"txt		text/plain "+
			"asc		text/plain "+
			"gif		image/gif "+
			"jpg		image/jpeg "+
			"jpeg		image/jpeg "+
			"png		image/png "+
			"mp3		audio/mpeg "+
			"m3u		audio/mpeg-url " +
			"mp4		video/mp4 " +
			"avi		video/avi " +
			"ogv		video/ogg " +
			"flv		video/x-flv " +
			"wmv		video/x-ms-wmv " +
			"mov		video/quicktime " +
			"asf		video/x-ms-asf " +
			"swf		application/x-shockwave-flash " +
			"js			application/javascript "+
			"pdf		application/pdf "+
			"doc		application/msword "+
			"ogg		application/x-ogg "+
			"zip		application/octet-stream "+
			"exe		application/octet-stream "+
			"class		application/octet-stream " );
		while ( st.hasMoreTokens())
			theMimeTypes.put( st.nextToken(), st.nextToken());
	}
	
	

	


	// Change this if you want to log to somewhere else than stdout
	protected static PrintStream myOut = System.out; 

	/**
	 * GMT date formatter
	 */
	private static java.text.SimpleDateFormat gmtFrmt;
	static
	{
		gmtFrmt = new java.text.SimpleDateFormat( "E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
		gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	/**
	 * The distribution licence
	 */
	private static final String LICENCE =
		"Copyright (C) 2001,2005-2011 by Jarno Elonen <elonen@iki.fi>\n"+
		"and Copyright (C) 2010 by Konstantinos Togias <info@ktogias.gr>\n"+
		"\n"+
		"Redistribution and use in source and binary forms, with or without\n"+
		"modification, are permitted provided that the following conditions\n"+
		"are met:\n"+
		"\n"+
		"Redistributions of source code must retain the above copyright notice,\n"+
		"this list of conditions and the following disclaimer. Redistributions in\n"+
		"binary form must reproduce the above copyright notice, this list of\n"+
		"conditions and the following disclaimer in the documentation and/or other\n"+
		"materials provided with the distribution. The name of the author may not\n"+
		"be used to endorse or promote products derived from this software without\n"+
		"specific prior written permission. \n"+
		" \n"+
		"THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR\n"+
		"IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES\n"+
		"OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.\n"+
		"IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,\n"+
		"INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT\n"+
		"NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,\n"+
		"DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY\n"+
		"THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT\n"+
		"(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE\n"+
		"OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.";
}

