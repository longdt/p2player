package com.solt.media.ui;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TrayItem;
import org.eclipse.wb.swt.SWTResourceManager;

import com.solt.libtorrent.TorrentManager;
import com.solt.media.config.ConfigurationManager;
import com.solt.media.update.UpdateChecker;
import com.solt.media.util.Constants;
import com.solt.mediaplayer.mplayer.swt.Player;

public class Main {
	protected Shell shell;
	private boolean minimize;
	private TorrentManager torrManager;
	private UpdateChecker updater;
	/**
	 * @wbp.nonvisual location=103,199
	 */
	private final TrayItem trtmMediaPlayer = new TrayItem(Display.getDefault().getSystemTray(), SWT.NONE);
	
	/**
	 * 
	 */
	public Main() {
		torrManager = TorrentManager.getInstance();
		minimize = true;
	}
	
	private void initUpdater() {
		updater = new UpdateChecker(this);
		updater.start();
	}

	/**
	 * Launch the application.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Main window = new Main();
			window.open(args);
			window.exit();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Open the window.
	 * @throws MalformedURLException 
	 */
	public void open(String[] args) throws MalformedURLException {
		if (args.length > 0 && args[0].startsWith(Constants.PROTOCOL + "://")) {
			TorrentManager.requestAddTorrent(args[0].substring(Constants.PROTOCOL.length() + 3));
		}
		if (torrManager == null) {
			return;
		}
		initUpdater();
		Display display = Display.getDefault();
		createContents();
		if (!minimize) {
			shell.open();
			shell.layout();
		}
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}
	
	public void requestShutdown() {
		Display.getDefault().asyncExec(new Runnable() {
		    public void run() {
		    	shell.dispose();
		    }
		});
	}
	
	private void exit() {
		if (torrManager == null) {
			return;
		}
		torrManager.shutdown();
		SWTResourceManager.dispose();
		try {
			ConfigurationManager.getInstance().save();
			updater.stop();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create contents of the window.
	 */
	protected void createContents() {
		shell = new Shell();
		shell.setSize(640, 360);
		shell.setText("SWT Application");
		
		final Menu menu = new Menu(shell, SWT.POP_UP);
		shell.setMenu(menu);
		MenuItem mntmOpen = new MenuItem(menu, SWT.NONE);
		mntmOpen.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog dialog = new FileDialog(shell, SWT.OPEN);
				String path = dialog.open();
				if (path != null) {
					File torrentFile = new File(path);
					if (torrentFile.isFile()) {
						String url = torrManager.addTorrent(torrentFile);
						if (url != null) {
							Shell mplayer = new Shell();
							mplayer.setText(url);
							try {
								Player.play(mplayer, url);
							} catch (Exception e1) {
								e1.printStackTrace();
							}
						}
					}
				}
			}
		});
		mntmOpen.setText("Open");
		MenuItem mntmAbout = new MenuItem(menu, SWT.NONE);
		mntmAbout.addSelectionListener(new SelectionAdapter() {
			private AboutWindow about;
			private volatile boolean opening;
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!opening) {
					about = new AboutWindow(shell);
					opening = true;
					about.open();
					opening = false;
				} else {
					about.forceFocus();
				}
			}
		});
		mntmAbout.setText("About");
		
		new MenuItem(menu, SWT.SEPARATOR);
		
		MenuItem mntmExit = new MenuItem(menu, SWT.NONE);
		mntmExit.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				requestShutdown();
			}
		});
		mntmExit.setText("Exit");
		trtmMediaPlayer.setImage(SWTResourceManager.getImage(Main.class, "/mediaplayer.ico"));
		
		trtmMediaPlayer.setToolTipText("Media Player");
		trtmMediaPlayer.addMenuDetectListener(new MenuDetectListener() {
			public void menuDetected(MenuDetectEvent e) {
				menu.setVisible(true);
			}
		});

	}
}
