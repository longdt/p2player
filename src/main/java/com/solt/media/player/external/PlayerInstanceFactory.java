package com.solt.media.player.external;

public class PlayerInstanceFactory {
	public static PlayerInstance newPlayerInstance() {
		return new VLCInstance();
	}
}
