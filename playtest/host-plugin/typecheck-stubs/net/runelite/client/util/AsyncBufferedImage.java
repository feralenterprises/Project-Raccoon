package net.runelite.client.util;
public class AsyncBufferedImage extends java.awt.image.BufferedImage {
	public AsyncBufferedImage(int w, int h, int type) { super(w, h, type); }
	public synchronized void onLoaded(Runnable r) { r.run(); }
}
