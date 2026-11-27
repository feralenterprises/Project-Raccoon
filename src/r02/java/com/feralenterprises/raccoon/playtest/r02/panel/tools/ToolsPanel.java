package com.feralenterprises.raccoon.playtest.r02.panel.tools;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.panel.files.ArchiveWins;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Label;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Style;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.layout.StackLayout;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.lifecycle.Disposable;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * R02 Tools (frozen {@code freshTools}): the map-desk illustration and the Adventure tools card. Five actions are typed
 * route links; Biggest Wins opens a local view (local Back) showing the latest verified notable loot from this scope's
 * archive, by the same rule as Feral Files. Tools opens from Settings, Interface (no rail entry).
 */
final class ToolsPanel extends JPanel implements HeightForWidth, LocalBack, Disposable {
	private final RouteContext context;
	private final UiContext uiContext;
	private final ToolsInputs in;
	private final ArchiveWins wins;
	private final BufferedImage stone, wood;
	private final Runnable textScaleChanged = this::render;
	private boolean showWins;
	private Runnable onBackChanged = () -> { };

	ToolsPanel(RouteContext context, ToolsInputs in, ArchiveWins wins, BufferedImage stone, BufferedImage wood) {
		super(new StackLayout(7));
		this.context = context;
		this.uiContext = context.ui();
		this.in = in;
		this.wins = wins;
		this.stone = stone;
		this.wood = wood;
		setName("tools");
		setOpaque(true);
		setBorder(new EmptyBorder(7, 6, 12, 6));
		render();
		context.onSnapshot(s -> render());
		uiContext.addChangeListener(textScaleChanged);
	}

	@Override public void dispose() { uiContext.removeChangeListener(textScaleChanged); }

	private void render() {
		removeAll();
		if (showWins) renderWins(context.snapshot());
		else {
			add(new Illustration());
			Card card = new Card("Adventure tools");
			card.setName("tools-card");
			for (ToolsInputs.Action a : in.actions) {
				Action b = new Action(a.icon, a.label);
				b.setName("tools-" + a.label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
				b.addActionListener(e -> {
					if (a.route == null) openWins();
					else context.navigate(a.route);
				});
				card.body.add(b);
			}
			add(card);
		}
		revalidate();
		repaint();
	}

	private void renderWins(FixtureSnapshot s) {
		Card card = new Card("Biggest Wins");
		card.setName("tools-wins");
		ArchiveWins.Win w = s.scope().signedIn() ? wins.latest(s, context.registry()) : null;
		if (w == null) card.body.add(paragraph("Verified wins unavailable.", "tools-wins-none"));
		else {
			card.body.add(paragraph(w.itemName + " · " + w.recordTitle, "tools-win"));
			Action open = new Action(null, "Open saved session");
			open.setName("tools-open-session");
			open.addActionListener(e -> context.navigate(RouteId.FILES, RouteRequest.Param.SESSION, w.recordId));
			card.body.add(open);
			card.body.add(paragraph("Latest verified notable loot in this demo archive.", "tools-wins-note"));
		}
		add(card);
	}

	private JComponent paragraph(String text, String name) {
		Label l = new Label(uiContext, text, Style.NOTE, new Color(0xeedbc0));
		l.setName(name);
		JPanel p = new StackPanel(0);
		p.setOpaque(false);
		p.setBorder(new EmptyBorder(3, 2, 3, 2));
		p.add(l);
		return p;
	}

	private void openWins() {
		showWins = true;
		render();
		onBackChanged.run();
	}

	@Override public boolean hasLocalBack() { return showWins; }

	@Override public void localBack() {
		if (!showWins) return;
		showWins = false;
		render();
		onBackChanged.run();
		SwingUtilities.invokeLater(() -> {
			JComponent opener = named(this, "tools-biggest-wins");
			if (opener != null) opener.requestFocusInWindow();
		});
	}

	private static JComponent named(java.awt.Component c, String name) {
		if (name.equals(c.getName()) && c instanceof JComponent) return (JComponent) c;
		if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
			JComponent j = named(k, name);
			if (j != null) return j;
		}
		return null;
	}

