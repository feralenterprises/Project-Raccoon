package com.feralenterprises.raccoon.playtest.r02.panel.xpstash;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.panel.xpstash.XpData.Recipe;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.SearchField;
import com.feralenterprises.raccoon.ui.component.SegmentedSelector;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.component.Tone;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.lifecycle.Disposable;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * R02 XP Stash (frozen {@code #xpstash}): training skill, Calculator / Resources / Options, the XP projection,
 * modifiers and per-resource method choice. Functional build on shared components. All options are page state, reset
 * with the page on a scope change and kept across same-scope revisions. Account XP is shown only from exact-XP evidence;
 * a verified level alone gives the current level but no projection.
 */
final class XpStashPanel extends StackPanel implements Disposable {
	private static final String[][] MODIFIERS_PRAYER = {{"", "None"}, {"demonic", "Demonic Offering"}, {"sinister", "Sinister Offering"},
		{"gilded", "Lit Gilded Altar"}, {"ecto", "Ectofuntus"}, {"wild", "Wilderness altar"}};
	private static final String[][] MODIFIERS_SAILING = {{"", "None"}, {"lure", "Horizon’s Lure"}};
	private static final Map<String, String> GEAR = Map.of("Prayer", "Zealot’s robes", "Firemaking", "Pyromancer outfit",
		"Construction", "Carpenter’s outfit", "Farming", "Farmer’s outfit");
	private static final String[] TABS = {"Calculator", "Resources", "Options"};

	private final RouteContext context;
	private final UiContext uiContext;
	private final XpData data;
	private final XpEngine engine;
	private final XpEngine.Options o = new XpEngine.Options();
	private final Runnable textScaleChanged = this::render;
	private final RDropdown<String> skill;
	private final SegmentedSelector tabs;
	private final SearchField search;
	private final Set<String> openSections = new HashSet<>();
	private int tab;
	private String focusAfter;

	XpStashPanel(RouteContext context, XpData data) {
		super(7);
		this.context = context;
		this.uiContext = context.ui();
		this.data = data;
		this.engine = new XpEngine(data);
		setName("xpstash");
		setOpaque(false);
		setBorder(new EmptyBorder(0, 0, 6, 0));
		skill = new RDropdown<>(uiContext, "Training skill", data.skills, s -> s, s -> {
			o.skill = s;
			o.search = "";
			o.boost = 0;
			clearSearch();
			render();
		});
		skill.setName("xp-skill");
		skill.restore(o.skill);
		tabs = new SegmentedSelector(uiContext, TABS);
		tabs.setName("xp-tabs");
		tabs.addSelectionListener(i -> { tab = i; render(); });
		search = new SearchField(uiContext, "Find a resource", q -> {
			if (q.equals(o.search)) return;
			o.search = q;
			focusAfter = "xp-search";
			render();
		});
		search.setName("xp-search");
		render();
		context.onSnapshot(s -> render());
		uiContext.addChangeListener(textScaleChanged);
	}

	@Override public void dispose() { uiContext.removeChangeListener(textScaleChanged); }

	private void clearSearch() { if (!search.getText().isEmpty()) search.setText(""); }

	XpEngine.Options options() { return o; }

	XpEngine.Result result() { return engine.calculate(context.snapshot(), o); }

	private void render() {
		removeAll();
		FixtureSnapshot s = context.snapshot();
		XpEngine.Result r = engine.calculate(s, o);
		Card head = new Card(uiContext, "Training skill");
		head.setName("xp-heading");
		head.addAll(skill, tabs);
		add(head);
		if (tab == 2) options(s);
		else {
			if (tab == 0) {
				add(projection(r));
				add(modifiers());
			}
			add(resources(r));
		}
		revalidate();
		repaint();
		if (focusAfter != null) {
			String f = focusAfter;
			focusAfter = null;
			SwingUtilities.invokeLater(() -> {
				JComponent c = find(this, f);
				if (c != null) c.requestFocusInWindow();
			});
		}
	}

	// ------------------------------------------------------------------ sections

	private JComponent projection(XpEngine.Result r) {
		Card c = new Card(uiContext, "XP projection");
		c.setName("xp-projection");
		Integer projected = r.projectedLevel();
		c.addAll(line("Account XP", XpEngine.money(r.currentXp)), line("Current level", XpEngine.money(r.currentLevel)),
			line("Banked XP", XpEngine.money(r.total)), line("Projected level", XpEngine.money(projected)),
			line("XP to next level", projected == null ? "Unavailable" : projected == 99 ? "At level cap"
				: XpEngine.money(XpEngine.threshold(projected + 1) - r.projectedXp)));
		String w = XpEngine.warning(r, o);
		if (w != null) c.addAll(text("xp-warning", w));
		if (o.respect && r.currentLevel == null) c.addAll(text("xp-level-note", "Skill-level evidence unavailable. Methods are listed without a level filter."));
		if (r.cyclic) c.addAll(text("xp-cyclic", "Cyclic outputs excluded from cascading."));
		return c;
	}

	private JComponent modifiers() {
		StackPanel body = new StackPanel(4);
		body.setOpaque(false);
		String[][] mods = o.skill.equals("Prayer") ? MODIFIERS_PRAYER : o.skill.equals("Sailing") ? MODIFIERS_SAILING : null;
		if (mods != null) {
			List<String[]> list = Arrays.asList(mods);
			RDropdown<String[]> d = new RDropdown<>(uiContext, "Method modifier", list, m -> m[1], m -> {
				o.modifiers.put(o.skill, m[0]);
				focusAfter = "xp-modifier";
				render();
			});
			d.setName("xp-modifier");
			String current = o.modifiers.getOrDefault(o.skill, "");
			list.stream().filter(m -> m[0].equals(current)).findFirst().ifPresent(d::restore);
			body.add(text("xp-modifier-label", "Method modifier"));
			body.add(d);
		}
		String gear = GEAR.get(o.skill);
		if (gear != null) {
			body.add(text("xp-gear", gear));
			Set<Integer> pieces = o.pieces.computeIfAbsent(o.skill, k -> new HashSet<>());
			String[] slots = {"Head", "Body", "Legs", "Feet"};
			for (int i = 0; i < 4; i++) {
				final int index = i;
				body.add(check("piece:" + i, slots[i], pieces.contains(i), on -> {
					if (on) pieces.add(index);
					else pieces.remove(index);
				}));
			}
		}
		if (mods == null && gear == null) body.add(text("xp-no-modifier", "No modifier in the supplied calculator for this skill."));
		Expandable e = new Expandable(uiContext, "Modifiers", body);
		e.setName("xp-modifiers");
		e.setExpanded(openSections.contains("modifiers"));
		e.addExpansionListener(x -> { if (x) openSections.add("modifiers"); else openSections.remove("modifiers"); });
		return e;
	}

	private JComponent resources(XpEngine.Result r) {
		Card c = new Card(uiContext, o.catalog ? "Recipe catalog" : "Skill resources");
		c.setName("xp-resources");
		c.addAll(search, check("catalog", "Browse all methods for this skill", o.catalog, on -> o.catalog = on));
		List<XpEngine.Entry> list = engine.visible(r, o);
		if (list.isEmpty()) c.addAll(text("xp-empty", !o.search.isEmpty() ? "No matching resources."
			: r.known ? "No matching observed resources in the selected sources." : "Resource quantities unavailable."));
		for (XpEngine.Entry e : list) c.addAll(resource(e, r.known));
		return c;
	}

	private JComponent resource(XpEngine.Entry e, boolean known) {
		StackPanel p = new StackPanel(3);
		p.setOpaque(false);
		p.setName("xp-entry-" + e.key);
		p.setBorder(new EmptyBorder(5, 0, 5, 0));
		String qty = known ? XpEngine.money(e.original) + (e.piped > 0 ? " + " + XpEngine.money(e.piped) + " linked" : "") : "Quantity unavailable";
		TextBlock name = new TextBlock(uiContext, data.displayId(e.items.ids[0]), FontRole.BODY, Tone.ACCENT);
		name.setName("xp-entry-name-" + e.key);
		p.add(name);
		p.add(text("xp-entry-qty-" + e.key, qty + (e.items.byDose ? " · doses" : "")));
		p.add(check("ignore:" + e.key, "Ignore", e.ignored, on -> { if (on) o.ignored.add(e.key); else o.ignored.remove(e.key); }));
		if (!e.eligible.isEmpty()) {
			RDropdown<Recipe> method = new RDropdown<>(uiContext, "Method for " + data.displayId(e.items.ids[0]), e.eligible,
				m -> m.name + " · L" + m.level + " · " + XpEngine.money(m.xp) + " XP", m -> {
					o.choices.put(e.key, m.key);
					focusAfter = "xp-method-" + e.key;
					render();
				});
			method.setName("xp-method-" + e.key);
			if (e.chosen != null) method.restore(e.chosen);
			p.add(method);
		}
		int locked = e.all.size() - e.eligible.size();
		if (locked > 0) p.add(text("xp-locked-" + e.key, locked + " method" + (locked == 1 ? "" : "s") + " above the current level"));
		if (!o.catalog && !e.ignored) {
			if (e.chosen == null) p.add(text("xp-gate-" + e.key, "Current level gate excludes these methods."));
			else p.add(line(e.chosen.rng ? "Expected XP · RNG" : "Estimated XP", known ? XpEngine.money(e.xp) : "Unavailable", "xp-estimate-" + e.key));
		}
		if (o.limit && known && !e.ignored) p.add(line("Actions under current limits", XpEngine.money(e.runs), "xp-runs-" + e.key));
		boolean missing = e.needs.stream().anyMatch(n -> n.available != null && n.wanted > n.available);
		if (!o.secondaries && !e.ignored && missing) p.add(text("xp-missing-" + e.key, "Some required ingredients are unavailable."));
		if (!e.needs.isEmpty() && o.secondaries) {
			StackPanel needs = new StackPanel(2);
			needs.setOpaque(false);
			for (XpEngine.Need n : e.needs) needs.add(line(n.label, XpEngine.money(n.available) + " / " + XpEngine.money(n.wanted), "xp-need-" + e.key + "-" + n.label));
			Expandable x = new Expandable(uiContext, "Secondaries", needs);
			x.setName("xp-secondaries-" + e.key);
			x.setExpanded(openSections.contains("sec:" + e.key));
			x.addExpansionListener(v -> { if (v) openSections.add("sec:" + e.key); else openSections.remove("sec:" + e.key); });
			p.add(x);
		}
		return p;
	}

	private void options(FixtureSnapshot s) {
		Card src = new Card(uiContext, "Resource sources");
		src.setName("xp-sources");
		String[][] labels = {{"bank", "Bank"}, {"vault", "Seed vault"}, {"inventory", "Inventory"}, {"bag", "Looting bag"}};
		for (String[] l : labels)
			src.addAll(check("source:" + l[0], l[1] + " · " + XpEngine.sourceStatus(s, l[0]), Boolean.TRUE.equals(o.sources.get(l[0])), on -> o.sources.put(l[0], on)));
		add(src);
		Card calc = new Card(uiContext, "Calculation");
		calc.setName("xp-calculation");
		calc.addAll(check("cascade", "Include linked output items", o.cascade, on -> o.cascade = on),
			check("respect", "Respect current skill level", o.respect, on -> o.respect = on));
		List<Integer> boosts = new ArrayList<>();
		for (int i = -20; i <= 20; i++) boosts.add(i);
		RDropdown<Integer> boost = new RDropdown<>(uiContext, "Preview boost", boosts, b -> (b > 0 ? "+" : "") + b, b -> {
			o.boost = Math.max(-20, Math.min(20, b));
			focusAfter = "xp-boost";
			render();
		});
		boost.setName("xp-boost");
		boost.restore(o.boost);
		boost.setEnabled(o.respect);
		calc.addAll(text("xp-boost-label", "Preview boost"), boost,
			check("secondaries", "Show required secondaries", o.secondaries, on -> o.secondaries = on),
			check("limit", "Limit by observed secondaries", o.limit, on -> o.limit = on),
			text("xp-limit-note", "Limits require complete evidence for the selected sources. Recipes share available secondaries in catalog order."));
		add(calc);
	}

	// ------------------------------------------------------------------ small components

	private TextBlock text(String name, String t) {
		TextBlock b = new TextBlock(uiContext, t, FontRole.SMALL, Tone.TEXT);
		b.setName(name);
		return b;
	}

	private JComponent line(String label, String value) { return line(label, value, "xp-line-" + label.toLowerCase().replaceAll("[^a-z0-9]+", "-")); }

	private JComponent line(String label, String value, String name) {
		Line l = new Line(label, value);
		l.setName(name);
		return l;
	}

	private JComponent check(String key, String label, boolean checked, Consumer<Boolean> apply) {
		Check c = new Check(label, checked, on -> {
			apply.accept(on);
			focusAfter = "xp-check-" + key;
			render();
		});
		c.setName("xp-check-" + key);
		return c;
	}

	static JComponent find(java.awt.Component c, String name) {
		if (name.equals(c.getName()) && c instanceof JComponent) return (JComponent) c;
		if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
			JComponent j = find(k, name);
			if (j != null) return j;
		}
		return null;
	}

	/** Label left, bold value right; wraps the label. */
	private final class Line extends JComponent implements HeightForWidth {
		private final String label, value;

		Line(String label, String value) {
			this.label = label;
			this.value = value;
		}

		private int valueW() { return getFontMetrics(uiContext.font(FontRole.VALUE)).stringWidth(value); }

		@Override public int heightForWidth(int width) {
			FontMetrics l = getFontMetrics(uiContext.font(FontRole.SMALL)), v = getFontMetrics(uiContext.font(FontRole.VALUE));
			return 4 + Math.max(TextWrap.wrap(label, l, Math.max(1, width - valueW() - 8)).size() * l.getHeight(), v.getHeight());
		}

		@Override public int minimumContentWidth() { return valueW() + 40; }

		@Override public Dimension getPreferredSize() { return new Dimension(200, heightForWidth(200)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = Paints.prepare(g);
			FontMetrics l = g2.getFontMetrics(uiContext.font(FontRole.SMALL)), v = g2.getFontMetrics(uiContext.font(FontRole.VALUE));
			g2.setFont(uiContext.font(FontRole.SMALL));
			g2.setColor(uiContext.palette().text);
			int y = 2;
			for (String s : TextWrap.wrap(label, l, Math.max(1, getWidth() - valueW() - 8))) { g2.drawString(s, 0, y + l.getAscent()); y += l.getHeight(); }
			g2.setFont(uiContext.font(FontRole.VALUE));
			g2.setColor(uiContext.palette().accent);
			g2.drawString(value, getWidth() - valueW(), 2 + v.getAscent());
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

	/** A themed checkbox: click, Enter or Space toggles; exposes CHECK_BOX with CHECKED state. */
	private final class Check extends JComponent implements HeightForWidth {
		private final String label;
		private boolean checked;

		Check(String label, boolean checked, Consumer<Boolean> onToggle) {
			this.label = label;
			this.checked = checked;
			setFocusable(true);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			Runnable toggle = () -> { this.checked = !this.checked; onToggle.accept(this.checked); };
			addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) { if (isEnabled()) toggle.run(); }
			});
			addKeyListener(new KeyAdapter() {
				@Override public void keyPressed(KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) { toggle.run(); e.consume(); }
				}
			});
			addFocusListener(new FocusAdapter() {
				@Override public void focusGained(FocusEvent e) { repaint(); }
				@Override public void focusLost(FocusEvent e) { repaint(); }
			});
		}

		boolean checked() { return checked; }

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(uiContext.font(FontRole.SMALL));
			return Math.max(18, 4 + TextWrap.wrap(label, fm, Math.max(1, width - 20)).size() * fm.getHeight());
		}

		@Override public Dimension getPreferredSize() { return new Dimension(200, heightForWidth(200)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = Paints.prepare(g);
			FontMetrics fm = g2.getFontMetrics(uiContext.font(FontRole.SMALL));
			int box = 12, by = 2 + (fm.getHeight() - box) / 2;
			g2.setColor(new Color(0x1d1a15));
			g2.fillRect(1, by, box, box);
			g2.setColor(uiContext.palette().accent);
			g2.drawRect(1, by, box, box);
			if (checked) {
				g2.setStroke(new BasicStroke(2f));
				g2.drawLine(3, by + 6, 6, by + 9);
				g2.drawLine(6, by + 9, 11, by + 3);
			}
			g2.setFont(uiContext.font(FontRole.SMALL));
			g2.setColor(uiContext.palette().text);
			int y = 2;
			for (String l : TextWrap.wrap(label, fm, Math.max(1, getWidth() - 20))) { g2.drawString(l, 19, y + fm.getAscent()); y += fm.getHeight(); }
			if (isFocusOwner()) {
				g2.setColor(new Color(0xd8bc73));
				g2.setStroke(new BasicStroke(1f));
				g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
			}
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.CHECK_BOX; }
				@Override public String getAccessibleName() { return label; }
				@Override public AccessibleStateSet getAccessibleStateSet() {
					AccessibleStateSet s = super.getAccessibleStateSet();
					if (checked) s.add(AccessibleState.CHECKED);
					return s;
				}
			};
			return accessibleContext;
		}
	}
}
