package com.solt.media.stream.helper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import com.solt.libtorrent.FileEntry;
import com.solt.libtorrent.LibTorrent;
import com.solt.libtorrent.PartialPieceInfo;
import com.solt.libtorrent.PartialPieceInfo.BlockState;
import com.solt.libtorrent.TorrentException;
import com.solt.media.util.FileUtils;

public class HttpDataHelper implements TDataHelper {
	private static final String STORE_HELPER_URL = "http://stream.sharephim.vn:443/";
	private static final int MAX_ERROR_COUNTER = 10;
	private LibTorrent libTorrent;
	private String hashCode;
	private long fileOffset;
	private long torrentOffset;
	private long itemOffset;
	private long itemLength;
	private long fileLength;
	private int startPiece;
	private int startPieceOffset;
	private int endPiece;
	private int pieceSize;
	private int errCnt;
	private URL url;
	
	public HttpDataHelper(LibTorrent libTorrent, String hashCode, int item, long fileOffset, long fileLength) {
		this.libTorrent = libTorrent;
		this.hashCode = hashCode;
		this.fileOffset = fileOffset;
		this.fileLength = fileLength;
		try {
			pieceSize = libTorrent.getPieceSize(hashCode, false);
			FileEntry[] entries = libTorrent.getTorrentFiles(hashCode);
			itemOffset = entries[item].getOffset();
			itemLength = entries[item].getSize();
			torrentOffset = fileOffset + itemOffset;
			startPiece = (int) (torrentOffset / pieceSize);
			startPieceOffset = (int) (torrentOffset - startPiece
					* pieceSize);
			endPiece = (int) ((torrentOffset + fileLength) / pieceSize) + 1;
			String path = entries[item].getPath().replace('\\', '/');
			url = new URL(STORE_HELPER_URL + path);
			URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
			url = new URL(uri.toASCIIString());
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TorrentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/* (non-Javadoc)
	 * @see com.solt.media.stream.TDataHelper#getPieceData(int, byte[])
	 */
	@Override
	public Result retrievePiece(int pieceIdx, byte[] data) {
		if (pieceIdx < startPiece || pieceIdx > endPiece || errCnt > MAX_ERROR_COUNTER) {
			return Result.ERROR_RESULT;
		}
		
		long startBytes = pieceIdx * pieceSize - itemOffset;
		long endBytes = startBytes + pieceSize;
		int state = Result.COMPLETE;
		int offset = 0;
		if (startBytes < 0) {
			offset = (int) -startBytes;
			startBytes = 0;
			state = Result.PARTIAL;
		}
		if (endBytes > itemLength) {
			endBytes = itemLength;
			state = Result.PARTIAL;
		}
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) url.openConnection();

			conn.setRequestProperty("Range", "bytes=" + startBytes + "-" + (endBytes - 1));
			conn.connect();
			int len = FileUtils.copyFile(conn.getInputStream(), data, offset, (int)(endBytes - startBytes));
			return  len > 0 ? new Result(state, offset, len) : Result.ERROR_RESULT;
		} catch (IOException e) {
			e.printStackTrace();
			++errCnt;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
		return Result.ERROR_RESULT;
	}

	@Override
	public boolean getPieceRemain(int pieceIdx, byte[] data) throws TorrentException {
		if (pieceIdx < startPiece || pieceIdx > endPiece || errCnt > MAX_ERROR_COUNTER) {
			return false;
		}
		
		long startBytes = pieceIdx * pieceSize - itemOffset;
		long endBytes = startBytes + pieceSize;
		if (startBytes < 0 || endBytes > itemLength) {
			return false;
		}
		PartialPieceInfo info = libTorrent.getPartialPieceInfo(hashCode, pieceIdx);
		if (info == null) {
			return retrieveData(startBytes, endBytes, data, 0);
		}
		int start = 0;
		int end = 0;
		boolean isReq = false;
		for (int i = 0; i < info.getNumBlocks(); ++i) {
			if (info.getBlockState(i) < BlockState.WRITING) {
				end = end + info.getBlockSize(i);
				isReq = true;
			} else if (isReq) {
				if (retrieveData(start + startBytes, end + startBytes, data, start)) {
					end += info.getBlockSize(i);
					start = end;
					isReq = false;
				} else {
					return false;
				}
			} else {
				start += info.getBlockSize(i);
				end = start;
			}
		}
		if (isReq) {
			return retrieveData(start + startBytes, end + startBytes, data, start);
		}
		return true;
	}

	private boolean retrieveData(long startBytes, long endBytes, byte[] data,
			int offset) {
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("Range", "bytes=" + startBytes + "-" + (endBytes - 1));
			conn.connect();
			int len = FileUtils.copyFile(conn.getInputStream(), data, offset, (int)(endBytes - startBytes));
			return  len > 0;
		} catch (IOException e) {
			e.printStackTrace();
			++errCnt;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
		return false;
	}

	@Override
	public void close() {
	}
}
