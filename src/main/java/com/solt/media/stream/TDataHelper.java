package com.solt.media.stream;

import java.io.IOException;

public interface TDataHelper {

	/**
	 * get data of a given pieceIdx. Data will written at offset 0.
	 * @param pieceIdx
	 * @param data
	 * @return 0: partial piece, 1: complete piece, -1 error
	 */
	public abstract Result retrievePiece(int pieceIdx, byte[] data);
	
	public abstract int getPiece(int pieceIdx, byte[] data) throws IOException;
	
	public static class Result {
		public static final int COMPLETE = 1;
		public static final int PARTIAL = 0;
		public static final int ERROR = -1;
		
		public static final Result ERROR_RESULT = new Result(ERROR, 0);
		private int state;
		private int length;
		
		public Result(int state, int length) {
			this.state = state;
			this.length = length;
		}

		public int getState() {
			return state;
		}

		public int getLength() {
			return length;
		}
	}

}