	@Override public void setLocalBackListener(Runnable onChange) { onBackChanged = onChange == null ? () -> { } : onChange; }

	@Override public int heightForWidth(int width) { return ((StackLayout) getLayout()).heightForWidth(this, width); }

	@Override public Dimension getPreferredSize() {
		int w = getWidth() > 0 ? getWidth() : 225;
		return new Dimension(w, heightForWidth(w));
	}

	/** Frozen {@code .r02-extension}: stone at 150px under a 100° dark gradient. */
	@Override protected void paintComponent(Graphics g) {
		Graphics2D g2 = AdventureUi.prepare(g);
		int w = getWidth(), h = getHeight();
		g2.setColor(new Color(0x211d17));
		g2.fillRect(0, 0, w, h);
		AdventureUi.tile(g2, stone, w, h, 150);
		g2.setPaint(new GradientPaint(0, 0, new Color(0x20, 0x1b, 0x15, 0xde), w, h * 0.18f, new Color(0x32, 0x26, 0x1d, 0xc9)));
		g2.fillRect(0, 0, w, h);
		g2.dispose();
	}

	// ------------------------------------------------------------------ components

	/** Frozen {@code .r02-illustration}: 67px camp backdrop with the PR-066 map desk at the right. */
	private final class Illustration extends JComponent implements HeightForWidth {
		private final BufferedImage camp = context.assets().art("PR-026"), desk = context.assets().art("PR-066");

		Illustration() { setName("tools-illustration"); }

		@Override public int heightForWidth(int width) { return 67; }

		@Override public Dimension getPreferredSize() { return new Dimension(213, 67); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight();
			g2.clip(new RoundRectangle2D.Float(0, 0, w, h, 8, 8));
			g2.setColor(new Color(0x291c12));
			g2.fillRect(0, 0, w, h);
			if (camp != null) {
				double s = Math.max(w / (double) camp.getWidth(), h / (double) camp.getHeight());
				int dw = (int) Math.ceil(camp.getWidth() * s), dh = (int) Math.ceil(camp.getHeight() * s);
				g2.drawImage(camp, (w - dw) / 2, Math.round((h - dh) * 0.6f), dw, dh, null);
			}
			g2.setPaint(new GradientPaint(0, 0, new Color(0x29, 0x1c, 0x12, 0xa0), w, 0, new Color(0x36, 0x26, 0x18, 0x60)));
			g2.fillRect(0, 0, w, h);
			if (desk != null) AdventureUi.contain(g2, desk, w - 4 - 115, h - 61, 115, 61);
			g2.setClip(null);
			g2.setColor(new Color(0x967449));
			g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
			g2.dispose();
		}
	}

	/** Frozen {@code .r02-card}: bronze edge, stone under a dark gradient, timber {@code h2} heading (14px bold). */
	private final class Card extends JPanel implements HeightForWidth {
		final StackPanel body = new StackPanel(5);

		Card(String title) {
			super(new StackLayout(0));
			setOpaque(false);
			setBorder(new EmptyBorder(1, 1, 1, 1));
			body.setOpaque(false);
			body.setBorder(new EmptyBorder(6, 6, 6, 6));
			add(new Heading(title));
			add(body);
		}

		@Override public int heightForWidth(int width) { return ((StackLayout) getLayout()).heightForWidth(this, width); }

		@Override public Dimension getPreferredSize() { return new Dimension(213, heightForWidth(213)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight();
			g2.clip(new RoundRectangle2D.Float(0, 0, w, h, 8, 8));
			AdventureUi.tile(g2, stone, w, h, 112);
			g2.setPaint(new GradientPaint(0, 0, new Color(0x3b, 0x31, 0x26, 0xee), w, h * 0.5f, new Color(0x1e, 0x1b, 0x17, 0xee)));
			g2.fillRect(0, 0, w, h);
			g2.dispose();
		}

		@Override protected void paintBorder(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			g2.setColor(new Color(0x9d8155));
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
			g2.dispose();
		}
	}

