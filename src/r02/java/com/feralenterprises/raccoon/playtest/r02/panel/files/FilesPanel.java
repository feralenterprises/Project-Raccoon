package com.feralenterprises.raccoon.playtest.r02.panel.files;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.panel.files.FilesModel.Item;
import com.feralenterprises.raccoon.playtest.r02.panel.files.FilesModel.Rec;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Label;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Section;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Style;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.SearchField;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.layout.StackLayout;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.lifecycle.Disposable;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteAware;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * R02 Feral Files (frozen {@code #feral-files}): Adventurer's journal with Journal and Sessions views, a record detail
 * (Loot, Supplies, Details) as a local Back level, and typed links: a {@code SESSION} payload opens that record,
 * combat records open Boss Chronicle by encounter, the current-login card opens Current Activity. View state (mode,
 * search, filter, open detail, earlier-history fold) lives in the page and is discarded with it on a scope change.
 */
final class FilesPanel extends JPanel implements HeightForWidth, LocalBack, RouteAware, Disposable {
	static final Color TITLE = new Color(0xf0ddae), SMALL = new Color(0xc6b79b), CHEVRON = new Color(0xc9a56a), LINE_LABEL = new Color(0xcfc0a4),
		LINE_VALUE = new Color(0xf0dfb9), NOTE = new Color(0xd4bc90), RULE = new Color(0x78, 0x63, 0x41, 0x45), OK = new Color(0xb8cc94),
		OFF = new Color(0xb8b1a5), FOLD = new Color(0xe1c998), EMPTY = new Color(0xd8c9ad), METRIC = new Color(0xf1dda9), METRIC_LABEL = new Color(0xcdbd9e);

	private final RouteContext context;
	private final UiContext uiContext;
	private final FilesModel.Presentation presentation;
	private final BufferedImage stone, wood;
	private final Runnable textScaleChanged = this::render;
	private final SearchField search;
	private final RDropdown<String> filter;
	private final StackPanel sessionList = new StackPanel(0);
	private FilesModel model;
	private boolean sessions, foldOpen;
	private String query = "", category = "All", detailId, detailTab = "loot";
	private boolean returnFold;
	private JComponent detailOpener;
	private Runnable onBackChanged = () -> { };

	FilesPanel(RouteContext context, FilesModel.Presentation presentation, BufferedImage stone, BufferedImage wood) {
		super(new StackLayout(7));
		this.context = context;
		this.uiContext = context.ui();
		this.presentation = presentation;
		this.stone = stone;
		this.wood = wood;
		setName("files");
		setOpaque(true);
		setBorder(new EmptyBorder(7, 7, 2, 7));
		search = new SearchField(uiContext, "Search sessions…", q -> { query = q; renderSessionList(); });
		search.setName("files-search");
		search.getAccessibleContext().setAccessibleName("Search sessions");
		filter = new RDropdown<>(uiContext, "Session category", Arrays.asList(FilesModel.FILTERS), s -> s, s -> { category = s; renderSessionList(); });
		filter.setName("files-filter");
		sessionList.setOpaque(false);
		model = FilesModel.of(context.snapshot(), context.registry(), presentation);
		render();
		context.onSnapshot(this::onSnapshot);
		uiContext.addChangeListener(textScaleChanged);
	}

	@Override public void dispose() { uiContext.removeChangeListener(textScaleChanged); }

	private void onSnapshot(FixtureSnapshot s) {
		model = FilesModel.of(s, context.registry(), presentation);
		if (detailId != null && model.record(detailId) == null) closeDetail(false);
		render();
	}

	FilesModel model() { return model; }

	// ------------------------------------------------------------------ navigation contracts

	@Override public void routeShown(RouteRequest request, boolean returning) {
		if (returning || request == null || request.param() != RouteRequest.Param.SESSION) return;
		if (model.record(request.paramId()) != null) openDetail(request.paramId(), null);
	}

	@Override public boolean hasLocalBack() { return detailId != null; }

	@Override public void localBack() { closeDetail(true); }

	@Override public void setLocalBackListener(Runnable onChange) { onBackChanged = onChange == null ? () -> { } : onChange; }

	private void openDetail(String id, JComponent opener) {
		returnFold = foldOpen;
		detailId = id;
		detailTab = "loot";
		detailOpener = opener;
		render();
		onBackChanged.run();
	}

	private void closeDetail(boolean restoreFocus) {
		if (detailId == null) return;
		detailId = null;
		foldOpen = returnFold;
		render();
		onBackChanged.run();
		JComponent opener = detailOpener;
		if (restoreFocus && opener != null) SwingUtilities.invokeLater(() -> {
			JComponent same = find(this, opener.getName());
			if (same != null) same.requestFocusInWindow();
		});
	}

	// ------------------------------------------------------------------ rendering

	private void render() {
		removeAll();
		Rec detail = model.record(detailId);
		if (detail != null) renderDetail(detail);
		else renderJournal();
		Label footer = new Label(uiContext, "Saved records · Offline preview", Style.TINY, new Color(0xb9aa90));
		footer.setName("files-footer");
		add(centered(footer, 8));
		revalidate();
		repaint();
	}

	private void renderJournal() {
		BufferedImage lectern = context.assets().art("PR-075");
		add(hero(lectern, "Adventurer’s journal", "Remember the trip, not every click.", "files-hero"));
		add(status());
		add(tabs(new String[] {"Journal", "Sessions"}, sessions ? 1 : 0, i -> {
			sessions = i == 1;
			query = "";
			category = "All";
			search.setText("");
			filter.restore("All");
			render();
		}, "files-mode"));
		if (model.records == null) {
			boolean signedIn = model.account != null;
			add(card(signedIn ? "Archive not verified" : "Log in to view your journal", empty(signedIn
				? "No complete archive observation is available. History and counts are unknown." : "No active account. Saved records are hidden.")));
			return;
		}
		if (!sessions) {
			Rec spoil = model.latestSpoil();
			add(card("Latest spoils", spoil == null ? empty("No notable loot in the available records.")
				: new RecordRow(itemImage(spoil.notable.id), spoil.notable.name, "×" + FilesModel.number(spoil.notable.quantity) + " · " + spoil.title,
					spoil.date + " · View loot", true, spoil.id, "files-spoil")));
			if (model.records.isEmpty()) add(card("Recent sessions", empty("No completed sessions in this verified archive.")));
			else {
				Section recent = card("Recent sessions");
				recent.setName("files-recent");
				for (Rec r : model.records.subList(0, Math.min(4, model.records.size()))) recent.body.add(row(r));
				recent.body.add(action("View all sessions ›", "files-view-all", () -> {
					sessions = true;
					render();
				}));
				add(recent);
			}
			add(history());
			return;
		}
		Section current = card("Current login");
		current.setName("files-current");
		if (model.current == null) current.body.add(empty("No account-bound current-login observation in this fixture."));
		else {
			current.body.add(line("Activity", model.current.activity));
			current.body.add(line("Observed XP", FilesModel.number(model.current.xp)));
			current.body.add(line(model.current.countLabel, FilesModel.number(model.current.count)));
			current.body.add(line("Active time", model.current.activeMinutes == null ? "Unavailable" : model.current.activeMinutes + " min"));
			current.body.add(note("Current Activity observation or labeled replay"));
			current.body.add(action("Open Current Activity ›", "files-open-activity", () -> context.navigate(RouteId.ACTIVITY)));
		}
		add(current);
		add(new FilterRow());
		add(sessionList);
		renderSessionList();
		if (query.trim().isEmpty() && category.equals("All")) add(history());
	}

	private void renderSessionList() {
		sessionList.removeAll();
		if (model.records == null) return;
		boolean searching = !query.trim().isEmpty() || !category.equals("All");
		List<Rec> found = model.matching(query, category);
		List<Rec> shown = searching ? found : found.subList(0, Math.min(10, found.size()));
		Section list = card(searching ? "Matching sessions" : "Recent sessions");
		list.setName("files-session-list");
		list.body.add(note(searching ? found.size() + " matching · All archived records" : shown.size() + " shown · " + model.records.size() + " archived"));
		if (shown.isEmpty()) list.body.add(empty(model.records.isEmpty() ? "No completed sessions in this verified archive." : "No matching archived records."));
		for (Rec r : shown) list.body.add(row(r));
		sessionList.add(list);
		sessionList.revalidate();
		sessionList.repaint();
		revalidate();
	}

	private JComponent history() {
		Section fold = card(null);
		fold.setName("files-earlier");
		List<Rec> older = model.records.size() > 10 ? model.records.subList(10, model.records.size()) : List.of();
		StackPanel body = new StackPanel(0);
		body.setOpaque(false);
		if (older.isEmpty()) body.add(note("No earlier records in this fixture."));
		for (Rec r : older) body.add(row(r));
		body.add(note("Completed login records only. Unobserved sessions are not reconstructed."));
		body.setVisible(foldOpen);
		Fold summary = new Fold("Earlier observations & login history", open -> { foldOpen = open; body.setVisible(open); revalidate(); repaint(); });
		summary.open = foldOpen;
		fold.body.add(summary);
		fold.body.add(body);
		return fold;
	}

	private void renderDetail(Rec r) {
		add(status());
		add(hero(icon(r.icon), r.title, r.date + " · Completed record", "files-detail-hero"));
		String[] ids = {"loot", "supplies", "details"};
		add(tabs(new String[] {"Loot", "Supplies", "Details"}, Arrays.asList(ids).indexOf(detailTab), i -> { detailTab = ids[i]; render(); }, "files-detail-tab"));
		Section summary = card(r.combat() ? "Trip summary" : "Activity summary");
		summary.setName("files-summary");
		summary.body.add(new Metrics(r.count == null ? "—" : FilesModel.number(r.count), r.countLabel, r.xp == null ? "—" : FilesModel.number(r.xp), "XP observed"));
		summary.body.add(line("Active time", r.activeMinutes == null ? "Unavailable" : r.activeMinutes + " min"));
		summary.body.add(line("Elapsed time", "Unavailable"));
		if (r.combat()) summary.body.add(line("Deaths", "Unavailable"));
		add(summary);
		if (detailTab.equals("loot")) {
			Section loot = card("Observed loot");
			loot.setName("files-loot");
			loot.body.add(r.loot.isEmpty() ? empty("Not observed. Quantities are unknown.") : new Items(r.loot));
			loot.body.add(line("Value estimate", "Unavailable"));
			loot.body.add(note("Illustrative valuation, not live prices. No unobserved drops inferred."));
			add(loot);
		} else if (detailTab.equals("supplies")) {
			Section sup = card("Observed supplies used");
			sup.setName("files-supplies");
			sup.body.add(empty("Not observed. Quantities are unknown."));
			sup.body.add(note("Only witnessed supply changes. Not inferred from Bank Memory; unknown is not zero."));
			add(sup);
		} else {
			Section d = card("Record details");
			d.setName("files-details");
			d.body.add(line("Type", r.kind));
			d.body.add(line("Observed activity", r.combat() ? r.title : r.rowMetric));
			if (r.combat()) {
				String kc = model.accountKc(r);
				d.body.add(line("Account-wide KC", kc == null ? "Unavailable" : kc));
				d.body.add(line("Trip " + r.countLabel.toLowerCase(Locale.ROOT), FilesModel.number(r.count)));
			}
			d.body.add(line("Source", "Sample session ledger"));
			if (r.note != null) d.body.add(note(r.note));
			if (r.combat() && r.encounterKey != null)
				d.body.add(action("Open in Boss Chronicle ›", "files-open-chronicle",
					() -> context.navigate(RouteId.CHRONICLE, RouteRequest.Param.ENCOUNTER, r.encounterKey)));
			add(d);
		}
	}

	// ------------------------------------------------------------------ pieces

	private Section card(String title) {
		Section s = Section.plain(uiContext, stone, wood, title == null ? "" : title);
		if (title == null) s.getComponent(0).setVisible(false);
		return s;
	}

	private Section card(String title, JComponent body) {
		Section s = card(title);
		s.body.add(body);
		return s;
	}

	private JComponent status() {
		boolean ok = model.archive == FilesModel.Archive.POPULATED || model.archive == FilesModel.Archive.EMPTY;
		String text = model.status + (model.account != null ? " · Demo " + model.account : "");
		return new Status(text, ok ? OK : OFF);
	}

	private JComponent row(Rec r) { return new RecordRow(icon(r.icon), r.title, r.rowMetric + " · " + r.rowTime, r.date, false, r.id, "files-row-" + r.id); }

	private JComponent empty(String text) {
		Label l = new Label(uiContext, text, Style.SMALL, EMPTY);
		l.setName("files-empty");
		JPanel p = new StackPanel(0);
		p.setOpaque(false);
		p.setBorder(new EmptyBorder(8, 8, 8, 8));
		p.add(l);
		return p;
	}

	private JComponent note(String text) {
		Label l = new Label(uiContext, text, Style.TINY, NOTE);
		l.setName("files-note");
		JPanel p = new StackPanel(0);
		p.setOpaque(false);
		p.setBorder(new EmptyBorder(6, 6, 6, 6));
		p.add(l);
		return p;
	}

	private JComponent centered(JComponent c, int top) {
		JPanel p = new StackPanel(0) {
			@Override public void doLayout() {
				int w = Math.min(getWidth(), c.getPreferredSize().width);
				c.setBounds((getWidth() - w) / 2, top, w, StackLayout.heightFor(c, w));
			}
			@Override public int heightForWidth(int width) { return top + 2 + StackLayout.heightFor(c, Math.min(width, c.getPreferredSize().width)); }
		};
		p.setOpaque(false);
		p.add(c);
		return p;
	}

	private BufferedImage itemImage(int id) { return context.assets().item(id); }

	/** Frozen icon keys: {@code i<id>.png} item, {@code s-<skill>.png} skill, {@code <encounter>.png} portrait. */
	private BufferedImage icon(String key) {
		if (key == null || !key.endsWith(".png")) return null;
		String k = key.substring(0, key.length() - 4);
		R02Assets a = context.assets();
		if (k.matches("i\\d+")) return a.item(Integer.parseInt(k.substring(1)));
		if (k.startsWith("s-")) return a.skillIcon(k.substring(2));
		return a.portrait(k);
	}

	private JComponent line(String label, String value) { return new Line(label, value); }

	private JComponent action(String text, String name, Runnable run) {
		FfAction b = new FfAction(text);
		b.setName(name);
		b.addActionListener(e -> run.run());
		JPanel p = new StackPanel(0);
		p.setOpaque(false);
		p.setBorder(new EmptyBorder(5, 5, 5, 5));
		p.add(b);
		return p;
	}

	private JComponent tabs(String[] labels, int selected, java.util.function.IntConsumer onSelect, String name) {
		JPanel p = new JPanel(null) {
			@Override public void doLayout() {
				int n = getComponentCount(), w = (getWidth() - 4 * (n - 1)) / n;
				for (int i = 0; i < n; i++) getComponent(i).setBounds(i * (w + 4), 0, i == n - 1 ? getWidth() - i * (w + 4) : w, getHeight());
			}
			@Override public Dimension getPreferredSize() { return new Dimension(211, tabHeight()); }
		};
		p.setOpaque(false);
		p.setName(name);
		for (int i = 0; i < labels.length; i++) {
			Tab t = new Tab(labels[i], i == selected);
			t.setName(name + "-" + labels[i].toLowerCase(Locale.ROOT));
			final int index = i;
			t.addActionListener(e -> onSelect.accept(index));
			p.add(t);
		}
		return new Fixed(p, tabHeight());
	}

	private int tabHeight() { return getFontMetrics(tabFont()).getHeight() + 14; }

	private Font tabFont() { return Style.ROW.font(uiContext); }

	static JComponent find(java.awt.Component c, String name) {
		if (name != null && name.equals(c.getName()) && c instanceof JComponent) return (JComponent) c;
		if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
			JComponent j = find(k, name);
			if (j != null) return j;
		}
		return null;
	}

	@Override public int heightForWidth(int width) { return ((StackLayout) getLayout()).heightForWidth(this, width); }

	@Override public Dimension getPreferredSize() {
		int w = getWidth() > 0 ? getWidth() : 225;
		return new Dimension(w, heightForWidth(w));
	}

	/** Frozen {@code #feral-files}: fieldstone at 160px under a light veil, darker 12% side bands. */
	@Override protected void paintComponent(Graphics g) {
		Graphics2D g2 = AdventureUi.prepare(g);
		int w = getWidth(), h = getHeight();
		g2.setColor(new Color(0x211d17));
		g2.fillRect(0, 0, w, h);
		AdventureUi.tile(g2, stone, w, h, 160);
		g2.setPaint(new GradientPaint(0, 0, new Color(0x29, 0x25, 0x1b, 0x25), 0, h, new Color(0x21, 0x1d, 0x17, 0x55)));
		g2.fillRect(0, 0, w, h);
		int band = Math.round(w * 0.12f);
		g2.setPaint(new GradientPaint(0, 0, new Color(0x12, 0x10, 0x0b, 0x80), band, 0, new Color(0x12, 0x10, 0x0b, 0)));
		g2.fillRect(0, 0, band, h);
		g2.setPaint(new GradientPaint(w - band, 0, new Color(0x12, 0x10, 0x0b, 0), w, 0, new Color(0x12, 0x10, 0x0b, 0x80)));
		g2.fillRect(w - band, 0, band, h);
		g2.dispose();
	}

	// ------------------------------------------------------------------ components

	/** A component with a fixed height (tab strips). */
	private static final class Fixed extends JPanel implements HeightForWidth {
		private final int h;

		Fixed(JComponent c, int h) {
			super(new java.awt.BorderLayout());
			this.h = h;
			setOpaque(false);
			add(c);
		}

		@Override public int heightForWidth(int width) { return h; }

		@Override public Dimension getPreferredSize() { return new Dimension(211, h); }
	}

	/** Frozen {@code .ff-hero}: image 48x53, title and subtitle, bronze-edged gradient box. */
	private JComponent hero(BufferedImage img, String title, String subtitle, String name) {
		Hero h = new Hero(img, title, subtitle);
		h.setName(name);
		return h;
	}

	private final class Hero extends JComponent implements HeightForWidth {
		private final BufferedImage img;
		private final String title, subtitle;

		Hero(BufferedImage img, String title, String subtitle) {
			this.img = img;
			this.title = title;
			this.subtitle = subtitle;
		}

		private int textWidth(int w) { return Math.max(1, w - 14 - 48 - 8); }

		@Override public int heightForWidth(int width) {
			FontMetrics t = getFontMetrics(Style.HEAD.font(uiContext)), s = getFontMetrics(Style.TINY.font(uiContext));
			int h = TextWrap.wrap(title, t, textWidth(width)).size() * t.getHeight() + 2 + TextWrap.wrap(subtitle, s, textWidth(width)).size() * s.getHeight();
			return Math.max(53, h) + 14;
		}

		@Override public Dimension getPreferredSize() { return new Dimension(211, heightForWidth(211)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight();
			g2.setPaint(new GradientPaint(0, 0, new Color(0x41, 0x36, 0x26, 0xe8), w, h / 3f, new Color(0x21, 0x1d, 0x17, 0xdd)));
			g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setColor(new Color(0x9b7c4c));
			g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setColor(new Color(0xd2, 0xb6, 0x81, 0x38));
			g2.drawLine(3, 1, w - 4, 1);
			if (img != null) AdventureUi.contain(g2, img, 7, (h - 53) / 2, 48, 53);
			int x = 7 + 48 + 8, tw = textWidth(w);
			FontMetrics t = g2.getFontMetrics(Style.HEAD.font(uiContext)), s = g2.getFontMetrics(Style.TINY.font(uiContext));
			int total = TextWrap.wrap(title, t, tw).size() * t.getHeight() + 2 + TextWrap.wrap(subtitle, s, tw).size() * s.getHeight();
			int y = (h - total) / 2;
			g2.setFont(Style.HEAD.font(uiContext));
			for (String l : TextWrap.wrap(title, t, tw)) { g2.setColor(new Color(0x10, 0x0a, 0x04, 0xc0)); g2.drawString(l, x, y + t.getAscent() + 1); g2.setColor(AdventureUi.HEAD); g2.drawString(l, x, y + t.getAscent()); y += t.getHeight(); }
			y += 2;
			g2.setFont(Style.TINY.font(uiContext));
			g2.setColor(new Color(0xe0d0b7));
			for (String l : TextWrap.wrap(subtitle, s, tw)) { g2.drawString(l, x, y + s.getAscent()); y += s.getHeight(); }
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.HEADER; }
				@Override public String getAccessibleName() { return title + ", " + subtitle + (img == null ? " (image unavailable)" : ""); }
			};
			return accessibleContext;
		}
	}

	/** Frozen {@code .ff-status}: dot and text, 10px. */
	private final class Status extends JComponent implements HeightForWidth {
		private final String text;
		private final Color color;

		Status(String text, Color color) {
			this.text = text;
			this.color = color;
			setName("files-status");
		}

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(Style.TINY.font(uiContext));
			return 6 + TextWrap.wrap(text, fm, Math.max(1, width - 16)).size() * fm.getHeight();
		}

		@Override public Dimension getPreferredSize() { return new Dimension(211, heightForWidth(211)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			g2.setFont(Style.TINY.font(uiContext));
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(color);
			g2.fillOval(2, 3 + (fm.getHeight() - 7) / 2, 7, 7);
			int y = 3;
			for (String l : TextWrap.wrap(text, fm, Math.max(1, getWidth() - 16))) { g2.drawString(l, 14, y + fm.getAscent()); y += fm.getHeight(); }
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

	/** Frozen {@code .ff-tabs button}: 11px bold, pressed state gold. */
	private final class Tab extends JButton {
		private final boolean selected;

		Tab(String text, boolean selected) {
			super(text);
			this.selected = selected;
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
		}

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight();
			g2.setPaint(selected ? new GradientPaint(0, 0, new Color(0x685034), 0, h, new Color(0x3f3020))
				: new GradientPaint(0, 0, new Color(0x3e3527), 0, h, new Color(0x24211b)));
			g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setColor(selected ? new Color(0xcc9c52) : new Color(0x786445));
			g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setFont(tabFont());
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(selected ? new Color(0xffe3a2) : new Color(0xddc9a6));
			g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h + fm.getAscent()) / 2 - 2);
			AdventureUi.focusRing(g2, this);
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			AccessibleContext c = super.getAccessibleContext();
			return c;
		}

		@Override public boolean isSelected() { return selected; }
	}

	/** Frozen {@code .ff-row} / {@code .ff-spoil}: icon, bold title, two small lines, chevron; opens the record. */
	private final class RecordRow extends JComponent implements HeightForWidth {
		private final BufferedImage img;
		private final String title, line1, line2, recordId;
		private final boolean boxed;

		RecordRow(BufferedImage img, String title, String line1, String line2, boolean boxed, String recordId, String name) {
			this.img = img;
			this.title = title;
			this.line1 = line1;
			this.line2 = line2;
			this.boxed = boxed;
			this.recordId = recordId;
			setName(name);
			AdventureUi.activator(this, () -> openDetail(recordId, this));
		}

		private int iconW() { return boxed ? 40 : 35; }

		private int textW(int w) { return Math.max(1, w - 10 - iconW() - 6 - 8 - 6); }

		private int textHeight(int w) {
			FontMetrics t = getFontMetrics(Style.ROW.font(uiContext)), s = getFontMetrics(Style.TINY.font(uiContext));
			int tw = textW(w);
			return TextWrap.wrap(title, t, tw).size() * t.getHeight() + 3 + TextWrap.wrap(line1, s, tw).size() * s.getHeight()
				+ 3 + TextWrap.wrap(line2, s, tw).size() * s.getHeight();
		}

		@Override public int heightForWidth(int width) { return Math.max(boxed ? 38 : 35, textHeight(width)) + (boxed ? 16 : 14); }

		@Override public Dimension getPreferredSize() { return new Dimension(209, heightForWidth(209)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight();
			int ih = boxed ? 38 : 35;
			if (boxed) {
				g2.setColor(new Color(0x181610));
				g2.fillRoundRect(5, (h - 38) / 2, 38, 38, 6, 6);
				g2.setColor(new Color(0x887041));
				g2.drawRoundRect(5, (h - 38) / 2, 38, 38, 6, 6);
				if (img != null) AdventureUi.contain(g2, img, 7, (h - 38) / 2 + 2, 34, 34);
			} else if (img != null) AdventureUi.contain(g2, img, 5, (h - ih) / 2, 33, 35);
			if (img == null) {
				g2.setColor(new Color(0x7c, 0x69, 0x43, 0x80));
				g2.drawRect(5, (h - 33) / 2, 32, 32);
			}
			int x = 5 + iconW() + 6, tw = textW(w), y = (h - textHeight(w)) / 2;
			FontMetrics t = g2.getFontMetrics(Style.ROW.font(uiContext)), s = g2.getFontMetrics(Style.TINY.font(uiContext));
			g2.setFont(Style.ROW.font(uiContext));
			g2.setColor(TITLE);
			for (String l : TextWrap.wrap(title, t, tw)) { g2.drawString(l, x, y + t.getAscent()); y += t.getHeight(); }
			g2.setFont(Style.TINY.font(uiContext));
			g2.setColor(SMALL);
			y += 3;
			for (String l : TextWrap.wrap(line1, s, tw)) { g2.drawString(l, x, y + s.getAscent()); y += s.getHeight(); }
			y += 3;
			for (String l : TextWrap.wrap(line2, s, tw)) { g2.drawString(l, x, y + s.getAscent()); y += s.getHeight(); }
			g2.setFont(Style.HEAD.font(uiContext));
			FontMetrics c = g2.getFontMetrics();
			g2.setColor(CHEVRON);
			g2.drawString("›", w - 5 - c.stringWidth("›"), (h + c.getAscent()) / 2 - 2);
			g2.setColor(new Color(0x76, 0x60, 0x41, 0x65));
			g2.fillRect(0, h - 1, w, 1);
			AdventureUi.focusRing(g2, this);
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.PUSH_BUTTON; }
				@Override public String getAccessibleName() { return title + ", " + line1 + ", " + line2 + (img == null ? " (image unavailable)" : ""); }
			};
			return accessibleContext;
		}
	}

	/** Frozen {@code .ff-line}: label left, bold value right (max 58%), bottom rule. */
	private final class Line extends JComponent implements HeightForWidth {
		private final String label, value;

		Line(String label, String value) {
			this.label = label;
			this.value = value;
			setName("files-line-" + label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
		}

		private int valueW(int width) { return Math.min(Math.round((width - 12) * 0.58f), getFontMetrics(Style.ROW.font(uiContext)).stringWidth(value)); }

		@Override public int heightForWidth(int width) {
			FontMetrics l = getFontMetrics(Style.SMALL.font(uiContext)), v = getFontMetrics(Style.ROW.font(uiContext));
			int vw = Math.max(1, valueW(width));
			return 10 + Math.max(TextWrap.wrap(label, l, Math.max(1, width - 12 - vw - 8)).size() * l.getHeight(), TextWrap.wrap(value, v, vw).size() * v.getHeight());
		}

		@Override public Dimension getPreferredSize() { return new Dimension(209, heightForWidth(209)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), vw = Math.max(1, valueW(w));
			FontMetrics l = g2.getFontMetrics(Style.SMALL.font(uiContext)), v = g2.getFontMetrics(Style.ROW.font(uiContext));
			g2.setFont(Style.SMALL.font(uiContext));
			g2.setColor(LINE_LABEL);
			int y = 5;
			for (String s : TextWrap.wrap(label, l, Math.max(1, w - 12 - vw - 8))) { g2.drawString(s, 6, y + l.getAscent()); y += l.getHeight(); }
			g2.setFont(Style.ROW.font(uiContext));
			g2.setColor(LINE_VALUE);
			y = 5;
			for (String s : TextWrap.wrap(value, v, vw)) { g2.drawString(s, w - 6 - v.stringWidth(s), y + v.getAscent()); y += v.getHeight(); }
			g2.setColor(RULE);
			g2.fillRect(0, getHeight() - 1, w, 1);
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

	/** Frozen {@code .ff-metrics}: two centred cells, 16px bold value over 10px label. */
	private final class Metrics extends JComponent implements HeightForWidth {
		private final String[] values, labels;

		Metrics(String v1, String l1, String v2, String l2) {
			values = new String[] {v1, v2};
			labels = new String[] {l1, l2};
			setName("files-metrics");
		}

		private Font valueFont() { Font f = Style.HEAD.font(uiContext); return f.deriveFont(f.getSize2D() * 16f / 13f); }

		@Override public int heightForWidth(int width) {
			int cw = Math.max(1, (width - 1) / 2 - 10);
			FontMetrics v = getFontMetrics(valueFont()), l = getFontMetrics(Style.TINY.font(uiContext));
			int h = 0;
			for (int i = 0; i < 2; i++) h = Math.max(h, TextWrap.wrap(values[i], v, cw).size() * v.getHeight() + TextWrap.wrap(labels[i], l, cw).size() * l.getHeight());
			return h + 14;
		}

		@Override public Dimension getPreferredSize() { return new Dimension(209, heightForWidth(209)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight(), cw = (w - 1) / 2;
			g2.setColor(new Color(0x77, 0x61, 0x3e, 0x77));
			g2.fillRect(0, 0, w, h);
			for (int i = 0; i < 2; i++) {
				int x0 = i == 0 ? 0 : cw + 1, x1 = i == 0 ? cw : w;
				g2.setColor(new Color(0x27231c));
				g2.fillRect(x0, 0, x1 - x0, h);
				int y = 7, inner = Math.max(1, x1 - x0 - 10);
				FontMetrics v = g2.getFontMetrics(valueFont()), l = g2.getFontMetrics(Style.TINY.font(uiContext));
				g2.setFont(valueFont());
				g2.setColor(METRIC);
				for (String s : TextWrap.wrap(values[i], v, inner)) { g2.drawString(s, x0 + (x1 - x0 - v.stringWidth(s)) / 2, y + v.getAscent()); y += v.getHeight(); }
				g2.setFont(Style.TINY.font(uiContext));
				g2.setColor(METRIC_LABEL);
				for (String s : TextWrap.wrap(labels[i], l, inner)) { g2.drawString(s, x0 + (x1 - x0 - l.stringWidth(s)) / 2, y + l.getAscent()); y += l.getHeight(); }
			}
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.LABEL; }
				@Override public String getAccessibleName() { return labels[0] + ": " + values[0] + ", " + labels[1] + ": " + values[1]; }
			};
			return accessibleContext;
		}
	}

	/** Frozen {@code .ff-items}: three columns of item tiles (quantity, sprite, name). */
	private final class Items extends JPanel implements HeightForWidth {
		private final List<Item> items;

		Items(List<Item> items) {
			super(null);
			this.items = items;
			setOpaque(false);
			setName("files-items");
		}

		private int cellH(int cw) {
			FontMetrics q = getFontMetrics(Style.ROW.font(uiContext)), n = getFontMetrics(Style.TINY.font(uiContext));
			int h = 0;
			for (Item i : items) h = Math.max(h, q.getHeight() + 30 + TextWrap.wrap(i.name, n, Math.max(1, cw - 6)).size() * n.getHeight() + 6);
			return h;
		}

		@Override public int heightForWidth(int width) {
			int cw = (width - 10 - 6) / 3, rows = (items.size() + 2) / 3;
			return 10 + rows * cellH(cw) + Math.max(0, rows - 1) * 3;
		}

		@Override public Dimension getPreferredSize() { return new Dimension(209, heightForWidth(209)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int cw = (getWidth() - 10 - 6) / 3, ch = cellH(cw);
			FontMetrics q = g2.getFontMetrics(Style.ROW.font(uiContext)), n = g2.getFontMetrics(Style.TINY.font(uiContext));
			for (int i = 0; i < items.size(); i++) {
				Item it = items.get(i);
				int x = 5 + (i % 3) * (cw + 3), y = 5 + (i / 3) * (ch + 3);
				g2.setColor(new Color(0x181610));
				g2.fillRoundRect(x, y, cw, ch, 6, 6);
				g2.setColor(new Color(0x6e5b3c));
				g2.drawRoundRect(x, y, cw - 1, ch - 1, 6, 6);
				String qty = FilesModel.number(it.quantity);
				g2.setFont(Style.ROW.font(uiContext));
				g2.setColor(new Color(0xf2ce79));
				g2.drawString(qty, x + (cw - q.stringWidth(qty)) / 2, y + 3 + q.getAscent());
				BufferedImage img = itemImage(it.id);
				if (img != null) AdventureUi.contain(g2, img, x + (cw - 30) / 2, y + 3 + q.getHeight() + 1, 30, 28);
				g2.setFont(Style.TINY.font(uiContext));
				g2.setColor(EMPTY);
				int ty = y + 3 + q.getHeight() + 30;
				for (String s : TextWrap.wrap(it.name, n, Math.max(1, cw - 6))) { g2.drawString(s, x + (cw - n.stringWidth(s)) / 2, ty + n.getAscent()); ty += n.getHeight(); }
			}
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJPanel() {
				@Override public String getAccessibleName() {
					StringBuilder b = new StringBuilder("Observed loot: ");
					for (Item i : items) b.append(i.name).append(" ×").append(FilesModel.number(i.quantity)).append("; ");
					return b.toString();
				}
			};
			return accessibleContext;
		}
	}

	/** Frozen {@code .ff-fold summary}: disclosure for earlier observations. */
	private final class Fold extends JComponent implements HeightForWidth {
		private final String text;
		private final java.util.function.Consumer<Boolean> toggle;
		boolean open;

		Fold(String text, java.util.function.Consumer<Boolean> toggle) {
			this.text = text;
			this.toggle = toggle;
			setName("files-earlier-toggle");
			AdventureUi.activator(this, () -> { open = !open; toggle.accept(open); repaint(); });
		}

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(Style.SMALL.font(uiContext));
			return 16 + TextWrap.wrap(text, fm, Math.max(1, width - 30)).size() * fm.getHeight();
		}

		@Override public Dimension getPreferredSize() { return new Dimension(209, heightForWidth(209)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			g2.setFont(Style.SMALL.font(uiContext));
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(FOLD);
			int y = 8;
			for (String l : TextWrap.wrap(text, fm, Math.max(1, getWidth() - 30))) { g2.drawString(l, 6, y + fm.getAscent()); y += fm.getHeight(); }
			g2.drawString(open ? "⌄" : "›", getWidth() - 12, (getHeight() + fm.getAscent()) / 2 - 1);
			if (open) {
				g2.setColor(new Color(0x78, 0x63, 0x41, 0x66));
				g2.fillRect(0, getHeight() - 1, getWidth(), 1);
			}
			AdventureUi.focusRing(g2, this);
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.TOGGLE_BUTTON; }
				@Override public String getAccessibleName() { return text; }
				@Override public AccessibleStateSet getAccessibleStateSet() {
					AccessibleStateSet s = super.getAccessibleStateSet();
					s.add(open ? AccessibleState.EXPANDED : AccessibleState.COLLAPSED);
					return s;
				}
			};
			return accessibleContext;
		}
	}

	/** Frozen {@code .ff-filter}: search and category side by side (shared components). */
	private final class FilterRow extends JPanel implements HeightForWidth {
		FilterRow() {
			super(null);
			setOpaque(false);
			setName("files-filter-row");
			add(search);
			add(filter);
		}

		@Override public int heightForWidth(int width) { return Math.max(search.heightForWidth(width), filter.heightForWidth(width)); }

		@Override public Dimension getPreferredSize() { return new Dimension(211, heightForWidth(211)); }

		@Override public void doLayout() {
			int fw = Math.max(filter.getMinimumSize().width, Math.round(getWidth() * 0.42f)), h = getHeight();
			search.setBounds(0, 0, Math.max(1, getWidth() - fw - 4), h);
			filter.setBounds(getWidth() - fw, 0, fw, h);
		}
	}

	/** Frozen {@code .ff-action}: bronze full-width button, 11px bold. */
	private final class FfAction extends JButton implements HeightForWidth {
		FfAction(String text) {
			super(text);
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
		}

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(Style.ROW.font(uiContext));
			return 14 + TextWrap.wrap(getText(), fm, Math.max(1, width - 8)).size() * fm.getHeight();
		}

		@Override public Dimension getPreferredSize() { return new Dimension(199, heightForWidth(199)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight();
			g2.setPaint(new GradientPaint(0, 0, new Color(0x59462d), 0, h, new Color(0x32281c)));
			g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setColor(new Color(0x9b7e4d));
			g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setFont(Style.ROW.font(uiContext));
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(new Color(0xf0dab0));
			int y = 7;
			for (String l : TextWrap.wrap(getText(), fm, Math.max(1, w - 8))) { g2.drawString(l, (w - fm.stringWidth(l)) / 2, y + fm.getAscent()); y += fm.getHeight(); }
			if (isFocusOwner()) {
				g2.setColor(AdventureUi.FOCUS);
				g2.setStroke(new BasicStroke(2f));
				g2.drawRoundRect(1, 1, w - 3, h - 3, 6, 6);
			}
			g2.dispose();
		}
	}
}
