package com.solt.media.update;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.eclipse.swt.program.Program;

public class UpdateTool {
	public static void main(String[] args) {
		if (args == null) {
			return;
		}
		Path file = null;
		Path targetFolder = null;
		Path installFolder = FileSystems.getDefault().getPath("./");
		Path libFolder = installFolder.resolveSibling(ComponentUpdater.LIB_FOLDER);
		try {
			//delete lib/*
			for (File f : libFolder.toFile().listFiles()) {
				f.delete();
			}
			for (String component : args) {
				file = installFolder.resolve(component);
				targetFolder = component.contains(ComponentUpdater.LIB_FOLDER) ? libFolder
						: installFolder;
				Files.move(file, targetFolder.resolve(file.getFileName()),
						StandardCopyOption.REPLACE_EXISTING);
			}
			Program.launch("MediaPlayer.exe");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}