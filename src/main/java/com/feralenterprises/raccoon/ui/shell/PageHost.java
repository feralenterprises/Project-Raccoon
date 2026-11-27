package com.feralenterprises.raccoon.ui.shell;

import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.layout.StackLayout;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * Scrollable page surface. Always exactly as wide as the viewport (no horizontal overflow) and at least as tall as
 * it, so the layered environment extends below the final content section. Footer art gets reserved space and never
 * sits under content.
 */
public final class PageHost extends JPanel implements Scrollable {
	public static final int HEADER_BAND = 44;
	/**
	 * Client property a page sets to {@code Boolean.TRUE} to be stretched to the visible height when its content is
	 * shorter, so its own background reaches the footer reserve instead of showing the host surface below it.
	 */
	public static final String FILL_VIEWPORT = "raccoon.page.fillViewport";

	private final UiContext ctx;
	private final JComponent content;
	private final Environment environment;

	public PageHost(UiContext ctx, JComponent content, Environment environment) {
		this.ctx = ctx;
		this.content = content;
		this.environment = environment;
		setLayout(null);
		setOpaque(true);
		add(content);
	}

	public JComponent content() { return content; }
	public Environment environment() { return environment; }

	public int topReserve() { return environment == Environment.FULL ? HEADER_BAND : Tokens.INSET; }

	public int footerReserve() {
		if (environment != Environment.FULL) return Tokens.INSET;
		BufferedImage f = ctx.assets().art(ArtKey.SCENE_FOOTER);
		int h = f == null ? 96 : Math.min(Tokens.FOOTER_ART_MAX, f.getHeight());
		return h + Tokens.GAP;
	}

	private int viewportWidth() {
		Container p = getParent();
		return p instanceof JViewport && p.getWidth() > 0 ? p.getWidth() : (getWidth() > 0 ? getWidth() : Tokens.CONTENT_WIDTH);
	}

	private int contentHeight(int width) { return StackLayout.heightFor(content, width - 2 * Tokens.INSET); }

	/** Bottom edge of the last content section, in host coordinates. */
	public int contentBottom() { return content.getY() + content.getHeight(); }

	@Override
	public Dimension getPreferredSize() {
		int w = viewportWidth();
		return new Dimension(w, topReserve() + contentHeight(w) + footerReserve());
	}

	@Override
	public void doLayout() {
		int w = getWidth();
		int h = contentHeight(w);
		if (Boolean.TRUE.equals(content.getClientProperty(FILL_VIEWPORT))) h = Math.max(h, getHeight() - topReserve() - footerReserve());
		content.setBounds(Tokens.INSET, topReserve(), w - 2 * Tokens.INSET, h);
	}

	@Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
	@Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
	@Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return o == SwingConstants.VERTICAL ? r.height - 32 : r.width; }
	@Override public boolean getScrollableTracksViewportWidth() { return true; }

	@Override
	public boolean getScrollableTracksViewportHeight() {
		Container p = getParent();
		return p instanceof JViewport && p.getHeight() > getPreferredSize().height;
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		Palette p = ctx.palette();
		int w = getWidth(), h = getHeight();
		g2.setColor(p.background);
		g2.fillRect(0, 0, w, h);
		if (ctx.themed()) paintThemed(g2, p, w, h);
		else paintStructural(g2, p, w, h);
		g2.dispose();
	}

	private void paintThemed(Graphics2D g2, Palette p, int w, int h) {
		Paints.tile(g2, ctx.assets().art(ArtKey.SURFACE_DARK_STONE), 0, 0, w, h, 0.28f);
		if (environment != Environment.FULL) return;
		BufferedImage scene = ctx.assets().art(ArtKey.SCENE_HEADER);
		if (scene != null) {
			int srcY = Math.max(0, (scene.getHeight() - Math.round(HEADER_BAND * scene.getWidth() / (float) w)) / 2);
			int srcH = Math.round(HEADER_BAND * scene.getWidth() / (float) w);
			g2.drawImage(scene, 0, 0, w, HEADER_BAND, 0, srcY, scene.getWidth(), Math.min(scene.getHeight(), srcY + srcH), null);
			g2.setPaint(new GradientPaint(0, HEADER_BAND / 3f, Paints.alpha(p.background, 0), 0, HEADER_BAND, p.background));
			g2.fillRect(0, 0, w, HEADER_BAND);
		}
		BufferedImage vines = ctx.assets().art(ArtKey.DECOR_TOP_RIGHT);
		if (vines != null) {
			int vh = 40, vw = Paints.widthForHeight(vines, vh);
			Paints.image(g2, vines, w - vw + 6, -4, vw, vh, 0.85f);
		}
		BufferedImage foot = ctx.assets().art(ArtKey.SCENE_FOOTER);
		int fr = footerReserve() - Tokens.GAP;
		if (foot != null) {
			int fh = Math.min(fr, foot.getHeight());
			int fw = Math.round(foot.getWidth() * (fh / (float) foot.getHeight()));
			if (fw > w) { fw = w; fh = Math.round(foot.getHeight() * (fw / (float) foot.getWidth())); }
			int fy = h - fh;
			g2.setPaint(new GradientPaint(0, fy - 24, Paints.alpha(p.background, 0), 0, fy + 8, Paints.alpha(p.shadow, 160)));
			g2.fillRect(0, fy - 24, w, fh + 24);
			Paints.image(g2, foot, (w - fw) / 2, fy, fw, fh, 1f);
		}
		BufferedImage rocks = ctx.assets().art(ArtKey.DECOR_FOOT_LEFT), grass = ctx.assets().art(ArtKey.DECOR_FOOT_RIGHT);
		if (rocks != null) { int rh = 30, rw = Paints.widthForHeight(rocks, rh); Paints.image(g2, rocks, -6, h - rh, rw, rh, 1f); }
		if (grass != null) { int gh = 38, gw = Paints.widthForHeight(grass, gh); Paints.image(g2, grass, w - gw + 4, h - gh, gw, gh, 1f); }
	}

	private void paintStructural(Graphics2D g2, Palette p, int w, int h) {
		if (environment != Environment.FULL) return;
		g2.setFont(ctx.font(FontRole.SMALL));
		Paints.dashedRect(g2, p.border, 0, 0, w, HEADER_BAND - 2);
		g2.setColor(p.muted);
		g2.drawString("header scene band", 4, 14);
		int fr = footerReserve() - Tokens.GAP;
		Paints.dashedRect(g2, p.border, 0, h - fr, w, fr);
		g2.drawString("footer scene (reserved " + fr + " px)", 4, h - fr + 14);
	}
}
