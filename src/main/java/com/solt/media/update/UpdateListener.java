package com.solt.media.update;

import java.io.File;

import com.solt.media.update.UpdateChecker.ErrorCode;

public interface UpdateListener {
	public boolean newVersionAvairable();
	
	public boolean downloadCompleted();
	
	public void downloadProgress(String fileName, int percent);
	
	public void downloadFailed(ErrorCode error);
}
