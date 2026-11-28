package com.feralenterprises.raccoon.playtest.r02.acceptance;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.ActivityObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.EvidenceStatus;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.QuestState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.fixture.SkillObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Environment;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.shell.Destination;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteAware;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Shared-owner acceptance for the R02 common contract (dispatch Assignment 0). Dependency-free main; optional
 * argument: the extracted frozen package root, which enables the asset resolver checks.
 */
public final class ContractTests {
	private static final List<String> failures = new ArrayList<>();
	private static int passed;
	private static Path pkg;

	public static void main(String[] args) throws Exception {
		pkg = args.length > 0 ? Paths.get(args[0]) : null;
		SwingUtilities.invokeAndWait(() -> {
			try { run(); } catch (Throwable t) { t.printStackTrace(); failures.add("exception: " + t); }
		});
		try { disposalFence(); } catch (Throwable t) { t.printStackTrace(); failures.add("exception: " + t); }
		// Lane C retained-bank evidence (Claude 2, b27e06c): permanent, synthetic quantities, no private inputs.
		try { com.feralenterprises.raccoon.playtest.r02.panel.bank.RetainedBankRegression.run((n, ok) -> check("retained bank: " + n, ok)); }
		catch (Throwable t) { t.printStackTrace(); failures.add("exception: " + t); }
		try { SharedGapTests.run(ContractTests::check); } catch (Throwable t) { t.printStackTrace(); failures.add("exception: " + t); }
		try { FrozenDataTests.literals(ContractTests::check); } catch (Throwable t) { t.printStackTrace(); failures.add("exception: " + t); }
		if (pkg != null) {
			try { assets(); } catch (Throwable t) { t.printStackTrace(); failures.add("exception: " + t); }
			try { FrozenDataTests.run(pkg, ContractTests::check); } catch (Throwable t) { t.printStackTrace(); failures.add("exception: " + t); }
		}
		System.out.println("R02 ContractTests: " + passed + " passed, " + failures.size() + " failed"
			+ (pkg == null ? " (frozen package asset and dataset checks SKIPPED)" : ""));
		for (String f : failures) System.out.println("FAIL " + f);
		System.exit(failures.isEmpty() ? 0 : 1);
	}

	static void check(String name, boolean ok) { if (ok) passed++; else failures.add(name); }

	static boolean throwsIt(Runnable r) {
		try { r.run(); return false; } catch (RuntimeException e) { return true; }
	}

	// ---- stub lane pages (test-only; real panels live in each lane's package) --------------------------------------

	/** Home stub: a long page with a typed Outfitter link at the bottom. */
	static final class HomeStub extends StackPanel {
		final JButton link = new JButton("Prepare loadout");
		HomeStub(RouteContext c) {
			super(4);
			for (int i = 0; i < 40; i++) add(new JLabel("home row " + i));
			link.addActionListener(e -> c.navigate(RouteId.OUTFIT, RouteRequest.Param.ENCOUNTER, "vorkath"));
			add(link);
		}
	}

	/** Outfitter stub: selects the requested encounter, opens a slot detail, closes it on local Back. */
	static final class OutfitStub extends StackPanel implements RouteAware, LocalBack {
		final RouteContext c;
		final JButton slot = new JButton("Head slot");
		String encounter = "scurrius";
		boolean detail;
		int deliveries;
		Runnable listener;
		OutfitStub(RouteContext c) {
			super(4);
			this.c = c;
			slot.addActionListener(e -> { detail = true; if (listener != null) listener.run(); });
			add(slot);
		}
		@Override public void routeShown(RouteRequest r, boolean returning) {
			deliveries++;
			if (!returning && r.param() == RouteRequest.Param.ENCOUNTER && c.registry().encounter(r.paramId()).isPresent()) encounter = r.paramId();
		}
		@Override public boolean hasLocalBack() { return detail; }
		@Override public void localBack() { detail = false; slot.requestFocusInWindow(); if (listener != null) listener.run(); }
		@Override public void setLocalBackListener(Runnable r) { listener = r; }
	}

