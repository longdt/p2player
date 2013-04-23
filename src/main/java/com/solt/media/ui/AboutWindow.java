package com.solt.media.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.wb.swt.SWTResourceManager;

import com.solt.media.util.Constants;

public class AboutWindow extends Dialog {
	private static final String ABOUT_US = "MediaPlayer: Chương trình xem phim HD\n\nVersion: " + Constants.VERSION + "\n\nBản build r122\n\n(c) Bản quyền thuộc nhóm SOLT" ;

	protected Object result;
	protected Shell shell;
	private Text text;

	/**
	 * Create the dialog.
	 * @param parent
	 * @param style
	 */
	public AboutWindow(Shell parent) {
		super(parent, SWT.CLOSE | SWT.TITLE);
		setText("Media Player");
	}

	/**
	 * Open the dialog.
	 * @return the result
	 */
	public Object open() {
		createContents();
		shell.open();
		shell.layout();
		Display display = getParent().getDisplay();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		return result;
	}

	/**
	 * Create contents of the dialog.
	 */
	private void createContents() {
		shell = new Shell(getParent(), SWT.DIALOG_TRIM);
		shell.setSize(580, 280);
		shell.setText(getText());
		Utils.centreWindow(shell);
		text = new Text(shell, SWT.BORDER | SWT.MULTI);
		text.setText(ABOUT_US);
		text.setEditable(false);
		text.setBounds(241, 20, 323, 185);
		Canvas canvas = new Canvas(shell, SWT.DOUBLE_BUFFERED);
		canvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				Image img = SWTResourceManager.getImage(AboutWindow.class, "/logo.png");
				e.gc.drawImage(img, 20, 10);
			}
		});
		canvas.setBounds(10, 20, 201, 185);
	}

	public void forceFocus() {
		shell.forceFocus();
	}
}
