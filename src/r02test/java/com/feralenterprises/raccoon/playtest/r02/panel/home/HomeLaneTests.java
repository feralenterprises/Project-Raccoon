package com.feralenterprises.raccoon.playtest.r02.panel.home;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.ActivityObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.CaptureState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.accessibility.Accessible;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Lane A Home regressions on the accepted shared composition (no RuneLite, simulated accounts). Args: the accepted
 * Lane Inputs ZIP, the extracted frozen {@code Project-Raccoon-R02/}, optional evidence directory for offscreen PNGs.
 */
public final class HomeLaneTests {
	private static int checks, failures;
	private static final java.time.Instant T0 = TestFixtures.T0;

	private final HomeInputs inputs;
	private final R02Assets assets;
	private final UiContext ui;
	private final FixtureDispatcher fixtures;
	private final R02Composition comp;
	private final List<FixtureAction> actions = new ArrayList<>();
	private final Path evidence;
	private int generation;
	private long revision = 1;

	private HomeLaneTests(HomeInputs inputs, R02Assets assets, Path evidence, FixtureSnapshot first) {
		this(inputs, assets, evidence, first, null);
	}

	/** {@code environment}: frozen art for the shared host's environment keys (PR-xxx/SIG-xx), or null for none. */
	private HomeLaneTests(HomeInputs inputs, R02Assets assets, Path evidence, FixtureSnapshot first, FrozenPackageAssets environment) {
		this.inputs = inputs;
		this.assets = assets;
		this.evidence = evidence;
		ui = new UiContext(new DefaultFontSource(), new AssetProvider() {
			public BufferedImage art(ArtKey key) {
				String f = key.candidateFile();
				return environment == null || !f.matches("(PR|SIG)-\\d+_.*") ? null : environment.art(f.substring(0, f.indexOf('_')));
			}
			public BufferedImage nativeSprite(NativeSprite sprite, Runnable onLoaded) { return null; }
			public BufferedImage item(int itemId, int quantity, Runnable onLoaded) { return null; }
			public String provenance() { return "Headless Lane A"; }
		});
		fixtures = new FixtureDispatcher(TestFixtures.registry(), TestFixtures.clock());
		fixtures.setActionHandler(actions::add);
		List<RouteRegistration> pages = new ArrayList<>();
		pages.add(HomePanels.registration(inputs));
		for (RouteId r : new RouteId[] {RouteId.OUTFIT, RouteId.CHRONICLE, RouteId.ACTIVITY, RouteId.FILES, RouteId.CAPTURE})
			pages.add(new RouteRegistration(r, Lane.owning(r).name(), c -> new JLabel(r.label() + " test receiver")));
		comp = new R02Composition(ui, fixtures, assets, pages);
		generation = fixtures.bind();
		comp.start(first == null ? TestFixtures.demoA(generation, revision) : first);
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) throw new IllegalArgumentException("Lane Inputs ZIP and extracted Project-Raccoon-R02 required");
		Path zip = Paths.get(args[0]), frozen = Paths.get(args[1]);
		Path evidence = args.length > 2 ? Paths.get(args[2]) : null;

		HomeInputs inputs = HomeInputs.load(zip);
		check("accepted lane-input archive verifies (" + inputs.lane().verifiedCount() + " files)", inputs.lane().verifiedCount() == 939);
		check("every frozen Home raster resolves from verified inputs", inputs.stone != null && inputs.wood != null
			&& inputs.plaster != null && inputs.ivyLeft != null && inputs.opportunitiesIcon != null && inputs.snapshotIcon != null);
		check("milestone and raw-tuna parameters come from A/home-and-tools.json", inputs.milestoneTarget == 10
			&& "scurrius".equals(inputs.milestoneEncounter) && inputs.tunaItem == 359 && inputs.tunaRequired == 27 && inputs.tunaXpTotal == 2700);
		rejectsTamperedInputs(zip);
		check("registration is owned by Lane A (Adventure) for Home", HomePanels.registration(inputs).route() == RouteId.HOME
			&& HomePanels.registration(inputs).owner().equals(Lane.ADVENTURE.name()));

		FrozenPackageAssets real = new FrozenPackageAssets(frozen);
		check("authentic skill icons and item sprites resolve", real.skillIcon("cooking") != null && real.skillIcon("attack") != null
			&& real.item(5295) != null && real.item(21512) != null && real.item(995) != null && real.portrait("vorkath") != null);

