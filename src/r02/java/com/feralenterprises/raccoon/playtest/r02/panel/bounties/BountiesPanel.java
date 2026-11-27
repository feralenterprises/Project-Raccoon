package com.feralenterprises.raccoon.playtest.r02.panel.bounties;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.PathfinderData;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.SearchField;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.component.Tone;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.lifecycle.Disposable;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * R02 Bandit's Bounties (frozen {@code #bounties}): search, readiness filter, readiness groups and a detail level with
 * the evidence reason, the archived preparation reference and routes to Boss Chronicle, Outfitter and the unmet
 * Pathfinder requirement. Readiness comes only from shared evidence. Functional build on shared components.
 */
final class BountiesPanel extends StackPanel implements LocalBack, Disposable {
	private final RouteContext context;
	private final UiContext uiContext;
	private final BountiesData data;
	private final Runnable textScaleChanged = this::render;
	private final SearchField search;
	private final Set<BountiesModel.Readiness> closed = new HashSet<>();
	private BountiesModel model;
	private BountiesModel.Readiness filter;
	private String query = "", detail, focusAfter;
	private Runnable onBackChanged = () -> { };

	BountiesPanel(RouteContext context, BountiesData data) {
		super(7);
		this.context = context;
		this.uiContext = context.ui();
		this.data = data;
		setName("bounties");
		setOpaque(false);
		setBorder(new EmptyBorder(0, 0, 6, 0));
		closed.add(BountiesModel.Readiness.OUTGROWN);
		search = new SearchField(uiContext, "Find a bounty", q -> {
			if (q.equals(query)) return;
			query = q;
			focusAfter = "bb-search";
			render();
		});
		search.setName("bb-search");
		model = BountiesModel.of(context.snapshot(), data);
		render();
		context.onSnapshot(this::onSnapshot);
		uiContext.addChangeListener(textScaleChanged);
	}

	@Override public void dispose() { uiContext.removeChangeListener(textScaleChanged); }

	BountiesModel model() { return model; }

	private void onSnapshot(FixtureSnapshot s) {
		model = BountiesModel.of(s, data);
		render();
	}

	@Override public boolean hasLocalBack() { return detail != null; }

	@Override public void localBack() {
		if (detail == null) return;
		focusAfter = "bb-row-" + detail;
		detail = null;
		render();
		onBackChanged.run();
	}

	@Override public void setLocalBackListener(Runnable onChange) { onBackChanged = onChange == null ? () -> { } : onChange; }

	private void render() {
		removeAll();
		BountiesModel.Result open = detail == null ? null : model.result(detail);
		if (open != null) renderDetail(open);
		else renderBoard();
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

	private void renderBoard() {
		Card head = new Card(uiContext, "Encounter bounties");
		head.setName("bb-heading");
		head.addAll(text("bb-account", FontRole.SMALL, Tone.MUTED, model.accountLine));
		add(head);
		add(search);
		List<String> options = new ArrayList<>();
		options.add("all");
		for (BountiesModel.Readiness r : BountiesModel.SECTIONS) options.add(r.name());
		RDropdown<String> f = new RDropdown<>(uiContext, "Readiness", options, k -> k.equals("all") ? "All statuses" : BountiesModel.Readiness.valueOf(k).label, k -> {
			filter = k.equals("all") ? null : BountiesModel.Readiness.valueOf(k);
			focusAfter = "bb-filter";
			render();
		});
		f.setName("bb-filter");
		f.restore(filter == null ? "all" : filter.name());
		add(f);
		List<BountiesModel.Result> matches = model.matches(filter, query);
		if (matches.isEmpty()) {
			add(text("bb-empty", FontRole.SMALL, Tone.TEXT, "No bounties match these filters."));
			return;
		}
		for (BountiesModel.Readiness section : BountiesModel.SECTIONS) {
			StackPanel body = new StackPanel(3);
			body.setOpaque(false);
			int n = 0;
			for (BountiesModel.Result r : matches) {
				if (r.readiness != section) continue;
				n++;
				RButton b = button("bb-row-" + r.entry.key, r.entry.name + "  ›", () -> openDetail(r.entry.key));
				b.getAccessibleContext().setAccessibleName(r.entry.name + ", " + section.label);
				body.add(b);
			}
			if (n == 0) continue;
			Expandable e = new Expandable(uiContext, section.label + " · " + n, body);
			e.setName("bb-group-" + section.name());
			e.setExpanded(!closed.contains(section));
			e.addExpansionListener(x -> { if (x) closed.remove(section); else closed.add(section); });
			add(e);
		}
	}

	private void renderDetail(BountiesModel.Result r) {
		BountiesData.Entry e = r.entry;
		Card c = new Card(uiContext, e.name);
		c.setName("bb-detail");
		c.addAll(text("bb-state", FontRole.BODY, Tone.ACCENT, r.readiness.label), text("bb-reason", FontRole.SMALL, Tone.TEXT, r.reason));
		StackPanel ref = new StackPanel(3);
		ref.setOpaque(false);
		ref.add(text("bb-ref-gear", FontRole.SMALL, Tone.TEXT, e.gear));
		ref.add(text("bb-ref-status", FontRole.SMALL, Tone.MUTED, "Reference status: " + e.referenceStatus));
		ref.add(text("bb-ref-access", FontRole.SMALL, Tone.MUTED, "Detailed access requirements unavailable."));
		Expandable x = new Expandable(uiContext, "Archived preparation reference", ref);
		x.setName("bb-reference");
		x.setExpanded(false);
		c.addAll(x);
		add(c);
		// ENCOUNTER ids are the archived encounter keys Lane B's Chronicle and Outfitter resolve (B/outfitter.json, frozen aliases).
		if (e.outfitKey != null) {
			String encounter = e.outfitKey;
			add(button("bb-chronicle", "Open Boss Chronicle", () -> context.navigate(RouteId.CHRONICLE, RouteRequest.Param.ENCOUNTER, encounter)));
			add(button("bb-outfit", "Open Outfitter", () -> context.navigate(RouteId.OUTFIT, RouteRequest.Param.ENCOUNTER, encounter)));
		}
		if (r.readiness == BountiesModel.Readiness.LOCKED && e.gate != null)
			add(button("bb-access", "View unmet requirement", () -> context.navigate(RouteId.PATHFINDER, RouteRequest.Param.MILESTONE, PathfinderData.slug(e.gate))));
	}

	private void openDetail(String key) {
		detail = key;
		render();
		onBackChanged.run();
	}

	private TextBlock text(String name, FontRole role, Tone tone, String t) {
		TextBlock b = new TextBlock(uiContext, t, role, tone);
		b.setName(name);
		return b;
	}

	private RButton button(String name, String label, Runnable run) {
		RButton b = new RButton(uiContext, label);
		b.setName(name);
		b.addActionListener(ev -> run.run());
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
}
