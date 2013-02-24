package com.solt.libtorrent;

import java.util.Comparator;


public class PieceInfoComparator implements Comparator<PartialPieceInfo> {

	@Override
	public int compare(PartialPieceInfo o1, PartialPieceInfo o2) {
		if (o1.getPieceIdx() < o2.getPieceIdx()) {
			return -1;
		} else if (o1.getPieceIdx() > o2.getPieceIdx()) {
			return 1;
		} else {
			return 0;
		}
	}
	
}