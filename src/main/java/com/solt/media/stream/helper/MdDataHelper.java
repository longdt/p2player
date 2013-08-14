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
import com.solt.libtorrent.TorrentException;
import com.solt.libtorrent.PartialPieceInfo.BlockState;
import com.solt.media.stream.helper.TDataHelper.Result;
import com.solt.media.util.FileUtils;

public class MdDataHelper implements TDataHelper {
	private static final String HOST = "localhost";// "stream.sharephim.vn";
	private static final int PORT = 8448;
	private static final int MAX_ERROR_COUNTER = 10;
	private LibTorrent libTorrent;
	private String hashCode;
	private long itemOffset;
	private long itemLength;
	private int startPiece;
	private int endPiece;
	private int pieceSize;
	private int errCnt;
	private DataConnector connector;
	
	public MdDataHelper(LibTorrent libTorrent, String hashCode, long fileId, int item) {
		this.libTorrent = libTorrent;
		this.hashCode = hashCode;
		try {
			pieceSize = libTorrent.getPieceSize(hashCode, false);
			FileEntry[] entries = libTorrent.getTorrentFiles(hashCode);
			itemOffset = entries[item].getOffset();
			itemLength = entries[item].getSize();
			startPiece = (int) (itemOffset / pieceSize);
			endPiece = (int) ((itemOffset + itemLength) / pieceSize) + 1;
			String path = entries[item].getPath().replace('\\', '/');
			connector = new DataConnector(HOST, PORT);
			connector.initData(path, fileId, (byte)item, pieceSize, itemOffset);
		} catch (TorrentException e) {
			e.printStackTrace();
		} catch (IOException e) {
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
		try {
			if(connector.getData(pieceIdx, offset, (int)(endBytes - startBytes), data)) {
				return new Result(state, offset, (int)(endBytes - startBytes));
			}
		} catch (IOException e) {
			e.printStackTrace();
			connector.reconnect(true);
			++errCnt;
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
		try {
			PartialPieceInfo info = libTorrent.getPartialPieceInfo(hashCode, pieceIdx);
			if (info == null) {
				return connector.getData(pieceIdx, 0, pieceSize, data);
			}
			int start = 0;
			int end = 0;
			boolean isReq = false;
			for (int i = 0; i < info.getNumBlocks(); ++i) {
				if (info.getBlockState(i) < BlockState.WRITING) {
					end = end + info.getBlockSize(i);
					isReq = true;
				} else if (isReq) {
					if(!connector.getData(pieceIdx, start, end - start, data)) {
						return false;
					}
					end += info.getBlockSize(i);
					start = end;
					isReq = false;
				} else {
					start += info.getBlockSize(i);
					end = start;
				}
			}
			return isReq ? connector.getData(pieceIdx, start, end - start, data) : true;
		} catch (IOException e) {
			e.printStackTrace();
			connector.reconnect(true);
			++errCnt;
		}
		return false;
	}

	@Override
	public void close() {
		connector.close();
	}

}
