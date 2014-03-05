package com.solt.media.update;

import java.io.IOException;

public interface Updater {
	public static final String UPDATE_FOLDER = "update";
	public abstract boolean update() throws InterruptedException, IOException;
}
