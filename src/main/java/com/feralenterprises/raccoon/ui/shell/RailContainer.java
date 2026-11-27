package com.feralenterprises.raccoon.ui.shell;

import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.paint.ThemedToolTip;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolTip;
import javax.swing.JViewport;

/**
 * Holds the rail's scroll pane (zero-width bar, wheel and focus scrolling) and adds accessible overflow cues: a chevron
 * button appears at an edge only while more destinations lie beyond it, so short sidebars (e.g. 1080p at 200 %) keep
 * every destination reachable by mouse without a permanent scrollbar or separator (owner correction UI-002/UI-003).
 */
final class RailContainer extends JPanel {
	static final int CUE_H = 14;
	private final JScrollPane scroll;
	private final Cue up, down;

	RailContainer(UiContext ctx, JScrollPane scroll) {
		super(null);
		this.scroll = scroll;
		setOpaque(false);
		up = new Cue(ctx, true);
		down = new Cue(ctx, false);
		add(up);
		add(down);
		add(scroll);
		scroll.getViewport().addChangeListener(e -> updateCues());
	}

	/** Cues overlap the scroll pane, so optimized (non-overlapping) drawing must be off. */
	@Override public boolean isOptimizedDrawingEnabled() { return false; }

	Cue upCue() { return up; }
	Cue downCue() { return down; }

	@Override
	public void doLayout() {
		scroll.setBounds(0, 0, getWidth(), getHeight());
		scroll.doLayout();
		up.setBounds(0, 0, getWidth(), CUE_H);
		down.setBounds(0, getHeight() - CUE_H, getWidth(), CUE_H);
		updateCues();
	}

	void updateCues() {
		JViewport vp = scroll.getViewport();
		if (vp.getView() == null) return;
		int y = vp.getViewPosition().y, max = vp.getView().getHeight() - vp.getHeight();
		up.setVisible(y > 0);
		down.setVisible(max > 0 && y < max);
	}

	private void step(int direction) {
		JViewport vp = scroll.getViewport();
		int max = Math.max(0, vp.getView().getHeight() - vp.getHeight());
		int y = Math.max(0, Math.min(max, vp.getViewPosition().y + direction * (Tokens.RAIL_BUTTON_HEIGHT + 2)));
		vp.setViewPosition(new Point(0, y));
		updateCues();
	}

	final class Cue extends JButton {
		private final UiContext ctx;
		private final boolean upward;

		Cue(UiContext ctx, boolean upward) {
			this.ctx = ctx;
			this.upward = upward;
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setOpaque(false);
			setVisible(false);
			String label = upward ? "Scroll destinations up" : "Scroll destinations down";
			setToolTipText(label);
			getAccessibleContext().setAccessibleName(label);
			addActionListener(e -> step(upward ? -1 : 1));
		}

		@Override public JToolTip createToolTip() { return ThemedToolTip.create(ctx, this); }
		@Override public Dimension getPreferredSize() { return new Dimension(Tokens.RAIL_WIDTH, CUE_H); }

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Paints.prepare(g);
			Palette p = ctx.palette();
			int w = getWidth(), h = getHeight();
			g2.setPaint(new GradientPaint(0, upward ? 0 : h, ctx.themed() ? p.background : p.panelRaised, 0, upward ? h : 0,
				Paints.alpha(ctx.themed() ? p.background : p.panelRaised, 0)));
			g2.fillRect(0, 0, w, h);
			g2.setColor(getModel().isRollover() ? p.focus : p.accent);
			int cx = w / 2, cy = h / 2;
			Polygon tri = upward ? new Polygon(new int[]{cx - 5, cx + 5, cx}, new int[]{cy + 2, cy + 2, cy - 3}, 3)
				: new Polygon(new int[]{cx - 5, cx + 5, cx}, new int[]{cy - 2, cy - 2, cy + 3}, 3);
			g2.fillPolygon(tri);
			g2.dispose();
		}
	}
}
