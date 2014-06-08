package com.solt.media.player.external;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.solt.media.util.Constants;

public class VLCInstance implements PlayerInstance {
	private static final File BINARY_PATH = new File("VLC.app/Contents/MacOS/VLC");
	private boolean delay = false;
	private Process process;
	private volatile boolean terminated;

	public void exit() {
		String process_name = BINARY_PATH.getName();
		if (delay) {
			try {
				Thread.sleep(250);

			} catch (Throwable e) {
			}
		}

		runCommand(new String[] { "killall", "-9", process_name });
	}
	
	private void
	runCommand(
		String[]	command )
	{
		try {
			if ( !Constants.isWindows){		
				command[0] = findCommand( command[0] );
			}
			Runtime.getRuntime().exec( command ).waitFor();
		} catch( Throwable e ) {
			e.printStackTrace();
		}
	}
	
	private String
	findCommand(
		String	name )
	{
		final String[]  locations = { "/bin", "/usr/bin" };
		for ( String s: locations ){
			File f = new File( s, name );
			if ( f.exists() && f.canRead()){
				return( f.getAbsolutePath());
			}
		}
		return( name );
	}

	public void play(String url, String[] subFiles) throws IOException {
		List<String> cmdList = new ArrayList<String>();
		cmdList.add( BINARY_PATH.getAbsolutePath());
		if (subFiles.length > 0 && subFiles[0] != null) {
			StringBuilder subOpts = new StringBuilder("--sub-file=").append(subFiles[0]);
//			for (int i = 1; i < subFiles.length; ++i) {
//				subOpts.append(',').append(subFiles[i]);
//			}
			cmdList.add(subOpts.toString());
		}
		cmdList.add(url);
		process = Runtime.getRuntime().exec(cmdList.toArray(new String[0]));
	}

	@Override
	public void waitForTerminate() throws InterruptedException {
		process.waitFor();
		terminated = true;
	}

	@Override
	public boolean isTerminated() {
		return terminated;
	}
}
