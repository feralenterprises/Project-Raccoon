package com.feralenterprises.raccoon.playtest.r02.panel.quests;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.PathfinderData;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.RButton;
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
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
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
import java.util.Set;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * R02 Quests & Diaries (frozen {@code #quests}): Quest Journal (search, status and type filters, the 215-entry archived
 * order) and Achievement Diaries (region and tier filters, tier groups), with quest and diary detail as a local Back
 * level. States come only from shared evidence. Functional build on shared components.
 */
final class QuestsPanel extends StackPanel implements LocalBack, Disposable {
	private static final String[][] STATUSES = {{"all", "All statuses"}, {"COMPLETE", "Complete"}, {"PROGRESS", "In progress"},
		{"NOT_STARTED", "Not started"}, {"UNKNOWN", "Unknown"}};
	private static final String[][] KINDS = {{"all", "All types"}, {"quest", "Quests"}, {"miniquest", "Miniquests"}, {"subquest", "Subquests"}};
	private static final Set<String> CURATED_REQUIREMENTS = Set.of("Song of the Elves", "Dragon Slayer II");

	private final RouteContext context;
	private final UiContext uiContext;
	private final PathfinderData data;
	private final QuestsModel.Diaries diaries;
	private final Runnable textScaleChanged = this::render;
	private final SearchField search;
	private final Set<String> closedTiers = new HashSet<>();
	private QuestsModel model;
	private boolean diaryTab;
	private String query = "", status = "all", kind = "all", region = "all", tier = "all";
	private Integer selectedQuest;
	private String selectedRegion, selectedTier, focusAfter;
	private Runnable onBackChanged = () -> { };

	QuestsPanel(RouteContext context, PathfinderData data, QuestsModel.Diaries diaries) {
		super(7);
		this.context = context;
		this.uiContext = context.ui();
		this.data = data;
		this.diaries = diaries;
		setName("quests");
		setOpaque(false);
		setBorder(new EmptyBorder(0, 0, 6, 0));
		search = new SearchField(uiContext, "Search quests…", q -> {
			if (q.equals(query)) return;
			query = q;
			focusAfter = "qd-search";
			render();
		});
		search.setName("qd-search");
		model = QuestsModel.of(context.snapshot(), data);
		render();
		context.onSnapshot(this::onSnapshot);
		uiContext.addChangeListener(textScaleChanged);
	}

	@Override public void dispose() { uiContext.removeChangeListener(textScaleChanged); }

	QuestsModel model() { return model; }

	private void onSnapshot(FixtureSnapshot s) {
		model = QuestsModel.of(s, data);
		render();
	}

	@Override public boolean hasLocalBack() { return selectedQuest != null || selectedRegion != null; }

	@Override public void localBack() {
		if (selectedQuest != null) { focusAfter = "qd-quest-" + selectedQuest; selectedQuest = null; }
		else if (selectedRegion != null) { focusAfter = "qd-diary-" + selectedRegion + "-" + selectedTier; selectedRegion = null; selectedTier = null; }
		else return;
		render();
		onBackChanged.run();
	}

	@Override public void setLocalBackListener(Runnable onChange) { onBackChanged = onChange == null ? () -> { } : onChange; }

	// ------------------------------------------------------------------ rendering

	private void render() {
		removeAll();
		SegmentedSelector tabs = new SegmentedSelector(uiContext, "Quests", "Diaries");
		tabs.setName("qd-tabs");
		tabs.setSelected(diaryTab ? 1 : 0);
		tabs.addSelectionListener(i -> {
			diaryTab = i == 1;
			query = "";
			if (!search.getText().isEmpty()) search.setText("");
			selectedQuest = null;
			selectedRegion = null;
			render();
			onBackChanged.run();
		});
		add(tabs);
		if (selectedQuest != null) renderQuest(data.quests.stream().filter(q -> q.id == selectedQuest).findFirst().get());
		else if (selectedRegion != null) renderDiary(selectedRegion, selectedTier);
		else {
			Card intro = new Card(uiContext, diaryTab ? "Achievement Diaries" : "Quest Journal");
			intro.setName("qd-intro");
			intro.addAll(text("qd-intro-sub", FontRole.SMALL, Tone.MUTED, diaryTab ? "Regional diaries and tier progress" : "Browse quests and miniquests"),
				text("qd-coverage", FontRole.SMALL, Tone.ACCENT, model.coverage));
			add(intro);
			add(search);
			RButton clear = button("qd-clear", "Clear search", () -> { query = ""; search.setText(""); focusAfter = "qd-search"; render(); });
			add(clear);
			if (diaryTab) renderDiaries();
			else renderQuests();
			add(text("qd-note", FontRole.SMALL, Tone.MUTED, (diaryTab ? "Tier progress and archived task lists · individual task observations unavailable."
				: "Archived quest-order records, not a certified complete quest list.") + " · Read-only"));
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

	private void renderQuests() {
		RDropdown<String[]> st = dropdown("qd-status", "Quest status", STATUSES, status, v -> status = v);
		RDropdown<String[]> kd = dropdown("qd-kind", "Quest type", KINDS, kind, v -> kind = v);
		add(st);
		add(kd);
		List<PathfinderData.Quest> list = model.quests(query, status, kind);
		add(text("qd-count", FontRole.SMALL, Tone.MUTED, list.size() + " of " + data.quests.size() + " archived entries · " + model.countText));
		StackPanel rows = new StackPanel(3);
		rows.setOpaque(false);
		rows.setName("qd-list");
		if (list.isEmpty()) rows.add(text("qd-empty", FontRole.SMALL, Tone.TEXT, "No matching quests."));
		for (PathfinderData.Quest q : list) rows.add(new Row("qd-quest-" + q.id, q.name, model.quest(q.name).label, () -> open(q.id, null, null)));
		add(rows);
	}

	private void renderDiaries() {
		String[][] regions = new String[QuestsModel.REGIONS.length + 1][];
		regions[0] = new String[] {"all", "All regions"};
		for (int i = 0; i < QuestsModel.REGIONS.length; i++) regions[i + 1] = new String[] {QuestsModel.REGIONS[i], QuestsModel.REGIONS[i]};
		String[][] tiers = new String[QuestsModel.TIERS.length + 1][];
		tiers[0] = new String[] {"all", "All tiers"};
		for (int i = 0; i < QuestsModel.TIERS.length; i++) tiers[i + 1] = new String[] {QuestsModel.TIERS[i], QuestsModel.TIERS[i]};
		add(dropdown("qd-region", "Diary region", regions, region, v -> region = v));
		add(dropdown("qd-tier", "Diary tier", tiers, tier, v -> tier = v));
		add(text("qd-count", FontRole.SMALL, Tone.MUTED, "12 regions · 4 tiers"));
		List<String> list = model.regions(query, region);
		for (String t : QuestsModel.TIERS) {
			if (!tier.equals("all") && !tier.equals(t)) continue;
			StackPanel body = new StackPanel(3);
			body.setOpaque(false);
			if (list.isEmpty()) body.add(text("qd-empty", FontRole.SMALL, Tone.TEXT, "No matching regions."));
			for (String r : list) body.add(new Row("qd-diary-" + r + "-" + t, r, model.diary(r, t).label, () -> open(null, r, t)));
			Expandable e = new Expandable(uiContext, t + " Diaries · " + model.tierCount(t, list), body);
			e.setName("qd-tier-" + t);
			e.setExpanded(!closedTiers.contains(t));
			e.addExpansionListener(x -> { if (x) closedTiers.remove(t); else closedTiers.add(t); });
			add(e);
		}
	}

	private void renderQuest(PathfinderData.Quest q) {
		add(button("qd-back", "‹ Quest journal", this::localBack));
		boolean curated = CURATED_REQUIREMENTS.contains(q.name);
		Card c = new Card(uiContext, q.name);
		c.setName("qd-detail");
		QuestsModel.State st = model.quest(q.name);
		c.addAll(text("qd-detail-state", FontRole.BODY, Tone.ACCENT, st.label),
			text("qd-detail-kind", FontRole.SMALL, Tone.MUTED, (q.kind.equals("subquest") ? "Subquest" : q.kind.equals("miniquest") ? "Miniquest" : "Quest-order entry")
				+ " · " + (st == QuestsModel.State.UNKNOWN ? "No saved observation" : "Observed state")),
			text("qd-req-title", FontRole.BODY, Tone.ACCENT, "Requirements"),
			text("qd-req", FontRole.SMALL, Tone.TEXT, curated ? "Skill requirements" : "Verified requirements unavailable for this entry."));
		if (q.skills != null) c.addAll(text("qd-skills", FontRole.SMALL, Tone.TEXT, (curated ? "" : "Archived order targets: ") + q.skills.replaceAll(",\\s*", ", ")));
		if (!q.deps.isEmpty()) c.addAll(text("qd-deps", FontRole.SMALL, Tone.TEXT, "Related prerequisites (archived, not verified direct edges): " + String.join(", ", q.deps)));
		c.addAll(text("qd-rewards-title", FontRole.BODY, Tone.ACCENT, "Rewards & unlocks"),
			text("qd-rewards", FontRole.SMALL, Tone.TEXT, q.curated && q.why != null ? q.why : "Individual rewards not verified."));
		if (q.curated && q.next != null) c.addAll(text("qd-next", FontRole.SMALL, Tone.TEXT, q.next));
		c.addAll(text("qd-source", FontRole.SMALL, Tone.MUTED, "Source: archived research workbook. No completion inferred from quest order."));
		add(c);
		if (data.node(nodeKey(q.name)) != null || q.name.equals("Song of the Elves"))
			add(button("qd-to-pathfinder", "View in Pathfinder ›", () -> context.navigate(RouteId.PATHFINDER, RouteRequest.Param.MILESTONE, PathfinderData.slug(q.name))));
	}

	private String nodeKey(String name) {
		return data.nodes.stream().filter(n -> n.name.equals(name)).map(n -> n.key).findFirst().orElse(null);
	}

	private void renderDiary(String r, String t) {
		add(button("qd-back", "‹ Achievement Diaries", this::localBack));
		Card c = new Card(uiContext, r + " · " + t);
		c.setName("qd-detail");
		QuestsModel.State st = model.diary(r, t);
		c.addAll(text("qd-detail-state", FontRole.BODY, Tone.ACCENT, st.label),
			text("qd-detail-kind", FontRole.SMALL, Tone.MUTED, st == QuestsModel.State.UNKNOWN ? "No saved observation" : "Observed tier state"),
			text("qd-tasks-title", FontRole.BODY, Tone.ACCENT, "Individual tasks"),
			text("qd-tasks-note", FontRole.SMALL, Tone.MUTED, "Task completion observations unavailable."));
		List<QuestsModel.Task> tasks = diaries.tasks.get(r + " " + t);
		if (tasks == null) c.addAll(text("qd-tasks-none", FontRole.SMALL, Tone.TEXT, "Archived task reference unavailable."));
		else for (int i = 0; i < tasks.size(); i++) {
			QuestsModel.Task k = tasks.get(i);
			StringBuilder s = new StringBuilder((i + 1) + ". " + k.text);
			if (k.skills != null) s.append(" — ").append(k.skills);
			if (k.other != null) s.append(" — Requires ").append(k.other);
			c.addAll(text("qd-task-" + i, FontRole.SMALL, Tone.TEXT, s.toString()));
		}
		add(c);
	}

	private void open(Integer quest, String r, String t) {
		selectedQuest = quest;
		selectedRegion = r;
		selectedTier = t;
		render();
		onBackChanged.run();
	}

	// ------------------------------------------------------------------ helpers

	private RDropdown<String[]> dropdown(String name, String label, String[][] options, String current, java.util.function.Consumer<String> apply) {
		List<String[]> list = new ArrayList<>(Arrays.asList(options));
		RDropdown<String[]> d = new RDropdown<>(uiContext, label, list, o -> o[1], o -> { apply.accept(o[0]); focusAfter = name; render(); });
		d.setName(name);
		list.stream().filter(o -> o[0].equals(current)).findFirst().ifPresent(d::restore);
		return d;
	}

	private TextBlock text(String name, FontRole role, Tone tone, String t) {
		TextBlock b = new TextBlock(uiContext, t, role, tone);
		b.setName(name);
		return b;
	}

	private RButton button(String name, String label, Runnable run) {
		RButton b = new RButton(uiContext, label);
		b.setName(name);
		b.addActionListener(e -> run.run());
		return b;
	}

	static JComponent find(java.awt.Component c, String name) {
		if (name.equals(c.getName()) && c instanceof JComponent) return (JComponent) c;
		if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
			JComponent j = find(k, name);
			if (j != null) return j;
		}
		return null;
	}

	/** A list row: name, state at the right, chevron; opens the detail. */
	private final class Row extends JComponent implements HeightForWidth {
		private final String title, state;

		Row(String name, String title, String state, Runnable open) {
			this.title = title;
			this.state = state;
			setName(name);
			setFocusable(true);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) { open.run(); }
			});
			addKeyListener(new KeyAdapter() {
				@Override public void keyPressed(KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) { open.run(); e.consume(); }
				}
			});
			addFocusListener(new FocusAdapter() {
				@Override public void focusGained(FocusEvent e) { repaint(); }
				@Override public void focusLost(FocusEvent e) { repaint(); }
			});
		}

		private int stateW() { return getFontMetrics(uiContext.font(FontRole.SMALL)).stringWidth(state); }

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(uiContext.font(FontRole.BODY));
			return Math.max(28, 8 + TextWrap.wrap(title, fm, Math.max(1, width - stateW() - 30)).size() * fm.getHeight());
		}

		@Override public int minimumContentWidth() { return stateW() + 60; }

		@Override public Dimension getPreferredSize() { return new Dimension(200, heightForWidth(200)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = Paints.prepare(g);
			int w = getWidth(), h = getHeight();
			g2.setColor(new Color(0x2a251d));
			g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setColor(new Color(0x6f5b3b));
			g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setFont(uiContext.font(FontRole.BODY));
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(new Color(0xf0dfbd));
			List<String> lines = TextWrap.wrap(title, fm, Math.max(1, w - stateW() - 30));
			int y = (h - lines.size() * fm.getHeight()) / 2;
			for (String l : lines) { g2.drawString(l, 7, y + fm.getAscent()); y += fm.getHeight(); }
			g2.setFont(uiContext.font(FontRole.SMALL));
			g2.setColor(state.equals("Complete") ? new Color(0xa9ca7c) : state.equals("Unknown") ? new Color(0xb8b1a5) : new Color(0xe6c06a));
			g2.drawString(state, w - 16 - stateW(), (h + g2.getFontMetrics().getAscent()) / 2 - 2);
			g2.setColor(new Color(0xd3b681));
			g2.drawString("›", w - 10, (h + g2.getFontMetrics().getAscent()) / 2 - 2);
			if (isFocusOwner()) {
				g2.setColor(new Color(0xd8bc73));
				g2.setStroke(new BasicStroke(2f));
				g2.drawRoundRect(1, 1, w - 3, h - 3, 6, 6);
			}
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.PUSH_BUTTON; }
				@Override public String getAccessibleName() { return title + ", " + state; }
			};
			return accessibleContext;
		}
	}
}