	/** Settings stub: Interface → Tools entry. */
	static final class SettingsStub extends StackPanel {
		final JButton tools = new JButton("Tools");
		SettingsStub(RouteContext c) { super(4); tools.addActionListener(e -> c.navigate(RouteId.TOOLS)); add(tools); }
	}

	/** Chronicle stub that records what it saw, to prove scope isolation. */
	static final class ChronicleStub extends StackPanel {
		final List<String> seen = new ArrayList<>();
		ChronicleStub(RouteContext c) {
			super(4);
			seen.add(c.snapshot().scope() + ":" + c.snapshot().officialKc("vorkath"));
			c.onSnapshot(s -> seen.add(s.scope() + ":r" + s.revision()));
		}
	}

	static void run() throws Exception {
		routes();
		evidence();
		dispatcher();
		composition();
	}

	static void routes() {
		List<String> ids = Arrays.stream(RouteId.values()).map(RouteId::id).collect(Collectors.toList());
		check("routes: exactly the 14 dispatch routes", ids.size() == 14 && ids.containsAll(Arrays.asList(
			"home", "outfit", "activity", "bank", "capture", "files", "chronicle", "pathfinder", "quests", "xpstash", "friends", "bounties", "tools", "settings")));
		List<String> rail = Arrays.stream(RouteId.values()).filter(RouteId::inRail).map(RouteId::id).collect(Collectors.toList());
		check("routes: 12 rail routes in frozen order", rail.equals(Arrays.asList(
			"home", "pathfinder", "outfit", "activity", "files", "bank", "quests", "chronicle", "bounties", "friends", "capture", "xpstash")));
		check("routes: Tools off the rail, Settings is the utility", !RouteId.TOOLS.destination().inRail()
			&& RouteId.SETTINGS.destination().group() == Destination.Group.UTILITY && !RouteId.SETTINGS.destination().inRail());
		check("routes: Capture is in the rail (D-14)", RouteId.CAPTURE.destination().inRail());
		check("routes: every route is account-scoped", Arrays.stream(RouteId.values()).allMatch(r -> r.destination().accountData()));
		check("routes: frozen titles", "Quests & Diaries".equals(RouteId.QUESTS.label()) && "Quests".equals(RouteId.QUESTS.railName())
			&& "Bandit’s Bounties".equals(RouteId.BOUNTIES.label()));
		check("routes: lookup by id", RouteId.of("xpstash") == RouteId.XPSTASH && throwsIt(() -> RouteId.of("profile")));
		Set<RouteId> owned = EnumSet.noneOf(RouteId.class);
		int total = 0;
		for (Lane l : Lane.values()) { owned.addAll(l.routes()); total += l.routes().size(); }
		check("lanes: every route owned exactly once", owned.size() == 14 && total == 14);
		check("lanes: dispatch allocation", Lane.COMBAT.routes().equals(EnumSet.of(RouteId.OUTFIT, RouteId.CHRONICLE))
			&& Lane.ADVENTURE.routes().equals(EnumSet.of(RouteId.HOME, RouteId.ACTIVITY, RouteId.FILES, RouteId.TOOLS))
			&& Lane.ACCOUNT.routes().equals(EnumSet.of(RouteId.BANK, RouteId.CAPTURE, RouteId.XPSTASH))
			&& Lane.PROGRESSION.routes().equals(EnumSet.of(RouteId.PATHFINDER, RouteId.QUESTS, RouteId.BOUNTIES))
			&& Lane.COLLECTION.routes().equals(EnumSet.of(RouteId.FRIENDS, RouteId.SETTINGS)));
		check("lanes: write scope is the panel package", "com/feralenterprises/raccoon/playtest/r02/panel/outfit/".equals(Lane.COMBAT.writeScope(RouteId.OUTFIT))
			&& throwsIt(() -> Lane.COMBAT.writeScope(RouteId.HOME)));
		check("environments: one per route", Arrays.stream(RouteId.values()).allMatch(r -> R02Environment.of(r).route() == r));
		check("environments: frozen textures", R02Environment.of(RouteId.BOUNTIES).texture() == R02Environment.Texture.TIMBER
			&& R02Environment.of(RouteId.CHRONICLE).texture() == R02Environment.Texture.FIELDSTONE
			&& "PR-028".equals(R02Environment.Texture.FIELDSTONE.artId()) && R02Environment.of(RouteId.QUESTS).tileSize() == 145);
		check("assets: none() resolves nothing, fallback labels", R02Assets.none().item(1511) == null
			&& "Logs".equals(R02Assets.fallbackLabel("Logs")) && "Image unavailable".equals(R02Assets.fallbackLabel(null)));
	}

