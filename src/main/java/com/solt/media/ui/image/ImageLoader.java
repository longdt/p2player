package com.solt.media.ui.image;

import java.io.InputStream;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

public class ImageLoader {
	private static ImageLoader instance;

	private Display display;

	public static ImageLoader getInstance() {
		if (ImageLoader.instance == null) {
			ImageLoader.instance = new ImageLoader(Display.getDefault());
		}
		return ImageLoader.instance;
	}

	public ImageLoader(Display display) {
		this.display = display;
	}

	public Image loadImage(String res) {
		Image img = null;
		try {
			InputStream is = this.getClass().getResourceAsStream(res);
			if (is != null) {
				img = new Image(display, is);
				is.close();
			}
		} catch (Throwable e) {
			System.err
					.println("ImageRepository:loadImage:: Resource not found: "
							+ res + "\n" + e);
		}
		return img;
	}
}
