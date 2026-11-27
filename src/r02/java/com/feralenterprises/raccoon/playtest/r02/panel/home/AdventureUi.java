package com.feralenterprises.raccoon.playtest.r02.panel.home;

import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.layout.StackLayout;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Lane A's frozen R02 presentation kit (Home first; reused by Current Activity, Feral Files and Tools). Colours and
 * metrics are the frozen {@code Project-Raccoon-R02.html} rules; fonts come from the shared {@link UiContext} roles so
 * appearance text size still scales. Every component reports height-for-width so text wraps instead of clipping.
 */
public final class AdventureUi {
	static final Color HEAD = new Color(0xf1d7a5), FEATURE_HEAD = new Color(0x342519), ROW_TITLE = new Color(0xf0dfbd),
		MUTED = new Color(0xc1b19a), END = new Color(0xdfc69c), CHEVRON = new Color(0xd3b681), DETAIL = new Color(0xd4c6ad),
		DETAIL_RULE = new Color(0x85704d), LINK = new Color(0xe8c477), TEXT_ACTION = new Color(0xc9bda5),
		CARD_EDGE = new Color(0x8f7148), FEATURE_EDGE = new Color(0xb08c53), HEAD_RULE = new Color(0x9a7950),
		ROW_RULE = new Color(0x7c, 0x69, 0x43, 0x2e), REMAINING = new Color(0xc9d799), SNAPSHOT = new Color(0xd8c8ac),
		SNAPSHOT_VALUE = new Color(0xf1dfb9), GOAL_TITLE = new Color(0xf5e1b8), FOCUS = new Color(0xd8bc73),
		METER_EDGE = new Color(0xbb955e), METER_TROUGH = new Color(0x181813), FILL_TOP = new Color(0xa4b575),
		FILL_BOTTOM = new Color(0x667e3d), PRIMARY_TOP = new Color(0x657543), PRIMARY_BOTTOM = new Color(0x4b572e),
		PRIMARY_EDGE = new Color(0xaab575), PRIMARY_TEXT = new Color(0xf3ead4);

	private AdventureUi() { }

	/** Text style: a framework role (for scaling), a weight and a size relative to the role's base size. */
	enum Style {
		HEAD(FontRole.TITLE, Font.BOLD, 1f), FEATURE(FontRole.TITLE, Font.BOLD, 14f / 13f), GOAL(FontRole.TITLE, Font.BOLD, 14f / 13f),
		ROW(FontRole.SMALL, Font.BOLD, 1f), SMALL(FontRole.SMALL, Font.PLAIN, 1f), TINY(FontRole.QUANTITY, Font.PLAIN, 1f),
		BUTTON(FontRole.TITLE, Font.BOLD, 1f);

		final FontRole role;
		final int weight;
		final float scale;

		Style(FontRole role, int weight, float scale) {
			this.role = role;
			this.weight = weight;
			this.scale = scale;
		}

		Font font(UiContext ui) {
			Font f = ui.font(role);
			return f.deriveFont(weight, f.getSize2D() * scale);
		}
	}

	// ------------------------------------------------------------------ painting helpers

	static void tile(Graphics2D g, BufferedImage img, int w, int h, int tile) {
		if (img == null || tile <= 0) return;
		int th = Math.max(1, Math.round(tile * img.getHeight() / (float) img.getWidth()));
		for (int y = 0; y < h; y += th) for (int x = 0; x < w; x += tile) g.drawImage(img, x, y, tile, th, null);
	}

	static void contain(Graphics2D g, BufferedImage img, int x, int y, int w, int h) {
		if (img == null) return;
		double s = Math.min(w / (double) img.getWidth(), h / (double) img.getHeight());
		int dw = (int) Math.round(img.getWidth() * s), dh = (int) Math.round(img.getHeight() * s);
		g.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
	}

	static Graphics2D prepare(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		return g2;
	}

	static void focusRing(Graphics2D g, JComponent c) {
		if (!c.isFocusOwner()) return;
		g.setColor(FOCUS);
		g.setStroke(new BasicStroke(2f));
		g.drawRect(1, 1, c.getWidth() - 3, c.getHeight() - 3);
	}

