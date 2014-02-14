package com.solt.media.stream.helper;

import com.solt.libtorrent.TorrentException;

public interface TDataHelper {

	/**
	 * get data of a given pieceIdx. Maybe partial piece (in case start/end piece of file).
	 * @param pieceIdx
	 * @param data
	 * @return {@link Result} object to indicate result of operation
	 */
	public abstract Result retrievePiece(int pieceIdx, byte[] data);
	
	public abstract boolean getPieceRemain(int pieceIdx, byte[] data) throws TorrentException;
	
	public void close();
	
	public static class Result {
		public static final int COMPLETE = 1;
		public static final int PARTIAL = 0;
		public static final int ERROR = -1;
		
		public static final Result ERROR_RESULT = new Result(ERROR, 0, 0);
		private int state;
		private int offset;
		private int length;
		
		public Result(int state, int offset, int length) {
			this.state = state;
			this.length = length;
			this.offset = offset;
		}

		public int getState() {
			return state;
		}

		public int getLength() {
			return length;
		}

		public int getOffset() {
			return offset;
		}

		public void setOffset(int offset) {
			this.offset = offset;
		}
	}

}