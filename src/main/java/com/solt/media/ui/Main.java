package com.solt.media.ui;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TrayItem;
import org.eclipse.wb.swt.SWTResourceManager;

import com.solt.libtorrent.TorrentManager;
import com.solt.media.config.ConfigurationManager;
import com.solt.media.update.UpdateChecker;
import com.solt.media.update.UpdateChecker.ErrorCode;
import com.solt.media.update.UpdateListener;
import com.solt.media.util.Constants;
import com.solt.media.util.SystemProperties;
import com.solt.mediaplayer.vlc.remote.MediaPlaybackState;
import com.solt.mediaplayer.vlc.remote.StateListener;
import com.solt.mediaplayer.vlc.swt.Player;

public class Main implements MediaPlayer {
	private static final Logger logger = Logger.getLogger(Main.class);
	protected static final String[] TORRENT_EXTENSION = {"*.torrent"};
	protected Shell shell;
	private Player player;
	private boolean minimize;
	private TorrentManager torrManager;
	private UpdateChecker updater;
	private boolean running;
	/**
	 * @wbp.nonvisual location=103,199
	 */
	private TrayItem trtmMediaPlayer;
	
	/**
	 * 
	 */
	public Main() {
		torrManager = TorrentManager.getInstance();
		minimize = false;
		running = true;
	}
	