	/** Activates on click, Enter or Space; repaints on focus change. */
	static void activator(JComponent c, Runnable action) {
		c.setFocusable(true);
		c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		c.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) { if (c.isEnabled()) { c.requestFocusInWindow(); action.run(); } }
		});
		c.addKeyListener(new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) { action.run(); e.consume(); }
			}
		});
		c.addFocusListener(new FocusAdapter() {
			@Override public void focusGained(FocusEvent e) { c.repaint(); }
			@Override public void focusLost(FocusEvent e) { c.repaint(); }
		});
	}

	// ------------------------------------------------------------------ text

	/** Wrapping text in a frozen colour; its accessible name is its text. */
	static class Label extends JComponent implements HeightForWidth {
		private final UiContext ui;
		private final Style style;
		private String text;
		private Color color;
		private boolean rule;

		Label(UiContext ui, String text, Style style, Color color) {
			this.ui = ui;
			this.text = text;
			this.style = style;
			this.color = color;
			setOpaque(false);
		}

		/** Frozen {@code .detail-copy}: a 1px rule at the left and 4px indent. */
		Label detailRule() { rule = true; return this; }

		String text() { return text; }

		void setText(String t) {
			if (t.equals(text)) return;
			text = t;
			revalidate();
			repaint();
		}

		void setColor(Color c) { color = c; repaint(); }

		private int indent() { return rule ? 5 : 0; }

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(style.font(ui));
			return Math.max(1, TextWrap.wrap(text, fm, Math.max(1, width - indent())).size()) * fm.getHeight() + (rule ? 2 : 0);
		}

		@Override public int minimumContentWidth() {
			FontMetrics fm = getFontMetrics(style.font(ui));
			int w = 0;
			for (String word : text.split("\\s+")) w = Math.max(w, fm.stringWidth(word));
			return w + indent();
		}

		@Override public Dimension getPreferredSize() {
			FontMetrics fm = getFontMetrics(style.font(ui));
			int w = fm.stringWidth(text) + indent();
			return new Dimension(w, heightForWidth(w));
		}

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = prepare(g);
			g2.setFont(style.font(ui));
			FontMetrics fm = g2.getFontMetrics();
			if (rule) {
				g2.setColor(DETAIL_RULE);
				g2.fillRect(0, 1, 1, getHeight() - 2);
			}
			g2.setColor(color);
			int y = fm.getAscent() + (rule ? 1 : 0);
			for (String l : TextWrap.wrap(text, fm, Math.max(1, getWidth() - indent()))) {
				g2.drawString(l, indent(), y);
				y += fm.getHeight();
			}
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.LABEL; }
				@Override public String getAccessibleName() { return text; }
			};
			return accessibleContext;
		}
	}

	// ------------------------------------------------------------------ section card

	/**
	 * Frozen {@code .card} with a timber {@code .sectionhead} band ({@code feature}: plaster band, gold edge). A
	 * collapsible section toggles its body from the band (click, Enter or Space) like the frozen {@code details}.
	 */
	static class Section extends JPanel implements HeightForWidth {
		final Band band;
		final StackPanel body = new StackPanel(0);
		private final Art art;
		private final boolean feature;

		Section(UiContext ui, Art art, String title, BufferedImage icon, boolean feature, boolean collapsible) {
			super(new StackLayout(0));
			this.art = art;
			this.feature = feature;
			setOpaque(false);
			setBorder(new EmptyBorder(1, 1, 1, 1));
			band = new Band(ui, art, title, icon, feature, collapsible ? this::setExpanded : null);
			body.setOpaque(false);
			add(band);
			add(body);
		}

		boolean expanded() { return body.isVisible(); }

		void setExpanded(boolean open) {
			body.setVisible(open);
			band.expanded = open;
			revalidate();
			repaint();
		}

		@Override public int heightForWidth(int width) { return ((StackLayout) getLayout()).heightForWidth(this, width); }

		@Override public Dimension getPreferredSize() {
			int w = getWidth() > 0 ? getWidth() : 225;
			return new Dimension(w, heightForWidth(w));
		}

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = prepare(g);
			java.awt.Shape clip = new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6);
			g2.clip(clip);
			tile(g2, art.stone, getWidth(), getHeight(), 160);
			g2.setPaint(new GradientPaint(0, 0, new Color(0x29, 0x25, 0x1d, 0xed), getWidth(), getHeight() / 3f,
				new Color(0x25, 0x22, 0x19, 0xe8)));
			g2.fillRect(0, 0, getWidth(), getHeight());
			g2.dispose();
		}

		@Override protected void paintBorder(Graphics g) {
			Graphics2D g2 = prepare(g);
			g2.setColor(feature ? FEATURE_EDGE : CARD_EDGE);
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
			g2.dispose();
		}
	}

	/** Frozen {@code .sectionhead}: 26px (feature 28px) minimum, 4px 6px padding, icon 17px, optional ⌄ toggle. */
	static final class Band extends JComponent implements HeightForWidth {
		private final UiContext ui;
		private final Art art;
		private final String title;
		private final BufferedImage icon;
		private final boolean feature;
		private final Consumer<Boolean> toggle;
		boolean expanded = true;

		Band(UiContext ui, Art art, String title, BufferedImage icon, boolean feature, Consumer<Boolean> toggle) {
			this.ui = ui;
			this.art = art;
			this.title = title;
			this.icon = icon;
			this.feature = feature;
			this.toggle = toggle;
			setOpaque(false);
			setName("section-" + title.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
			if (toggle != null) activator(this, () -> toggle.accept(!expanded));
		}

		private Font font() { return (feature ? Style.FEATURE : Style.HEAD).font(ui); }

		private int textX() { return 6 + (icon != null ? 17 + 5 : 0); }

		private int textWidth(int width) { return Math.max(1, width - textX() - 6 - (toggle != null ? 14 : 0)); }

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(font());
			int lines = TextWrap.wrap(title, fm, textWidth(width)).size();
			return Math.max(feature ? 28 : 26, 8 + lines * fm.getHeight()) + 1;
		}

		@Override public Dimension getPreferredSize() { return new Dimension(225, heightForWidth(225)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = prepare(g);
			int w = getWidth(), h = getHeight() - 1;
			if (feature && art.plaster != null) {
				int ph = Math.round(w * art.plaster.getHeight() / (float) art.plaster.getWidth());
				g2.drawImage(art.plaster, 0, (h - ph) / 2, w, ph, null);
				g2.setPaint(new GradientPaint(0, 0, new Color(0xff, 0xf0, 0xb8, 0x2c), 0, h, new Color(0x50, 0x33, 0x11, 0x30)));
			} else {
				tile(g2, art.wood, w, h, 130);
				g2.setPaint(new GradientPaint(0, 0, new Color(0xbc, 0x98, 0x6b, 0x33), 0, h, new Color(0x2a, 0x19, 0x0a, 0x55)));
			}
			g2.fillRect(0, 0, w, h);
			g2.setColor(feature ? new Color(0x97, 0x73, 0x45) : HEAD_RULE);
			g2.fillRect(0, h, w, 1);
			if (icon != null) contain(g2, icon, 6, (h - 17) / 2, 17, 17);
			g2.setFont(font());
			FontMetrics fm = g2.getFontMetrics();
			List<String> lines = TextWrap.wrap(title, fm, textWidth(w));
			int y = (h - lines.size() * fm.getHeight()) / 2 + fm.getAscent();
			for (String l : lines) {
				if (!feature) {
					g2.setColor(new Color(0x10, 0x0a, 0x04, 0xc0));
					g2.drawString(l, textX(), y + 1);
				}
				g2.setColor(feature ? FEATURE_HEAD : HEAD);
				g2.drawString(l, textX(), y);
				y += fm.getHeight();
			}
			if (toggle != null) {
				g2.setColor(new Color(0xd6bd8a));
				g2.drawString(expanded ? "⌄" : "›", w - 6 - fm.stringWidth("⌄"), (h + fm.getAscent()) / 2 - 2);
			}
			focusRing(g2, this);
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return toggle != null ? AccessibleRole.TOGGLE_BUTTON : AccessibleRole.HEADER; }
				@Override public String getAccessibleName() { return title; }
				@Override public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
					javax.accessibility.AccessibleStateSet s = super.getAccessibleStateSet();
					if (toggle != null) s.add(expanded ? AccessibleState.EXPANDED : AccessibleState.COLLAPSED);
					return s;
				}
			};
			return accessibleContext;
		}
	}

	// ------------------------------------------------------------------ rows

	/**
	 * Frozen {@code .row}: 25px sprite, bold title, muted subtitle, end value, chevron; 34px minimum, 4px vertical padding.
	 * A row either opens its own detail copy (frozen {@code details}) or runs an action (navigation). An unresolved sprite
	 * draws {@link R02Assets#fallbackLabel} initials and the row's accessible name always carries the full text.
	 */
	static final class Row extends JComponent implements HeightForWidth {
		private final UiContext ui;
		private BufferedImage icon;
		private String fallback, title, subtitle, end;
		private final String chevron;
		private final Label detail;
		private boolean open, last;

		Row(UiContext ui, BufferedImage icon, String fallback, String title, String subtitle, String end, String chevron,
			String detailText, Runnable action) {
			this.ui = ui;
			this.icon = icon;
			this.fallback = R02Assets.fallbackLabel(fallback);
			this.title = title;
			this.subtitle = subtitle;
			this.end = end;
			this.chevron = chevron;
			this.detail = detailText == null ? null : new Label(ui, detailText, Style.TINY, DETAIL).detailRule();
			setOpaque(false);
			setLayout(null);
			if (detail != null) {
				detail.setVisible(false);
				add(detail);
			}
			activator(this, () -> {
				if (detail != null) setOpen(!open);
				if (action != null) action.run();
			});
		}

		void setLast(boolean last) { this.last = last; repaint(); }

		boolean open() { return open; }

		void setOpen(boolean o) {
			if (detail == null) return;
			open = o;
			detail.setVisible(o);
			revalidate();
			repaint();
		}

		String subtitle() { return subtitle; }
		String end() { return end; }
		String title() { return title; }

		void set(String title, String subtitle, String end, BufferedImage icon, String fallback) {
			this.title = title;
			this.subtitle = subtitle;
			this.end = end;
			this.icon = icon;
			this.fallback = R02Assets.fallbackLabel(fallback);
			revalidate();
			repaint();
		}

		private int endWidth() {
			int w = end == null ? 0 : getFontMetrics(Style.TINY.font(ui)).stringWidth(end) + 2;
			if (chevron != null) w += 5 + getFontMetrics(Style.HEAD.font(ui)).stringWidth(chevron);
			return w;
		}

		private int copyWidth(int width) { return Math.max(1, width - 25 - 5 - (endWidth() > 0 ? 5 + endWidth() : 0)); }

		private int copyHeight(int width) {
			int cw = copyWidth(width);
			FontMetrics t = getFontMetrics(Style.ROW.font(ui)), m = getFontMetrics(Style.TINY.font(ui));
			int h = TextWrap.wrap(title, t, cw).size() * t.getHeight();
			if (subtitle != null && !subtitle.isEmpty()) h += TextWrap.wrap(subtitle, m, cw).size() * m.getHeight();
			return h;
		}

		private int summaryHeight(int width) { return Math.max(34, 8 + Math.max(25, copyHeight(width))); }

		@Override public int heightForWidth(int width) {
			int h = summaryHeight(width);
			if (open && detail != null) h += 3 + detail.heightForWidth(width) + 4;
			return h;
		}

		@Override public int minimumContentWidth() { return 25 + 5 + 40 + endWidth(); }

		@Override public Dimension getPreferredSize() { return new Dimension(225, heightForWidth(225)); }

		@Override public void doLayout() {
			if (detail != null && detail.isVisible()) {
				int top = summaryHeight(getWidth()) + 3;
				detail.setBounds(0, top, getWidth(), detail.heightForWidth(getWidth()));
			}
		}

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = prepare(g);
			int w = getWidth(), sh = summaryHeight(w);
			int iy = (sh - 25) / 2;
			if (icon != null) contain(g2, icon, 0, iy, 25, 25);
			else {
				g2.setColor(new Color(0x7c, 0x69, 0x43, 0x80));
				g2.drawRect(0, iy, 24, 24);
				g2.setFont(Style.TINY.font(ui));
				String ab = initials(fallback);
				FontMetrics fm = g2.getFontMetrics();
				g2.setColor(MUTED);
				g2.drawString(ab, (25 - fm.stringWidth(ab)) / 2, iy + (25 + fm.getAscent()) / 2 - 2);
			}
			int cx = 30, cw = copyWidth(w);
			FontMetrics t = g2.getFontMetrics(Style.ROW.font(ui)), m = g2.getFontMetrics(Style.TINY.font(ui));
			int y = (sh - copyHeight(w)) / 2;
			g2.setFont(Style.ROW.font(ui));
			g2.setColor(ROW_TITLE);
			for (String l : TextWrap.wrap(title, t, cw)) { g2.drawString(l, cx, y + t.getAscent()); y += t.getHeight(); }
			if (subtitle != null && !subtitle.isEmpty()) {
				g2.setFont(Style.TINY.font(ui));
				g2.setColor(MUTED);
				for (String l : TextWrap.wrap(subtitle, m, cw)) { g2.drawString(l, cx, y + m.getAscent()); y += m.getHeight(); }
			}
			int x = w;
			if (chevron != null) {
				g2.setFont(Style.HEAD.font(ui));
				FontMetrics c = g2.getFontMetrics();
				x -= c.stringWidth(chevron);
				g2.setColor(CHEVRON);
				g2.drawString(chevron, x, (sh + c.getAscent()) / 2 - 2);
				x -= 5;
			}
			if (end != null) {
				g2.setFont(Style.TINY.font(ui));
				g2.setColor(END);
				x -= m.stringWidth(end);
				g2.drawString(end, x, (sh + m.getAscent()) / 2 - 1);
			}
			if (!last) {
				g2.setColor(ROW_RULE);
				g2.fillRect(0, getHeight() - 1, w, 1);
			}
			focusRing(g2, this);
			g2.dispose();
		}

		private static String initials(String name) {
			StringBuilder b = new StringBuilder();
			for (String w : name.split("\\s+")) if (!w.isEmpty() && b.length() < 2) b.append(Character.toUpperCase(w.charAt(0)));
			return b.toString();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return detail != null ? AccessibleRole.TOGGLE_BUTTON : AccessibleRole.PUSH_BUTTON; }
				@Override public String getAccessibleName() {
					StringBuilder b = new StringBuilder(title);
					if (subtitle != null && !subtitle.isEmpty()) b.append(", ").append(subtitle);
					if (end != null) b.append(", ").append(end);
					if (icon == null) b.append(" (image unavailable: ").append(fallback).append(')');
					return b.toString();
				}
				@Override public String getAccessibleDescription() { return detail != null && open ? detail.text() : null; }
			};
			return accessibleContext;
		}
	}

	/** Frozen {@code .rows}: 0 6px padding, last row without rule. */
	static StackPanel rows(JComponent... rows) {
		StackPanel p = new StackPanel(0);
		p.setOpaque(false);
		p.setBorder(new EmptyBorder(0, 6, 0, 6));
		for (JComponent r : rows) p.add(r);
		markLast(p);
		return p;
	}

	static void markLast(JPanel p) {
		Row last = null;
		for (java.awt.Component c : p.getComponents()) if (c instanceof Row && c.isVisible()) { ((Row) c).setLast(false); last = (Row) c; }
		if (last != null) last.setLast(true);
	}

	// ------------------------------------------------------------------ actions

	/** Frozen {@code .primary}: full width green action, 13px bold. */
	static final class Primary extends JButton implements HeightForWidth {
		private final UiContext ui;

		Primary(UiContext ui, String text) {
			super(text);
			this.ui = ui;
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setFont(Style.BUTTON.font(ui));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(Style.BUTTON.font(ui));
			return Math.max(30, TextWrap.wrap(getText(), fm, Math.max(1, width - 8)).size() * fm.getHeight() + 12);
		}

		@Override public Dimension getPreferredSize() { return new Dimension(200, heightForWidth(200)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = prepare(g);
			int w = getWidth(), h = getHeight();
			g2.setPaint(new GradientPaint(0, 0, getModel().isRollover() ? PRIMARY_TOP.brighter() : PRIMARY_TOP, 0, h, PRIMARY_BOTTOM));
			g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setColor(new Color(0xd3, 0xdf, 0x9b, 0x66));
			g2.drawLine(3, 1, w - 4, 1);
			g2.setColor(PRIMARY_EDGE);
			g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setFont(Style.BUTTON.font(ui));
			FontMetrics fm = g2.getFontMetrics();
			List<String> lines = TextWrap.wrap(getText(), fm, Math.max(1, w - 8));
			int y = (h - lines.size() * fm.getHeight()) / 2 + fm.getAscent();
			for (String l : lines) {
				int x = (w - fm.stringWidth(l)) / 2;
				g2.setColor(new Color(0x14, 0x16, 0x0b, 0xc0));
				g2.drawString(l, x, y + 1);
				g2.setColor(PRIMARY_TEXT);
				g2.drawString(l, x, y);
				y += fm.getHeight();
			}
			if (isFocusOwner()) {
				g2.setColor(FOCUS);
				g2.setStroke(new BasicStroke(2f));
				g2.drawRoundRect(1, 1, w - 3, h - 3, 6, 6);
			}
			g2.dispose();
		}
	}

	/** Frozen {@code .textaction} / {@code .reason summary}: borderless text button, 11px. */
	static final class TextAction extends JButton implements HeightForWidth {
		private final UiContext ui;
		private final Color color;

		TextAction(UiContext ui, String text, Color color) {
			super(text);
			this.ui = ui;
			this.color = color;
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setHorizontalAlignment(LEFT);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(Style.SMALL.font(ui));
			return TextWrap.wrap(getText(), fm, Math.max(1, width)).size() * fm.getHeight() + 6;
		}

		@Override public Dimension getPreferredSize() { return new Dimension(200, heightForWidth(200)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = prepare(g);
			g2.setFont(Style.SMALL.font(ui));
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(color);
			int y = 3 + fm.getAscent();
			for (String l : TextWrap.wrap(getText(), fm, Math.max(1, getWidth()))) { g2.drawString(l, 0, y); y += fm.getHeight(); }
			if (isFocusOwner()) {
				g2.setColor(FOCUS);
				g2.drawRect(0, 0, Math.min(getWidth() - 1, fm.stringWidth(getText()) + 2), getHeight() - 1);
			}
			g2.dispose();
		}
	}

	/** Frozen {@code .meter}: 8px, gold edge, green fill; accessible as a progress bar with value text. */
	static final class Meter extends JComponent implements HeightForWidth {
		private double fraction;
		private String valueText = "";
		private Integer value;
		private int max;

		Meter() { setOpaque(false); }

		void set(double fraction, Integer value, int max, String valueText) {
			this.fraction = Math.max(0, Math.min(1, fraction));
			this.value = value;
			this.max = max;
			this.valueText = valueText;
			repaint();
		}

		double fraction() { return fraction; }

		@Override public int heightForWidth(int width) { return 4 + 10 + 2; }

		@Override public Dimension getPreferredSize() { return new Dimension(100, heightForWidth(100)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = prepare(g);
			int w = getWidth();
			g2.setColor(METER_TROUGH);
			g2.fillRect(0, 4, w, 10);
			int fill = (int) Math.round((w - 2) * fraction);
			if (fill > 0) {
				g2.setPaint(new GradientPaint(0, 5, FILL_TOP, 0, 13, FILL_BOTTOM));
				g2.fillRect(1, 5, fill, 8);
			}
			g2.setColor(METER_EDGE);
			g2.drawRect(0, 4, w - 1, 9);
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.PROGRESS_BAR; }
				@Override public String getAccessibleName() { return valueText; }
				@Override public javax.accessibility.AccessibleValue getAccessibleValue() {
					return new javax.accessibility.AccessibleValue() {
						public Number getCurrentAccessibleValue() { return value == null ? null : Math.min(max, value); }
						public boolean setCurrentAccessibleValue(Number n) { return false; }
						public Number getMinimumAccessibleValue() { return 0; }
						public Number getMaximumAccessibleValue() { return max; }
					};
				}
			};
			return accessibleContext;
		}
	}

	/** Frozen textures every Lane A page paints with. */
	static final class Art {
		final BufferedImage stone, wood, plaster;

		Art(BufferedImage stone, BufferedImage wood, BufferedImage plaster) {
			this.stone = stone;
			this.wood = wood;
			this.plaster = plaster;
		}
	}
}
