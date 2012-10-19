package com.solt.media.util;

import java.io.File;
import java.net.URL;

public interface Downloader {

	public abstract void download(URL file, File target);

	public abstract void shutdown();

}