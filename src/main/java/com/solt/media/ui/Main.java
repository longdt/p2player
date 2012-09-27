package com.solt.media.ui;

import java.io.File;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TrayItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.wb.swt.SWTResourceManager;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

import com.solt.libtorrent.TorrentManager;

public class Main {
	protected Shell shell;
	private boolean minimize = true;
	private TorrentManager torrManager;
	/**
	 * @wbp.nonvisual location=103,199
	 */
	private final TrayItem trtmMediaPlayer = new TrayItem(Display.getDefault().getSystemTray(), SWT.NONE);

	/**
	 * Launch the application.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Main window = new Main();
			window.open();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Open the window.
	 */
	public void open() {
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
						torrManager.addTorrent(torrentFile);
					}
				}
			}
		});
		mntmOpen.setText("Open");
		MenuItem mntmAbout = new MenuItem(menu, SWT.NONE);
		mntmAbout.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				new AboutWindow(shell).open();
			}
		});
		mntmAbout.setText("About");
		
		new MenuItem(menu, SWT.SEPARATOR);
		
		MenuItem mntmExit = new MenuItem(menu, SWT.NONE);
		mntmExit.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				shell.dispose();
			}
		});
		mntmExit.setText("Exit");
		trtmMediaPlayer.setImage(SWTResourceManager.getImage(Main.class, "/systemtray.png"));
		
		trtmMediaPlayer.setToolTipText("Media Player");
		trtmMediaPlayer.addMenuDetectListener(new MenuDetectListener() {
			public void menuDetected(MenuDetectEvent e) {
				menu.setVisible(true);
			}
		});

	}
}
