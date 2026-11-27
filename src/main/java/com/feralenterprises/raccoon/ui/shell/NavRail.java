package com.feralenterprises.raccoon.ui.shell;

import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.layout.StackLayout;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.paint.ThemedToolTip;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToolTip;

/**
 * Vertical navigation rail. One button per rail destination, grouped; the selected destination is highlighted.
 * Destination icons are not yet supplied, so buttons show live monograms (see docs: missing inputs).
 */
public final class NavRail extends JPanel implements com.feralenterprises.raccoon.ui.lifecycle.Disposable {
	private final UiContext ctx;
	private final Navigator nav;
	private final Map<String, RailButton> buttons = new LinkedHashMap<>();
	private final java.util.function.Consumer<String> onNavigate = id -> {
		buttons.values().forEach(JComponent::repaint);
		RailButton b = buttons.get(id);
		if (b != null && b.getHeight() > 0) b.scrollRectToVisible(new java.awt.Rectangle(0, 0, b.getWidth(), b.getHeight()));
	};

	public NavRail(UiContext ctx, Navigator nav) {
		this.ctx = ctx;
		this.nav = nav;
		setLayout(new StackLayout(2));
		setOpaque(true);
		Destination.Group last = null;
		for (Destination d : nav.destinations()) {
			if (!d.inRail()) continue;
			if (last != null && d.group() != last) add(new Separator());
			last = d.group();
			RailButton b = new RailButton(d);
			buttons.put(d.id(), b);
			add(b);
		}
		nav.addListener(onNavigate);
	}

	public RailButton button(String id) { return buttons.get(id); }
	public int buttonCount() { return buttons.size(); }

	@Override
	public void dispose() { nav.removeListener(onNavigate); }

	@Override
	public Dimension getPreferredSize() {
		int w = Tokens.RAIL_WIDTH - Tokens.RAIL_SCROLLBAR;
		return new Dimension(w, ((StackLayout) getLayout()).heightForWidth(this, w) + 4);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		Palette p = ctx.palette();
		g2.setColor(ctx.themed() ? p.background : p.panelRaised);
		g2.fillRect(0, 0, getWidth(), getHeight());
		if (ctx.themed()) Paints.tile(g2, ctx.assets().art(ArtKey.SURFACE_FIELDSTONE), 0, 0, getWidth(), getHeight(), 0.22f);
		g2.dispose();
	}

	private final class Separator extends JComponent {
		@Override public Dimension getPreferredSize() { return new Dimension(10, 7); }
		@Override protected void paintComponent(Graphics g) {
			g.setColor(ctx.themed() ? Paints.alpha(ctx.palette().accent, 120) : ctx.palette().border);
			g.drawLine(5, 3, getWidth() - 6, 3);
		}
	}

	public final class RailButton extends JButton {
		private final Destination d;

		RailButton(Destination d) {
			this.d = d;
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setOpaque(false);
			setRolloverEnabled(true);
			setToolTipText(d.label());
			getAccessibleContext().setAccessibleName(d.label());
			addActionListener(e -> nav.navigate(d.id()));
			// No visible rail scrollbar: keyboard focus scrolls the button into view.
			addFocusListener(new java.awt.event.FocusAdapter() {
				@Override public void focusGained(java.awt.event.FocusEvent e) { scrollRectToVisible(new java.awt.Rectangle(0, 0, getWidth(), getHeight())); }
			});
		}

		public Destination destination() { return d; }
		public boolean isCurrent() { return d.id().equals(nav.current()); }

		@Override public JToolTip createToolTip() { return ThemedToolTip.create(ctx, this); }
		@Override public Dimension getPreferredSize() { return new Dimension(Tokens.RAIL_WIDTH - Tokens.RAIL_SCROLLBAR, Tokens.RAIL_BUTTON_HEIGHT); }

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Paints.prepare(g);
			Palette p = ctx.palette();
			int w = getWidth(), h = getHeight();
			boolean on = isCurrent();
			g2.setColor(on ? p.accentDim : (getModel().isRollover() ? p.panelRaised : p.panel));
			g2.fillRect(3, 1, w - 6, h - 2);
			g2.setColor(on ? p.accent : p.border);
			g2.drawRect(3, 1, w - 7, h - 3);
			if (on) { g2.setColor(p.accent); g2.fillRect(0, 3, 2, h - 6); }
			if (isFocusOwner()) {
				g2.setColor(p.focus);
				g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{2f, 2f}, 0f));
				g2.drawRect(5, 3, w - 11, h - 7);
			}
			g2.setFont(ctx.font(FontRole.SMALL).deriveFont(java.awt.Font.BOLD));
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(on ? p.text : p.muted);
			String m = d.monogram();
			g2.drawString(m, (w - fm.stringWidth(m)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
			g2.dispose();
		}
	}
}
