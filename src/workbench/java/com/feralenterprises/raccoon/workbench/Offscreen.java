package com.feralenterprises.raccoon.workbench;

import com.feralenterprises.raccoon.ui.shell.PageHost;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

/** Offscreen sizing and painting of real Swing components (no display needed). */
public final class Offscreen {
	private Offscreen() { }

	/** Sizes the shell at its preferred width (rail + chrome + content + scrollbar). */
	public static void size(RaccoonShell shell, int height) { size(shell, shell.shellWidth(), height); }

	/** Sizes the shell as if a host (RuneLite sidebar) gave it exactly {@code width}. */
	public static void size(RaccoonShell shell, int width, int height) {
		shell.setSize(width, height);
		for (int i = 0; i < 3; i++) LayoutAudit.layoutTree(shell);
	}

	public static BufferedImage paint(JComponent c, double scale) {
		int w = (int) Math.ceil(c.getWidth() * scale), h = (int) Math.ceil(c.getHeight() * scale);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.MAGENTA);
		g.fillRect(0, 0, w, h);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.scale(scale, scale);
		c.printAll(g);
		g.dispose();
		return img;
	}

	/** Paints a page host at its full preferred length (the whole scrollable page, not just the viewport). */
	public static BufferedImage fullPage(PageHost host) {
		int w = host.getWidth(), h = host.getPreferredSize().height;
		int oldH = host.getHeight();
		host.setSize(w, Math.max(h, oldH));
		LayoutAudit.layoutTree(host);
		BufferedImage img = paint(host, 1.0);
		host.setSize(w, oldH);
		LayoutAudit.layoutTree(host);
		return img;
	}

	public static void write(BufferedImage img, File f) throws IOException {
		f.getParentFile().mkdirs();
		ImageIO.write(img, "png", f);
	}
}