	static void evidence() {
		java.time.Instant t = TestFixtures.T0;
		check("evidence: zero count is VERIFIED_ZERO", Evidence.count(0, t, "x").status() == EvidenceStatus.VERIFIED_ZERO && Evidence.count(0, t, "x").value() == 0);
		check("evidence: unknown has no value", !Evidence.unknown().hasValue() && throwsIt(() -> Evidence.unknown().value()));
		check("evidence: unavailable has no value", throwsIt(() -> Evidence.unavailable().value()));
		check("evidence: stale needs a time", throwsIt(() -> Evidence.stale(1, null, "x")));
		check("evidence: absent is distinct from zero and unknown", Evidence.absent(t, "x").status() == EvidenceStatus.VERIFIED_ABSENT && !Evidence.absent(t, "x").hasValue());
		check("evidence: negative count refused", throwsIt(() -> Evidence.count(-1, t, "x")));

		ContainerObservation cur = new ContainerObservation(Container.BANK, ContainerState.CURRENT, t, "x", Collections.singletonMap(1511, 1_000L));
		check("bank current: present verified", cur.quantity(1511).status() == EvidenceStatus.VERIFIED && cur.quantity(1511).value() == 1_000L);
		check("bank current: missing is verified absence (raw tuna, D-05 join)", cur.quantity(359).status() == EvidenceStatus.VERIFIED_ABSENT);
		ContainerObservation empty = new ContainerObservation(Container.BANK, ContainerState.CURRENT, t, "x", Collections.emptyMap());
		check("bank verified empty reads empty, not unknown", empty.verifiedEmpty() && empty.quantity(1511).status() == EvidenceStatus.VERIFIED_ABSENT);
		ContainerObservation hist = new ContainerObservation(Container.BANK, ContainerState.HISTORICAL, t, "saved", Collections.singletonMap(1511, 5L));
		check("bank historical: present is stale, absence unknown", hist.quantity(1511).status() == EvidenceStatus.STALE && hist.quantity(359).status() == EvidenceStatus.UNKNOWN && !hist.verifiedEmpty());
		ContainerObservation part = new ContainerObservation(Container.BANK, ContainerState.INCOMPLETE, t, "x", Collections.singletonMap(1511, 5L));
		check("bank incomplete: seen is verified, absence unknown", part.quantity(1511).status() == EvidenceStatus.VERIFIED && part.quantity(359).status() == EvidenceStatus.UNKNOWN);
		check("bank unavailable: unavailable", ContainerObservation.unavailable(Container.BANK).quantity(1511).status() == EvidenceStatus.UNAVAILABLE);
		check("bank: zero quantities refused", throwsIt(() -> new ContainerObservation(Container.BANK, ContainerState.CURRENT, t, "x", Collections.singletonMap(1, 0L))));
		check("bank: unavailable holds no items", throwsIt(() -> new ContainerObservation(Container.BANK, ContainerState.UNAVAILABLE, null, null, Collections.singletonMap(1, 1L))));

		FixtureSnapshot a = TestFixtures.demoA(0, 1);
		SkillObservation fm = a.skill("firemaking");
		check("skills: verified level kept while exact XP unavailable", fm.level().status() == EvidenceStatus.VERIFIED && fm.level().value() == 60
			&& fm.exactXp().status() == EvidenceStatus.UNAVAILABLE);
		check("skills: unobserved skill is unknown level, unavailable XP", a.skill("sailing").level().status() == EvidenceStatus.UNKNOWN
			&& a.skill("sailing").exactXp().status() == EvidenceStatus.UNAVAILABLE);
		check("skills: level out of range refused", throwsIt(() -> new SkillObservation(Evidence.verified(120, TestFixtures.T0, "x"), Evidence.unavailable())));
		check("snapshot: unobserved KC is unknown, not zero", a.officialKc("zulrah").status() == EvidenceStatus.UNKNOWN);
		check("snapshot: observed zero KC kept", a.officialKc("brutus").status() == EvidenceStatus.VERIFIED_ZERO);
		check("snapshot: unsupplied container unavailable", a.container(Container.SEED_VAULT).state() == ContainerState.UNAVAILABLE);
		check("snapshot: C-01 inputs (A: DS II complete, 48 KC)", a.quest("Dragon Slayer II").value() == QuestState.COMPLETE && a.officialKc("vorkath").value() == 48);
		FixtureSnapshot b = TestFixtures.demoB(0, 1);
		check("snapshot: C-01 inputs (B: DS II not started, verified 0)", b.quest("Dragon Slayer II").value() == QuestState.NOT_STARTED
			&& b.officialKc("vorkath").status() == EvidenceStatus.VERIFIED_ZERO && !b.record("a1").isPresent());
		check("snapshot: record a1 keeps trip kills separate from official KC (D-13)", a.record("a1").get().units() == 12 && a.officialKc("vorkath").value() == 48);
		check("snapshot: collections are immutable", throwsIt(() -> a.archive().clear()) && throwsIt(() -> a.container(Container.BANK).items().clear()));
		check("snapshot: unobserved archive cannot hold records", throwsIt(() -> FixtureSnapshot.builder(AccountScope.of("A", 1))
			.archive(Evidence.unknown(), Collections.singletonList(a.record("a1").get()))));
		check("snapshot: signed out has no current activity", throwsIt(() -> FixtureSnapshot.builder(AccountScope.signedOut(1))
			.activity(new ActivityObservation(ActivityObservation.Mode.IDLE, "x", null, TestFixtures.T0, null, 0, Evidence.unavailable())).build()));
		check("activity: a replay names its record", throwsIt(() -> new ActivityObservation(ActivityObservation.Mode.REPLAY, "vorkath", null, null, null, 0, Evidence.unavailable())));

		// D-19 shape: idle keeps elapsed advancing and active fixed; resume advances active.
		com.feralenterprises.raccoon.playtest.r02.fixture.FixtureClock clock = TestFixtures.clock();
		ActivityObservation idle = new ActivityObservation(ActivityObservation.Mode.IDLE, "vorkath", null, clock.now(), null, 60_000, Evidence.unavailable());
		long e0 = idle.elapsedMillis(clock.now()), a0 = idle.activeMillis(clock.now());
		clock.advance(Duration.ofSeconds(30));
		check("clock: idle elapsed +30 s, active unchanged", idle.elapsedMillis(clock.now()) - e0 == 30_000 && idle.activeMillis(clock.now()) == a0);
		ActivityObservation resumed = new ActivityObservation(ActivityObservation.Mode.REPLAY, "vorkath", "a1", idle.sessionStart(), clock.now(), a0, Evidence.unavailable());
		clock.advance(Duration.ofSeconds(10));
		check("clock: resume advances active", resumed.activeMillis(clock.now()) == a0 + 10_000);
		check("clock: cannot go back", throwsIt(() -> clock.advance(Duration.ofSeconds(-1))));

		Registry r = TestFixtures.registry();
		check("registry: exact names (D-09, D-15)", "Bird house".equals(r.itemName(21512)) && "Iban's staff".equals(r.itemName(1409))
			&& "Ancient chaps".equals(r.itemName(12494)) && "Teleport to house".equals(r.itemName(8013)) && "Fishing potion(2)".equals(r.itemName(153)));
		check("registry: Helm of Neitiznot tradeable (D-10, OSRS Wiki: Tradeable Yes)", r.item(10828).get().tradeable());
		check("registry: unregistered item never guessed", "Item 42".equals(r.itemName(42)));
		check("registry: Kings grouped with three separate counts (D-18), as in the frozen registry",
			r.encounter("dagannoth-kings").get().kcKeys().equals(Arrays.asList("dagannoth-rex", "dagannoth-prime", "dagannoth-supreme"))
			&& !r.encounter("dagannoth-rex").isPresent());
		check("registry: the tiny fixture leaves other encounters to the lanes (no duplicates when they add their own)",
			!r.encounter("barrows").isPresent() && !r.encounter("giant-mole").isPresent());
		check("registry: unranked difficulty stays null", r.encounter("hespori").get().difficulty() == null);
		check("registry: duplicates refused", throwsIt(() -> new Registry(Arrays.asList(r.item(995).get(), r.item(995).get()), Collections.emptyList())));
	}

