package com.solt.media.ui;

public interface MediaPlayer {
	
	public void prepare();
	
	public void play(String url, String subFile);
	
	public void play(String url, String subFile, String otherSubName, String otherSubUrl);
	
	public void play(String url);
	
	public void requestShutdown();
}
