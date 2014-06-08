package com.solt.media.player.external;

import java.io.IOException;

public interface PlayerInstance {
	public void play(String url, String[] subFiles) throws IOException;
	
	public void exit();
	
	public void waitForTerminate() throws InterruptedException;

	public boolean isTerminated();
}
