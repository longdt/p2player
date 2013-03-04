package com.solt.libtorrent;

public class PiecesState {
	private String hashCode;
	private int fromIdx;
	private int len;
	private byte[] states;
	private int stateLen;
	private int stateIdx;
	
	public PiecesState(String hashCode, int fromIdx, int len) {
		this.hashCode = hashCode;
		setFromIdx(fromIdx);
		setLength(len, true);
	}

	byte[] getStates() {
		return states;
	}

	int getStateLen() {
		return stateLen;
	}

	int getStateIdx() {
		return stateIdx;
	}

	public int getFromIdx() {
		return fromIdx;
	}

	public void setFromIdx(int fromIdx) {
		this.fromIdx = fromIdx;
		stateIdx = fromIdx / 8;
	}

	public int getLenght() {
		return len;
	}

	public void setLength(int len, boolean force) {
		if (len <= 0)
			return;
		stateLen = (len + 7) / 8;
		if (states == null || stateLen > states.length || force) {
			states = new byte[stateLen];
		}
		this.len = len;
	}

	public boolean isDone(int index) {
		return (states[index / 8 - stateIdx] & (0x80 >> (index & 7))) != 0;
	}

	public String getHashCode() {
		return hashCode;
	}

	public void setHashCode(String hashCode) {
		this.hashCode = hashCode;
	}
	
	public int getFirstIncomplete() {
		int i = fromIdx;
		for (int n = fromIdx + len; i < n && isDone(i); ++i) {
		}
		return i;
	}

}