	private void initUpdater() {
		updater = new UpdateChecker(this, new UpdateListener() {
			private Lock lock = new ReentrantLock();
			private Condition cond = lock.newCondition();
			private Boolean userAllowUpdate;
			@Override
			public boolean newVersionAvairable() {
				return true;
			}

			@Override
			public boolean downloadCompleted() {
				try {
					lock.lock();
					if (userAllowUpdate != null) {
						return userAllowUpdate;
					}
					Display.getDefault().asyncExec(new Runnable() {
					    public void run() {
					        MessageBox messageBox = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.YES | SWT.NO);
					        messageBox.setText("Mdplayer Update Information");
					        messageBox.setMessage("Mdplayer has release new version. Do u want update now?");
					        int buttonID = messageBox.open();
					        lock.lock();
					        userAllowUpdate = new Boolean(buttonID == SWT.YES ? true : false);
					        cond.signalAll();
							lock.unlock();
					    }});
				
					cond.await();
					return userAllowUpdate == null ? false : userAllowUpdate;
				} catch (InterruptedException e) {
					return false;
				} finally {
					lock.unlock();
				}
			}

			@Override
			public void downloadFailed(ErrorCode error) {
			}

			@Override
			public void downloadProgress(String fileName, int percent) {
			}
		});
		updater.start();
	}

	/**
	 * Launch the application.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			System.setProperty("java.io.tmpdir", SystemProperties.getMetaDataPath());
			Main window = new Main();
			window.open(args);
			window.exit();
		} catch (Exception e) {
			logger.error("EXCEPTION!!!", e);
		}
	}

	private void requestPlay(String[] args) {
		if (args.length > 0) {
			String link = args[0];
			if (link.charAt(link.length() - 1) == '/') {
				link = link.substring(0, link.length() - 1);
			}
			boolean sub = false;
			if (link.charAt(link.length() - 1) == 's') {
				link = link.substring(0, link.length() - 1);
				sub = true;
			}
			if (link.startsWith(Constants.PROTOCOL + "://tor")) {
				TorrentManager.requestAddTorrent(link.substring(Constants.PROTOCOL.length() + 6), true, sub);
			} else if (link.startsWith(Constants.PROTOCOL + "://mag")) {
				TorrentManager.requestAddTorrent(link.substring(Constants.PROTOCOL.length() + 6), false, sub);
			}
		}
	}
	/**
	 * Open the window.
	 */
	public void open(String[] args) {
		if (torrManager == null) {
			requestPlay(args);
			return;
		}
		initUpdater();
		Display display = Display.getDefault();
		createContents();
		torrManager.setMediaPlayer(this);
		requestPlay(args);
        Runtime.getRuntime().addShutdownHook(new Thread("Shutdowner") {
            @Override
            public void run() {
                requestShutdown();
            }
        });
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		SWTResourceManager.dispose();
		display.dispose();
	}
	
	public synchronized void play(final String url, final String subFile) {
		Display.getDefault().asyncExec(new Runnable() {
		    public void run() {
		    	shell.setVisible(true);
				shell.forceActive();
				shell.forceFocus();
				initPlayer();
				player.open(url, subFile, true);
		    }
		});
	}
	
	public void play(final String url) {
		play(url, null);
	}
	
	public synchronized void prepare() {
		Display.getDefault().asyncExec(new Runnable() {
		    public void run() {
		    	shell.setVisible(true);
				shell.forceActive();
				shell.forceFocus();
				initPlayer();
				player.prepare();
		    }
		});
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
		shell.setImage(SWTResourceManager.getImage(Main.class, "/logoIcon.png"));
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
				} else if (newState == MediaPlaybackState.Buffering) {
					String hashCode = torrManager.getCurrentStream();
					if (hashCode == null) {
						return;
					}
					try {
						int state = torrManager.getTorrentState(hashCode);
						String status = "";
						String rate = "";
						if (state == 1) {
							status = "Checking file";
						} else if (state == 2) {
							status = "Loading metadata";
						} else if (state == 3) {
							status = "Buffering";
							rate = "Download rate: " + (torrManager.getTorrentDownloadRate(hashCode) / 1024) + "KB/s";
						}
						player.buffering(status, rate, "");
					} catch (Exception e) {
					}
				} else if (newState == MediaPlaybackState.Continue) {
					player.resume();
				}
			}
		});
		shell.addShellListener(new ShellAdapter() {
			@Override
			public void shellClosed(ShellEvent e) {
				e.doit = false;
				player.stop();
				torrManager.cancelStream();
				shell.setVisible(false);
			}
		});
	}

	public synchronized void requestShutdown() {
		if (running) {
			running = false;
			torrManager.cancelStream();
			Display.getDefault().asyncExec(new Runnable() {
			    public void run() {
			    	shell.dispose();
			    }
			});
		}
	}
	
	private void exit() {
		if (torrManager == null) {
			return;
		}
		torrManager.shutdown();
		try {
			ConfigurationManager conf = ConfigurationManager.getInstance();
			conf.setStrings(ConfigurationManager.TORRENT_HASHCODES, torrManager.getTorrents());
			conf.save();
			updater.stop();
		} catch (IOException | InterruptedException e) {
			logger.error("cant exit app", e);
		}
	}

	/**
	 * Create contents of the window.
	 */
	protected void createContents() {
		trtmMediaPlayer = new TrayItem(Display.getDefault().getSystemTray(), SWT.NONE);
		shell = new Shell();
		
		final Menu menu = new Menu(shell, SWT.POP_UP);
		shell.setMenu(menu);
		if (!minimize) {
			createOpenMenu(menu);
		}
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
	
	private void createOpenMenu(Menu menu) {
		MenuItem mntmOpenFile = new MenuItem(menu, SWT.NONE);
		mntmOpenFile.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog dialog = new FileDialog(shell, SWT.OPEN);
				dialog.setFilterExtensions(TORRENT_EXTENSION);
				String path = dialog.open();
				if (path != null) {
					File torrentFile = new File(path);
					String url = null;
					if (torrentFile.isFile()) {
						url = torrManager.addTorrent(torrentFile);
					}
					if (url != null) {
						try {
							play(url);
						} catch (Exception e1) {
							logger.error("cant play file: " + path, e1);
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
				InputDialog input = new InputDialog(shell, "Enter torrent magnet link or directly http torrent link", SWT.CLOSE | SWT.TITLE);
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
						play(url);
					}
				} catch (Exception e1) {
					logger.error("cant play link: " + link, e1);
				}
			}
		});
		mntmOpenLink.setText("Open Link");
		
		MenuItem mntmDir = new MenuItem(menu, SWT.NONE);
		mntmDir.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String downDir = ConfigurationManager.getInstance().get(ConfigurationManager.TORRENT_DOWNLOAD_DIR);
				Program.launch(downDir);
			}
		});
		mntmDir.setText("Download Folder");
	}
}
