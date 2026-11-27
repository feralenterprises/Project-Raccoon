package com.feralenterprises.raccoon.ui.paint;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Draws a frame from a nine-slice atlas: four fixed-size corners, straight edges stretched along one axis only, and
 * a skipped centre (content draws its own surface). The full frame image is never stretched.
 */
public final class NineSlice {
	private NineSlice() { }

	public static void paint(Graphics2D g, BufferedImage atlas, int slice, int corner, int x, int y, int w, int h) {
		if (atlas == null || w <= 0 || h <= 0) return;
		int sw = atlas.getWidth(), sh = atlas.getHeight();
		int c = Math.min(corner, Math.min(w / 2, h / 2));
		Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		// corners
		draw(g, atlas, 0, 0, slice, slice, x, y, c, c);
		draw(g, atlas, sw - slice, 0, slice, slice, x + w - c, y, c, c);
		draw(g, atlas, 0, sh - slice, slice, slice, x, y + h - c, c, c);
		draw(g, atlas, sw - slice, sh - slice, slice, slice, x + w - c, y + h - c, c, c);
		// edges (stretched along their length only)
		draw(g, atlas, slice, 0, sw - 2 * slice, slice, x + c, y, w - 2 * c, c);
		draw(g, atlas, slice, sh - slice, sw - 2 * slice, slice, x + c, y + h - c, w - 2 * c, c);
		draw(g, atlas, 0, slice, slice, sh - 2 * slice, x, y + c, c, h - 2 * c);
		draw(g, atlas, sw - slice, slice, slice, sh - 2 * slice, x + w - c, y + c, c, h - 2 * c);
		if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
	}

	private static void draw(Graphics2D g, BufferedImage img, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh) {
		if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) return;
		g.drawImage(img, dx, dy, dx + dw, dy + dh, sx, sy, sx + sw, sy + sh, null);
	}
}