	static void dispatcher() {
		FixtureDispatcher d = new FixtureDispatcher(TestFixtures.registry(), TestFixtures.clock());
		List<String> scopes = new ArrayList<>();
		d.setOnScopeChange(s -> scopes.add(s.scope().toString()));
		int g = d.bind();
		check("dispatcher: first publication applies", d.publish(TestFixtures.demoA(g, 1)) && scopes.equals(Arrays.asList("A:1")));
		List<Long> revs = new ArrayList<>();
		d.onSnapshot(s -> revs.add(s.revision()));
		check("dispatcher: newer revision delivered", d.publish(TestFixtures.demoA(g, 2)) && revs.equals(Arrays.asList(2L)));
		check("dispatcher: stale revision rejected", !d.publish(TestFixtures.demoA(g, 2)) && !d.publish(TestFixtures.demoA(g, 1)) && d.current().revision() == 2);
		check("dispatcher: other generation rejected", !d.publish(TestFixtures.demoA(g + 7, 9)) && d.rejected() == 3);
		int g2 = d.bind();
		check("dispatcher: work from a retired binding rejected", !d.publish(TestFixtures.demoA(g, 3)) && d.publish(TestFixtures.demoA(g2, 3)));
		check("dispatcher: scope change applied, listeners not told", d.publish(TestFixtures.demoB(g2, 1)) && scopes.equals(Arrays.asList("A:1", "B:1")) && revs.equals(Arrays.asList(2L, 3L)));
		check("dispatcher: new scope starts its own revisions", "B".equals(d.current().scope().account()) && d.current().revision() == 1);
		check("dispatcher: preferences never touch observations", setAppearanceKeeps(d));
	}

