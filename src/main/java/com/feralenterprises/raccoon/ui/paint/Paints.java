package com.feralenterprises.raccoon.ui.paint;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;

/** Small shared painting helpers. */
public final class Paints {
	private Paints() { }

	public static Graphics2D prepare(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return g2;
	}

	/** Tiles a texture at native resolution, anchored to the origin so it stays put while scrolling. */
	public static void tile(Graphics2D g, BufferedImage tex, int x, int y, int w, int h, float alpha) {
		if (tex == null) return;
		Composite old = g.getComposite();
		if (alpha < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		g.setPaint(new TexturePaint(tex, new Rectangle(0, 0, tex.getWidth(), tex.getHeight())));
		g.fillRect(x, y, w, h);
		g.setComposite(old);
	}

	public static void image(Graphics2D g, BufferedImage img, int x, int y, int w, int h, float alpha) {
		if (img == null) return;
		Composite old = g.getComposite();
		if (alpha < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(img, x, y, w, h, null);
		if (interp != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
		g.setComposite(old);
	}

	/** Draws game pixel art (item and interface sprites) with nearest-neighbour sampling so it stays crisp when
	 * the display scale is not an integer (e.g. Windows 150 %). */
	public static void sprite(Graphics2D g, BufferedImage img, int x, int y, int w, int h, float alpha) {
		if (img == null) return;
		Composite old = g.getComposite();
		if (alpha < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(img, x, y, w, h, null);
		if (interp != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
		else g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setComposite(old);
	}

	/** Scales to fit height h while keeping aspect; returns drawn width. */
	public static int widthForHeight(BufferedImage img, int h) {
		return img == null ? 0 : Math.round(img.getWidth() * (h / (float) img.getHeight()));
	}

	public static void dashedRect(Graphics2D g, Color c, int x, int y, int w, int h) {
		Stroke old = g.getStroke();
		g.setColor(c);
		g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f));
		g.drawRect(x, y, w - 1, h - 1);
		g.setStroke(old);
	}

	public static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }
}
