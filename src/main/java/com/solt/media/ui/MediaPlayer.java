package com.solt.media.ui;

public interface MediaPlayer {
	public void play(String url, String subFile);
	
	public void play(String url);
	
	public void requestShutdown();
}
