package com.feralenterprises.raccoon.playtest.r02.host;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenDataset;
import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios;
import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios.Selection;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureClock;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Environment;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.lifecycle.Disposable;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.ui.theme.FontSource;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import javax.swing.SwingUtilities;

/**
 * One native playtest session: the verified frozen package, the R02 composition root and a scenario driver that
 * publishes simulated A/B snapshots. Account-scope protection: simulated values are shown only while the client phase
 * is {@link ClientPhase#OPEN}; on {@link ClientPhase#SUSPENDED} every value is hidden and every page subscription
 * closed, and resuming always publishes a brand-new scope (fresh epoch), so no page instance outlives a suspension.
 * Every account switch also gets a fresh epoch: A, B, A never reuses A's first pages. EDT only after {@link #load}.
 */
public final class PlaytestSession implements Disposable {
	/** Off-EDT loading: pin, verified dataset, verified assets decoded in advance. */
	public static final class Package {
		public final Path root;
		public final FrozenDataset data;
		public final FrozenPackageAssets assets;

		private Package(Path root, FrozenDataset data, FrozenPackageAssets assets) {
			this.root = root;
			this.data = data;
			this.assets = assets;
		}

		public static Package load(Path given) throws IOException {
			if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("load off the EDT");
			Path root = PackagePin.verify(given);
			FrozenDataset data = FrozenDataset.load(root);
			FrozenPackageAssets assets = new FrozenPackageAssets(root);
			List<String> ids = new ArrayList<>();
			for (R02Environment.Texture t : R02Environment.Texture.values()) if (t.artId() != null) ids.add(t.artId());
			for (ArtKey k : ArtKey.values()) { String id = HostAssets.assetId(k); if (id != null) ids.add(id); }
			assets.warm(ids);
			return new Package(root, data, assets);
		}
	}

	private final Package pkg;
	private final UiContext ui;
	private final FixtureDispatcher fixtures;
	private final FrozenScenarios scenarios;
	private final R02Composition composition;
	private final RunRecord record;
	private final int uiListenerBaseline;
	private final List<Runnable> stateListeners = new ArrayList<>();
	private Selection selection;
	private long revision;
	private int epoch;
	private ClientPhase phase = ClientPhase.SUSPENDED;
	private boolean started;
	private boolean disposed;
	private Map<String, Integer> teardown;

	/** The session's dispatcher, made before the lanes load (a lane entry may keep it). Any thread. */
	public static FixtureDispatcher newDispatcher(Package pkg) {
		return new FixtureDispatcher(pkg.data.registry(), new FixtureClock(FrozenDataset.bankTime(false)));
	}

	public PlaytestSession(Package pkg, FontSource fonts, AssetProvider nativeAssets, Collection<RouteRegistration> lanes, RunRecord record) {
		this(pkg, newDispatcher(pkg), fonts, nativeAssets, lanes, record);
	}

	public PlaytestSession(Package pkg, FixtureDispatcher dispatcher, FontSource fonts, AssetProvider nativeAssets,
		Collection<RouteRegistration> lanes, RunRecord record) {
		requireEdt();
		this.pkg = Objects.requireNonNull(pkg);
		this.record = Objects.requireNonNull(record);
		ui = new UiContext(Objects.requireNonNull(fonts), new HostAssets(pkg.assets, nativeAssets));
		uiListenerBaseline = ui.listenerCount();
		fixtures = Objects.requireNonNull(dispatcher);
		scenarios = new FrozenScenarios(pkg.data);
		composition = new R02Composition(ui, fixtures, pkg.assets, lanes);
		fixtures.setActionHandler(this::action);
		record.event("session", "package=" + pkg.root.getFileName() + " sums=" + PackagePin.SUMS_SHA256.substring(0, 12)
			+ " lanes=" + lanes.size() + " placeholders=" + composition.unregisteredRoutes().size());
		for (RouteRegistration r : lanes) record.event("lane", r.route().id() + " owner=" + r.owner());
	}

	public UiContext ui() { return ui; }
	public RaccoonShell shell() { return composition.shell(); }
	public R02Composition composition() { return composition; }
	public FixtureDispatcher fixtures() { return fixtures; }
	public FrozenDataset data() { return pkg.data; }
	public Selection selection() { return selection; }
	public ClientPhase phase() { return phase; }
	public boolean suspended() { return phase != ClientPhase.OPEN; }
	public boolean disposed() { return disposed; }
	public void addStateListener(Runnable r) { stateListeners.add(r); }

	/** First publication. Simulated data appears only if the phase is OPEN; otherwise the shell stays neutral. */
	public void start(ClientPhase initial) {
		requireEdt();
		if (started || disposed) throw new IllegalStateException("start once");
		started = true;
		selection = Selection.demoA(++epoch);
		phase = Objects.requireNonNull(initial);
		record.event("phase", "initial=" + initial);
		if (phase == ClientPhase.OPEN) publishNewScope(true);
		else composition.suspend();
		fire();
	}

	/** GameState-driven guard. Repeats are ignored. */
	public void clientPhase(ClientPhase p) {
		requireEdt();
		Objects.requireNonNull(p);
		if (!started || disposed || p == phase) return;
		phase = p;
		record.event("phase", p.name());
		if (p == ClientPhase.SUSPENDED) {
			composition.suspend();
		} else {
			selection = reEpoch(selection, ++epoch);
			publishNewScope(false);
		}
		fire();
	}

