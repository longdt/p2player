package com.solt.media.ui;

import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

public class InputDialog extends Dialog {

	protected String result;
	protected Shell shell;
	private Text input;

	/**
	 * Create the dialog.
	 * @param parent
	 * @param style
	 */
	public InputDialog(Shell parent, String message, int style) {
		super(parent, style);
		setText(message);
	}

	/**
	 * Open the dialog.
	 * @return the result
	 */
	public String open() {
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
		shell = new Shell(getParent(), getStyle());
		shell.setSize(450, 78);
		shell.setText(getText());
		Utils.centreWindow(shell);
		input = new Text(shell, SWT.BORDER);
		input.setBounds(10, 10, 331, 21);
		
		Button btnOk = new Button(shell, SWT.NONE);
		btnOk.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				result = input.getText();
				Display.getDefault().asyncExec(new Runnable() {
				    public void run() {
				    	shell.dispose();
				    }
				});
			}
		});
		btnOk.setBounds(359, 10, 75, 25);
		btnOk.setText("Ok");

	}
}
