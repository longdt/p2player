package com.solt.libtorrent;

public class FileEntry {
	private String path;
	private long offset;
	private long size;
	private long fileBase;
	private boolean padFile;
	private boolean hiddenAttr;
	private boolean execAttr;
	
	FileEntry(String path, long offset, long size, long fileBase, boolean padFile, boolean hiddenAttr, boolean execAttr) {
		this.path = path;
		this.offset = offset;
		this.size = size;
		this.fileBase = fileBase;
		this.padFile = padFile;
		this.hiddenAttr = hiddenAttr;
		this.execAttr = execAttr;
	}
	
	public String getPath() {
		return path;
	}
	public long getOffset() {
		return offset;
	}
	public long getSize() {
		return size;
	}
	public long getFileBase() {
		return fileBase;
	}
	public boolean isPadFile() {
		return padFile;
	}
	public boolean isHiddenAttr() {
		return hiddenAttr;
	}
	public boolean isExecAttr() {
		return execAttr;
	}
	
	
}