	/** Switches the simulated account: "A", "B" or null (signed out). Always a new scope. */
	public boolean selectAccount(String account) {
		requireEdt();
		if (!active()) return refused("select-account");
		int e = ++epoch;
		selection = account == null ? Selection.signedOut(e) : "A".equals(account) ? Selection.demoA(e)
			: "B".equals(account) ? Selection.demoB(e) : null;
		if (selection == null) throw new IllegalArgumentException("unknown simulated account " + account);
		publishNewScope(false);
		fire();
		return true;
	}

	/** A same-scope variation (bank, archive, quest data, route, activity): a newer revision, pages kept. */
	public boolean vary(String what, UnaryOperator<Selection> change) {
		requireEdt();
		if (!active()) return refused("vary " + what);
		Selection next = change.apply(selection);
		if (!Objects.equals(next.account, selection.account) || next.epoch != selection.epoch)
			throw new IllegalArgumentException("a variation must keep the scope");
		selection = next;
		revision++;
		FixtureSnapshot s = scenarios.snapshot(selection, fixtures.generation(), revision, fixtures.clock().now());
		if (!fixtures.publish(s)) throw new IllegalStateException("variation refused by the dispatcher");
		record.event("vary", what + " revision=" + revision);
		fire();
		return true;
	}

	/** Bank, archive and activity choices offered for the current simulated account. */
	public List<String> activityOptions() {
		List<String> out = new ArrayList<>();
		if (selection == null || selection.account == null) return out;
		out.addAll(pkg.data.archivedActivity(selection.account).keySet());
		for (String k : new String[] {"unrecognized", "idle", "banking", "travel", "paused"}) if (!out.contains(k)) out.add(k);
		return out;
	}

	public List<String> routeOptions() {
		List<String> out = new ArrayList<>(pkg.data.routeScenarios().keySet());
		if (!out.contains("unknown")) out.add("unknown");
		return out;
	}

	public String status() {
		if (disposed) return "Closed";
		if (suspended()) return "Simulated data hidden: a game account may be signed in";
		FixtureSnapshot s = fixtures.current();
		String acct = selection.account == null ? "Signed out" : "Demo " + selection.account;
		return acct + " | scope " + (s == null ? "-" : s.scope()) + " | revision " + revision + " | "
			+ composition.unregisteredRoutes().size() + " of " + RouteId.values().length + " routes awaiting lanes";
	}

	/** Disposes the composition and reports what is still held (every value must be 0). Idempotent. */
	@Override
	public void dispose() {
		requireEdt();
		if (disposed) return;
		disposed = true;
		composition.dispose();
		fixtures.setActionHandler(a -> { });
		// A lane entry may still hold the dispatcher and queue work: retire its publication authority for good.
		fixtures.retire();
		stateListeners.clear();
		Map<String, Integer> t = new LinkedHashMap<>();
		t.put("pageSubscriptions", composition.openPageSubscriptions());
		t.put("fixtureListeners", fixtures.listenerCount());
		t.put("uiContextListenersAboveBaseline", ui.listenerCount() - uiListenerBaseline);
		t.put("shellNotDisposed", composition.shell().isDisposed() ? 0 : 1);
		t.put("dispatcherNotRetired", fixtures.retired() ? 0 : 1);
		teardown = t;
		record.event("teardown", t.toString().replace('{', '[').replace('}', ']'));
	}

	/** Null until disposed. */
	public Map<String, Integer> teardownReport() { return teardown; }

	public static boolean clean(Map<String, Integer> report) {
		if (report == null) return false;
		for (int v : report.values()) if (v != 0) return false;
		return true;
	}

	private boolean active() { return started && !disposed && phase == ClientPhase.OPEN; }

	private boolean refused(String what) {
		record.event("refused", what + " phase=" + phase + (disposed ? " disposed" : ""));
		return false;
	}

	private void publishNewScope(boolean first) {
		int g = fixtures.bind();
		revision = 1;
		FixtureSnapshot s = scenarios.snapshot(selection, g, revision, fixtures.clock().now());
		if (first) composition.start(s);
		else if (!fixtures.publish(s)) throw new IllegalStateException("new scope refused by the dispatcher");
		record.event("scope", s.scope() + " generation=" + g);
	}

	private void action(FixtureAction a) {
		record.event("action", a.toString().replaceAll("[^A-Za-z0-9_:-]", "_"));
		if (a.kind() == FixtureAction.Kind.SWITCH_SCENARIO) {
			String t = a.target();
			selectAccount("logout".equals(t) || "signedout".equals(t) ? null : t);
		} else if (a.kind() == FixtureAction.Kind.RESET_OBSERVATIONS) {
			// Settings "Reset demo observations": the current simulated account's default observations, as a fresh scope.
			selectAccount(selection.account);
		} else if (a.kind() == FixtureAction.Kind.REFRESH_CAPTURE) {
			vary("refresh-capture", s -> s);
		}
	}

	private static Selection reEpoch(Selection s, int e) {
		Selection base = s.account == null ? Selection.signedOut(e) : "A".equals(s.account) ? Selection.demoA(e) : Selection.demoB(e);
		Selection r = base.withBank(s.bank).withArchive(s.archive).withQuestData(s.questData).withRoute(s.route);
		if (s.previousActivity != null) r = r.withActivity(s.previousActivity);
		return r.withActivity(s.activity);
	}

	private void fire() { new ArrayList<>(stateListeners).forEach(Runnable::run); }

	private static void requireEdt() {
		if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("EDT only");
	}
}
