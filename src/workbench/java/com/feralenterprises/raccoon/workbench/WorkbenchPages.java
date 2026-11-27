package com.feralenterprises.raccoon.workbench;

import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.Divider;
import com.feralenterprises.raccoon.ui.component.EquipmentGrid;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.FilterList;
import com.feralenterprises.raccoon.ui.component.InventoryGrid;
import com.feralenterprises.raccoon.ui.component.ProgressBar;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.SectionHeader;
import com.feralenterprises.raccoon.ui.component.SegmentedSelector;
import com.feralenterprises.raccoon.ui.component.StateNotice;
import com.feralenterprises.raccoon.ui.component.StatusIndicator;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.component.Tone;
import com.feralenterprises.raccoon.ui.layout.ColumnsPanel;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.shell.Destination;
import com.feralenterprises.raccoon.ui.shell.Environment;
import com.feralenterprises.raccoon.ui.shell.Navigator;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;

/**
 * Builds the workbench: the real shell plus three workbench pages (components, gear grids, states) and the full
 * product destination list as structural placeholders. Holds references so controls and tests can drive updates.
 */
public final class WorkbenchPages {
	/** Simulator-only account source and scope for ungated fixture mode. */
	public static final Object FIXTURE_SOURCE = new Object() { @Override public String toString() { return "fixture-source"; } };
	public static final Object FIXTURE_SCOPE = new Object() { @Override public String toString() { return "fixture-scope"; } };

	public static final String COMPONENTS = "wb-components", GEAR = "wb-gear", STATES = "wb-states", SETTINGS = "settings";

	public final UiContext ctx;
	public final RaccoonShell shell;

	// live references for dynamic updates and tests
	public SegmentedSelector styleSelector, gearLoadoutSelector, statesSelector;
	public TextBlock styleReadout;
	public ProgressBar progress, goal, unavailable;
	public RButton advance, prepot, bank;
	public Expandable details;
	public FilterList filter;
	public EquipmentGrid equipment, statesEquipment;
	public InventoryGrid inventory;
	public StatusIndicator gearStatus, statesStatus;
	public ProgressBar statesProgress;
	private DataState gearState = DataState.ACTIVE;
	private final boolean accountGated;
	private long progressValue = 640;

	public WorkbenchPages(UiContext ctx) {
		this(ctx, false);
	}

	/**
	 * @param accountGated when true, account pages are built empty (UNAVAILABLE) and only show fixture data delivered
	 *                     through {@link #deliverFixture()} for a published scope, as production pages will.
	 */
	public WorkbenchPages(UiContext ctx, boolean accountGated) {
		this.ctx = ctx;
		this.accountGated = accountGated;
		if (accountGated) gearState = DataState.UNAVAILABLE;
		Navigator nav = new Navigator(destinations());
		shell = new RaccoonShell(ctx, nav);
		if (accountGated) shell.addClearListener(() -> gearState = DataState.UNAVAILABLE);
		shell.register(COMPONENTS, Environment.FULL, this::components);
		shell.register(GEAR, Environment.PLAIN, this::gear);
		shell.register(STATES, Environment.PLAIN, this::states);
		shell.register(SETTINGS, Environment.PLAIN, this::settings);
		if (!accountGated) {
			// Standalone simulator: a fixture source publishes one fixture scope. Gated mode waits for a real binding.
			shell.attachAccountSource(FIXTURE_SOURCE, null);
			shell.showAccount(FIXTURE_SOURCE, FIXTURE_SCOPE);
		}
	}

	public static List<Destination> destinations() {
		List<Destination> d = new ArrayList<>();
		// W1 is a static showcase (no account data); W2 and W3 behave like account pages.
		d.add(new Destination(COMPONENTS, "Workbench: Components", "W1", Destination.Group.WORKBENCH, false, false));
		d.add(new Destination(GEAR, "Workbench: Gear grids", "W2", Destination.Group.WORKBENCH, false, true));
		d.add(new Destination(STATES, "Workbench: States", "W3", Destination.Group.WORKBENCH, false, true));
		String[][] product = {
			{"home", "Home", "HM"}, {"outfitter", "Outfitter", "OF"}, {"activity", "Current Activity", "CA"},
			{"bank", "Bank Memory", "BM"}, {"capture", "Account Capture & Calibration", "AC"},
			{"files", "Feral Files", "FF"}, {"chronicle", "Boss Chronicle", "BC"}, {"pathfinder", "Pathfinder", "PF"},
			{"quests", "Quests & Diaries", "QD"}, {"supplies", "Stored Supplies / XP Stash", "SS"},
			{"friends", "Feral Friends", "FR"}, {"bounties", "Bandit's Bounties", "BB"}};
		for (String[] p : product) d.add(new Destination(p[0], p[1], p[2], Destination.Group.PRODUCT, false));
		// Owner decision (roadmap #1): Tools is removed from the rail; it stays registered (disposition open).
		d.add(new Destination("tools", "Tools", "TL", Destination.Group.PRODUCT, false, true, false));
		d.add(new Destination("gim", "GIM Hub", "GH", Destination.Group.PRODUCT, true));
		d.add(new Destination(SETTINGS, "Settings", "SE", Destination.Group.UTILITY, false));
		return d;
	}

