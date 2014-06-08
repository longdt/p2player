package com.solt.media.player.external;

import java.io.IOException;

public interface PlayerInstance {
	public void play(String url, String[] subFiles) throws IOException;
	
	public void exit();
}
