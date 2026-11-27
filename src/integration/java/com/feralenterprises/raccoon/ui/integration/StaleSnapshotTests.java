package com.feralenterprises.raccoon.ui.integration;

import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.ClientPhase;
import com.feralenterprises.raccoon.ui.component.StateNotice;
import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.shell.PageHost;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.workbench.Offscreen;
import com.feralenterprises.raccoon.workbench.Workbench;
import com.feralenterprises.raccoon.workbench.WorkbenchPages;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * UI-STALE-SNAPSHOT regressions (Solis QA of UI-003..UI-005). A publishable snapshot that was queued for the EDT
 * while the lifecycle moved on (logout, account switch, hop, revalidation) must not reopen account pages, must not
 * be announced to scope listeners, and {@link LifecycleBinding#currentScope} must never report a retired authority.
 * A snapshot that is still current when it reaches the EDT must still publish normally.
 *
 * <p>Technique: the EDT is held while the simulated client thread drives the real FR-002 lifecycle, so snapshots
 * queue up; probes posted between client steps run on the EDT exactly between the queued snapshots and record what
 * the shell shows at that point. Headless; synthetic identities only.
 */
public final class StaleSnapshotTests {
	/** Synchronized: the EDT and the uncaught-exception handler also record failures. */
	private static final List<String> failures = Collections.synchronizedList(new ArrayList<>());
	private static int passed;
	private static final ExecutorService clientThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "simulated-client-thread"));

	public static void main(String[] args) throws Exception {
		// Any exception escaping on the EDT (or another thread) fails the suite instead of being printed and ignored.
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> failures.add("uncaught on " + t.getName() + ": " + e));
		try {
			queuedLoginThenLogout();
			currentScopeBeforeRetirementIsDelivered();
			accountSwitchAToB();
			hopAndObservationEpochChanges();
			validCurrentPublication();
			disposedBindingReportsNoScope();
		} catch (Throwable t) {
			t.printStackTrace();
			failures.add("exception: " + t);
		}
		clientThread.shutdownNow();
		System.out.println("StaleSnapshotTests: " + passed + " passed, " + failures.size() + " failed");
		failures.forEach(f -> System.out.println("  FAIL " + f));
		System.exit(failures.isEmpty() ? 0 : 1);
	}

	static void check(String name, boolean ok) { if (ok) passed++; else failures.add(name); }

	/** Awaits on the test thread; a timeout is a failure, never ignored. */
	static void await(String what, CountDownLatch l) throws InterruptedException {
		if (!l.await(5, TimeUnit.SECONDS)) failures.add("timeout: " + what);
	}

	/** Awaits on the EDT (inside a held runnable); a timeout or interrupt is a failure. */
	static void awaitOnEdt(String what, CountDownLatch l) {
		try {
			if (!l.await(5, TimeUnit.SECONDS)) failures.add("timeout on EDT: " + what);
		} catch (InterruptedException e) {
			failures.add("interrupted on EDT: " + what);
			Thread.currentThread().interrupt();
		}
	}

	/** Equipment state, or null when the pages or the equipment page are absent. EDT only. */
	static DataState equipmentState(WorkbenchPages pages) {
		if (pages == null || pages.equipment == null || pages.equipment.model() == null) return null;
		return pages.equipment.model().state();
	}

	static void onClient(Runnable r) throws Exception { clientThread.submit(r).get(5, TimeUnit.SECONDS); }

	static void drain() throws Exception { for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> { }); }

	static <T> T edt(java.util.concurrent.Callable<T> c) throws Exception {
		AtomicReference<T> r = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> { try { r.set(c.call()); } catch (Exception e) { throw new RuntimeException(e); } });
		return r.get();
	}

	/** One shell + binding on a fresh lifecycle. Records every scope announced to listeners and whether it was live. */
	static final class Fixture {
		final SimulatedClient client = new SimulatedClient();
		WorkbenchPages pages;
		RaccoonShell shell;
		LifecycleBinding binding;
		int announced, announcedRetired;

		Fixture() throws Exception {
			SwingUtilities.invokeAndWait(() -> {
				pages = new WorkbenchPages(Workbench.context(Paths.get("review-assets")), true);
				shell = pages.shell;
				shell.navigator().navigate(WorkbenchPages.GEAR);
				binding = new LifecycleBinding(client.lifecycle(), shell);
				shell.addScopeListener(scope -> {
					announced++;
					if (!client.lifecycle().isCurrent((AccountScope) scope)) announcedRetired++;
					binding.apply((AccountScope) scope, pages::deliverFixture);
				});
			});
			SwingUtilities.invokeAndWait(() -> Offscreen.size(shell, 720));
			drain();
		}

		void close() throws Exception { SwingUtilities.invokeAndWait(shell::dispose); }

		/** What the shell shows at one point in the EDT queue. */
		final class Probe {
			final CountDownLatch done = new CountDownLatch(1);
			volatile StateNotice.Presence presence;
			volatile String shown;
			volatile AccountScope current;
			volatile boolean currentIsLive;
			volatile DataState equipment;

			Probe post() {
				SwingUtilities.invokeLater(() -> {
					try {
						presence = shell.presence();
						shown = shell.currentHost() == null ? null : shell.currentHost().getName();
						current = binding.currentScope().orElse(null);
						currentIsLive = current != null && client.lifecycle().isCurrent(current);
						equipment = equipmentState(pages);
					} catch (Throwable t) {
						failures.add("EDT exception in probe: " + t);
					} finally {
						done.countDown();
					}
				});
				return this;
			}

			boolean accountPageOpen() { return presence == null && ("page:" + WorkbenchPages.GEAR).equals(shown); }
			boolean noRetiredScope() { return current == null || currentIsLive; }
		}

		Probe probe() { return new Probe().post(); }

		/** Holds the EDT until the returned latch is released. */
		CountDownLatch holdEdt() throws Exception {
			CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
			SwingUtilities.invokeLater(() -> { held.countDown(); awaitOnEdt("held EDT release", release); });
			await("EDT held", held);
			return release;
		}
	}

	/** S1: login snapshots still queued when the player logs out must not reopen account pages. */
	static void queuedLoginThenLogout() throws Exception {
		Fixture f = new Fixture();
		CountDownLatch release = f.holdEdt();
		onClient(() -> { f.client.start(); f.client.login(SimulatedClient.FIXTURE_A); });
		Fixture.Probe afterLogin = f.probe();       // runs after A's queued login snapshots, before the logout snapshot
		onClient(f.client::logout);
		release.countDown();
		await("probe afterLogin", afterLogin.done);
		drain();
		check("S1 queued login->logout: account page not reopened by retired login snapshot", !afterLogin.accountPageOpen());
		check("S1 queued login->logout: notice reflects the live authority (SIGNED_OUT)", afterLogin.presence == StateNotice.Presence.SIGNED_OUT);
		check("S1 queued login->logout: currentScope never reports retired authority", afterLogin.noRetiredScope());
		check("S1 queued login->logout: no account values delivered", afterLogin.equipment != DataState.ACTIVE);
		check("S1 queued login->logout: retired scope never announced to scope listeners", f.announcedRetired == 0);
		check("S1 final: signed out, no scope", f.shell.presence() == StateNotice.Presence.SIGNED_OUT && !edt(() -> f.binding.currentScope().isPresent()));
		check("S1 final: no publication at all (only retired authorities were queued)", f.announced == 0);
		f.close();
	}

	/** S2: after logout on the client thread, currentScope on the EDT must not report A before RETIRED is delivered. */
	static void currentScopeBeforeRetirementIsDelivered() throws Exception {
		Fixture f = new Fixture();
		onClient(() -> { f.client.start(); f.client.login(SimulatedClient.FIXTURE_A); });
		drain();
		AccountScope a = edt(() -> f.binding.currentScope().orElse(null));
		check("S2 precondition: A published and live", a != null && f.client.lifecycle().isCurrent(a));
		CountDownLatch inEdt = new CountDownLatch(1), loggedOut = new CountDownLatch(1);
		AtomicReference<Object> during = new AtomicReference<>("unset");
		SwingUtilities.invokeLater(() -> {
			inEdt.countDown();
			awaitOnEdt("S2 logout on client", loggedOut);
			during.set(f.binding.currentScope().orElse(null));
		});
		await("S2 EDT entered", inEdt);
		onClient(f.client::logout);
		loggedOut.countDown();
		drain();
		check("S2 logout queued: currentScope empty once the lifecycle retired A", during.get() == null);
		check("S2 final: SIGNED_OUT", f.shell.presence() == StateNotice.Presence.SIGNED_OUT);
		f.close();
	}

	/** S3: A -> B switching with A's login still queued; A never shown, B shown with its own scope and fresh pages. */
	static void accountSwitchAToB() throws Exception {
		Fixture f = new Fixture();
		int discards0 = f.shell.pageDiscards();
		CountDownLatch release = f.holdEdt();
		onClient(() -> { f.client.start(); f.client.login(SimulatedClient.FIXTURE_A); });
		Fixture.Probe afterA = f.probe();
		onClient(() -> { f.client.logout(); f.client.login(SimulatedClient.FIXTURE_B); });
		Fixture.Probe afterB = f.probe();
		release.countDown();
		await("probe afterA", afterA.done);
		await("probe afterB", afterB.done);
		drain();
		AccountScope b = edt(() -> f.binding.currentScope().orElse(null));
		check("S3 A->B: retired A login snapshot does not open account pages", !afterA.accountPageOpen());
		check("S3 A->B: currentScope never reports retired A", afterA.noRetiredScope());
		check("S3 A->B: hidden as WAITING while B's publication is still queued", afterA.presence == StateNotice.Presence.WAITING);
		check("S3 A->B: B (live) published once its snapshot arrives", afterB.accountPageOpen() && afterB.currentIsLive);
		check("S3 A->B: B's values delivered", afterB.equipment == DataState.ACTIVE);
		check("S3 A->B: final scope is B and live", b != null && f.client.lifecycle().isCurrent(b));
		check("S3 A->B: A's retired scope never announced", f.announcedRetired == 0 && f.announced == 1);
		check("S3 A->B: no extra page discard (attach reset only; first publication)", f.shell.pageDiscards() == discards0);

		// Shown A, then a queued switch: A's re-validated snapshot is overtaken by B.
		onClient(() -> { f.client.logout(); f.client.login(SimulatedClient.FIXTURE_A); });
		drain();
		AccountScope a2 = edt(() -> f.binding.currentScope().orElse(null));
		PageHost aHost = edt(() -> f.shell.host(WorkbenchPages.GEAR));
		int discards1 = f.shell.pageDiscards();
		release = f.holdEdt();
		onClient(f.client::regionChange);           // queues A (new authority, same epoch)
		Fixture.Probe afterA2 = f.probe();
		onClient(() -> { f.client.logout(); f.client.login(SimulatedClient.FIXTURE_B); });
		release.countDown();
		await("probe afterA2", afterA2.done);
		drain();
		AccountScope b2 = edt(() -> f.binding.currentScope().orElse(null));
		check("S3 shown A->B: precondition A was live", a2 != null);
		check("S3 shown A->B: retired A revalidation does not reopen A's pages", !afterA2.accountPageOpen() && afterA2.noRetiredScope());
		check("S3 shown A->B: B published with B's scope", b2 != null && !b2.sameLogin(a2) && f.client.lifecycle().isCurrent(b2));
		check("S3 shown A->B: A's pages discarded for B", edt(() -> f.shell.host(WorkbenchPages.GEAR)) != aHost && f.shell.pageDiscards() == discards1 + 1);
		check("S3 shown A->B: no retired scope ever announced", f.announcedRetired == 0);
		f.close();
	}

	/** S4: hop and observation-epoch/authority changes overtaking queued publications. */
	static void hopAndObservationEpochChanges() throws Exception {
		Fixture f = new Fixture();
		onClient(() -> { f.client.start(); f.client.login(SimulatedClient.FIXTURE_A); });
		drain();
		AccountScope a1 = edt(() -> f.binding.currentScope().orElse(null));
		PageHost host1 = edt(() -> f.shell.host(WorkbenchPages.GEAR));
		int discards0 = f.shell.pageDiscards();

		// Two queued same-epoch revalidations: the first is retired by the second before it reaches the EDT.
		CountDownLatch release = f.holdEdt();
		onClient(f.client::regionChange);
		Fixture.Probe afterFirst = f.probe();
		onClient(f.client::regionChange);
		release.countDown();
		await("probe afterFirst", afterFirst.done);
		drain();
		AccountScope a3 = edt(() -> f.binding.currentScope().orElse(null));
		check("S4 revalidation: retired same-epoch authority not published", !afterFirst.accountPageOpen() && afterFirst.noRetiredScope());
		check("S4 revalidation: final authority live, same epoch as A1", a3 != null && f.client.lifecycle().isCurrent(a3) && a3.sameEpoch(a1) && !a3.equals(a1));
		check("S4 revalidation: page state kept across the skipped authority", edt(() -> f.shell.host(WorkbenchPages.GEAR)) == host1 && f.shell.pageDiscards() == discards0);
		check("S4 revalidation: values re-delivered for live authority", edt(() -> equipmentState(f.pages)) == DataState.ACTIVE);

		// Region change queued, then a hop (new epoch) before the EDT runs.
		release = f.holdEdt();
		onClient(f.client::regionChange);
		Fixture.Probe afterRegion = f.probe();
		onClient(f.client::hop);
		release.countDown();
		await("probe afterRegion", afterRegion.done);
		drain();
		AccountScope hopped = edt(() -> f.binding.currentScope().orElse(null));
		check("S4 hop: pre-hop authority not published after the hop", !afterRegion.accountPageOpen() && afterRegion.noRetiredScope());
		check("S4 hop: new epoch, same login, live", hopped != null && !hopped.sameEpoch(a3) && hopped.sameLogin(a3) && f.client.lifecycle().isCurrent(hopped));
		check("S4 hop: pages discarded exactly once for the new epoch", edt(() -> f.shell.host(WorkbenchPages.GEAR)) != host1 && f.shell.pageDiscards() == discards0 + 1);

		// Hop queued, then connection lost (suspended) before the EDT runs: the hop publication is not current.
		release = f.holdEdt();
		onClient(f.client::hop);
		Fixture.Probe afterHop = f.probe();
		onClient(f.client::connectionLost);
		release.countDown();
		await("probe afterHop", afterHop.done);
		drain();
		check("S4 hop->connection lost: queued hop publication does not reopen pages", !afterHop.accountPageOpen() && afterHop.noRetiredScope());
		check("S4 hop->connection lost: live suspension shown as RECONNECTING", afterHop.presence == StateNotice.Presence.RECONNECTING
			&& f.shell.presence() == StateNotice.Presence.RECONNECTING);
		check("S4: no retired scope ever announced", f.announcedRetired == 0);
		f.close();
	}

	/** S5: a publication that is still current when it reaches the EDT is shown, announced and applied as before. */
	static void validCurrentPublication() throws Exception {
		Fixture f = new Fixture();
		CountDownLatch release = f.holdEdt();
		onClient(() -> { f.client.start(); f.client.login(SimulatedClient.FIXTURE_A); });
		Fixture.Probe queued = f.probe();
		release.countDown();
		await("probe queued", queued.done);
		drain();
		check("S5 valid queued publication: account page shown", queued.accountPageOpen());
		check("S5 valid queued publication: currentScope live", queued.current != null && queued.currentIsLive);
		check("S5 valid queued publication: values delivered", queued.equipment == DataState.ACTIVE);
		check("S5 valid queued publication: announced exactly once, never retired", f.announced == 1 && f.announcedRetired == 0);
		AccountScope a1 = edt(() -> f.binding.currentScope().orElse(null));
		check("S5 apply accepts the live published scope", edt(() -> f.binding.apply(a1, () -> { })));
		check("S5 ticket for the live scope applies", edt(() -> f.binding.apply(f.binding.ticket(), () -> { })));

		PageHost host = edt(() -> f.shell.host(WorkbenchPages.GEAR));
		onClient(f.client::regionChange);
		drain();
		AccountScope a2 = edt(() -> f.binding.currentScope().orElse(null));
		check("S5 unqueued region change: republished for new live authority", a2 != null && f.client.lifecycle().isCurrent(a2) && a2.sameEpoch(a1));
		check("S5 unqueued region change: page kept, values active", edt(() -> f.shell.host(WorkbenchPages.GEAR)) == host
			&& edt(() -> equipmentState(f.pages)) == DataState.ACTIVE && f.shell.presence() == null);
		check("S5 old authority rejected after revalidation", !edt(() -> f.binding.apply(a1, () -> { })));
		check("S5 no retired scope ever announced", f.announcedRetired == 0 && f.announced == 2);
		f.close();
	}

	/** S6: a disposed binding reports no scope (its authority over the shell is retired). */
	static void disposedBindingReportsNoScope() throws Exception {
		Fixture f = new Fixture();
		onClient(() -> { f.client.start(); f.client.login(SimulatedClient.FIXTURE_A); });
		drain();
		check("S6 precondition: scope present before dispose", edt(() -> f.binding.currentScope().isPresent()));
		SwingUtilities.invokeAndWait(f.binding::dispose);
		check("S6 dispose: currentScope empty after dispose", !edt(() -> f.binding.currentScope().isPresent()));
		check("S6 dispose: shell neutral", f.shell.presence() == StateNotice.Presence.UNAVAILABLE && f.shell.accountSource() == null);
		f.close();
	}
}