	private JComponent components() {
		StackPanel s = new StackPanel(Tokens.GAP);
		s.add(new SectionHeader(ctx, "Shared components", ArtKey.CHARACTER_BRAND, Divider.Kind.PAW));

		Card buttons = new Card(ctx, "Buttons");
		ColumnsPanel actions = new ColumnsPanel(2, Tokens.GAP);
		prepot = new RButton(ctx, "Prepot").withIcon(ArtKey.ICON_PREPOT);
		bank = new RButton(ctx, "Bank").withIcon(ArtKey.ICON_BANK);
		actions.add(prepot);
		actions.add(bank);
		ColumnsPanel states = new ColumnsPanel(3, Tokens.GAP_SMALL);
		RButton normal = new RButton(ctx, "Normal"), selected = new RButton(ctx, "Selected"), disabled = new RButton(ctx, "Disabled");
		selected.setSelected(true);
		disabled.setEnabled(false);
		states.add(normal); states.add(selected); states.add(disabled);
		buttons.addAll(actions, states);
		s.add(buttons);

		Card selector = new Card(ctx, "Selector");
		styleSelector = new SegmentedSelector(ctx, "Melee", "Ranged", "Magic");
		styleReadout = new TextBlock(ctx, "Selected: Melee", FontRole.SMALL, Tone.MUTED);
		styleSelector.addSelectionListener(i -> styleReadout.setText("Selected: " + styleSelector.selectedOption()));
		selector.addAll(styleSelector, styleReadout);
		s.add(selector);

		Card bars = new Card(ctx, "Progress");
		progress = new ProgressBar(ctx, Fixtures.progress("Example progress", progressValue, 1000, DataState.ACTIVE), ProgressBar.Variant.PROGRESS);
		goal = new ProgressBar(ctx, Fixtures.progress("Example goal", 28, 100, DataState.ACTIVE), ProgressBar.Variant.GOAL);
		unavailable = new ProgressBar(ctx, Fixtures.progress("Unavailable value", 0, 0, DataState.UNAVAILABLE), ProgressBar.Variant.PROGRESS);
		advance = new RButton(ctx, "Advance example (+90)");
		advance.addActionListener(e -> tick());
		bars.addAll(progress, goal, unavailable, advance);
		s.add(bars);

		Card status = new Card(ctx, "Status and evidence");
		status.addAll(new StatusIndicator(ctx, DataState.ACTIVE, Fixtures.evidence(DataState.ACTIVE)),
			new StatusIndicator(ctx, DataState.STALE, Fixtures.evidence(DataState.STALE)));
		s.add(status);

		details = new Expandable(ctx, "More detail", new TextBlock(ctx,
			"Progressive disclosure: the short answer stays visible and the explanation opens on request.",
			FontRole.SMALL, Tone.MUTED));
		Card disclose = new Card(ctx, null);
		disclose.addAll(details);
		s.add(disclose);

		Card search = new Card(ctx, "Search and filter");
		filter = new FilterList(ctx, "Search examples", Arrays.asList("Dragon scimitar", "Dragon boots", "Fire cape",
			"Helm of neitiznot", "Super combat potion(4)"));
		search.addAll(filter);
		s.add(search);

		s.add(new Divider(ctx, Divider.Kind.DIAMOND));
		Card notice = new Card(ctx, null);
		notice.addAll(new StateNotice(ctx, StateNotice.Presence.SIGNED_OUT));
		s.add(notice);
		s.add(new Divider(ctx, Divider.Kind.PLAIN));
		s.add(new TextBlock(ctx, "All values on this page are fixtures. " + ctx.assets().provenance(), FontRole.SMALL, Tone.MUTED));
		return s;
	}

