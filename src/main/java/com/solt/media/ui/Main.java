package com.solt.media.ui;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.FillLayout;
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
import com.solt.mediaplayer.vlc.remote.MediaPlaybackState;
import com.solt.mediaplayer.vlc.remote.StateListener;
import com.solt.mediaplayer.vlc.swt.Player;

public class Main {
	protected Shell shell;
	private Player player;
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
        Runtime.getRuntime().addShutdownHook(new Thread("Shutdowner") {
            @Override
            public void run() {
                requestShutdown();
            }
        });
		if (!minimize) {
			shell.open();
			shell.layout();
		}
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		SWTResourceManager.dispose();
		display.dispose();
	}
	
	public synchronized void play(String url) {
		initPlayer();
		player.open(url, true);
	}
	
	private synchronized void initPlayer() {
		if (player != null) {
			return;
		}
		shell.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_BLACK));
		shell.setLocation(200, 200);
		shell.setSize(720, 480);

		shell.setText("Loading...");
		shell.setLayout(new FillLayout());
		player = new Player(shell);

		player.setAutoResize(true);

		player.addStateListener(new StateListener() {

			public void stateChanged(MediaPlaybackState newState) {
				if (newState == MediaPlaybackState.Closed) {
					shell.getDisplay().asyncExec(new Runnable() {

						public void run() {
							shell.close();

						}
					});
				}
			}
		});
		shell.addShellListener(new ShellAdapter() {
			@Override
			public void shellClosed(ShellEvent e) {
				e.doit = false;
				player.stop();
				shell.setVisible(false);
			}
		});
	}

	public void requestShutdown() {
		torrManager.cancelStream();
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
		
		final Menu menu = new Menu(shell, SWT.POP_UP);
		shell.setMenu(menu);
		MenuItem mntmOpenFile = new MenuItem(menu, SWT.NONE);
		mntmOpenFile.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog dialog = new FileDialog(shell, SWT.OPEN);
				String path = dialog.open();
				if (path != null) {
					File torrentFile = new File(path);
					String url = null;
					if (torrentFile.isFile()) {
						url = torrManager.addTorrent(torrentFile);
					}
					if (url != null) {
						shell.setVisible(true);
						shell.forceFocus();
						try {
							play(url);
						} catch (Exception e1) {
							e1.printStackTrace();
						}
					}
				}
			}
		});
		mntmOpenFile.setText("Open File");
		
		MenuItem mntmOpenLink = new MenuItem(menu, SWT.NONE);
		mntmOpenLink.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				InputDialog input = new InputDialog(shell, "Enter torrent link", SWT.CLOSE | SWT.TITLE);
				String link = input.open();
				if (link == null) return;
				try {
					String url = null;
					if (link.startsWith("magnet:")) {
							url = torrManager.addTorrent(URI.create(link));
					} else if (link.startsWith("http:")) {
							url = torrManager.addTorrent(new URL(link));
					}
					if (url != null) {
						shell.setVisible(true);
						shell.forceFocus();
						try {
							play(url);
						} catch (Exception e1) {
							e1.printStackTrace();
						}
	
					}
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		});
		mntmOpenLink.setText("Open Link");
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