		modelRules(inputs);

		SwingUtilities.invokeAndWait(() -> {
			HomeLaneTests t = new HomeLaneTests(inputs, real, evidence, null, real);
			try {
				t.mounted();
				t.navigation();
				t.profile();
				t.sameScope();
				t.scopeIsolation();
				t.layout();
				t.screenshots();
			} finally { t.comp.dispose(); }
			HomeLaneTests none = new HomeLaneTests(inputs, R02Assets.none(), null, null);
			try {
				none.size(225, 900);
				check("unresolved sprites keep an accessible text fallback",
					none.accessibleNames().stream().anyMatch(n -> n.contains("Herb run") && n.contains("image unavailable")));
			} finally { none.comp.dispose(); }
		});
		System.out.println("Home lane: " + (checks - failures) + "/" + checks + " checks passed");
		if (failures > 0) System.exit(1);
	}

	// ------------------------------------------------------------------ model (pure evidence rules)

	private static void modelRules(HomeInputs in) {
		com.feralenterprises.raccoon.playtest.r02.fixture.Registry reg = TestFixtures.registry();
		HomeModel a = HomeModel.of(TestFixtures.demoA(1, 1), reg, in);
		check("C-04 Home uses official Scurrius KC 12, not an example 8", a.kc == 12 && a.goalValue.equals("12 / 10 kills · demo goal")
			&& a.goalRemaining.equals("Goal complete"));
		check("C-04 completed goal opens the boss record", a.primaryRoute == RouteId.CHRONICLE && a.primaryLabel.equals("View boss record ›"));
		HomeModel b = HomeModel.of(TestFixtures.demoB(1, 1), reg, in);
		check("Account B official KC 3 leaves 7 and prepares a loadout", b.kc == 3 && b.goalRemaining.equals("7 kills remaining")
			&& b.primaryRoute == RouteId.OUTFIT && b.primaryLabel.equals("Prepare loadout ›"));
		for (Evidence<Integer> e : Arrays.asList(Evidence.<Integer>unknown(), Evidence.<Integer>unavailable(),
			Evidence.stale(12, T0, "saved"), Evidence.<Integer>absent(T0, "kill log"))) {
			HomeModel m = HomeModel.of(account("A").officialKc("scurrius", e).build(), reg, in);
			check("KC " + e.status() + " is never shown as a count", m.kc == null && m.goalValue.equals("Kill count unavailable")
				&& m.goalRemaining.equals("Waiting for a kill-log observation") && m.fraction() == 0 && m.primaryRoute == RouteId.CHRONICLE);
		}
		HomeModel zero = HomeModel.of(account("A").officialKc("scurrius", Evidence.count(0, T0, "kill log")).build(), reg, in);
		check("verified zero KC is a real count", zero.kc == 0 && zero.goalValue.startsWith("0 / 10") && zero.goalRemaining.equals("10 kills remaining"));
		HomeModel archivedKills = HomeModel.of(account("A").archive(Evidence.verified(true, T0, "archive"),
			Collections.singletonList(record("a9", "scurrius", "scurrius", 12, "kills", 1000L))).build(), reg, in);
		check("C-10 archived trip kills never become official KC", archivedKills.kc == null);

		check("C-05 current bank without raw tuna: fewer than 27, no cooking claim",
			a.tuna.equals("Verified bank has fewer than 27 raw tuna"));
		check("C-05 current bank with 30 raw tuna recommends the cook",
			tuna(in, ContainerState.CURRENT, 30L).equals("Cook 27 tuna · 2,700 XP"));
		check("C-05 historical bank is only a saved observation", tuna(in, ContainerState.HISTORICAL, 30L).equals("Raw tuna · only a saved observation"));
		check("C-05 incomplete bank without tuna is unknown, never fewer-than", tuna(in, ContainerState.INCOMPLETE, null).equals("Raw tuna availability unknown"));
		check("C-05 incomplete bank with 5 tuna is unknown, never fewer-than", tuna(in, ContainerState.INCOMPLETE, 5L).equals("Raw tuna availability unknown"));
		check("C-05 incomplete bank with 27 tuna still proves enough", tuna(in, ContainerState.INCOMPLETE, 27L).equals("Cook 27 tuna · 2,700 XP"));
		check("C-05 unavailable bank is unknown", tuna(in, ContainerState.UNAVAILABLE, null).equals("Raw tuna availability unknown")
			&& HomeModel.tuna(null, in).equals("Raw tuna availability unknown"));

		check("C-08 Vorkath replay is a saved session replay with saved XP, never current login", a.recent.size() == 1
			&& a.recent.get(0).title.equals("Vorkath") && a.recent.get(0).subtitle.equals(HomeModel.SAVED_REPLAY)
			&& a.recent.get(0).end.equals("38,400 XP") && a.recent.get(0).route == RouteId.ACTIVITY);
		FixtureSnapshot live = account("A").activity(new ActivityObservation(ActivityObservation.Mode.IDLE, "cooking", null, T0, T0, 0,
			Evidence.unavailable())).archive(Evidence.verified(true, T0, "archive"), Arrays.asList(
			record("a6", "cooking", null, 84, "fish cooked", 10_080L), record("a1", "vorkath", "vorkath", 12, "kills", 38_400L),
			record("a3", "fishing", null, 96, "fish caught", 8_640L))).build();
		HomeModel l = HomeModel.of(live, reg, in);
		check("C-09 current-login Cooking shows no XP without a live XP observation", l.recent.get(0).subtitle.equals(HomeModel.CURRENT)
			&& l.recent.get(0).end.equals("— XP") && !l.recent.get(0).end.contains("8,410"));
		check("saved sessions stay saved and are capped at two", l.recent.size() == 3 && l.recent.get(1).subtitle.equals(HomeModel.SAVED)
			&& l.recent.get(2).subtitle.equals(HomeModel.SAVED) && l.recent.get(1).payload.equals("a6") && l.recent.get(2).payload.equals("a1"));
		check("saved rows open the typed Feral Files session", l.recent.get(1).route == RouteId.FILES
			&& l.recent.get(1).param == RouteRequest.Param.SESSION);
		FixtureSnapshot replayFirst = account("A").activity(new ActivityObservation(ActivityObservation.Mode.REPLAY, "vorkath", "a1", T0, T0, 0,
			Evidence.unavailable())).archive(Evidence.verified(true, T0, "archive"), Arrays.asList(
			record("a1", "vorkath", "vorkath", 12, "kills", 38_400L), record("a2", "barrows", "barrows", 8, "chests", 12_600L),
			record("a3", "fishing", null, 96, "fish caught", 8_640L))).build();
		HomeModel rf = HomeModel.of(replayFirst, reg, in);
		check("replayed record is not repeated in saved sessions (frozen order Vorkath, Barrows, Fishing)", rf.recent.size() == 3
			&& rf.recent.get(0).title.equals("Vorkath") && rf.recent.get(1).title.equals("Barrows") && rf.recent.get(2).title.equals("Fishing"));
		check("C-11 Barrows row carries saved XP only, never a kill or chest claim", rf.recent.get(1).end.equals("12,600 XP")
			&& !rf.recent.get(1).subtitle.toLowerCase().contains("kill"));
		boolean refused;
		try { account("A").archive(Evidence.<Boolean>unknown(), Collections.singletonList(record("a2", "barrows", "barrows", 8, "chests", 12_600L))); refused = false; }
		catch (IllegalArgumentException e) { refused = true; }
		HomeModel hidden = HomeModel.of(account("A").archive(Evidence.<Boolean>unknown(), Collections.emptyList()).build(), reg, in);
		check("unverified archive yields no saved sessions (shared fixture also refuses records there)", refused && hidden.recent.isEmpty());
		check("unknown XP stays unknown", HomeModel.of(account("A").archive(Evidence.verified(true, T0, "archive"), Collections.singletonList(
			record("a4", "giant-mole", "giant-mole", 1, "kills", null))).build(), reg, in).recent.get(0).end.equals("— XP"));

		check("C-06 no quest observation is Unknown", a.questProgress.equals("Unknown"));
		check("C-06 partial quest capture is Partial, never complete", HomeModel.of(account("A").capture("quests",
			Evidence.verified(CaptureState.PARTIAL, T0, "capture")).build(), reg, in).questProgress.equals("Partial"));
		check("C-06 pending quest capture stays Unknown", HomeModel.of(account("A").capture("quests",
			Evidence.verified(CaptureState.PENDING, T0, "capture")).build(), reg, in).questProgress.equals("Unknown"));
		check("C-07 total and combat level stay unavailable without an observation", a.totalLevel.equals("Unavailable") && a.combatLevel.equals("Unavailable"));

		HomeModel out = HomeModel.of(TestFixtures.signedOut(1, 2), reg, in);
		check("signed out: no KC, bank, activity or quest value", out.kc == null && out.tuna.equals("Raw tuna availability unknown")
			&& out.recent.isEmpty() && out.questProgress.equals("Unknown"));
	}

	private static FixtureSnapshot.Builder account(String a) {
		return FixtureSnapshot.builder(AccountScope.of(a, 1)).generation(1).revision(1);
	}

	private static ArchiveRecord record(String id, String activity, String encounter, int units, String unit, Long xp) {
		return new ArchiveRecord(id, activity, encounter, T0, Duration.ofMinutes(30), units, unit,
			xp == null ? Evidence.unavailable() : Evidence.verified(xp, T0, "saved"), Collections.emptyMap());
	}

	private static String tuna(HomeInputs in, ContainerState state, Long qty) {
		if (state == ContainerState.UNAVAILABLE) return HomeModel.tuna(ContainerObservation.unavailable(Container.BANK), in);
		Map<Integer, Long> items = new LinkedHashMap<>();
		if (qty != null) items.put(359, qty);
		items.put(995, 1L);
		return HomeModel.tuna(new ContainerObservation(Container.BANK, state, T0, "test", items), in);
	}

	private static void rejectsTamperedInputs(Path zip) throws IOException {
		Path dir = Files.createTempDirectory("lane-a-tamper");
		Path bad = dir.resolve("inputs.zip");
		byte[] b = Files.readAllBytes(zip);
		b[b.length / 2] ^= 1;
		Files.write(bad, b);
		boolean refused;
		try { HomeInputs.load(bad); refused = false; } catch (IOException e) { refused = e.getMessage().contains("SHA-256"); }
		check("tampered lane-input archive is refused", refused);
	}

	// ------------------------------------------------------------------ mounted page

	private HomePanel page() { return find(comp.shell(), HomePanel.class); }

	private void mounted() {
		size(225, 900);
		check("composition mounts Lane A Home with no unregistered Lane A route", comp.navigator().current().equals("home")
			&& page() != null && !comp.unregisteredRoutes().contains(RouteId.HOME));
		check("mounted Home shows official KC and the completed goal", text("home-goal-value").equals("12 / 10 kills · demo goal")
			&& text("home-goal-remaining").equals("Goal complete"));
		AdventureUi.Meter meter = (AdventureUi.Meter) named("home-goal-meter");
		check("goal meter is full and accessible", meter.fraction() == 1.0
			&& meter.getAccessibleContext().getAccessibleValue().getCurrentAccessibleValue().intValue() == 10);
		check("mounted Cooking row uses current bank evidence", ((AdventureUi.Row) named("home-cooking")).subtitle()
			.equals("Verified bank has fewer than 27 raw tuna"));
		check("mounted Recent Activity row is the saved replay", accessible("home-recent-activity").startsWith("Vorkath, Saved session replay, 38,400 XP"));
		AdventureUi.Row cooking = (AdventureUi.Row) named("home-cooking");
		cooking.dispatchEvent(new java.awt.event.MouseEvent(cooking, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false));
		check("opportunity row opens its frozen detail copy", cooking.open()
			&& cooking.getAccessibleContext().getAccessibleDescription().startsWith("Requires 27 raw tuna for 2,700 XP"));
		AdventureUi.Section recurring = (AdventureUi.Section) named("home-recurring");
		recurring.band.dispatchEvent(new java.awt.event.MouseEvent(recurring.band, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false));
		check("Recurring Activities collapses from its header", !recurring.expanded());
		recurring.setExpanded(true);
	}

	private void navigation() {
		click("home-primary");
		check("completed goal navigates to the typed Scurrius boss record", comp.navigator().currentRequest()
			.equals(RouteRequest.of("chronicle", RouteRequest.Param.ENCOUNTER, "scurrius")));
		comp.shell().header().back().doClick();
		check("Back returns to the same Home page", comp.navigator().current().equals("home") && page() != null);
		AdventureUi.Row activity = (AdventureUi.Row) named("home-recent-activity");
		activity.dispatchEvent(new java.awt.event.MouseEvent(activity, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false));
		check("saved replay row opens Current Activity", comp.navigator().current().equals("activity"));
		comp.shell().header().back().doClick();
		click("home-dismiss");
		check("dismissal hides the lead and dispatches the typed fixture action", !shown("home-primary")
			&& dispatched("DISMISS_OPPORTUNITY:" + HomePanel.OPPORTUNITY_ID));
		click("home-restore");
		check("restore brings the lead back", shown("home-primary")
			&& dispatched("RESTORE_OPPORTUNITIES"));
	}

	private void profile() {
		click("home-profile-action");
		LocalBack home = page();
		check("profile opens as a Home view with local Back", home.hasLocalBack() && comp.navigator().current().equals("home")
			&& find(comp.shell(), HomePanel.class) == home
			&& shown("home-profile") && !shown("home-footer"));
		check("profile shows the same scoped snapshot", texts(named("profile-snapshot-grid")).contains("Unknown"));
		click("profile-capture");
		check("profile links to Account Capture", comp.navigator().current().equals("capture"));
		comp.shell().header().back().doClick();
		check("returning keeps the profile view", page().hasLocalBack());
		comp.shell().header().back().doClick();
		check("header Back closes the profile first and stays on Home", !page().hasLocalBack()
			&& comp.navigator().current().equals("home") && shown("home-footer"));
	}

	private void sameScope() {
		click("home-dismiss");
		AdventureUi.Row cooking = (AdventureUi.Row) named("home-cooking");
		FixtureSnapshot more = TestFixturesWithTuna.demoA(generation, ++revision, 30);
		boolean published = fixtures.publish(more);
		check("same-scope revision updates evidence in place", published && cooking == named("home-cooking")
			&& cooking.subtitle().equals("Cook 27 tuna · 2,700 XP"));
		check("same-scope revision keeps local dismissal and open detail", !shown("home-primary") && cooking.open());
		boolean stale = fixtures.publish(TestFixtures.demoA(generation, revision - 1));
		check("an older revision is refused and changes nothing", !stale && cooking.subtitle().equals("Cook 27 tuna · 2,700 XP"));
		click("home-restore");
	}

	private void scopeIsolation() {
		HomePanel before = page();
		generation = fixtures.bind();
		boolean ok = fixtures.publish(TestFixtures.demoB(generation, 1));
		size(225, 900);
		HomePanel after = page();
		check("a new account scope discards the A page", ok && after != null && after != before);
		check("Account B shows only B evidence", text("home-goal-value").equals("3 / 10 kills · demo goal")
			&& text("home-goal-remaining").equals("7 kills remaining") && texts(comp.shell()).stream().noneMatch(t -> t.contains("Vorkath"))
			&& ((AdventureUi.Row) named("home-cooking")).subtitle().equals("Verified bank has fewer than 27 raw tuna"));
		check("Account B has no activity evidence", named("home-recent-unavailable") != null);
		check("A's dismissal and open rows do not carry into B", shown("home-primary") && !((AdventureUi.Row) named("home-cooking")).open());
		check("B's primary action prepares the Scurrius loadout", ((AbstractButton) named("home-primary")).getText().equals("Prepare loadout ›"));
		click("home-primary");
		check("B navigates to the typed Scurrius Outfitter", comp.navigator().currentRequest()
			.equals(RouteRequest.of("outfit", RouteRequest.Param.ENCOUNTER, "scurrius")));
		generation = fixtures.bind();
		fixtures.publish(TestFixtures.signedOut(generation, 2));
		size(225, 900);
		check("sign-out resets to Home with nothing account-specific", comp.navigator().current().equals("home")
			&& text("home-goal-value").equals("Kill count unavailable") && named("home-recent-unavailable") != null);
		generation = fixtures.bind();
		fixtures.publish(TestFixtures.demoA(generation, 1));
		size(225, 900);
	}

	private void layout() {
		for (TextScale scale : TextScale.values()) {
			ui.setTextScale(scale);
			for (int width : new int[] {225, 200}) {
				size(width, 900);
				List<String> issues = new ArrayList<>();
				audit(page(), issues);
				check(scale + " text at " + width + "px: no clipped or overflowing Home component " + issues, issues.isEmpty());
			}
		}
		ui.setTextScale(TextScale.DEFAULT);
		size(225, 900);
	}

	private static void audit(java.awt.Container c, List<String> issues) {
		for (Component child : c.getComponents()) {
			if (!child.isVisible()) continue;
			if (child.getX() < 0 || child.getX() + child.getWidth() > c.getWidth() + 1)
				issues.add(child.getName() + " overflows " + c.getName());
			if (child instanceof HeightForWidth && child.getWidth() > 0) {
				HeightForWidth h = (HeightForWidth) child;
				if (child.getHeight() + 1 < h.heightForWidth(child.getWidth())) issues.add(child.getName() + " clipped vertically");
				if (h.minimumContentWidth() > child.getWidth() + 1) issues.add(child.getName() + " narrower than its content");
			}
			if (child instanceof java.awt.Container) audit((java.awt.Container) child, issues);
		}
	}

	private void screenshots() {
		shot("01-home-demo-a.png", TestFixtures.demoA(generation, ++revision));
		FixtureSnapshot frozenLike = FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(++revision)
			.container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "Frozen-equivalent", Collections.singletonMap(995, 2_350_840L)))
			.officialKc("scurrius", Evidence.count(12, T0, "Frozen-equivalent"))
			.capture("quests", Evidence.verified(CaptureState.PARTIAL, T0, "Frozen-equivalent"))
			.archive(Evidence.verified(true, T0, "Frozen-equivalent"), Arrays.asList(
				record("a1", "vorkath", "vorkath", 12, "kills", 38_400L), record("a2", "barrows", "barrows", 8, "chests", 12_600L),
				record("a3", "fishing", null, 96, "fish caught", 8_640L)))
			.activity(new ActivityObservation(ActivityObservation.Mode.REPLAY, "vorkath", "a1", T0, T0, 0, Evidence.unavailable()))
			.build();
		shot("02-home-frozen-equivalent.png", frozenLike);
		check("frozen-equivalent Recent Activity matches the frozen rows", accessible("home-recent-activity").startsWith("Vorkath, Saved session replay, 38,400 XP")
			&& accessible("home-recent-a2").startsWith("Barrows, Saved session, 12,600 XP") && accessible("home-recent-a3").startsWith("Fishing, Saved session, 8,640 XP")
			&& texts(named("home-snapshot-grid")).contains("Partial"));
		check("frozen record icons resolve (Vorkath portrait, Barrows portrait, lobster sprite)",
			!accessible("home-recent-activity").contains("image unavailable") && !accessible("home-recent-a2").contains("image unavailable")
			&& !accessible("home-recent-a3").contains("image unavailable"));
		ui.setTextScale(TextScale.LARGE);
		shot("03-home-large-text.png", null);
		ui.setTextScale(TextScale.DEFAULT);
		click("home-profile-action");
		shot("04-home-profile.png", null);
		comp.shell().header().back().doClick();
		generation = fixtures.bind();
		fixtures.publish(TestFixtures.demoB(generation, 1));
		shot("05-home-demo-b.png", null);
		generation = fixtures.bind();
		fixtures.publish(TestFixtures.signedOut(generation, 3));
		shot("06-home-signed-out.png", null);
	}

	private void shot(String name, FixtureSnapshot s) {
		if (s != null) fixtures.publish(s);
		if (evidence == null) return;
		size(225, 900);
		int h = Math.max(900, comp.shell().getPreferredSize().height);
		BufferedImage image = new BufferedImage(comp.shell().getWidth(), comp.shell().getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		try {
			comp.shell().printAll(g);
			Files.createDirectories(evidence);
			ImageIO.write(image, "png", evidence.resolve(name).toFile());
			// full page (unscrolled) for review of everything below the fold
			HomePanel p = page();
			if (p != null) {
				int ph = p.heightForWidth(p.getWidth());
				BufferedImage full = new BufferedImage(p.getWidth(), ph, BufferedImage.TYPE_INT_RGB);
				p.setSize(p.getWidth(), ph);
				layoutAll(p);
				Graphics2D fg = full.createGraphics();
				p.printAll(fg);
				fg.dispose();
				ImageIO.write(full, "png", evidence.resolve(name.replace(".png", "-full-page.png")).toFile());
				size(225, 900);
			}
		} catch (IOException e) {
			throw new AssertionError(e);
		} finally { g.dispose(); }
	}

	// ------------------------------------------------------------------ helpers

	private void size(int contentWidth, int height) {
		comp.shell().setSize(contentWidth + 43, height);
		layoutAll(comp.shell());
		layoutAll(comp.shell());
	}

	private static void layoutAll(java.awt.Container parent) {
		parent.doLayout();
		for (Component child : parent.getComponents()) if (child instanceof java.awt.Container) layoutAll((java.awt.Container) child);
	}

	@SuppressWarnings("unchecked")
	private static <T> T find(Component c, Class<T> type) {
		if (type != null && type.isInstance(c)) return (T) c;
		if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
			T t = find(k, type);
			if (t != null) return t;
		}
		return null;
	}

	private JComponent named(String name) {
		JComponent c = named(comp.shell(), name);
		return c;
	}

	private static JComponent named(Component c, String name) {
		if (name.equals(c.getName()) && c instanceof JComponent) return (JComponent) c;
		if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
			JComponent j = named(k, name);
			if (j != null) return j;
		}
		return null;
	}

	/** FixtureAction has no value equality; compare its stable text form (kind[:target]). */
	private boolean dispatched(String action) { return actions.stream().anyMatch(a -> a.toString().equals(action)); }

	/** Visible along its whole ancestor chain (headless components never report isShowing). */
	private boolean shown(String name) {
		Component c = named(name);
		if (c == null) return false;
		for (; c != null && c != comp.shell(); c = c.getParent()) if (!c.isVisible()) return false;
		return c == comp.shell();
	}

	private String text(String name) { return ((AdventureUi.Label) named(name)).text(); }

	private String accessible(String name) {
		JComponent c = named(name);
		return c == null ? "<missing " + name + ">" : c.getAccessibleContext().getAccessibleName();
	}

	private List<String> accessibleNames() {
		List<String> out = new ArrayList<>();
		collect(comp.shell(), out);
		return out;
	}

	private static void collect(Component c, List<String> out) {
		if (!c.isVisible()) return;
		if (c instanceof Accessible || c instanceof JComponent) {
			javax.accessibility.AccessibleContext ac = c instanceof JComponent ? ((JComponent) c).getAccessibleContext()
				: ((Accessible) c).getAccessibleContext();
			String n = ac == null ? null : ac.getAccessibleName();
			if (n != null) out.add(n);
		}
		if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) collect(k, out);
	}

	private static List<String> texts(Component root) {
		List<String> out = new ArrayList<>();
		collectTexts(root, out);
		return out;
	}

	private static void collectTexts(Component c, List<String> out) {
		if (!c.isVisible()) return;
		if (c instanceof AdventureUi.Label) out.add(((AdventureUi.Label) c).text());
		if (c instanceof AdventureUi.Row) out.add(((AdventureUi.Row) c).getAccessibleContext().getAccessibleName());
		if (c instanceof AbstractButton) out.add(((AbstractButton) c).getText());
		if (c instanceof JLabel) out.add(((JLabel) c).getText());
		if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) collectTexts(k, out);
	}

	private void click(String name) {
		JComponent c = named(name);
		if (c == null) throw new AssertionError("missing component " + name);
		((AbstractButton) c).doClick();
		size(225, 900);
	}

	private static void check(String name, boolean ok) {
		checks++;
		if (!ok) failures++;
		System.out.println((ok ? "  PASS " : "  FAIL ") + name);
	}

	/** demoA with a raw-tuna quantity (same scope and generation): a same-scope revision. */
	private static final class TestFixturesWithTuna {
		static FixtureSnapshot demoA(int generation, long revision, long tuna) {
			FixtureSnapshot a = TestFixtures.demoA(generation, revision);
			Map<Integer, Long> items = new LinkedHashMap<>(a.container(Container.BANK).items());
			items.put(359, tuna);
			FixtureSnapshot.Builder b = FixtureSnapshot.builder(a.scope()).generation(generation).revision(revision)
				.container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "Demo A fixture", items))
				.officialKc("scurrius", a.officialKc("scurrius")).officialKc("vorkath", a.officialKc("vorkath"))
				.archive(a.archiveAvailable(), a.archive()).activity(a.activity());
			return b.build();
		}
	}
}
