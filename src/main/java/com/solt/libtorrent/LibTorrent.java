package com.solt.libtorrent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LibTorrent {
	private static final String LIBTORRENT_DLL = "libtorrent.dll";
	private static final Set<String> mediaExts = new HashSet<String>();

	static {
		loadLibraryFromJar();
		String[] extensions = new String[] {"mp3", "mp4", "ogv", "flv", "mov", "mkv", "avi", "asf", "wmv"};
		mediaExts.addAll(Arrays.asList(extensions));
	}

	private static void loadLibraryFromJar() {
		AccessController.doPrivileged(new PrivilegedAction<Void>() {
			public Void run() {

				File tmpDir = new File(System.getProperty("java.io.tmpdir"));
				File tmpFile = new File(tmpDir, LIBTORRENT_DLL);
				InputStream in = null;
				OutputStream out = null;
				try {
					in = getClass().getResourceAsStream("/" + LIBTORRENT_DLL);
					out = new FileOutputStream(tmpFile);

					byte[] buf = new byte[8192];
					int len;
					while ((len = in.read(buf)) != -1) {
						out.write(buf, 0, len);
					}
				} catch (Exception e) {
					// deal with exception
					e.printStackTrace();
				} finally {
					if (in != null) {
						try {
							in.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					if (out != null) {
						try {
							out.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				System.load(tmpFile.getAbsolutePath());
				System.out.println("loaded");
				return null;
			}
		});
	}

	// public static final LibTorrent Instance = new LibTorrent();
	//
	// @Override
	// public Object clone() throws CloneNotSupportedException {
	// throw new CloneNotSupportedException();
	// }
	//
	// private LibTorrent(){};
	// -----------------------------------------------------------------------------
	/**
	 * create session which listens on a given port and has defaultSave path.
	 * It's also set download/upload limit speed. This method only was called
	 * one time
	 * 
	 * @param listenPort
	 * @param defaultSave
	 * @param uploadLimit
	 * @param downloadLimit
	 * @return true if successful and false if otherwise
	 */
	public native boolean setSession(int listenPort, String defaultSave,
			int uploadLimit, int downloadLimit);

	/**
	 * Same as {@code LibTorrent#setSession(listenPort, defaultSave, 0, 0)}.<br/>
	 * See {@link LibTorrent#setSession(int, String, int, int)} for more
	 * information
	 * 
	 * @param listenPort
	 * @param defaultSave
	 * @return true if successful and false if otherwise
	 */
	public boolean setSession(int listenPort, String defaultSave) {
		return setSession(listenPort, defaultSave, 0, 0);
	}

	/**
	 * Set proxy for session, supports following proxy types<br>
	 * proxy_type:<br>
	 * <ul>
	 * <li>0 - none, // a plain tcp socket is used, and the other settings are
	 * ignored.
	 * <li>1 - socks4, // socks4 server, requires username.
	 * <li>2 - socks5, // the hostname and port settings are used to connect to
	 * the proxy. No username or password is sent.
	 * <li>3 - socks5_pw, // the hostname and port are used to connect to the
	 * proxy. the username and password are used to authenticate with the proxy
	 * server.
	 * <li>4 - http, // the http proxy is only available for tracker and web
	 * seed traffic assumes anonymous access to proxy
	 * <li>5 - http_pw // http proxy with basic authentication uses username and
	 * password
	 * </ul>
	 * 
	 * @param type
	 * @param hostName
	 * @param port
	 * @param userName
	 * @param password
	 * @return true if successful and false if otherwise
	 */
	public native boolean setProxy(int type, String hostName, int port,
			String userName, String password);

	// -----------------------------------------------------------------------------
	/**
	 * Start/stop services: <i>DHT, Local Service Discovery, UPnP, NATPMP</i>. Set
	 * true to start and false to stop corresponding service.<br>
	 * When started, the listen port and the DHT port are attempted to be
	 * forwarded on local UPnP router devices.
	 * 
	 * @param dht to start DHT
	 * 
	 * @param lsd
	 *            to start service LSD
	 * @param upnp
	 *            to start service UPNP
	 * @param natpmp
	 *            to start service NATPMP
	 * @return true if successful and false if otherwise
	 */
	public native boolean setSessionOptions(boolean dht, boolean lsd, boolean upnp,
			boolean natpmp);

	// -----------------------------------------------------------------------------

	/**
	 * Add torrent to download, same as
	 * {@code LibTorrent.addTorrent(torentFile, storageMode, true)}<br>
	 * See {@link LibTorrent#addTorrent(String, int, boolean)} for more
	 * information. <br>
	 * <br>
	 * StorageMode:
	 * <ul>
	 * <li>0-storage_mode_allocate
	 * <li>1-storage_mode_sparse
	 * <li>2-storage_mode_compact
	 * </ul>
	 * 
	 * @param torentFile
	 * @param storageMode
	 * @return hashCode of added torrent or <code>null</code> if error occurs
	 */
	public String addTorrent(String torentFile, int storageMode) {
		return addTorrent(torentFile, storageMode, true);
	}

	/**
	 * Add torrent to download. If <i>autoManaged</i> is true then added torrent
	 * will be auto managed <br>
	 * <br>
	 * StorageMode:
	 * <ul>
	 * <li>0-storage_mode_allocate
	 * <li>1-storage_mode_sparse
	 * <li>2-storage_mode_compact
	 * </ul>
	 * 
	 * @param torentFile
	 * @param storageMode
	 * @return hashCode of added torrent or <code>null</code> if error occurs
	 */
	public native String addTorrent(String torentFile, int storageMode,
			boolean autoManaged);

	// TODO implement
	//public native String addTorrent(URI magnetLink, int storageMode,
	//		boolean autoManaged);

	// TODO implement
	public native boolean saveResumeData();

	// -----------------------------------------------------------------------------
	/**
	 * Perform pausing session. Pausing the session has the same effect as
	 * pausing every torrent in it, except that torrents will not be resumed by
	 * the auto-manage mechanism. Resuming will restore the torrents to their
	 * previous paused state. i.e. the session pause state is separate from the
	 * torrent pause state. A torrent is inactive if it is paused or if the
	 * session is paused.
	 * 
	 * @return true if successful and false if otherwise
	 */
	public native boolean pauseSession();

	// -----------------------------------------------------------------------------
	/**
	 * Perform resuming session. Resuming will restore the torrents to their
	 * previous paused state. i.e. the session pause state is separate from the
	 * torrent pause state.
	 * 
	 * @return true if successful and false if otherwise
	 */
	public native boolean resumeSession();

	// -----------------------------------------------------------------------------
	/**
	 * abort session
	 * 
	 * @return true if successful and false if otherwise
	 */
	public native boolean abortSession();

	// -----------------------------------------------------------------------------
	/**
	 * remove torrent which has a given hashCode and close all peer connections
	 * associated with the torrent, tell the tracker that we've stopped
	 * participating in the swarm. The deleteData second argument options can be
	 * used to delete all the files downloaded by this torrent.
	 * 
	 * @param hashCode
	 *            the hash code of torrent to remove
	 * @param deleteData
	 *            flag to delete files data
	 * @return true if successful and false if otherwise
	 * @throws TorrentException
	 */
	public native boolean removeTorrent(String hashCode, boolean deleteData)
			throws TorrentException;

	// -----------------------------------------------------------------------------
	/**
	 * pause torrent which has a given hashCode and disconnect all peers
	 * Torrents may be paused automatically if there is a file error (e.g. disk
	 * full) or something similar. Torrents that are auto-managed may be
	 * automatically resumed again. It does not make sense to pause an
	 * auto-managed torrent without making it not automanaged first. Torrents
	 * are auto-managed by default when added to the session.
	 * 
	 * @param hashCode
	 * @return true if successful and false if otherwise
	 * @throws TorrentException
	 */
	public native boolean pauseTorrent(String hashCode) throws TorrentException;

	// -----------------------------------------------------------------------------
	/**
	 * resume torrent which has a given hashCode and reconnect all peers
	 * 
	 * @param hashCode
	 * @return true if successful and false if otherwise
	 * @throws TorrentException
	 */
	public native boolean resumeTorrent(String hashCode)
			throws TorrentException;

	// -----------------------------------------------------------------------------
	/**
	 * get percent of downloaded torrent.
	 * 
	 * @param hashCode
	 * @return
	 * @throws TorrentException
	 */
	public native int getTorrentProgress(String hashCode)
			throws TorrentException;

	/**
	 * Get torrent's progress size. The flags parameter can be used to specify
	 * the granularity of the torrent progress. If flag = 0, the progress will
	 * be as accurate as possible, but also more expensive to calculate. If flag
	 * = 1 is specified, the progress will be specified in piece granularity.
	 * i.e. only pieces that have been fully downloaded and passed the hash
	 * check count. When specifying piece granularity, the operation is a lot
	 * cheaper, since libtorrent already keeps track of this internally and no
	 * calculation is required.
	 * 
	 * @param hashCode
	 * @param flags
	 * @return
	 * @throws TorrentException
	 */
	public native long getTorrentProgressSize(String hashCode, int flags)
			throws TorrentException;

	/**
	 * get number continuous bytes from a given offset
	 * 
	 * @param hashCode
	 * @param offset
	 * @return
	 */
	public native long getTorrentContinuousSize(String hashCode, long offset)
			throws TorrentException;

	/**
	 * set read operation of the specified piece from torrent. You must have
	 * completed the download of the specified piece before calling this
	 * function. Note that if you read multiple pieces, the read operations are
	 * not guaranteed to finish in the same order as you initiated them.
	 * 
	 * @param hashCode
	 * @param pieceIdx
	 * @throws TorrentException
	 */
	public native void setTorrentReadPiece(String hashCode, int pieceIdx)
			throws TorrentException;

	/**
	 * asynchronous read the specified piece from torrent. In order to read
	 * piece, it must call {@link #setTorrentReadPiece(String, int)} first.
	 * buffer'size must be large enough to hold piece's data
	 * 
	 * @param hashCode
	 * @param pieceIdx
	 * @param buffer
	 * @return The number of bytes read, possibly zero
	 * @throws TorrentException
	 */
	public native int readTorrentPiece(String hashCode, int pieceIdx,
			byte[] buffer) throws TorrentException;

	/**
	 * sets the session-global limits of download rate limits, in bytes per
	 * second.
	 * 
	 * @param downloadRate
	 */
	public native void setDownloadRateLimit(int downloadRate);

	/**
	 * gets the session-global limits of download rate limits, in bytes per
	 * second.
	 * 
	 * @return
	 */
	public native int getDownloadRateLimit();

	/**
	 * sets the session-global limits of upload rate limits, in bytes per
	 * second.
	 * 
	 * @param uploadRate
	 */
	public native void setUploadRateLimit(int uploadRate);

	/**
	 * gets the session-global limits of upload rate limits, in bytes per
	 * second.
	 * 
	 * @return
	 */
	public native int getUploadRateLimit();

	/**
	 * limit the download bandwidth used by this particular torrent to the limit
	 * you set. It is given as the number of bytes per second the torrent is
	 * allowed to download
	 * 
	 * @param hashCode
	 * @param downloadRate
	 * @throws TorrentException
	 */
	public native void setTorrentDownloadLimit(String hashCode, int downloadRate)
			throws TorrentException;

	/**
	 * get current download limit setting of a given torrent
	 * 
	 * @param hashCode
	 * @return
	 * @throws TorrentException
	 */
	public native int getTorrentDownloadLimit(String hashCode)
			throws TorrentException;

	/**
	 * get download rate of a given torrent hashCode. The rates are given as the
	 * number of bytes per second. If payload specified be true then the total
	 * transfer rate of payload only, not counting protocol chatter. This might
	 * be slightly smaller than the other rates, but if projected over a long
	 * time (e.g. when calculating ETA:s) the difference may be noticeable.
	 * 
	 * @param hashCode
	 * @param payload
	 * @return
	 * @throws TorrentException
	 */
	public native int getTorrentDownloadRate(String hashCode, boolean payload)
			throws TorrentException;

	/**
	 * limit the upload bandwidth used by this particular torrent to the limit
	 * you set. It is given as the number of bytes per second the torrent is
	 * allowed to upload. Note that setting a higher limit on a torrent then the
	 * global limit will not override the global rate limit. The torrent can
	 * never upload more than the global rate limit.
	 * 
	 * @param hashCode
	 * @param uploadRate
	 * @throws TorrentException
	 */
	public native void setTorrentUploadLimit(String hashCode, int uploadRate)
			throws TorrentException;

	/**
	 * get current upload limit setting of a given torrent
	 * 
	 * @param hashCode
	 * @return
	 * @throws TorrentException
	 */
	public native int getTorrentUploadLimit(String hashCode)
			throws TorrentException;

	/**
	 * Explicitly sets the upload mode of the torrent. In upload mode, the
	 * torrent will not request any pieces. If the torrent is auto managed, it
	 * will automatically be taken out of upload mode periodically. Torrents are
	 * automatically put in upload mode whenever they encounter a disk write
	 * error.
	 * 
	 * @param hashCode
	 * @param mode
	 *            should be true to enter upload mode, and false to leave it.
	 * @throws TorrentException
	 */
	public native void setUploadMode(String hashCode, boolean mode)
			throws TorrentException;

	/**
	 * test if a torrent is in auto managed mode
	 * 
	 * @param hashCode
	 * @return true if torrent is in auto managed mode or false otherwise
	 * @throws TorrentException
	 */
	public native boolean isAutoManaged(String hashCode)
			throws TorrentException;

	/**
	 * <p>
	 * sets the auto managed mode of the torrent. Torrents that are auto managed
	 * are subject to the queuing and the active torrents limits. To make a
	 * torrent auto managed, set auto_managed to true when adding the torrent.
	 * </p>
	 * <p>
	 * The limits of the number of downloading and seeding torrents are
	 * controlled via active_downloads, active_seeds and active_limit in
	 * session_settings. These limits takes non auto managed torrents into
	 * account as well. If there are more non-auto managed torrents being
	 * downloaded than the active_downloads setting, any auto managed torrents
	 * will be queued until torrents are removed so that the number drops below
	 * the limit.
	 * </p>
	 * <p>
	 * The default values are 8 active downloads and 5 active seeds.
	 * </p>
	 * At a regular interval, torrents are checked if there needs to be any
	 * re-ordering of which torrents are active and which are queued. This
	 * interval can be controlled via auto_manage_interval in session_settings.
	 * It defaults to every 30 seconds.
	 * 
	 * @param hashCode
	 * @param auto
	 *            should be true to enter auto managed mode, and false to leave
	 *            it.
	 * @throws TorrentException
	 */
	public native void setAutoManaged(String hashCode, boolean auto)
			throws TorrentException;

	/**
	 * get the total number of pieces of a given hashCode torrent
	 * 
	 * @param hashCode
	 * @return total number of pieces
	 * @throws TorrentException
	 */
	public native int getPieceNum(String hashCode) throws TorrentException;

	/**
	 * get the number of byte for each piece. All pieces will always be the same
	 * except in the case of the last piece, which may be smaller.
	 * 
	 * @param hashCode
	 * @param isLastPiece
	 *            be true to get number bytes of last piece
	 * @return the number of byte for piece.
	 * @throws TorrentException
	 */
	public native int getPieceSize(String hashCode, boolean isLastPiece)
			throws TorrentException;

	/**
	 * get index of first in-completed piece from a given offset
	 * 
	 * @param hashCode
	 * @param offset
	 * @return index of first in-completed piece
	 * @throws TorrentException
	 */
	public native int getFirstPieceIncomplete(String hashCode, long offset)
			throws TorrentException;

	/**
	 * <p>
	 * These functions are used to set the prioritiy of individual pieces. By
	 * default all pieces have priority 1. That means that the random rarest
	 * first algorithm is effectively active for all pieces. You may however
	 * change the priority of individual pieces. There are 8 different priority
	 * levels:
	 * <ul>
	 * <li>0: piece is not downloaded at all
	 * <li>1: normal priority. Download order is dependent on availability
	 * <li>2: higher than normal priority. Pieces are preferred over pieces with
	 * the same availability, but not over pieces with lower availability
	 * <li>3: pieces are as likely to be picked as partial pieces.
	 * <li>4: pieces are preferred over partial pieces, but not over pieces with
	 * lower availability
	 * <li>5: currently the same as 4
	 * <li>6: piece is as likely to be picked as any piece with availability 1
	 * <li>7: maximum priority, availability is disregarded, the piece is
	 * preferred over any other piece with lower priority
	 * </ul>
	 * 
	 * @param hashCode
	 * @param pieceIdx
	 * @param priorityLevel
	 * @throws TorrentException
	 */
	public native void setPiecePriority(String hashCode, int pieceIdx,
			int priorityLevel) throws TorrentException;

	/**
	 * gets the priority for an individual piece, specified by pieceIdx
	 * 
	 * @param hashCode
	 * @param pieceIdx
	 * @return piece's priority
	 * @throws TorrentException
	 */
	public native int getPiecePriority(String hashCode, int pieceIdx)
			throws TorrentException;

	// TODO implementation
	public native void setPiecePriorities(String hashCode, byte[] priorities)
			throws TorrentException;

	// TODO implementation
	public native byte[] getPiecePriorities(String hashCode)
			throws TorrentException;

	/**
	 * This function sets or the deadline associated with a specific piece index
	 * (pieceIdx). libtorrent will attempt to download this entire piece before
	 * the deadline expires. This is not necessarily possible, but pieces with a
	 * more recent deadline will always be prioritized over pieces with a
	 * deadline further ahead in time. The deadline of a piece can be changed by
	 * calling this function again.
	 * 
	 * @param hashCode
	 * @param pieceIdx
	 * @param deadline
	 *            is the number of milliseconds until this piece should be
	 *            completed.
	 * @throws TorrentException
	 */
	public void setPieceDeadline(String hashCode, int pieceIdx, int deadline)
			throws TorrentException {
		setPieceDeadline(hashCode, pieceIdx, deadline, false);
	}

	/**
	 * <p>
	 * This function sets the deadline associated with a specific piece index
	 * (pieceIdx). libtorrent will attempt to download this entire piece before
	 * the deadline expires. This is not necessarily possible, but pieces with a
	 * more recent deadline will always be prioritized over pieces with a
	 * deadline further ahead in time. The deadline (and readPiece) of a piece
	 * can be changed by calling this function again.
	 * <p>
	 * The readPiece parameter can be used to ask libtorrent to call
	 * {@link #setTorrentReadPiece(String, int)} once the piece has been
	 * downloaded
	 * 
	 * @param hashCode
	 * @param pieceIdx
	 * @param deadline
	 *            is the number of milliseconds until this piece should be
	 *            completed.
	 * @param readPiece
	 * @throws TorrentException
	 */
	public native void setPieceDeadline(String hashCode, int pieceIdx,
			int deadline, boolean readPiece) throws TorrentException;

	/**
	 * reset_piece_deadline removes the deadline from the piece. If it hasn't
	 * already been downloaded, it will no longer be considered a priority.
	 * 
	 * @param hashCode
	 * @param pieceIdx
	 * @throws TorrentException
	 */
	public native void resetPieceDeadline(String hashCode, int pieceIdx)
			throws TorrentException;

	// TODO test implementation
	public native void clearPiecesDeadline(String hashCode)
			throws TorrentException;

	public PartialPieceInfo getPartialPieceInfo(String hashCode, int pieceIdx)
			throws TorrentException {
		int[] fields = new int[2];
		int[] blocks = initPartialPiece(hashCode, pieceIdx, fields);
		if (blocks != null) {
			return new PartialPieceInfo(pieceIdx, fields[0], fields[1], blocks);
		}
		return null;
	}

	public native void cancelTorrentPiece(String hashCode, int pieceIdx)
			throws TorrentException;

	// -----------------------------------------------------------------------------
	// TODO test implementation already synchronize
	private native int[] initPartialPiece(String hashCode, int pieceIdx,
			int[] fields) throws TorrentException;

	// TODO test implementation already synchronize
	public native PartialPieceInfo[] getPieceDownloadQueue(String hashCode)
			throws TorrentException;

	// -----------------------------------------------------------------------------
	/**
	 * state_t:<br>
	 * </ul> <li>0 queued_for_checking, <li>1 checking_files, <li>2
	 * downloading_metadata, <li>3 downloading, <li>4 finished, <li>5 seeding,
	 * <li>6 allocating, <li>7 checking_resume_data <li>8 paused <li>9 queued
	 * </ul>
	 * 
	 * @param hashCode
	 * @return
	 */
	public native int getTorrentState(String hashCode) throws TorrentException;

	// -----------------------------------------------------------------------------
	// static char const* state_str[] =
	// {"checking (q)", "checking", "dl metadata", "downloading", "finished",
	// "seeding", "allocating", "checking (r)"};
	// -----------------------------------------------------------------------------
	public native String getTorrentStatusText(String hashCode)
			throws TorrentException;

	// -----------------------------------------------------------------------------
	public native String getSessionStatusText();

	// -----------------------------------------------------------------------------
	// separator between files '\n'
	// -----------------------------------------------------------------------------
	/**
	 * get file entry in a given torrent
	 * 
	 * @param hashCode
	 * @return files in a given torrent.
	 * @throws TorrentException
	 */
	public native FileEntry[] getTorrentFiles(String hashCode)
			throws TorrentException;

	// -----------------------------------------------------------------------------
	// 0 - piece is not downloaded at all
	// 1 - normal priority. Download order is dependent on availability
	// 2 - higher than normal priority. Pieces are preferred over pieces with
	// the same availability, but not over pieces with lower availability
	// 3 - pieces are as likely to be picked as partial pieces.
	// 4 - pieces are preferred over partial pieces, but not over pieces with
	// lower availability
	// 5 - currently the same as 4
	// 6 - piece is as likely to be picked as any piece with availability 1
	// 7 - maximum priority, availability is disregarded, the piece is preferred
	// over any other piece with lower priority
	public native boolean setTorrentFilesPriority(byte[] filesPriority,
			String hashCode) throws TorrentException;

	// -----------------------------------------------------------------------------
	public native byte[] getTorrentFilesPriority(String hashCode)
			throws TorrentException;

	// -----------------------------------------------------------------------------
	/**
	 * get name of torrent which has a given hashCode
	 * 
	 * @param hashCode
	 * @return name of torrent
	 * @throws TorrentException
	 */
	public native String getTorrentName(String hashCode)
			throws TorrentException;

	/**
	 * get number bytes of torrent's data
	 * 
	 * @param torrentFile
	 * @return number bytes of torrent's data
	 */
	public native long getTorrentSize(String torrentFile);
	// -----------------------------------------------------------------------------
	
	public int getBestStreamableFile(String hashCode) throws TorrentException {
		FileEntry[] entries = getTorrentFiles(hashCode);
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
}