	static boolean setAppearanceKeeps(FixtureDispatcher d) {
		FixtureSnapshot before = d.current();
		d.setAppearance(d.appearance().withAccent(com.feralenterprises.raccoon.playtest.r02.fixture.Appearance.Accent.SAPPHIRE));
		return d.current() == before && d.appearance().accent() == com.feralenterprises.raccoon.playtest.r02.fixture.Appearance.Accent.SAPPHIRE;
	}

	/**
	 * Disposal fence (Feral, 2026-11-27): when the assembled session is finally disposed the dispatcher's publication
	 * authority is retired, so publications queued before or after disposal never apply, re-binding cannot revive it, and
	 * appearance and fixture actions are inert. Runs across EDT tasks, because queued work only runs after the task that
	 * queued it.
	 */
	static void disposalFence() throws Exception {
		UiContext ui = new UiContext(new DefaultFontSource(), new com.feralenterprises.raccoon.ui.asset.AssetProvider() {
			@Override public BufferedImage art(com.feralenterprises.raccoon.ui.asset.ArtKey key) { return null; }
			@Override public BufferedImage nativeSprite(com.feralenterprises.raccoon.ui.asset.NativeSprite sp, Runnable onLoaded) { return null; }
			@Override public BufferedImage item(int itemId, int quantity, Runnable onLoaded) { return null; }
			@Override public String provenance() { return "none"; }
		});
		FixtureDispatcher d = new FixtureDispatcher(TestFixtures.registry(), TestFixtures.clock());
		List<String> pagesBuilt = new ArrayList<>(), actions = new ArrayList<>(), seen = new ArrayList<>();
		R02Composition[] c = new R02Composition[1];
		Object[] state = new Object[3];
		SwingUtilities.invokeAndWait(() -> {
			c[0] = new R02Composition(ui, d, R02Assets.none(), Collections.singletonList(
				new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), ctx -> { pagesBuilt.add(String.valueOf(ctx.snapshot().scope())); return new HomeStub(ctx); })));
			d.setActionHandler(a -> actions.add(a.toString()));
			d.onSnapshot(s -> seen.add(s.scope() + ":" + s.revision()));
			int g = d.bind();
			c[0].start(TestFixtures.demoA(g, 1));
			// Queued while the session is live, from "another thread" (publishLater), then the session is finally disposed.
			d.publishLater(TestFixtures.demoA(g, 2));
			d.publishLater(TestFixtures.demoB(g, 1));
			c[0].dispose();
			d.retire();
			state[0] = d.current();
			state[1] = d.rejected();
			state[2] = pagesBuilt.size();
		});
		SwingUtilities.invokeAndWait(() -> { });
		SwingUtilities.invokeAndWait(() -> {
			check("fence: final disposal retires the dispatcher", d.retired());
			check("fence: publications queued before disposal never apply", d.current() == state[0] && d.rejected() == (int) state[1] + 2);
			check("fence: no listener or page sees a queued publication after disposal", seen.isEmpty() && pagesBuilt.size() == (int) state[2]);
			d.publishLater(TestFixtures.demoA(d.generation(), 9));
		});
		SwingUtilities.invokeAndWait(() -> { });
		SwingUtilities.invokeAndWait(() -> {
			check("fence: publications queued after disposal never apply", d.current() == state[0] && d.rejected() == (int) state[1] + 3);
			check("fence: a direct publication with the current generation is refused", !d.publish(TestFixtures.demoB(d.generation(), 5)) && d.current() == state[0]);
			check("fence: re-binding cannot revive a retired dispatcher", throwsIt(d::bind) && d.retired());
			com.feralenterprises.raccoon.playtest.r02.fixture.Appearance before = d.appearance();
			d.setAppearance(before.withAccent(com.feralenterprises.raccoon.playtest.r02.fixture.Appearance.Accent.SAPPHIRE));
			check("fence: appearance changes are inert after disposal", d.appearance() == before);
			d.dispatch(com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction.of(com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction.Kind.SWITCH_SCENARIO, "B"));
			check("fence: fixture actions are inert after disposal", actions.isEmpty());
			d.retire();
			check("fence: retire is idempotent", d.retired() && d.current() == state[0]);
		});
	}

	static void composition() {
		UiContext ui = new UiContext(new DefaultFontSource(), new com.feralenterprises.raccoon.ui.asset.AssetProvider() {
			@Override public BufferedImage art(com.feralenterprises.raccoon.ui.asset.ArtKey key) { return null; }
			@Override public BufferedImage nativeSprite(com.feralenterprises.raccoon.ui.asset.NativeSprite sp, Runnable onLoaded) { return null; }
			@Override public BufferedImage item(int itemId, int quantity, Runnable onLoaded) { return null; }
			@Override public String provenance() { return "none"; }
		});
		FixtureDispatcher d = new FixtureDispatcher(TestFixtures.registry(), TestFixtures.clock());
		check("composition: wrong lane refused", throwsIt(() -> new R02Composition(ui, d, R02Assets.none(),
			Collections.singletonList(new RouteRegistration(RouteId.OUTFIT, Lane.ADVENTURE.name(), HomeStub::new)))));
		check("composition: duplicate registration refused", throwsIt(() -> new R02Composition(ui, d, R02Assets.none(), Arrays.asList(
			new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), HomeStub::new), new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), HomeStub::new)))));

		List<OutfitStub> outfits = new ArrayList<>();
		List<HomeStub> homes = new ArrayList<>();
		List<ChronicleStub> chronicles = new ArrayList<>();
		FixtureDispatcher fx = new FixtureDispatcher(TestFixtures.registry(), TestFixtures.clock());
		R02Composition c = new R02Composition(ui, fx, R02Assets.none(), Arrays.asList(
			new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), ctx -> { HomeStub h = new HomeStub(ctx); homes.add(h); return h; }),
			new RouteRegistration(RouteId.OUTFIT, Lane.COMBAT.name(), ctx -> { OutfitStub o = new OutfitStub(ctx); outfits.add(o); return o; }),
			new RouteRegistration(RouteId.CHRONICLE, Lane.COMBAT.name(), ctx -> { ChronicleStub s = new ChronicleStub(ctx); chronicles.add(s); return s; }),
			new RouteRegistration(RouteId.SETTINGS, Lane.COLLECTION.name(), SettingsStub::new)));
		check("composition: unregistered routes listed", c.unregisteredRoutes().size() == 10 && !c.unregisteredRoutes().contains(RouteId.HOME));
		check("composition: rail shows 12 routes", c.navigator().destinations().stream().filter(Destination::inRail).count() == 12);
		c.shell().setSize(263, 420);
		c.shell().doLayout();
		int g = fx.bind();
		c.start(TestFixtures.demoA(g, 1));
		check("composition: starts on Home", "home".equals(c.navigator().current()) && homes.size() == 1);

		// Home -> Outfitter slot/detail -> Back proof (dispatch first proof, with stub lane pages)
		c.shell().scrollPane().validate();
		c.shell().scrollPane().getViewport().setViewPosition(new Point(0, 300));
		Point homeScroll = c.shell().scrollPane().getViewport().getViewPosition();
		homes.get(0).link.doClick();
		OutfitStub o = outfits.get(0);
		check("proof: typed link opens Outfitter on Vorkath", "outfit".equals(c.navigator().current())
			&& RouteRequest.of("outfit", RouteRequest.Param.ENCOUNTER, "vorkath").equals(c.navigator().currentRequest()) && "vorkath".equals(o.encounter));
		o.slot.doClick();
		check("proof: slot detail open, Back enabled", o.detail && c.shell().header().back().isEnabled());
		c.shell().header().back().doClick();
		check("proof: Back closes the slot detail first", !o.detail && "outfit".equals(c.navigator().current()));
		c.shell().header().back().doClick();
		check("proof: second Back returns Home", "home".equals(c.navigator().current()));
		check("proof: Home scroll restored", c.shell().scrollPane().getViewport().getViewPosition().equals(homeScroll) && homeScroll.y > 0);
		check("proof: same pages reused (state kept)", homes.size() == 1 && outfits.size() == 1);
		check("proof: Back disabled at start", !c.shell().header().back().isEnabled());
		c.navigator().navigate("outfit");
		check("proof: rail re-entry keeps the selected encounter", "vorkath".equals(o.encounter) && outfits.size() == 1);

		// Tools via Settings
		c.shell().header().settings().doClick();
		check("settings: header Settings opens Settings", "settings".equals(c.navigator().current()));
		c.navigator().navigate("settings");
		c.shell().goBack();
		c.shell().header().settings().doClick();

		// Scope isolation
		c.navigator().navigate("chronicle");
		ChronicleStub ca = chronicles.get(0);
		fx.publish(TestFixtures.demoA(g, 2));
		check("scope: same-scope revision reaches the page", ca.seen.equals(Arrays.asList("A:1:VERIFIED(48)", "A:1:r2")));
		int subsBefore = c.openPageSubscriptions();
		fx.publish(TestFixtures.demoB(g, 1));
		check("scope: A->B returns Home with empty history", "home".equals(c.navigator().current()) && !c.navigator().canGoBack() && !c.shell().canGoBack());
		check("scope: A's page subscriptions closed", subsBefore >= 1 && c.openPageSubscriptions() == 0 && ca.seen.size() == 2);
		check("scope: pages rebuilt for B", homes.size() == 2);
		c.navigator().navigate("chronicle");
		check("scope: B's Chronicle built from B's snapshot only", chronicles.size() == 2 && chronicles.get(1).seen.equals(Arrays.asList("B:1:VERIFIED_ZERO(0)")));
		c.navigator().navigate("outfit");
		check("scope: B's Outfitter starts fresh", outfits.size() == 2 && "scurrius".equals(outfits.get(1).encounter));
		fx.publish(TestFixtures.signedOut(g, 2));
		check("scope: signed out is its own scope", !fx.current().scope().signedIn() && "home".equals(c.navigator().current()) && c.openPageSubscriptions() == 0);
		int g3 = fx.bind();
		int homesBefore = homes.size();
		check("scope: work queued under the retired binding is refused", !fx.publish(TestFixtures.demoB(g, 5)) && !fx.current().scope().signedIn() && homes.size() == homesBefore);
		g = g3;
		int subs = c.openPageSubscriptions();
		c.navigator().navigate("chronicle");
		check("dispose: page subscribed", c.openPageSubscriptions() == subs + 1);
		c.dispose();
		check("dispose: subscriptions closed, shell disposed", c.openPageSubscriptions() == 0 && c.shell().isDisposed() && fx.listenerCount() == 0);
		c.dispose();
		check("dispose: idempotent", c.shell().isDisposed());
		int homesAtDispose = homes.size();
		check("dispose: later scope change builds nothing", fx.publish(TestFixtures.demoA(g, 1)) && homes.size() == homesAtDispose);
	}

	static void assets() throws Exception {
		FrozenPackageAssets a = new FrozenPackageAssets(pkg);
		BufferedImage td = a.portrait("tormented-demons");
		check("assets: Tormented Demon portrait verified and decoded", td != null);
		check("assets: authentic item sprite 21512 (Bird house, embedded set)", a.item(21512) != null);
		check("assets: authentic item sprite 359 (Raw tuna, packaged file)", a.item(359) != null);
		check("assets: skill icon firemaking", a.skillIcon("firemaking") != null);
		check("assets: frozen art PR-032 timber", a.art("PR-032") != null);
		check("assets: unknown resolves to null (fallback)", a.item(-5) == null && a.portrait("no-such-boss") == null && a.art("PR-999") == null);
		check("assets: bad skill name refused", a.skillIcon("../x") == null);
		int portraits = 0;
		for (String k : Arrays.asList("vorkath", "scurrius", "barrows", "the-nightmare")) if (a.portrait(k) != null) portraits++;
		check("assets: encounter portraits resolve", portraits == 4);
		Map<String, String> refusedOk = a.refused();
		check("assets: nothing refused in the genuine package", refusedOk.isEmpty());

		// Fail-closed: a tampered copy of one sprite is refused.
		Path tmp = Files.createTempDirectory("r02pkg");
		Files.copy(pkg.resolve("SHA256SUMS.txt"), tmp.resolve("SHA256SUMS.txt"));
		Path dir = Files.createDirectories(tmp.resolve("source/osrs-item-sprites"));
		Files.copy(pkg.resolve("source/osrs-item-sprites/359.png"), dir.resolve("359.png"), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(pkg.resolve("source/osrs-item-sprites/363.png"), dir.resolve("363.png"), StandardCopyOption.REPLACE_EXISTING);
		Files.write(dir.resolve("363.png"), new byte[] {1, 2, 3}, java.nio.file.StandardOpenOption.APPEND);
		FrozenPackageAssets t = new FrozenPackageAssets(tmp);
		check("assets: verified copy decodes", t.item(359) != null);
		check("assets: tampered sprite refused", t.item(363) == null && t.refused().containsKey("source/osrs-item-sprites/363.png"));
		check("assets: missing HTML yields no portraits, no crash", t.portrait("tormented-demons") == null);
	}
}