	private JComponent gear() {
		StackPanel s = new StackPanel(Tokens.GAP);
		s.add(new SectionHeader(ctx, "Equipment", null, Divider.Kind.PLAIN));
		gearStatus = new StatusIndicator(ctx, gearState, Fixtures.evidence(gearState));
		s.add(gearStatus);
		gearLoadoutSelector = new SegmentedSelector(ctx, "Fixture A", "Fixture B");
		gearLoadoutSelector.addSelectionListener(i -> applyGear());
		s.add(gearLoadoutSelector);
		equipment = new EquipmentGrid(ctx, Fixtures.equipment(gearState, 0));
		s.add(equipment);
		s.add(new SectionHeader(ctx, "Inventory", null, Divider.Kind.PLAIN));
		inventory = new InventoryGrid(ctx, Fixtures.inventory(gearState, 0));
		s.add(inventory);
		s.add(new TextBlock(ctx, "Item images: prior RuneLite cache captures (FIXTURE). Slot icons and slot backgrounds "
			+ "come from RuneLite at runtime and show labelled fallbacks here.", FontRole.SMALL, Tone.MUTED));
		return s;
	}

	private JComponent states() {
		StackPanel s = new StackPanel(Tokens.GAP);
		s.add(new TextBlock(ctx, "Every data-bearing component supports the same five states.", FontRole.SMALL, Tone.MUTED));
		statesSelector = new SegmentedSelector(ctx, "Active", "Empty", "Loading", "Unavailable", "Stale");
		s.add(statesSelector);
		DataState first = accountGated ? DataState.UNAVAILABLE : DataState.ACTIVE;
		statesStatus = new StatusIndicator(ctx, first, Fixtures.evidence(first));
		statesProgress = new ProgressBar(ctx, Fixtures.progress("Example progress", 640, 1000, first), ProgressBar.Variant.PROGRESS);
		statesEquipment = new EquipmentGrid(ctx, Fixtures.equipment(first, 0));
		Card c = new Card(ctx, "Selected state");
		c.addAll(statesStatus, statesProgress, statesEquipment);
		s.add(c);
		statesSelector.addSelectionListener(i -> {
			DataState st = DataState.values()[i];
			statesStatus.set(st, Fixtures.evidence(st));
			statesProgress.setModel(Fixtures.progress("Example progress", 640, 1000, st));
			statesEquipment.setModel(Fixtures.equipment(st, 0));
		});
		for (DataState st : DataState.values()) {
			Card k = new Card(ctx, null);
			k.addAll(new StatusIndicator(ctx, st, Fixtures.evidence(st)),
				new ProgressBar(ctx, Fixtures.progress(st.label() + " example", 640, 1000, st), ProgressBar.Variant.PROGRESS));
			s.add(k);
		}
		return s;
	}

	private JComponent settings() {
		StackPanel s = new StackPanel(Tokens.GAP);
		Card c = new Card(ctx, "Settings");
		c.addAll(new TextBlock(ctx, "Reached from the header control only, so it never appears twice. Text size and "
			+ "skin are driven by the workbench controls in this prototype.", FontRole.SMALL, Tone.MUTED));
		s.add(c);
		return s;
	}

	/** Dynamic update through read models: advances the progress example and swaps the gear loadout. */
	public void tick() {
		progressValue = progressValue >= 1000 ? 100 : Math.min(1000, progressValue + 90);
		if (progress != null) progress.setModel(Fixtures.progress("Example progress", progressValue, 1000, DataState.ACTIVE));
		if (gearLoadoutSelector != null) gearLoadoutSelector.setSelected((gearLoadoutSelector.selected() + 1) % 2);
	}

	public long progressValue() { return progressValue; }

	public boolean accountGated() { return accountGated; }

	/** Delivers the fixture account values (called only after the integration gate accepted the scope). */
	public void deliverFixture() {
		setGearState(DataState.ACTIVE);
		if (statesSelector != null) statesSelector.setSelected(0);
		if (statesStatus != null) {
			statesStatus.set(DataState.ACTIVE, Fixtures.evidence(DataState.ACTIVE));
			statesProgress.setModel(Fixtures.progress("Example progress", 640, 1000, DataState.ACTIVE));
			statesEquipment.setModel(Fixtures.equipment(DataState.ACTIVE, 0));
		}
	}

	public void setGearState(DataState s) {
		gearState = s;
		applyGear();
	}

	private void applyGear() {
		if (equipment == null) return;
		int loadout = gearLoadoutSelector.selected();
		gearStatus.set(gearState, Fixtures.evidence(gearState));
		equipment.setModel(Fixtures.equipment(gearState, loadout));
		inventory.setModel(Fixtures.inventory(gearState, loadout));
	}
}
