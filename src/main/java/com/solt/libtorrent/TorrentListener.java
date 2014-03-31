package com.solt.libtorrent;

public interface TorrentListener {
	public void hashPieceFailed(String hashCode, int pieceIdx);
}
