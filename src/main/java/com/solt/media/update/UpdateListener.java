package com.solt.media.update;

import java.io.File;

import com.solt.media.update.UpdateChecker.ErrorCode;

public interface UpdateListener {
	public boolean newVersionAvairable();
	
	public void downloadCompleted(File file);
	
	public void downloadFailed(ErrorCode error);
}
