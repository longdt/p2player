package com.solt.media.player.external;

import java.io.IOException;

public class ExtenalPlayer {
	private PlayerInstance instance;
	
	public void open(String url, String... subFile) {
		instance = PlayerInstanceFactory.newPlayerInstance();
		try {
			instance.play(url, subFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void prepare() {
		if (instance != null) {
			instance.exit();
		}
	}
	

	
}
