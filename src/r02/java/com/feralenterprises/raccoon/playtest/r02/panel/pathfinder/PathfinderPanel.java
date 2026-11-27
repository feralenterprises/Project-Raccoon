package com.feralenterprises.raccoon.playtest.r02.panel.pathfinder;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.PathfinderModel.State;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.ProgressBar;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.SegmentedSelector;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.component.Tone;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.lifecycle.Disposable;
import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.model.Evidence;
import com.feralenterprises.raccoon.ui.model.ProgressView;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteAware;
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
import java.awt.image.BufferedImage;
import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * R02 Pathfinder (frozen {@code #pathfinder}): Bow of Faerdhinen goal and next step, Map / Reqs / Paths / Info, the
 * grouped route (compact or expanded) with milestone detail, goal progress and legend. A typed {@code MILESTONE}
 * payload opens a route milestone, or a quest milestone (catalogue slug, e.g. {@code the-ides-of-milk} from Boss
 * Chronicle) showing only that quest's shared evidence (C-03). Detail and quest context are local Back levels.
 * Functional build on shared components; the frozen pop-out full map is deferred.
 */
final class PathfinderPanel extends StackPanel implements LocalBack, RouteAware, Disposable {
	static final String[] TABS = {"Map", "Reqs", "Paths", "Info"};

	private final RouteContext context;
	private final UiContext uiContext;
	private final PathfinderData data;
	private final Runnable textScaleChanged = this::render;
	private PathfinderModel model;
	private int tab;
	private boolean expanded, npc;
	private String selected, questContext, focusAfter;
	private Runnable onBackChanged = () -> { };

	PathfinderPanel(RouteContext context, PathfinderData data) {
		super(7);
		this.context = context;
		this.uiContext = context.ui();
		this.data = data;
		setName("pathfinder");
		setOpaque(false);
		setBorder(new EmptyBorder(0, 0, 6, 0));
		model = PathfinderModel.of(context.snapshot(), data);
		render();
		context.onSnapshot(this::onSnapshot);
		uiContext.addChangeListener(textScaleChanged);
	}

	@Override public void dispose() { uiContext.removeChangeListener(textScaleChanged); }

	PathfinderModel model() { return model; }

	private void onSnapshot(FixtureSnapshot s) {
		model = PathfinderModel.of(s, data);
		render();
	}

	// ------------------------------------------------------------------ navigation contracts

	@Override public void routeShown(RouteRequest request, boolean returning) {
		if (returning) return;
		if (request == null || request.param() != RouteRequest.Param.MILESTONE) {
			// A plain visit drops a sender's access context (frozen pfAccessContext); returning keeps it.
			if (questContext != null) { questContext = null; render(); onBackChanged.run(); }
			return;
		}
		String id = request.paramId();
		if (data.node(id) != null) { questContext = null; selected = id; render(); onBackChanged.run(); }
		else if (data.questBySlug(id) != null) { selected = null; questContext = id; render(); onBackChanged.run(); }
	}

	/** The typed quest context is the sender's access context, not a local level: Back returns to the sender (frozen R02). */
	@Override public boolean hasLocalBack() { return selected != null || tab != 0; }

	@Override public void localBack() {
		if (selected != null) { focusAfter = "pf-node-" + selected; selected = null; }
		else if (tab != 0) tab = 0;
		else return;
		render();
		onBackChanged.run();
	}

	@Override public void setLocalBackListener(Runnable onChange) { onBackChanged = onChange == null ? () -> { } : onChange; }

	private void open(String key) {
		selected = key;
		render();
		onBackChanged.run();
	}

	// ------------------------------------------------------------------ rendering

	private void render() {
		removeAll();
		if (questContext != null) renderQuestContext(data.questBySlug(questContext));
		else {
			Card goal = new Card(uiContext, "Current Goal");
			goal.setName("pf-goal");
			goal.addAll(text("pf-goal-title", FontRole.BODY, Tone.ACCENT, "Bow of Faerdhinen"), text("pf-goal-sub", FontRole.SMALL, Tone.MUTED, "Unlock and create the Bowfa"),
				bar("pf-goal-bar"), text("pf-goal-progress", FontRole.SMALL, Tone.TEXT, model.progressText()));
			add(goal);
			Card next = new Card(uiContext, "Next Step");
			next.setName("pf-next-card");
			PathfinderData.Node cur = model.current();
			RButton nb = new RButton(uiContext, model.nextTitle() + " ›");
			nb.setName("pf-next");
			nb.addActionListener(e -> { if (cur != null) open(cur.key); else { tab = 3; render(); onBackChanged.run(); } });
			next.addAll(nb, text("pf-next-sub", FontRole.SMALL, Tone.MUTED, model.nextSubtitle()));
			add(next);
			SegmentedSelector tabs = new SegmentedSelector(uiContext, TABS);
			tabs.setName("pf-tabs");
			if (selected == null) tabs.setSelected(tab);
			tabs.addSelectionListener(i -> { tab = i; selected = null; render(); onBackChanged.run(); });
			add(tabs);
			if (selected != null) renderDetail(data.node(selected));
			else if (tab == 0) renderRoute();
			else if (tab == 1) renderReqs();
			else if (tab == 2) renderPaths();
			else renderInfo();
			Card overall = new Card(uiContext, "Goal Progress");
			overall.setName("pf-overall");
			overall.addAll(text("pf-overall-text", FontRole.VALUE, Tone.ACCENT, model.overallText()), bar("pf-overall-bar"));
			add(overall);
			StringBuilder legend = new StringBuilder("Complete · Current · Available · Blocked");
			if (model.step == null) legend.append(" · Unknown");
			add(text("pf-legend", FontRole.SMALL, Tone.MUTED, legend.toString()));
			add(text("pf-note", FontRole.SMALL, Tone.MUTED, "Grouped milestones · shared account evidence"));
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

	private void renderRoute() {
		List<PathfinderData.Node> visible = expanded ? data.nodes : model.compactRoute();
		StackPanel route = new StackPanel(3);
		route.setOpaque(false);
		route.setName("pf-route");
		if (!expanded && visible.size() < PathfinderModel.TOTAL) route.add(button("pf-expand", "Show " + (PathfinderModel.TOTAL - visible.size()) + " earlier milestones",
			() -> { expanded = true; focusAfter = "pf-expand"; render(); }));
		for (PathfinderData.Node n : visible) route.add(new Step(n));
		if (expanded) route.add(button("pf-expand", "Compact route", () -> { expanded = false; focusAfter = "pf-expand"; render(); }));
		add(route);
	}

	private void renderDetail(PathfinderData.Node n) {
		add(button("pf-detail-back", "‹ Back to route", this::localBack));
		State st = model.state(n.key);
		Card c = new Card(uiContext, n.name);
		c.setName("pf-detail");
		c.addAll(text("pf-detail-state", FontRole.SMALL, Tone.ACCENT, st.label + " · account evidence"), text("pf-detail-copy", FontRole.SMALL, Tone.TEXT, n.detail),
			text("pf-detail-deps-title", FontRole.BODY, Tone.ACCENT, "Route dependencies"));
		if (n.deps.isEmpty()) c.addAll(text("pf-detail-nodeps", FontRole.SMALL, Tone.TEXT, "No earlier displayed dependency."));
		for (String d : n.deps) c.addAll(text("pf-dep-" + d, FontRole.SMALL, Tone.TEXT, data.node(d).name + ": " + model.state(d).label));
		if (n.key.equals("method")) c.addAll(button("pf-to-paths", "View crafting paths", () -> { selected = null; tab = 2; render(); }));
		if (n.key.equals("reqs")) c.addAll(button("pf-to-reqs", "View requirements", () -> { selected = null; tab = 1; render(); }));
		add(c);
	}

	private void renderReqs() {
		Card c = new Card(uiContext, "Quest prerequisites");
		c.setName("pf-reqs");
		for (String[] r : model.skillRows()) c.addAll(text("pf-req-" + r[0].toLowerCase(), FontRole.SMALL, Tone.TEXT, r[0] + ": " + r[1]));
		c.addAll(text("pf-req-note", FontRole.SMALL, Tone.MUTED, "Additional quests are grouped into the prerequisite checkpoint. This is not the complete quest chain."),
			text("pf-bow-materials", FontRole.BODY, Tone.ACCENT, "Bow materials"),
			text("pf-req-seed", FontRole.SMALL, Tone.TEXT, "Enhanced seed: " + model.state("seed").label),
			text("pf-req-shards", FontRole.SMALL, Tone.TEXT, "Crystal shards: " + (npc ? "150 required" : "100 required")),
			text("pf-equip", FontRole.BODY, Tone.ACCENT, "Equip the bow"),
			text("pf-equip-ranged", FontRole.SMALL, Tone.TEXT, "Ranged: 80"), text("pf-equip-agility", FontRole.SMALL, Tone.TEXT, "Agility: 70"),
			text("pf-equip-note", FontRole.SMALL, Tone.MUTED, "Equipment levels are separate from acquisition progress."));
		add(c);
	}

	private void renderPaths() {
		Card c = new Card(uiContext, "Singing bowl routes");
		c.setName("pf-paths");
		SegmentedSelector method = new SegmentedSelector(uiContext, "Self-sing", "NPC assistance");
		method.setName("pf-method");
		method.setSelected(npc ? 1 : 0);
		method.addSelectionListener(i -> { npc = i == 1; focusAfter = "pf-method"; render(); });
		c.addAll(text("pf-paths-copy", FontRole.SMALL, Tone.TEXT, "Both methods create the same bow. Select a planning route, not a game action."), method,
			text("pf-path-detail", FontRole.SMALL, Tone.TEXT, npc ? "150 shards total, no self-singing skill levels required."
				: "82 Crafting & Smithing (boostable), 100 shards."),
			text("pf-paths-note", FontRole.SMALL, Tone.MUTED, "Changing the route does not mark requirements complete."));
		add(c);
	}

	private void renderInfo() {
		Card c = new Card(uiContext, "About this goal");
		c.setName("pf-info");
		c.addAll(text("pf-info-1", FontRole.SMALL, Tone.TEXT, "Unlock Prifddinas, obtain the enhanced seed and shards, and create the Bow of Faerdhinen."),
			text("pf-info-2", FontRole.SMALL, Tone.TEXT, "Progress measures completed grouped milestones, not time or drop probability. Corruption is outside this goal."),
			text("pf-info-3", FontRole.SMALL, Tone.TEXT, "Milestone states come only from observed quest, skill and bank evidence for this account. No walkthrough or automatic tracking."));
		add(c);
	}

	/** C-03: a quest milestone from another panel shows only that quest's evidence; unknown stays Unknown. */
	private void renderQuestContext(PathfinderData.Quest q) {
		PathfinderModel.QuestLabel st = model.questState(q.name);
		Card c = new Card(uiContext, "Quest milestone");
		c.setName("pf-quest-context");
		c.addAll(text("pf-quest-name", FontRole.BODY, Tone.ACCENT, q.name), text("pf-quest-state", FontRole.SMALL, Tone.ACCENT, "Quest state: " + st.label),
			text("pf-quest-access", FontRole.SMALL, Tone.TEXT, st == PathfinderModel.QuestLabel.COMPLETE ? "Requirement observed complete."
				: st == PathfinderModel.QuestLabel.UNKNOWN ? "Requirement unverified. No quest observation is available; it is not treated as unmet."
				: "Requirement not met: the quest is not complete."));
		if (q.skills != null) c.addAll(text("pf-quest-skills", FontRole.SMALL, Tone.TEXT, "Skill requirements: " + q.skills));
		if (!q.deps.isEmpty()) c.addAll(text("pf-quest-deps", FontRole.SMALL, Tone.TEXT, "Quest requirements: " + String.join(", ", q.deps)));
		c.addAll(text("pf-quest-note", FontRole.SMALL, Tone.MUTED, "Detailed prerequisite route not available for this milestone."));
		add(c);
		add(button("pf-return", "Return to Bowfa goal", this::localBack));
	}

	// ------------------------------------------------------------------ helpers

	private ProgressBar bar(String name) {
		ProgressBar b = new ProgressBar(uiContext, new ProgressView("Goal milestones", model.step == null ? 0 : model.step, PathfinderModel.TOTAL,
			model.step == null ? DataState.UNAVAILABLE : DataState.ACTIVE, Evidence.NONE), ProgressBar.Variant.PROGRESS);
		b.setName(name);
		b.getAccessibleContext().setAccessibleDescription(model.step == null ? "Unknown" : model.step + " of " + PathfinderModel.TOTAL);
		return b;
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

	private BufferedImage icon(PathfinderData.Node n) {
		switch (n.key) {
			case "seed": return context.assets().item(25859);
			case "shards": return context.assets().item(23962);
			case "bow": return context.assets().item(PathfinderModel.BOW_ITEM);
			default: return n.kind.equals("skill") ? context.assets().skillIcon("construction") : null;
		}
	}

	/** Route step: state marker, sprite, name and chevron; opens the milestone detail. */
	private final class Step extends JComponent implements HeightForWidth {
		private final PathfinderData.Node n;
		private final State st;
		private final BufferedImage img;

		Step(PathfinderData.Node n) {
			this.n = n;
			this.st = model.state(n.key);
			this.img = icon(n);
			setName("pf-node-" + n.key);
			setFocusable(true);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) { open(n.key); }
			});
			addKeyListener(new KeyAdapter() {
				@Override public void keyPressed(KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) { open(n.key); e.consume(); }
				}
			});
			addFocusListener(new FocusAdapter() {
				@Override public void focusGained(FocusEvent e) { repaint(); }
				@Override public void focusLost(FocusEvent e) { repaint(); }
			});
		}

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(uiContext.font(FontRole.BODY));
			return Math.max(30, 8 + TextWrap.wrap(n.name, fm, Math.max(1, width - 62)).size() * fm.getHeight());
		}

		@Override public Dimension getPreferredSize() { return new Dimension(200, heightForWidth(200)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = Paints.prepare(g);
			int w = getWidth(), h = getHeight();
			g2.setColor(st == State.CURRENT ? new Color(0x4a3c24) : new Color(0x2a251d));
			g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setColor(st == State.CURRENT ? new Color(0xe6ac38) : new Color(0x6f5b3b));
			g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
			Color dot = st == State.COMPLETE ? new Color(0x789b42) : st == State.CURRENT ? new Color(0xe6ac38) : st == State.AVAILABLE
				? new Color(0xe5cf99) : st == State.BLOCKED ? new Color(0x6e6651) : new Color(0xa99b7a);
			g2.setColor(dot);
			g2.fillOval(5, (h - 12) / 2, 12, 12);
			g2.setColor(Color.BLACK);
			g2.setFont(uiContext.font(FontRole.QUANTITY));
			String mark = st == State.COMPLETE ? "✓" : st == State.UNKNOWN ? "?" : "";
			g2.drawString(mark, 7, (h + g2.getFontMetrics().getAscent()) / 2 - 2);
			if (img != null) {
				double s = Math.min(1, Math.min(22.0 / img.getWidth(), 22.0 / img.getHeight()));
				int dw = (int) (img.getWidth() * s), dh = (int) (img.getHeight() * s);
				g2.drawImage(img, 21 + (22 - dw) / 2, (h - dh) / 2, dw, dh, null);
			}
			g2.setFont(uiContext.font(FontRole.BODY));
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(st == State.BLOCKED ? new Color(0xa49b87) : new Color(0xf0dfbd));
			List<String> lines = TextWrap.wrap(n.name, fm, Math.max(1, w - 62));
			int y = (h - lines.size() * fm.getHeight()) / 2;
			for (String l : lines) { g2.drawString(l, 47, y + fm.getAscent()); y += fm.getHeight(); }
			g2.setColor(new Color(0xd3b681));
			g2.drawString("›", w - 10, (h + fm.getAscent()) / 2 - 2);
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
				@Override public String getAccessibleName() { return n.name + ", " + st.label; }
			};
			return accessibleContext;
		}
	}
}
