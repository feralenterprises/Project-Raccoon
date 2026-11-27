package com.feralenterprises.raccoon.ui.shell;

import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Narrow arrowless scrollbar coloured from the palette. Per-component; never installed globally. */
public final class ThinScrollBarUI extends BasicScrollBarUI {
	private final UiContext ctx;
	private final int width;

	public ThinScrollBarUI(UiContext ctx, int width) {
		this.ctx = ctx;
		this.width = width;
	}

	@Override protected JButton createDecreaseButton(int o) { return zero(); }
	@Override protected JButton createIncreaseButton(int o) { return zero(); }

	private static JButton zero() {
		JButton b = new JButton();
		b.setPreferredSize(new Dimension(0, 0));
		b.setMinimumSize(new Dimension(0, 0));
		b.setMaximumSize(new Dimension(0, 0));
		return b;
	}

	@Override
	public Dimension getPreferredSize(JComponent c) { return new Dimension(width, 48); }

	@Override
	protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
		g.setColor(ctx.palette().trough);
		g.fillRect(r.x, r.y, r.width, r.height);
	}

	@Override
	protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
		if (r.isEmpty() || !scrollbar.isEnabled()) return;
		g.setColor(ctx.themed() ? ctx.palette().accentDim : ctx.palette().border);
		g.fillRect(r.x + 1, r.y + 1, Math.max(1, r.width - 2), Math.max(1, r.height - 2));
	}
}
