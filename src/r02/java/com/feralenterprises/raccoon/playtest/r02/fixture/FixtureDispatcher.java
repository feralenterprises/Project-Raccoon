package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * The one fixture dispatcher. Holds the current snapshot and appearance and applies publications on the EDT:
 * <ul>
 * <li>a publication with another binding generation is rejected (late work from a retired binding);</li>
 * <li>in the same scope, a revision not newer than the current one is rejected (stale or duplicate);</li>
 * <li>a new scope becomes current and {@code onScopeChange} then discards every route page and the history in the
 * same EDT task, so pages of the new scope are built from the new snapshot and no old page is told about it.</li>
 * </ul>
 * Work queued from another thread goes through {@link #publishLater}; it is checked again when it runs. When the
 * assembled session is finally disposed it calls {@link #retire()}, after which nothing is published again.
 */
public final class FixtureDispatcher {
	private final Registry registry;
	private final FixtureClock clock;
	private final List<Consumer<FixtureSnapshot>> snapshotListeners = new CopyOnWriteArrayList<>();
	private final List<Consumer<Appearance>> appearanceListeners = new CopyOnWriteArrayList<>();
	private Consumer<FixtureSnapshot> onScopeChange = s -> { };
	private Consumer<FixtureAction> actionHandler = a -> { };
	private FixtureSnapshot current;
	private Appearance appearance = Appearance.DEFAULTS;
	private int generation;
	private int rejected;
	private boolean retired;

	public FixtureDispatcher(Registry registry, FixtureClock clock) {
		this.registry = Objects.requireNonNull(registry);
		this.clock = Objects.requireNonNull(clock);
	}

	/** Called (EDT) with the new snapshot when the scope changes; it must discard all scoped pages and history. */
	public void setOnScopeChange(Consumer<FixtureSnapshot> c) { onScopeChange = Objects.requireNonNull(c); }
	/** The scenario driver that turns fixture actions into new publications. */
	public void setActionHandler(Consumer<FixtureAction> h) { actionHandler = Objects.requireNonNull(h); }

	public Registry registry() { return registry; }
	public FixtureClock clock() { return clock; }
	public FixtureSnapshot current() { return current; }
	public Appearance appearance() { return appearance; }
	public int generation() { return generation; }
	/** Count of publications refused as stale or from another generation (tests assert on it). */
	public int rejected() { return rejected; }

	/**
	 * Final disposal of the assembled session: retires publication authority for good. The binding generation moves on
	 * (so every queued publication is from a retired binding), every later publication is refused, {@link #bind()} can
	 * no longer revive it, and appearance and fixture actions become inert. Idempotent. EDT only.
	 */
	public void retire() {
		requireEdt();
		if (retired) return;
		retired = true;
		generation++;
		actionHandler = a -> { };
		onScopeChange = s -> { };
		snapshotListeners.clear();
		appearanceListeners.clear();
	}

	/** True once {@link #retire()} has run. */
	public boolean retired() { return retired; }

	/** A new binding generation: earlier queued publications are refused when they arrive. EDT only. */
	public int bind() {
		requireEdt();
		if (retired) throw new IllegalStateException("dispatcher retired: the assembled session was disposed");
		return ++generation;
	}

	/** Applies a publication now. EDT only. Returns false when it was refused. */
	public boolean publish(FixtureSnapshot s) {
		requireEdt();
		if (retired || s.generation() != generation) { rejected++; return false; }
		boolean newScope = current == null || !current.scope().equals(s.scope());
		if (!newScope && s.revision() <= current.revision()) { rejected++; return false; }
		current = s;
		if (newScope) onScopeChange.accept(s);
		else snapshotListeners.forEach(l -> l.accept(s));
		return true;
	}

	/** Queues a publication from any thread; it is validated when it runs on the EDT. */
	public void publishLater(FixtureSnapshot s) { SwingUtilities.invokeLater(() -> publish(s)); }

	public Subscription onSnapshot(Consumer<FixtureSnapshot> l) {
		snapshotListeners.add(l);
		return () -> snapshotListeners.remove(l);
	}

	public Subscription onAppearance(Consumer<Appearance> l) {
		appearanceListeners.add(l);
		return () -> appearanceListeners.remove(l);
	}

	public int listenerCount() { return snapshotListeners.size() + appearanceListeners.size(); }

	/** Preferences only; never touches observations. EDT only. */
	public void setAppearance(Appearance a) {
		requireEdt();
		if (retired || a.equals(appearance)) return;
		appearance = a;
		appearanceListeners.forEach(l -> l.accept(a));
	}

	public void dispatch(FixtureAction a) {
		requireEdt();
		Objects.requireNonNull(a);
		if (!retired) actionHandler.accept(a);
	}

	private static void requireEdt() {
		if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("EDT only");
	}
}
