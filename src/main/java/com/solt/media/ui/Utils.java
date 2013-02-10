package com.solt.media.ui;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

public class Utils {

	public static void centreWindow(Shell shell) {
		Rectangle displayArea; // area to center in
		if (shell.getParent() != null) {
			displayArea = shell.getParent().getBounds();
		} else {
  		try {
  			displayArea = shell.getMonitor().getClientArea();
  		} catch (NoSuchMethodError e) {
  			displayArea = shell.getDisplay().getClientArea();
  		}
		}

		Rectangle shellRect = shell.getBounds();

		if (shellRect.height > displayArea.height) {
			shellRect.height = displayArea.height;
		}
		if (shellRect.width > displayArea.width - 50) {
			shellRect.width = displayArea.width;
		}

		shellRect.x = displayArea.x + (displayArea.width - shellRect.width) / 2;
		shellRect.y = displayArea.y + (displayArea.height - shellRect.height) / 2;

		shell.setBounds(shellRect);
		shell.setLocation(shellRect.x, shellRect.y);
	}

	/**
	 * Centers a window relative to a control. That is to say, the window will be located at the center of the control.
	 * @param window
	 * @param control
	 */
	public static void centerWindowRelativeTo(final Shell window,
			final Control control) {
		final Rectangle bounds = control.getBounds();
		final Point shellSize = window.getSize();
		window.setLocation(bounds.x + (bounds.width / 2) - shellSize.x / 2,
				bounds.y + (bounds.height / 2) - shellSize.y / 2);
	}
}
