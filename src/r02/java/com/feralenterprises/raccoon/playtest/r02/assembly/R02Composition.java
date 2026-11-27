package com.feralenterprises.raccoon.playtest.r02.assembly;

import com.feralenterprises.raccoon.playtest.r02.fixture.Appearance;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureClock;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.fixture.Subscription;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Environment;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.lifecycle.Disposable;
import com.feralenterprises.raccoon.ui.shell.Destination;
import com.feralenterprises.raccoon.ui.shell.Navigator;
import com.feralenterprises.raccoon.ui.shell.Placeholder;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The R02 composition root (shared owner only). Builds the 14-route navigator and the existing adaptive shell,
 * installs lane registrations (each checked against the dispatch ownership table), binds the fixture dispatcher as
 * the shell's single account source and enforces the scope rule: a new scope discards every route page, closes every
 * page subscription and resets history to Home.
 */
public final class R02Composition implements Disposable {
	private final UiContext ui;
	private final FixtureDispatcher fixtures;
	private final R02Assets assets;
	private final Navigator nav;
	private final RaccoonShell shell;
	private final Object source = new Object();
	private final Map<RouteId, RouteRegistration> registrations = new EnumMap<>(RouteId.class);
	private final List<Subscription> pageSubscriptions = new ArrayList<>();
	private final Runnable onClear = this::closePageSubscriptions;
	private boolean disposed;

	public R02Composition(UiContext ui, FixtureDispatcher fixtures, R02Assets assets, Collection<RouteRegistration> lanes) {
		requireEdt();
		this.ui = Objects.requireNonNull(ui);
		this.fixtures = Objects.requireNonNull(fixtures);
		this.assets = Objects.requireNonNull(assets);
		for (RouteRegistration r : lanes) {
			Lane owner = Lane.owning(r.route());
			if (!owner.name().equals(r.owner()))
				throw new IllegalArgumentException(r.route() + " is owned by " + owner + ", not " + r.owner());
			if (registrations.put(r.route(), r) != null) throw new IllegalArgumentException("duplicate registration for " + r.route());
		}
		List<Destination> all = new ArrayList<>();
		for (RouteId r : RouteId.values()) all.add(r.destination());
		nav = new Navigator(all);
		shell = new RaccoonShell(ui, nav);
		for (RouteId r : RouteId.values()) shell.register(r.id(), R02Environment.of(r).frameworkEnvironment(), () -> page(r));
		shell.addClearListener(onClear);
		shell.attachAccountSource(source, this::shellDisposed);
		fixtures.setOnScopeChange(this::scopeChanged);
	}

	public RaccoonShell shell() { return shell; }
	public Navigator navigator() { return nav; }
	public FixtureDispatcher fixtures() { return fixtures; }
	/** Routes with no lane registration yet render the framework placeholder; a complete candidate has none. */
	public List<RouteId> unregisteredRoutes() {
		List<RouteId> out = new ArrayList<>();
		for (RouteId r : RouteId.values()) if (!registrations.containsKey(r)) out.add(r);
		return Collections.unmodifiableList(out);
	}
	public int openPageSubscriptions() { return pageSubscriptions.size(); }

	/** Publishes the first snapshot (bind generation first) and opens Home. EDT only. */
	public void start(FixtureSnapshot first) {
		requireEdt();
		if (!fixtures.publish(first)) throw new IllegalArgumentException("initial snapshot refused (generation " + first.generation() + ")");
	}

	private void scopeChanged(FixtureSnapshot s) {
		if (disposed || shell.isDisposed()) return;
		nav.resetHistory(RouteRequest.of(RouteId.HOME.id()));
		shell.showAccount(source, s.scope());
	}

	private JComponent page(RouteId r) {
		RouteRegistration reg = registrations.get(r);
		if (reg == null) return Placeholder.forDestination(ui, nav.destination(r.id()));
		return reg.factory().create(new PageContext(r));
	}

	private void closePageSubscriptions() {
		List<Subscription> copy = new ArrayList<>(pageSubscriptions);
		pageSubscriptions.clear();
		copy.forEach(Subscription::close);
	}

	/** Disposes the shell; the shell's disposal callback closes every page subscription. Idempotent. EDT only. */
	@Override
	public void dispose() {
		requireEdt();
		if (!shell.isDisposed()) shell.dispose();
		shellDisposed();
	}

	private void shellDisposed() {
		if (disposed) return;
		disposed = true;
		closePageSubscriptions();
		fixtures.setOnScopeChange(s -> { });
	}

	private static void requireEdt() {
		if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("EDT only");
	}

	private final class PageContext implements RouteContext {
		private final RouteId route;
		PageContext(RouteId route) { this.route = route; }

		@Override public RouteId route() { return route; }
		@Override public UiContext ui() { return ui; }
		@Override public FixtureSnapshot snapshot() { return fixtures.current(); }
		@Override public Registry registry() { return fixtures.registry(); }
		@Override public R02Assets assets() { return assets; }
		@Override public FixtureClock clock() { return fixtures.clock(); }
		@Override public Appearance appearance() { return fixtures.appearance(); }

		@Override public Subscription onSnapshot(Consumer<FixtureSnapshot> l) { return track(fixtures.onSnapshot(l)); }
		@Override public Subscription onAppearance(Consumer<Appearance> l) { return track(fixtures.onAppearance(l)); }

		private Subscription track(Subscription s) {
			pageSubscriptions.add(s);
			return () -> { pageSubscriptions.remove(s); s.close(); };
		}

		@Override public void navigate(RouteId r) { nav.navigate(r.id()); }
		@Override public void navigate(RouteId r, RouteRequest.Param p, String id) { nav.navigate(RouteRequest.of(r.id(), p, id)); }
		@Override public void dispatch(FixtureAction a) { fixtures.dispatch(a); }
	}
}
