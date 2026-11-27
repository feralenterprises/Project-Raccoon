package com.feralenterprises.raccoon.playtest.r02.panel.activity;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.panel.activity.ActivityInputs.Environment;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Label;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Style;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.TextAction;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.layout.StackLayout;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.lifecycle.Disposable;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * R02 Current Activity (frozen {@code #activity}): environment hero, status line, Session progress card, notes, Data
 * details and the environment footer. Every colour and raster comes from the frozen environment preset for the
 * activity; values come only from the shared snapshot ({@link ActivityModel}). The page paints its own per-activity
 * environment because it changes with the observed activity. The Data details disclosure survives same-scope revisions.
 */
final class ActivityPanel extends JPanel implements HeightForWidth, Disposable {
	private final RouteContext context;
	private final UiContext ui;
	private final ActivityInputs in;
	private final Runnable textScaleChanged = this::rebuild;
	private ActivityModel model;
	private Environment env;
	private boolean detailsOpen;

	ActivityPanel(RouteContext context, ActivityInputs inputs) {
		super(new StackLayout(0));
		this.context = context;
		this.ui = context.ui();
		this.in = inputs;
		setName("activity");
		setOpaque(true);
		model = ActivityModel.of(context.snapshot(), context.registry(), in, context.clock().now());
		rebuild();
		context.onSnapshot(this::onSnapshot);
		ui.addChangeListener(textScaleChanged);
	}

	@Override public void dispose() { ui.removeChangeListener(textScaleChanged); }

	private void onSnapshot(FixtureSnapshot s) {
		model = ActivityModel.of(s, context.registry(), in, context.clock().now());
		rebuild();
	}

	ActivityModel model() { return model; }

	private void rebuild() {
		env = in.environment(model.environment);
		removeAll();
		add(new Hero());
		StackPanel main = new StackPanel(7);
		main.setOpaque(false);
		main.setBorder(new EmptyBorder(7, 7, 10, 7));
		main.add(new StatusRow());
		main.add(card());
		if (model.state == ActivityModel.State.IDLE && !model.metrics.isEmpty()) main.add(note(model.notes.get(0), "activity-idle-note"));
		if (model.coverage != null) main.add(note(model.coverage, "activity-coverage"));
		main.add(details());
		add(main);
		if (model.footer != null) add(new Footer());
		revalidate();
		repaint();
	}

	// ------------------------------------------------------------------ content

	private JComponent card() {
		ThemedCard card = new ThemedCard(model.heading);
		card.setName("activity-card");
		if (!model.metrics.isEmpty()) {
			card.body.add(new Metrics(model.metrics));
			StackPanel sub = new StackPanel(0);
			sub.setOpaque(false);
			sub.setBorder(new EmptyBorder(5, 8, 7, 8));
			for (ActivityModel.Line l : model.lines) sub.add(new LineRow(l));
			card.body.add(new Rule(sub));
		} else {
			StackPanel n = new StackPanel(6);
			n.setOpaque(false);
			n.setBorder(new EmptyBorder(8, 8, 8, 8));
			for (String t : model.notes) {
				Label p = new Label(ui, t, Style.NOTE, env.text);
				p.setName("activity-note");
				n.add(p);
			}
			for (ActivityModel.Line l : model.lines) n.add(new LineRow(l));
			card.body.add(n);
		}
		return card;
	}

	private JComponent note(String text, String name) {
		Label l = new Label(ui, text, Style.NOTE, env.text);
		l.setName(name);
		JPanel p = new StackPanel(0);
		p.setOpaque(false);
		p.setBorder(new EmptyBorder(8, 8, 8, 8));
		p.add(l);
		return p;
	}

	private JComponent details() {
		StackPanel p = new StackPanel(0);
		p.setOpaque(false);
		p.setBorder(new EmptyBorder(0, 3, 0, 3));
		TextAction summary = new TextAction(ui, "Data details ›", env.light);
		summary.setName("activity-details");
		Label a = new Label(ui, ActivityModel.DATA_DETAILS_1, Style.SMALL, env.text), b = new Label(ui, ActivityModel.DATA_DETAILS_2, Style.SMALL, env.text);
		a.setName("activity-details-text");
		a.setVisible(detailsOpen);
		b.setVisible(detailsOpen);
		summary.addActionListener(e -> {
			detailsOpen = !detailsOpen;
			a.setVisible(detailsOpen);
			b.setVisible(detailsOpen);
			revalidate();
			repaint();
		});
		p.add(summary);
		p.add(a);
		p.add(b);
		return p;
	}

	@Override public int heightForWidth(int width) { return ((StackLayout) getLayout()).heightForWidth(this, width); }

	@Override public Dimension getPreferredSize() {
		int w = getWidth() > 0 ? getWidth() : 225;
		return new Dimension(w, heightForWidth(w));
	}

	/** Frozen {@code #activity} background: ink (88% to 70%) over the surface texture at 180px. */
	@Override protected void paintComponent(Graphics g) {
		Graphics2D g2 = AdventureUi.prepare(g);
		int w = getWidth(), h = getHeight();
		g2.setColor(env.ink);
		g2.fillRect(0, 0, w, h);
		AdventureUi.tile(g2, env.surface, w, h, 180);
		g2.setPaint(new GradientPaint(0, h, alpha(env.ink, 0.88f), 0, 0, alpha(env.ink, 0.70f)));
		g2.fillRect(0, 0, w, h);
		g2.dispose();
	}

	static Color alpha(Color c, float a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * a)); }

	static Color mix(Color a, Color b, float wa) {
		return new Color(Math.round(a.getRed() * wa + b.getRed() * (1 - wa)), Math.round(a.getGreen() * wa + b.getGreen() * (1 - wa)),
			Math.round(a.getBlue() * wa + b.getBlue() * (1 - wa)));
	}

	private Font font(Style s) { return s.font(ui); }

	// ------------------------------------------------------------------ hero

	/** Frozen {@code .of-hero}: 100px scene, portrait at the left, kind/title/area copy. */
	private final class Hero extends JComponent implements HeightForWidth {
		private final BufferedImage portrait;

		Hero() {
			setName("activity-hero");
			R02Assets a = context.assets();
			portrait = model.portraitBoss != null ? a.portrait(model.portraitBoss) : model.portraitArt != null ? in.activityArt.get(model.portraitArt)
				: model.portraitItem != null ? a.item(model.portraitItem) : model.portraitSkill != null ? a.skillIcon(model.portraitSkill) : null;
		}

		private boolean hasPortrait() { return model.state == ActivityModel.State.ACTIVE && portrait != null; }

		private int copyX() { return hasPortrait() ? 91 : 11; }

		private Style titleStyle() { return model.title.length() > 18 || model.planned ? Style.HERO_TITLE_LONG : Style.HERO_TITLE; }

		private int copyHeight(int width) {
			int cw = Math.max(1, width - copyX() - 6);
			FontMetrics k = getFontMetrics(font(Style.TINY)), t = getFontMetrics(font(titleStyle())), s = getFontMetrics(font(Style.SMALL));
			return k.getHeight() + 5 + TextWrap.wrap(model.title, t, cw).size() * t.getHeight() + 4
				+ (model.subtitle == null ? 0 : TextWrap.wrap(model.subtitle, s, cw).size() * s.getHeight());
		}

		@Override public int heightForWidth(int width) { return Math.max(100, copyHeight(width) + 11 + 8); }

		@Override public Dimension getPreferredSize() { return new Dimension(225, heightForWidth(225)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight();
			g2.setColor(env.ink);
			g2.fillRect(0, 0, w, h);
			if (env.scene != null) cover(g2, env.scene, w, h);
			g2.setPaint(new GradientPaint(0, h, env.ink, 0, h * 0.62f, alpha(env.ink, 0)));
			g2.fillRect(0, 0, w, h);
			g2.setPaint(new GradientPaint(w * 0.24f, 0, alpha(env.ink, 0), w * 0.85f, 0, alpha(env.ink, 0.8f)));
			g2.fillRect(0, 0, w, h);
			g2.setColor(env.edge);
			g2.fillRect(0, h - 1, w, 1);
			if (hasPortrait()) {
				if (model.itemPortrait()) AdventureUi.contain(g2, portrait, 20, h - 24 - 48, 48, 48);
				else AdventureUi.contain(g2, portrait, 6, h - 5 - 88, 78, 88);
			}
			int x = copyX(), cw = Math.max(1, w - x - 6), y = h - 11 - copyHeight(w);
			y = text(g2, model.kind.toUpperCase(Locale.ROOT), font(Style.TINY), env.light, x, y, cw) + 5;
			y = text(g2, model.title, font(titleStyle()), env.light, x, y, cw) + 4;
			if (model.subtitle != null) text(g2, model.subtitle, font(Style.SMALL), env.text, x, y, cw);
			g2.dispose();
		}

		private int text(Graphics2D g2, String s, Font f, Color c, int x, int y, int width) {
			g2.setFont(f);
			FontMetrics fm = g2.getFontMetrics();
			for (String l : TextWrap.wrap(s, fm, width)) {
				g2.setColor(new Color(0, 0, 0, 200));
				g2.drawString(l, x, y + fm.getAscent() + 2);
				g2.setColor(c);
				g2.drawString(l, x, y + fm.getAscent());
				y += fm.getHeight();
			}
			return y;
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.HEADER; }
				@Override public String getAccessibleName() {
					return model.kind + ", " + model.title + (model.subtitle == null ? "" : ", " + model.subtitle)
						+ (model.state == ActivityModel.State.ACTIVE && portrait == null && model.portraitBoss != null
							? " (image unavailable: " + R02Assets.fallbackLabel(model.title) + ")" : "");
				}
			};
			return accessibleContext;
		}
	}

	static void cover(Graphics2D g, BufferedImage img, int w, int h) {
		double s = Math.max(w / (double) img.getWidth(), h / (double) img.getHeight());
		int dw = (int) Math.ceil(img.getWidth() * s), dh = (int) Math.ceil(img.getHeight() * s);
		g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
	}

	// ------------------------------------------------------------------ status line

	/** Frozen {@code .activity-status}: state dot and label, "Simulated" at the right. */
	private final class StatusRow extends JComponent implements HeightForWidth {
		StatusRow() { setName("activity-status"); }

		private Color dot() {
			if (model.planned) return new Color(0xa7a398);
			switch (model.state) {
				case IDLE: return new Color(0xd4b66d);
				case UNAVAILABLE: return new Color(0x9a9b95);
				default: return new Color(0xa9ca7c);
			}
		}

		private int rightWidth() { return getFontMetrics(font(Style.TINY)).stringWidth("Simulated"); }

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(font(Style.ROW));
			return 1 + 3 + TextWrap.wrap(model.status, fm, Math.max(1, width - 16 - rightWidth() - 9)).size() * fm.getHeight();
		}

		@Override public Dimension getPreferredSize() { return new Dimension(211, heightForWidth(211)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			g2.setFont(font(Style.ROW));
			FontMetrics fm = g2.getFontMetrics();
			int y = 1;
			g2.setColor(alpha(dot(), 0.33f));
			g2.fillOval(1, y + (fm.getHeight() - 11) / 2, 11, 11);
			g2.setColor(dot());
			g2.fillOval(3, y + (fm.getHeight() - 7) / 2, 7, 7);
			g2.setColor(env.light);
			for (String l : TextWrap.wrap(model.status, fm, Math.max(1, getWidth() - 16 - rightWidth() - 9))) {
				g2.drawString(l, 14, y + fm.getAscent());
				y += fm.getHeight();
			}
			g2.setFont(font(Style.TINY));
			FontMetrics s = g2.getFontMetrics();
			g2.setColor(env.text);
			g2.drawString("Simulated", getWidth() - 2 - rightWidth(), 1 + (fm.getHeight() + s.getAscent()) / 2 - 1);
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.LABEL; }
				@Override public String getAccessibleName() { return model.status + ", simulated"; }
			};
			return accessibleContext;
		}
	}

	// ------------------------------------------------------------------ card

	/** Frozen {@code .of-card} in the activity environment, with its themed {@code .sectionhead}. */
	private final class ThemedCard extends JPanel implements HeightForWidth {
		final StackPanel body = new StackPanel(0);
		private final String heading;

		ThemedCard(String heading) {
			super(new StackLayout(0));
			this.heading = heading;
			setOpaque(false);
			setBorder(new EmptyBorder(1, 1, 1, 1));
			body.setOpaque(false);
			add(new Head());
			add(body);
		}

		@Override public int heightForWidth(int width) { return ((StackLayout) getLayout()).heightForWidth(this, width); }

		@Override public Dimension getPreferredSize() { return new Dimension(211, heightForWidth(211)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight();
			g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, 6, 6));
			g2.setColor(env.ink);
			g2.fillRect(0, 0, w, h);
			AdventureUi.tile(g2, env.surface, w, h, 180);
			g2.setPaint(new GradientPaint(0, 0, alpha(env.face, 0.75f), w, h, alpha(env.ink, 0.94f)));
			g2.fillRect(0, 0, w, h);
			g2.dispose();
		}

		@Override protected void paintBorder(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			g2.setColor(mix(env.edge, new Color(0x1d1c16), 0.7f));
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
			g2.dispose();
		}

		private final class Head extends JComponent implements HeightForWidth {
			Head() { setName("activity-card-heading"); }

			@Override public int heightForWidth(int width) {
				FontMetrics fm = getFontMetrics(font(Style.HEAD));
				return Math.max(26, 8 + TextWrap.wrap(heading, fm, Math.max(1, width - 12)).size() * fm.getHeight()) + 1;
			}

			@Override public Dimension getPreferredSize() { return new Dimension(211, heightForWidth(211)); }

			@Override protected void paintComponent(Graphics g) {
				Graphics2D g2 = AdventureUi.prepare(g);
				int w = getWidth(), h = getHeight() - 1;
				AdventureUi.tile(g2, env.surface, w, h, 160);
				g2.setPaint(new java.awt.LinearGradientPaint(0, 0, w, h * 0.2f, new float[] {0f, 0.5f, 1f},
					new Color[] {alpha(env.face, 0.8f), alpha(env.tint, 0.5f), alpha(env.ink, 0.7f)}));
				g2.fillRect(0, 0, w, h);
				g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 0x15), 0, h, new Color(0, 0, 0, 0x33)));
				g2.fillRect(0, 0, w, h);
				g2.setColor(alpha(env.metal, 0.5f));
				g2.fillRect(0, 0, w, 1);
				g2.setColor(env.edge);
				g2.fillRect(0, h, w, 1);
				g2.setFont(font(Style.HEAD));
				FontMetrics fm = g2.getFontMetrics();
				List<String> lines = TextWrap.wrap(heading, fm, Math.max(1, w - 12));
				int y = (h - lines.size() * fm.getHeight()) / 2 + fm.getAscent();
				for (String l : lines) {
					g2.setColor(new Color(0x10, 0x0a, 0x04, 0xc0));
					g2.drawString(l, 6, y + 1);
					g2.setColor(env.light);
					g2.drawString(l, 6, y);
					y += fm.getHeight();
				}
				g2.dispose();
			}

			@Override public AccessibleContext getAccessibleContext() {
				if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
					@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.HEADER; }
					@Override public String getAccessibleName() { return heading; }
				};
				return accessibleContext;
			}
		}
	}

	/** Frozen {@code .activity-metrics}: two columns, value over label over optional quality; 8/8/6 padding, 9/6 gaps. */
	private final class Metrics extends JPanel implements HeightForWidth {
		Metrics(List<ActivityModel.Metric> metrics) {
			super(null);
			setOpaque(false);
			setName("activity-metrics");
			for (ActivityModel.Metric m : metrics) add(new MetricCell(m));
		}

		private int colWidth(int width) { return Math.max(1, (width - 16 - 6) / 2); }

		@Override public int heightForWidth(int width) {
			int cw = colWidth(width), h = 8 + 6;
			for (int i = 0; i < getComponentCount(); i += 2) {
				int a = ((MetricCell) getComponent(i)).heightForWidth(cw);
				int b = i + 1 < getComponentCount() ? ((MetricCell) getComponent(i + 1)).heightForWidth(cw) : 0;
				h += Math.max(a, b) + (i + 2 < getComponentCount() ? 9 : 0);
			}
			return h;
		}

		@Override public Dimension getPreferredSize() { return new Dimension(209, heightForWidth(209)); }

		@Override public void doLayout() {
			int cw = colWidth(getWidth()), y = 8;
			for (int i = 0; i < getComponentCount(); i += 2) {
				MetricCell a = (MetricCell) getComponent(i);
				MetricCell b = i + 1 < getComponentCount() ? (MetricCell) getComponent(i + 1) : null;
				int h = Math.max(a.heightForWidth(cw), b == null ? 0 : b.heightForWidth(cw));
				a.setBounds(8, y, cw, h);
				if (b != null) b.setBounds(8 + cw + 6, y, cw, h);
				y += h + 9;
			}
		}
	}

	private final class MetricCell extends JComponent implements HeightForWidth {
		private final ActivityModel.Metric m;

		MetricCell(ActivityModel.Metric m) {
			this.m = m;
			setName("metric-" + m.label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
		}

		private String value() { return ActivityModel.number(m.value); }

		private Font valueFont() { return font(m.value == null ? Style.METRIC_UNAVAILABLE : Style.METRIC); }

		@Override public int heightForWidth(int width) {
			FontMetrics v = getFontMetrics(valueFont()), l = getFontMetrics(font(Style.SMALL)), q = getFontMetrics(font(Style.TINY));
			return TextWrap.wrap(value(), v, width).size() * v.getHeight() + 3 + TextWrap.wrap(m.label, l, width).size() * l.getHeight()
				+ (m.quality == null ? 0 : TextWrap.wrap(m.quality, q, width).size() * q.getHeight());
		}

		@Override public int minimumContentWidth() { return 0; }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int y = 0, w = getWidth();
			y = draw(g2, value(), valueFont(), env.light, y, w) + 3;
			y = draw(g2, m.label, font(Style.SMALL), env.text, y, w);
			if (m.quality != null) draw(g2, m.quality, font(Style.TINY), env.text, y, w);
			g2.dispose();
		}

		private int draw(Graphics2D g2, String s, Font f, Color c, int y, int w) {
			g2.setFont(f);
			g2.setColor(c);
			FontMetrics fm = g2.getFontMetrics();
			for (String l : TextWrap.wrap(s, fm, w)) { g2.drawString(l, 0, y + fm.getAscent()); y += fm.getHeight(); }
			return y;
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.LABEL; }
				@Override public String getAccessibleName() { return m.label + ": " + value() + (m.quality == null ? "" : " (" + m.quality + ")"); }
			};
			return accessibleContext;
		}
	}

	/** Frozen {@code .activity-sub} top rule (edge at 38%). */
	private final class Rule extends JPanel implements HeightForWidth {
		Rule(JComponent inner) {
			super(new StackLayout(0));
			setOpaque(false);
			add(inner);
		}

		@Override public int heightForWidth(int width) { return ((StackLayout) getLayout()).heightForWidth(this, width); }

		@Override public Dimension getPreferredSize() { return new Dimension(209, heightForWidth(209)); }

		@Override protected void paintComponent(Graphics g) {
			g.setColor(alpha(env.edge, 0.38f));
			g.fillRect(0, 0, getWidth(), 1);
		}
	}

	/** Frozen {@code .activity-line}: label left (11px), bold value right (12px); "Unavailable" when null. */
	private final class LineRow extends JComponent implements HeightForWidth {
		private final String label, value;

		LineRow(ActivityModel.Line l) {
			label = l.label;
			value = l.value == null ? "Unavailable" : l.value;
			setName("line-" + label.toLowerCase(Locale.ROOT).replace(' ', '-'));
		}

		private int valueWidth() { return getFontMetrics(font(Style.VALUE)).stringWidth(value); }

		@Override public int heightForWidth(int width) {
			FontMetrics l = getFontMetrics(font(Style.SMALL)), v = getFontMetrics(font(Style.VALUE));
			return 8 + Math.max(TextWrap.wrap(label, l, Math.max(1, width - valueWidth() - 7)).size() * l.getHeight(), v.getHeight());
		}

		@Override public int minimumContentWidth() { return valueWidth() + 30; }

		@Override public Dimension getPreferredSize() { return new Dimension(193, heightForWidth(193)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			g2.setFont(font(Style.SMALL));
			FontMetrics l = g2.getFontMetrics();
			g2.setColor(env.text);
			int y = 4;
			for (String s : TextWrap.wrap(label, l, Math.max(1, getWidth() - valueWidth() - 7))) { g2.drawString(s, 0, y + l.getAscent()); y += l.getHeight(); }
			g2.setFont(font(Style.VALUE));
			g2.setColor(env.light);
			FontMetrics v = g2.getFontMetrics();
			g2.drawString(value, getWidth() - valueWidth(), 4 + v.getAscent());
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.LABEL; }
				@Override public String getAccessibleName() { return label + ": " + value; }
			};
			return accessibleContext;
		}
	}

	// ------------------------------------------------------------------ footer

	/** Frozen {@code .of-footer} for Activity: 65px, prop-a left, prop-b right, tinted glow, "Session simulator" tag. */
	private final class Footer extends JComponent implements HeightForWidth {
		Footer() { setName("activity-footer"); }

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.LABEL; }
				@Override public String getAccessibleName() { return model.footer; }
			};
			return accessibleContext;
		}

		@Override public int heightForWidth(int width) { return 65; }

		@Override public Dimension getPreferredSize() { return new Dimension(225, 65); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight();
			g2.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f), Math.max(1, w * 0.6f), new float[] {0f, 1f},
				new Color[] {alpha(env.tint, 0.45f), alpha(env.tint, 0f)}));
			g2.fillRect(0, 0, w, h);
			bottom(g2, env.propA, 0, 60, h, false);
			bottom(g2, env.propB, w - 70, 70, h, true);
			g2.setFont(font(Style.TINY));
			FontMetrics fm = g2.getFontMetrics();
			int tw = fm.stringWidth(model.footer), x = (w - tw) / 2, y = h - 7 - fm.getHeight();
			g2.setColor(alpha(env.ink, 0.8f));
			g2.fillRoundRect(x - 6, y - 3, tw + 12, fm.getHeight() + 6, 6, 6);
			g2.setColor(env.text);
			g2.drawString(model.footer, x, y + fm.getAscent());
			g2.dispose();
		}

		private void bottom(Graphics2D g2, BufferedImage img, int x, int width, int h, boolean right) {
			if (img == null || img.getWidth() <= 1) return;
			int ih = Math.round(width * img.getHeight() / (float) img.getWidth());
			g2.drawImage(img, x, h - ih, width, ih, null);
		}
	}
}