	private final class Heading extends JComponent implements HeightForWidth {
		private final String title;

		Heading(String title) {
			this.title = title;
			setName("tools-heading");
			getAccessibleContext();
		}

		private java.awt.Font font() { return Style.FEATURE.font(uiContext); }

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(font());
			return 12 + TextWrap.wrap(title, fm, Math.max(1, width - 16)).size() * fm.getHeight() + 1;
		}

		@Override public Dimension getPreferredSize() { return new Dimension(211, heightForWidth(211)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight() - 1;
			AdventureUi.tile(g2, wood, w, h, 130);
			g2.setPaint(new GradientPaint(0, 0, new Color(0x4a, 0x36, 0x25, 0xa8), 0, h, new Color(0x28, 0x1e, 0x16, 0xc7)));
			g2.fillRect(0, 0, w, h);
			g2.setColor(new Color(0x91724f));
			g2.fillRect(0, h, w, 1);
			g2.setFont(font());
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(new Color(0xfae0a7));
			int y = 6;
			for (String l : TextWrap.wrap(title, fm, Math.max(1, w - 16))) { g2.drawString(l, 8, y + fm.getAscent()); y += fm.getHeight(); }
			g2.dispose();
		}

		@Override public javax.accessibility.AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public javax.accessibility.AccessibleRole getAccessibleRole() { return javax.accessibility.AccessibleRole.HEADER; }
				@Override public String getAccessibleName() { return title; }
			};
			return accessibleContext;
		}
	}

	/** Frozen {@code .r02-action}: optional 20px icon, bold label, gold chevron; 32px minimum. */
	private final class Action extends JButton implements HeightForWidth {
		private final BufferedImage icon;

		Action(BufferedImage icon, String label) {
			super(label);
			this.icon = icon;
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			getAccessibleContext().setAccessibleName(label);
		}

		private int textX() { return 7 + (icon != null ? 20 + 5 : 0); }

		private int textW(int w) { return Math.max(1, w - textX() - 6 - 14); }

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(Style.ROW.font(uiContext));
			return Math.max(32, 12 + TextWrap.wrap(getText(), fm, textW(width)).size() * fm.getHeight());
		}

		@Override public Dimension getPreferredSize() { return new Dimension(199, heightForWidth(199)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight();
			g2.setPaint(new GradientPaint(0, 0, getModel().isRollover() ? new Color(0x5e4b33) : new Color(0x51402c), 0, h, new Color(0x282116)));
			g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
			g2.setColor(new Color(0x9a8057));
			g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
			if (icon != null) AdventureUi.contain(g2, icon, 7, (h - 20) / 2, 20, 20);
			g2.setFont(Style.ROW.font(uiContext));
			FontMetrics fm = g2.getFontMetrics();
			java.util.List<String> lines = TextWrap.wrap(getText(), fm, textW(w));
			int y = (h - lines.size() * fm.getHeight()) / 2;
			g2.setColor(new Color(0xf4dec0));
			for (String l : lines) { g2.drawString(l, textX(), y + fm.getAscent()); y += fm.getHeight(); }
			g2.setFont(Style.HEAD.font(uiContext));
			FontMetrics c = g2.getFontMetrics();
			g2.setColor(new Color(0xe4bd72));
			g2.drawString("›", w - 7 - c.stringWidth("›"), (h + c.getAscent()) / 2 - 2);
			if (isFocusOwner()) {
				g2.setColor(AdventureUi.FOCUS);
				g2.setStroke(new BasicStroke(2f));
				g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
			}
			g2.dispose();
		}
	}
}
