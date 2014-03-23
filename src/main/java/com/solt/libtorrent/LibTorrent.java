package com.solt.libtorrent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.apache.log4j.Logger;

import com.solt.media.util.Constants;
import com.solt.media.util.FileUtils;

public class LibTorrent {
	private static final Logger logger = Logger.getLogger(LibTorrent.class);
	/**
	 * <p>
	 * If flag_seed_mode is set, libtorrent will assume that all files are
	 * present for this torrent and that they all match the hashes in the
	 * torrent file. Each time a peer requests to download a block, the piece is
	 * verified against the hash, unless it has been verified already. If a hash
	 * fails, the torrent will automatically leave the seed mode and recheck all
	 * the files. The use case for this mode is if a torrent is created and
	 * seeded, or if the user already know that the files are complete, this is
	 * a way to avoid the initial file checks, and significantly reduce the
	 * startup time.
	 * <p>
	 * Setting flag_seed_mode on a torrent without metadata (a .torrent file) is
	 * a no-op and will be ignored.
	 * <p>
	 * If resume data is passed in with this torrent, the seed mode saved in
	 * there will override the seed mode you set here.
	 */
	public static final int FLAG_SEED_MODE = 0x001;
	/**
	 * If flag_override_resume_data is set, the paused and auto_managed state of
	 * the torrent are not loaded from the resume data, but the states requested
	 * by the flags in add_torrent_params will override them.
	 */
	public static final int FLAG_OVERRIDE_RESUME_DATA = 0x002;
	/**
	 * <p>
	 * If flag_upload_mode is set, the torrent will be initialized in
	 * upload-mode, which means it will not make any piece requests. This state
	 * is typically entered on disk I/O errors, and if the torrent is also auto
	 * managed, it will be taken out of this state periodically. This mode can
	 * be used to avoid race conditions when adjusting priorities of pieces
	 * before allowing the torrent to start downloading.
	 * <p>
	 * If the torrent is auto-managed (flag_auto_managed), the torrent will
	 * eventually be taken out of upload-mode, regardless of how it got there.
	 * If it's important to manually control when the torrent leaves upload
	 * mode, don't make it auto managed.
	 */
	public static final int FLAG_UPLOAD_MODE = 0x004;
	/**
	 * <p>determines if the torrent should be added in share mode or not. Share
	 * mode indicates that we are not interested in downloading the torrent, but
	 * merlely want to improve our share ratio (i.e. increase it). A torrent
	 * started in share mode will do its best to never download more than it
	 * uploads to the swarm. If the swarm does not have enough demand for upload
	 * capacity, the torrent will not download anything. This mode is intended
	 * to be safe to add any number of torrents to, without manual screening,
	 * without the risk of downloading more than is uploaded.
	 * <p>
	 * A torrent in share mode sets the priority to all pieces to 0, except for
	 * the pieces that are downloaded, when pieces are decided to be downloaded.
	 * This affects the progress bar, which might be set to "100% finished" most
	 * of the time. Do not change file or piece priorities for torrents in share
	 * mode, it will make it not work.
	 * <p>
	 * The share mode has one setting, the share ratio target, see
	 * session_settings::share_mode_target for more info.
	 */
	public static final int FLAG_SHARE_MODE = 0x008;
	/**
	 * determines if the IP filter should apply to this torrent or not. By
	 * default all torrents are subject to filtering by the IP filter (i.e. this
	 * flag is set by default). This is useful if certain torrents needs to be
	 * excempt for some reason, being an auto-update torrent for instance.
	 */
	public static final int FLAG_APPLY_IP_FILTER = 0x010;
	/**
	 * <p>
	 * specifies whether or not the torrent is to be started in a paused state.
	 * I.e. it won't connect to the tracker or any of the peers until it's
	 * resumed. This is typically a good way of avoiding race conditions when
	 * setting configuration options on torrents before starting them.
	 * <p>
	 * If you pass in resume data, the paused state of the torrent when the
	 * resume data was saved will override the paused state you pass in here.
	 * You can override this by setting flag_override_resume_data.
	 * <p>
	 * If the torrent is auto-managed (flag_auto_managed), the torrent may be
	 * resumed at any point, regardless of how it paused. If it's important to
	 * manually control when the torrent is paused and resumed, don't make it
	 * auto managed.
	 */
	public static final int FLAG_PAUSED = 0x020;
	/**
	 * <p>
	 * If flag_auto_managed is set, the torrent will be queued, started and
	 * seeded automatically by libtorrent. When this is set, the torrent should
	 * also be started as paused. The default queue order is the order the
	 * torrents were added. They are all downloaded in that order. For more
	 * details, see queuing.
	 * <p>
	 * If you pass in resume data, the auto_managed state of the torrent when
	 * the resume data was saved will override the auto_managed state you pass
	 * in here. You can override this by setting override_resume_data.
	 */
	public static final int FLAG_AUTO_MANAGED = 0x040;
	public static final int FLAG_DUPLICATE_IS_ERROR = 0x080;
	/**
	 * defaults to off and specifies whether tracker URLs loaded from resume
	 * data should be added to the trackers in the torrent or replace the
	 * trackers.
	 */
	public static final int FLAG_MERGE_RESUME_TRACKERS = 0x100;
	/**
	 * is on by default and means that this torrent will be part of state
	 * updates when calling post_torrent_updates().
	 */
	public static final int FLAG_UPDATE_SUBSCRIBE = 0x200;
	public static final int DEFAULT_FLAGS = FLAG_UPDATE_SUBSCRIBE
			| FLAG_AUTO_MANAGED | FLAG_PAUSED | FLAG_APPLY_IP_FILTER;

