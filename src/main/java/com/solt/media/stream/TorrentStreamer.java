package com.solt.media.stream;

public interface TorrentStreamer {

	public abstract void stream() throws Exception;
	
	public void close();

}