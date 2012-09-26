package com.solt.libtorrent;

public class PartialPieceInfo {
	int pieceIdx;
	int numBlocks;
	int pieceState;
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
	
	int[] getBlocks() {
		return blocks;
	}
}