	private static final String LIBTORRENT_DLL = Constants.isLinux ? "libtorrent.so" : "libtorrent.dll";
	
	static {
		loadLibraryFromJar();
	}
	
	LibTorrent() {
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
					if (in == null) {
						in = getClass().getResourceAsStream("/resources/" + LIBTORRENT_DLL);
					}
					out = new FileOutputStream(tmpFile);

					byte[] buf = new byte[8192];
					int len;
					while ((len = in.read(buf)) != -1) {
						out.write(buf, 0, len);
					}
				} catch (Exception e) {
					// deal with exception
					logger.error("can't extract dll lib", e);
				} finally {
					if (in != null) {
						try {
							in.close();
						} catch (IOException e) {
						}
					}
					if (out != null) {
						try {
							out.close();
						} catch (IOException e) {
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
	private native boolean setSession(int listenPort, String defaultSave,
			int uploadLimit, int downloadLimit);

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
	 * @throws IOException 
	 */
	public boolean setSession(int listenPort, File defaultSave, int uploadLimit, int downloadLimit) throws IOException {
		return setSession(listenPort, defaultSave.getCanonicalPath(), uploadLimit, downloadLimit);
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
	 * Start/stop services: <i>DHT, Local Service Discovery, UPnP, NATPMP</i>.
	 * Set true to start and false to stop corresponding service.<br>
	 * When started, the listen port and the DHT port are attempted to be
	 * forwarded on local UPnP router devices.
	 * 
	 * @param dht
	 *            to start DHT
	 * 
	 * @param lsd
	 *            to start service LSD
	 * @param upnp
	 *            to start service UPNP
	 * @param natpmp
	 *            to start service NATPMP
	 * @return true if successful and false if otherwise
	 */
	public native boolean setSessionOptions(boolean dht, boolean lsd,
			boolean upnp, boolean natpmp);

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
	 * </ul>
	 * 
	 * @param torentFile
	 * @param storageMode
	 * @return hashCode of added torrent or <code>null</code> if error occurs
	 */
	public String addTorrent(String torentFile, int storageMode) {
		return addTorrent(torentFile, storageMode, DEFAULT_FLAGS);
	}

	/**
	 * Add torrent to download. If <i>autoManaged</i> is true then added torrent
	 * will be auto managed <br>
	 * <br>
	 * StorageMode:
	 * <ul>
	 * <li>0-storage_mode_allocate
	 * <li>1-storage_mode_sparse
	 * </ul>
	 * 
	 * @param torentFile
	 * @param storageMode
	 * @return hashCode of added torrent or <code>null</code> if error occurs
	 */
	public native String addTorrent(String torentFile, int storageMode,
			int flags);

	public String addAsyncTorrent(String torentFile, int storageMode) {
		return addAsyncTorrent(torentFile, storageMode, DEFAULT_FLAGS);
	}

	public native String addAsyncTorrent(String torentFile, int storageMode,
			int flags);

	public native String addMagnetUri(String magnetLink, int storageMode,
			int flags);

	public native String addAsyncMagnetUri(String magnetLink, int storageMode,
			int flags);

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
	public native boolean abortSession(boolean saveResumeData);
	
	public boolean abortSession() {
		return abortSession(true);
	}

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
	public native void pauseTorrent(String hashCode) throws TorrentException;

	// -----------------------------------------------------------------------------
	/**
	 * resume torrent which has a given hashCode and reconnect all peers
	 * 
	 * @param hashCode
	 * @return true if successful and false if otherwise
	 * @throws TorrentException
	 */
	public native void resumeTorrent(String hashCode)
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
	
	public native void addTorrentPiece(String hashCode, int pieceIdx,
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
	 * get upload rate of a given torrent hashCode. The rates are given as the
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
	public native int getTorrentUploadRate(String hashCode, boolean payload)
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

	
	public native boolean isUploadMode(String hashCode) throws TorrentException;
	
	public native void setShareMode(String hashCode, boolean mode)
			throws TorrentException;
	
	public native boolean isShareMode(String hashcode) throws TorrentException;
	
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
	 * get index of first in-completed piece from a given piece index
	 * 
	 * @param hashCode
	 * @param offset
	 * @return index of first in-completed piece
	 * @throws TorrentException
	 */
	public native int getFirstPieceIncomplete(String hashCode, int fromPiece)
			throws TorrentException;
	
	public boolean getPieceState(PiecesState state) throws TorrentException {
		return getPieceState(state.getHashCode(), state.getStateIdx(), state.getStateLen(), state.getStates()) > 0;
	}
	
	/**
	 * get piece's states from a given fromIdx to <i>len</i>. Return len of state.
	 * @param hashCode
	 * @param fromIdx
	 * @param len
	 * @param state
	 * @return
	 */
	private native int getPieceState(String hashCode, int fromIdx, int len, byte[] state);

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

	public native void cancelTorrentPiece(String hashCode, int pieceIdx, boolean force)
			throws TorrentException;
	
	public void cancelTorrentPiece(String hashCode, int pieceIdx)
			throws TorrentException {
		cancelTorrentPiece(hashCode, pieceIdx, false);
	}

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
	
	public native void handleAlerts();
	
	public int getBestStreamableFile(String hashCode) throws TorrentException {
		FileEntry[] entries = getTorrentFiles(hashCode);
		long maxSize = 0;
		int index = -1;
		for (int i = 0; i < entries.length; ++i) {
			if (FileUtils.isStreamable(entries[i]) && entries[i].getSize() > maxSize) {
				maxSize = entries[i].getSize();
				index = i;
			}
		}
		return index;
	}

}
