package com.solt.media.update;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.eclipse.swt.program.Program;

import com.solt.libtorrent.TorrentManager;

public class UpdateTool {
	private static ServerSocket ss;
	public static void main(String[] args) {
		if (args == null) {
			return;
		}
		Path file = null;
		Path targetFolder = null;
		Path installFolder = FileSystems.getDefault().getPath("./");
		Path libFolder = installFolder.resolveSibling(ComponentUpdater.LIB_FOLDER);
        PrintStream stream = null;
		try {
            stream = new PrintStream(new File("updatetool.txt"));
            System.setErr(stream); //This is important, need to direct error stream somewhere
			waitForPlayerShutdown();
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
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			if (ss != null) {
				try {
					ss.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (stream != null) {
				stream.close();
			}
		}
	}
	
	private static void waitForPlayerShutdown() throws InterruptedException {
		while (true) {
			try {
				ss = new ServerSocket(TorrentManager.HTTPD_PORT);
				break;
			} catch (IOException e) {
				Thread.sleep(500);
			}
		}
	}
}