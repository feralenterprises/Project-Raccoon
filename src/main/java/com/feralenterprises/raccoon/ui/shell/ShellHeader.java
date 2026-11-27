package com.feralenterprises.raccoon.ui.shell;

import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.component.Tone;
import com.feralenterprises.raccoon.ui.layout.StackLayout;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.swing.JPanel;

/**
 * The one compact header every page shares (owner clarification, roadmap #1): panel name on the left; Back and Settings
 * are adjacent fixed controls at the far right, Settings rightmost. While the rail is hidden, a destinations menu
 * button sits left of the title. Height is about 29 px at Default text; the title wraps rather than clips.
 */
public final class ShellHeader extends JPanel {
	private static final int BTN = 22;
	private static final int PAD = 4, GAP = 2;
	private final UiContext ctx;
	private final GlyphButton back, settings, menu;
	private final TextBlock title;

	public ShellHeader(UiContext ctx) {
		this.ctx = ctx;
		setOpaque(true);
		back = new GlyphButton(ctx, "Back", true);
		settings = new GlyphButton(ctx, "Settings", false);
		menu = new GlyphButton(ctx, "Destinations", null);
		menu.setVisible(false);
		title = new TextBlock(ctx, "", FontRole.TITLE, Tone.TEXT);
		add(back);
		add(menu);
		add(title);
		add(settings);
		setLayout(new Layout());
	}

	public RButton back() { return back; }
	public RButton settings() { return settings; }
	public TextBlock title() { return title; }
	/** Destination menu shown only while the rail is hidden, so navigation is never duplicated. */
	public RButton menu() { return menu; }

	private int leftControls() { return menu.isVisible() ? PAD + BTN + GAP : PAD; }
	private int rightControls() { return 2 * BTN + GAP + PAD; }

	private int titleWidth(int w) { return Math.max(1, w - leftControls() - rightControls() - 2 * GAP); }

	@Override
	public Dimension getPreferredSize() {
		int w = getWidth() > 0 ? getWidth() : 225;
		return new Dimension(w, Math.max(BTN, StackLayout.heightFor(title, titleWidth(w))) + 7);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		Palette p = ctx.palette();
		g2.setColor(ctx.themed() ? p.panel : p.panelRaised);
		g2.fillRect(0, 0, getWidth(), getHeight());
		if (ctx.themed()) {
			Paints.tile(g2, ctx.assets().art(ArtKey.SURFACE_TIMBER), 0, 0, getWidth(), getHeight(), 0.30f);
			g2.setColor(Paints.alpha(p.accent, 150));
		} else g2.setColor(p.border);
		g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
		g2.dispose();
	}

	private final class Layout implements LayoutManager {
		@Override
		public void layoutContainer(Container c) {
			int w = getWidth(), h = getHeight();
			int y = (h - BTN) / 2;
			if (menu.isVisible()) menu.setBounds(PAD, y, BTN, BTN);
			settings.setBounds(w - PAD - BTN, y, BTN, BTN);
			back.setBounds(w - PAD - 2 * BTN - GAP, y, BTN, BTN);
			int tw = titleWidth(w), th = StackLayout.heightFor(title, tw);
			title.setBounds(leftControls() + GAP, (h - th) / 2, tw, th);
		}

		@Override public Dimension preferredLayoutSize(Container c) { return getPreferredSize(); }
		@Override public Dimension minimumLayoutSize(Container c) { return getPreferredSize(); }
		@Override public void addLayoutComponent(String n, Component c) { }
		@Override public void removeLayoutComponent(Component c) { }
	}

	/** Icon-only control drawn with vector shapes (no font glyph dependence), labelled for tooltips and a11y. */
	static final class GlyphButton extends RButton {
		private final Boolean backGlyph;

		GlyphButton(UiContext ctx, String label, Boolean backGlyph) {
			super(ctx, "");
			this.backGlyph = backGlyph;
			setToolTipText(label);
			getAccessibleContext().setAccessibleName(label);
			setName(label.toLowerCase());
		}

		@Override public Dimension getPreferredSize() { return new Dimension(BTN, BTN); }
		@Override public int heightForWidth(int w) { return BTN; }

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = Paints.prepare(g);
			UiContext c = context();
			g2.setColor(isEnabled() ? c.palette().text : c.palette().muted);
			g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			int w = getWidth(), h = getHeight();
			if (backGlyph == null) {
				for (int i = 0; i < 3; i++) g2.drawLine((int) (w * 0.3), (int) (h * (0.32 + 0.18 * i)), (int) (w * 0.7), (int) (h * (0.32 + 0.18 * i)));
			} else if (backGlyph) {
				Path2D a = new Path2D.Float();
				a.moveTo(w * 0.58, h * 0.28); a.lineTo(w * 0.36, h * 0.5); a.lineTo(w * 0.58, h * 0.72);
				g2.draw(a);
			} else {
				double cx = w / 2.0, cy = h / 2.0;
				for (int i = 0; i < 8; i++) {
					double t = Math.PI * i / 4;
					g2.draw(new java.awt.geom.Line2D.Double(cx + Math.cos(t) * 4.5, cy + Math.sin(t) * 4.5, cx + Math.cos(t) * 7.5, cy + Math.sin(t) * 7.5));
				}
				g2.draw(new Ellipse2D.Double(cx - 4.5, cy - 4.5, 9, 9));
			}
			g2.dispose();
		}
	}
}
