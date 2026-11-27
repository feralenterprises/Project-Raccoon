package com.feralenterprises.raccoon.playtest.r02.panel.home;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Art;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Label;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Meter;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Primary;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Row;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Section;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Style;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.TextAction;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.layout.StackLayout;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * R02 Home (frozen {@code #home}): Next Opportunity, Other Opportunities, Recurring Activities, Recent Activity, Account
 * Snapshot and the "View account profile" action. The environment (fieldstone, header scene, vines and the burrow
 * entrance footer art) is painted by the shared {@code PageHost} for this {@code FULL} route, so the page draws only its
 * content and stays transparent. The account profile is a Home view (local Back), not a route. One instance per account scope; same-scope revisions refresh in place, a new scope discards the
 * page (shared composition), so no value can outlive its scope.
 */
final class HomePanel extends JPanel implements LocalBack, HeightForWidth, com.feralenterprises.raccoon.ui.lifecycle.Disposable {
	static final String OPPORTUNITY_ID = "scurrius-kc-goal";

	private final RouteContext context;
	private final UiContext ui;
	private final HomeInputs in;
	private final Art art;
	private final StackPanel home = new StackPanel(6), profile = new StackPanel(6);
	private final JComponent footer;
	private final ProfileAction profileAction;
	private HomeModel model;
	private Runnable onBackChanged = () -> { };
	private final Runnable textScaleChanged = this::relayout;

	// Next Opportunity
	private final JPanel lead = new StackPanel(0), dismissedArea = new StackPanel(0);
	private final Label goalTitle, goalValue, goalRemaining, reason;
	private final Meter meter = new Meter();
	private final Primary primary;
	private final TextAction why;
	// Other opportunities / recent / snapshot
	private final Row tunaRow;
	private final Section recentSection;
	private final List<Label> snapshotValues = new ArrayList<>(), profileSnapshotValues = new ArrayList<>();

	HomePanel(RouteContext context, HomeInputs inputs) {
		super(new StackLayout(0));
		this.context = context;
		this.ui = context.ui();
		this.in = inputs;
		this.art = new Art(inputs.stone, inputs.wood, inputs.plaster);
		setName("home");
		setOpaque(false);
		model = HomeModel.of(context.snapshot(), context.registry(), inputs);
		R02Assets assets = context.assets();

		// ---- Next Opportunity (feature card)
		goalTitle = new Label(ui, model.goalTitle, Style.GOAL, AdventureUi.GOAL_TITLE);
		goalValue = new Label(ui, model.goalValue, Style.TINY, AdventureUi.MUTED);
		goalRemaining = new Label(ui, model.goalRemaining, Style.TINY, AdventureUi.REMAINING);
		goalValue.setName("home-goal-value");
		goalRemaining.setName("home-goal-remaining");
		meter.setName("home-goal-meter");
		StackPanel goalCopy = stack(0, goalTitle, goalValue, meter, goalRemaining);
		JComponent goal = new Emblem(ui, assets.skillIcon("attack"), "Combat skill fixture", goalCopy);
		primary = new Primary(ui, model.primaryLabel);
		primary.setName("home-primary");
		primary.addActionListener(e -> context.navigate(model.primaryRoute, RouteRequest.Param.ENCOUNTER, model.encounterKey));
		reason = new Label(ui, model.reason, Style.TINY, AdventureUi.DETAIL).detailRule();
		reason.setVisible(false);
		why = new TextAction(ui, "Why this opportunity? ›", AdventureUi.LINK);
		why.setName("home-why");
		why.addActionListener(e -> { reason.setVisible(!reason.isVisible()); relayout(); });
		TextAction dismiss = new TextAction(ui, "Dismiss suggestion", AdventureUi.TEXT_ACTION);
		dismiss.setName("home-dismiss");
		TextAction restore = new TextAction(ui, "Restore suggestion", AdventureUi.LINK);
		restore.setName("home-restore");
		JComponent gap = new JPanel();
		gap.setOpaque(false);
		gap.setPreferredSize(new Dimension(1, 7));
		JComponent gapAfter = new JPanel();
		gapAfter.setOpaque(false);
		gapAfter.setPreferredSize(new Dimension(1, 3));
		for (JComponent c : new JComponent[] {goal, gap, primary, gapAfter, why, reason, dismiss}) lead.add(c);
		lead.setOpaque(false);
		lead.setBorder(new EmptyBorder(6, 7, 5, 7));
		dismissedArea.add(new Label(ui, "Suggestion dismissed for this preview.", Style.TINY, AdventureUi.MUTED));
		dismissedArea.add(restore);
		dismissedArea.setOpaque(false);
		dismissedArea.setBorder(new EmptyBorder(6, 7, 5, 7));
		dismissedArea.setVisible(false);
		dismiss.addActionListener(e -> setDismissed(true, restore));
		restore.addActionListener(e -> setDismissed(false, dismiss));
		Section next = new Section(ui, art, "Next Opportunity", null, true, false) {
			@Override protected void paintChildren(Graphics g) {
				super.paintChildren(g);
				Graphics2D g2 = AdventureUi.prepare(g);
				AdventureUi.contain(g2, in.ivyLeft, getWidth() - 43, -7, 45, 43);
				g2.dispose();
			}
		};
		next.setName("home-next");
		next.body.add(lead);
		next.body.add(dismissedArea);

		// ---- Other Opportunities
		tunaRow = new Row(ui, assets.skillIcon("cooking"), "Cooking", "Cooking 70", model.tuna, null, "›",
			"Requires " + in.tunaRequired + " raw tuna for " + HomeModel.fmt(in.tunaXpTotal)
				+ " XP from successful cooks. Check Bank Memory for the current observation.", null);
		tunaRow.setName("home-cooking");
		Row construction = new Row(ui, assets.skillIcon("construction"), "Construction", "Construction 50",
			"XP remaining unavailable", null, "›", "Review available materials and goal unlocks. No verified bank snapshot.", null);
		construction.setName("home-construction");
		Section other = new Section(ui, art, "Other Opportunities", in.opportunitiesIcon, false, false);
		other.body.add(AdventureUi.rows(tunaRow, construction));

		// ---- Recurring Activities (static: nothing tracked in R02)
		Section recurring = new Section(ui, art, "Recurring Activities", assets.skillIcon("farming"), false, true);
		recurring.setName("home-recurring");
		recurring.body.add(AdventureUi.rows(
			new Row(ui, assets.item(5295), "Herb run", "Herb run", "Timer not tracked", null, "›",
				"Check patch states in game. No reliable timer in this demo.", null),
			new Row(ui, assets.item(21512), "Birdhouse run", "Birdhouse run", "Not tracked", null, "›",
				"No observed placement time. Check birdhouses in game.", null),
			new Row(ui, assets.item(995), "Kingdom of Miscellania", "Kingdom of Miscellania", "Not checked", null, "›",
				"No current approval or coffer observation.", null)));

		// ---- Recent Activity
		recentSection = new Section(ui, art, "Recent Activity", assets.skillIcon("attack"), false, true);
		recentSection.setName("home-recent");

		// ---- Account Snapshot
		Section snapshot = new Section(ui, art, "Account Snapshot", in.snapshotIcon, false, false);
		snapshot.setName("home-snapshot");
		snapshot.body.add(snapshotGrid(snapshotValues));

		home.setOpaque(false);
		home.setBorder(new EmptyBorder(0, 0, 2, 0)); // the host insets page content; frozen .content padding is its INSET
		for (JComponent c : new JComponent[] {next, other, recurring, recentSection, snapshot}) home.add(c);
		profileAction = new ProfileAction(ui, inputs.wood, "View account profile ›");
		profileAction.setName("home-profile-action");
		profileAction.addActionListener(e -> openProfile());
		footer = new ActionBar(profileAction);
		footer.setName("home-footer");
		buildProfile();

		add(home);
		add(footer);
		add(profile);
		refresh();
		context.onSnapshot(this::onSnapshot);
		ui.addChangeListener(textScaleChanged);
	}

	/** Called by the shared composition when it discards this page (scope change). */
	@Override public void dispose() { ui.removeChangeListener(textScaleChanged); }

	private static StackPanel stack(int gap, JComponent... children) {
		StackPanel p = new StackPanel(gap);
		p.setOpaque(false);
		for (JComponent c : children) p.add(c);
		return p;
	}

	private JComponent snapshotGrid(List<Label> values) {
		JPanel grid = new SnapshotGrid(ui, values);
		grid.setName(values == snapshotValues ? "home-snapshot-grid" : "profile-snapshot-grid");
		return grid;
	}

	private void buildProfile() {
		profile.setName("home-profile");
		profile.setOpaque(false);
		profile.setBorder(new EmptyBorder(0, 0, 2, 0));
		Label title = new Label(ui, "Account profile", Style.HEAD, AdventureUi.HEAD);
		Label demo = new Label(ui, "Demonstration profile · not connected to RuneLite.", Style.TINY, AdventureUi.MUTED);
		Section snap = new Section(ui, art, "Account Snapshot", in.snapshotIcon, false, false);
		snap.body.add(snapshotGrid(profileSnapshotValues));
		Label note = new Label(ui, "Full profile will use the shared account intelligence service. No additional account information is collected here.",
			Style.SMALL, AdventureUi.DETAIL);
		Primary capture = new Primary(ui, "Account Capture ›");
		capture.setName("profile-capture");
		capture.addActionListener(e -> context.navigate(RouteId.CAPTURE));
		TextAction back = new TextAction(ui, "Return to Home", AdventureUi.LINK);
		back.setName("profile-return");
		back.addActionListener(e -> localBack());
		for (JComponent c : new JComponent[] {title, demo, snap, note, capture, back}) profile.add(c);
		profile.setVisible(false);
	}

	private void setDismissed(boolean dismissed, JComponent focusNext) {
		if (dismissed) context.dispatch(FixtureAction.of(FixtureAction.Kind.DISMISS_OPPORTUNITY, OPPORTUNITY_ID));
		else context.dispatch(FixtureAction.of(FixtureAction.Kind.RESTORE_OPPORTUNITIES));
		lead.setVisible(!dismissed);
		dismissedArea.setVisible(dismissed);
		relayout();
		SwingUtilities.invokeLater(focusNext::requestFocusInWindow);
	}

	private void onSnapshot(FixtureSnapshot s) {
		model = HomeModel.of(s, context.registry(), in);
		refresh();
	}

	/** Re-renders every evidence-conditioned value from {@link #model}; local selections (dismissal, open rows) stay. */
	private void refresh() {
		goalTitle.setText(model.goalTitle);
		goalValue.setText(model.goalValue);
		goalRemaining.setText(model.goalRemaining);
		reason.setText(model.reason);
		meter.set(model.fraction(), model.kc, model.target, model.goalValue);
		primary.setText(model.primaryLabel);
		primary.getAccessibleContext().setAccessibleName(model.primaryLabel.replace(" ›", ""));
		tunaRow.set("Cooking 70", model.tuna, null, context.assets().skillIcon("cooking"), "Cooking");

		recentSection.body.removeAll();
		if (model.recent.isEmpty()) {
			Label none = new Label(ui, "Activity evidence unavailable.", Style.TINY, AdventureUi.DETAIL).detailRule();
			none.setName("home-recent-unavailable");
			JPanel wrap = stack(0, none);
			wrap.setBorder(new EmptyBorder(5, 6, 6, 6));
			recentSection.body.add(wrap);
		} else {
			List<JComponent> rows = new ArrayList<>();
			for (HomeModel.Recent r : model.recent) {
				BufferedImage icon = HomeInputs.icon(context.assets(), in.recordIcon(context.snapshot().scope().account(), r.recordId));
				if (icon == null && r.encounterKey != null) icon = context.assets().portrait(r.encounterKey);
				if (icon == null && r.activityKey != null) icon = context.assets().portrait(r.activityKey);
				if (icon == null && r.activityKey != null && r.activityKey.matches("[a-z]+")) icon = context.assets().skillIcon(r.activityKey);
				Row row = new Row(ui, icon, r.title, r.title, r.subtitle, r.end, null, null, () -> {
					if (r.param == null) context.navigate(r.route);
					else context.navigate(r.route, r.param, r.payload);
				});
				row.setName(r.payload != null ? "home-recent-" + r.payload : "home-recent-activity");
				rows.add(row);
			}
			recentSection.body.add(AdventureUi.rows(rows.toArray(new JComponent[0])));
		}
		String[] values = {model.totalLevel, model.combatLevel, model.questProgress};
		for (List<Label> l : List.of(snapshotValues, profileSnapshotValues))
			for (int i = 0; i < values.length; i++) l.get(i).setText(values[i]);
		relayout();
	}

	private void relayout() {
		revalidate();
		repaint();
	}

	// ------------------------------------------------------------------ profile view (local Back)

	private void openProfile() {
		if (profile.isVisible()) return;
		home.setVisible(false);
		footer.setVisible(false);
		profile.setVisible(true);
		relayout();
		onBackChanged.run();
		SwingUtilities.invokeLater(() -> profile.getComponent(0).requestFocusInWindow());
	}

	@Override public boolean hasLocalBack() { return profile.isVisible(); }

	@Override public void localBack() {
		if (!profile.isVisible()) return;
		profile.setVisible(false);
		home.setVisible(true);
		footer.setVisible(true);
		relayout();
		onBackChanged.run();
		SwingUtilities.invokeLater(profileAction::requestFocusInWindow);
	}

	@Override public void setLocalBackListener(Runnable onChange) { onBackChanged = onChange == null ? () -> { } : onChange; }

	// ------------------------------------------------------------------ painting and layout

	@Override public int heightForWidth(int width) { return ((StackLayout) getLayout()).heightForWidth(this, width); }

	@Override public Dimension getPreferredSize() {
		int w = getWidth() > 0 ? getWidth() : 225;
		return new Dimension(w, heightForWidth(w));
	}

	// test hooks (package-private)
	HomeModel model() { return model; }
	JComponent footerAction() { return profileAction; }

	/** Frozen {@code .goal}: 32x42 emblem beside the goal copy. */
	private static final class Emblem extends JPanel implements HeightForWidth {
		private final BufferedImage icon;
		private final JComponent copy;

		Emblem(UiContext ui, BufferedImage icon, String alt, JComponent copy) {
			super(null);
			this.icon = icon;
			this.copy = copy;
			setOpaque(false);
			add(copy);
			getAccessibleContext().setAccessibleDescription(alt);
		}

		@Override public int heightForWidth(int width) {
			return Math.max(42, StackLayout.heightFor(copy, Math.max(1, width - 38)));
		}

		@Override public Dimension getPreferredSize() { return new Dimension(211, heightForWidth(211)); }

		@Override public void doLayout() {
			int h = StackLayout.heightFor(copy, Math.max(1, getWidth() - 38));
			copy.setBounds(38, (getHeight() - h) / 2, Math.max(1, getWidth() - 38), h);
		}

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			AdventureUi.contain(g2, icon, 0, (getHeight() - 42) / 2, 32, 42);
			g2.dispose();
		}
	}

	/** Frozen {@code .snapshot}: two columns (label, bold value), 7px padding, 4px gap, 11px. */
	private static final class SnapshotGrid extends JPanel implements HeightForWidth {
		private final UiContext ui;
		private final String[] labels = {"Total level", "Combat level", "Quest progress"};

		SnapshotGrid(UiContext ui, List<Label> values) {
			super(null);
			this.ui = ui;
			setOpaque(false);
			for (String l : labels) {
				Label k = new Label(ui, l, Style.SMALL, AdventureUi.SNAPSHOT);
				Label v = new Label(ui, "Unavailable", Style.ROW, AdventureUi.SNAPSHOT_VALUE);
				v.setName("snapshot-" + l.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
				v.getAccessibleContext();
				values.add(v);
				add(k);
				add(v);
			}
		}

		private int rowHeight() { return getFontMetrics(Style.ROW.font(ui)).getHeight(); }

		@Override public int heightForWidth(int width) {
			int h = 14;
			for (int i = 0; i < getComponentCount(); i += 2) {
				Label k = (Label) getComponent(i), v = (Label) getComponent(i + 1);
				int vw = Math.min(width / 2, v.getPreferredSize().width);
				h += Math.max(k.heightForWidth(width - 14 - vw - 6), v.heightForWidth(vw)) + 4;
			}
			return h - 4;
		}

		@Override public Dimension getPreferredSize() { return new Dimension(211, heightForWidth(211)); }

		@Override public void doLayout() {
			int y = 7, w = getWidth();
			for (int i = 0; i < getComponentCount(); i += 2) {
				Label k = (Label) getComponent(i), v = (Label) getComponent(i + 1);
				int vw = Math.min(w / 2, v.getPreferredSize().width);
				int h = Math.max(k.heightForWidth(w - 14 - vw - 6), v.heightForWidth(vw));
				k.setBounds(7, y, w - 14 - vw - 6, h);
				v.setBounds(w - 7 - vw, y, vw, h);
				y += h + 4;
			}
		}

		@Override public void invalidate() { super.invalidate(); }
	}

	/**
	 * Frozen {@code .profile-action} placement: 33px side insets at the reference width, narrowing to 8px when the
	 * label (Large text) would not fit, so the action never clips.
	 */
	static final class ActionBar extends JPanel implements HeightForWidth {
		private final ProfileAction action;

		ActionBar(ProfileAction action) {
			super(null);
			this.action = action;
			setOpaque(false);
			add(action);
		}

		private int inset(int width) { return Math.max(0, Math.min(33, (width - action.minimumContentWidth()) / 2)); }

		@Override public int heightForWidth(int width) { return 4 + action.heightForWidth(Math.max(1, width - 2 * inset(width))) + 6; }

		@Override public int minimumContentWidth() { return action.minimumContentWidth(); }

		@Override public Dimension getPreferredSize() { return new Dimension(225, heightForWidth(225)); }

		@Override public void doLayout() {
			int in = inset(getWidth()), w = Math.max(1, getWidth() - 2 * in);
			action.setBounds(in, 4, w, action.heightForWidth(w));
		}
	}

	/** Frozen {@code .profile-action}: timber button, gold edge, 13px bold. */
	static final class ProfileAction extends javax.swing.JButton implements HeightForWidth {
		private final UiContext ui;
		private final BufferedImage wood;

		ProfileAction(UiContext ui, BufferedImage wood, String text) {
			super(text);
			this.ui = ui;
			this.wood = wood;
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			getAccessibleContext().setAccessibleName("View account profile");
		}

		@Override public int minimumContentWidth() { return getFontMetrics(Style.BUTTON.font(ui)).stringWidth(getText()) + 12; }

		@Override public int heightForWidth(int width) { return Math.max(29, getFontMetrics(Style.BUTTON.font(ui)).getHeight() + 10); }

		@Override public Dimension getPreferredSize() { return new Dimension(159, heightForWidth(159)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = AdventureUi.prepare(g);
			int w = getWidth(), h = getHeight();
			java.awt.Shape r = new java.awt.geom.RoundRectangle2D.Float(0, 0, w - 1, h - 1, 10, 10);
			Graphics2D c = (Graphics2D) g2.create();
			c.clip(r);
			if (wood != null) c.drawImage(wood, 0, (h - 110) / 2, w, 110, null);
			c.setPaint(new GradientPaint(0, 0, new Color(0xb8, 0x87, 0x4e, 0x70), 0, h, new Color(0x3b, 0x27, 0x15, 0xc9)));
			c.fill(r);
			c.dispose();
			g2.setColor(new Color(0xd5ad6c));
			g2.draw(r);
			g2.setFont(Style.BUTTON.font(ui));
			FontMetrics fm = g2.getFontMetrics();
			String t = getText();
			int x = (w - fm.stringWidth(t)) / 2, y = (h + fm.getAscent()) / 2 - 2;
			g2.setColor(new Color(0x1b, 0x0d, 0x04, 0xc0));
			g2.drawString(t, x, y + 1);
			g2.setColor(new Color(0xffe4ac));
			g2.drawString(t, x, y);
			if (isFocusOwner()) {
				g2.setColor(AdventureUi.FOCUS);
				g2.setStroke(new java.awt.BasicStroke(2f));
				g2.drawRoundRect(1, 1, w - 3, h - 3, 10, 10);
			}
			g2.dispose();
		}
	}
}
