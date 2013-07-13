package com.solt.libtorrent;

public class PartialPieceInfo {
	int pieceIdx;
	int numBlocks;
	int pieceState;
	/**
	 * block: [state, bytes_progress, block_size, num_peers]
	 */
	int[]	blocks; 
	PartialPieceInfo(int pieceIdx, int pieceState, int numBlocks, int[] blocks) {
		this.pieceIdx = pieceIdx;
		this.numBlocks = numBlocks;
		this.pieceState = pieceState;
		this.blocks = blocks;
	}

	public int getPieceIdx() {
		return pieceIdx;
	}

	public void setPieceIdx(int pieceIdx) {
		this.pieceIdx = pieceIdx;
	}

	public int getNumBlocks() {
		return numBlocks;
	}

	public void setNumBlocks(int numBlocks) {
		this.numBlocks = numBlocks;
	}

	public int getPieceState() {
		return pieceState;
	}

	public void setPieceState(int pieceState) {
		this.pieceState = pieceState;
	}
	
	public int[] getBlocks() {
		return blocks;
	}
	
	public int getDownloadedBytes() {
		int totalBytes = 0;
		for (int i = 0; i < numBlocks; ++i) {
			totalBytes += blocks[i * 4 + 1];
		}
		return totalBytes;
	}
	
	public int getBlockState(int index) {
		return blocks[index * 4];
	}
	
	public int getBlockSize(int index) {
		return blocks[index * 4 + 2];
	}
	
	public static class BlockState {
		public static final int NONE = 0;
		public static final int REQUESTED = 1;
		public static final int WRITING = 2;
		public static final int FINISHED = 3;
	}
}
