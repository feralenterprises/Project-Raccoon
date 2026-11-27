package com.feralenterprises.raccoon.ui.integration;

import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.ui.component.EquipmentGrid;
import com.feralenterprises.raccoon.ui.component.InventoryGrid;
import com.feralenterprises.raccoon.ui.component.StateNotice;
import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.shell.PageHost;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.workbench.Offscreen;
import com.feralenterprises.raccoon.workbench.Workbench;
import com.feralenterprises.raccoon.workbench.WorkbenchPages;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * UI-003: no stale account information through mounting, detaching, rebinding, queued snapshots or disposal. Uses
 * the real FR-002 AccountLifecycle (foundation core.account tree pinned in ui-pins.properties) on a simulated client thread. Optional
 * argument: an output directory for screenshots of each state.
 */
public final class ExposureTests {
	private static final List<String> failures = new ArrayList<>();
	private static int passed;
	private static final ExecutorService clientThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "simulated-client-thread"));
	private static File shots;

	public static void main(String[] args) throws Exception {
		shots = args.length > 0 ? new File(args[0]) : null;
		try {
			detachWhileMounted();
			initialMount();
			rebindWithCollidingScopes();
			doubleAttachRefused();
			queuedSnapshotsAcrossRebind();
			shellDisposeCancelsBinding();
			generationGuardSameLifecycle();
			cancelWaitsForDeliveryRound();
		} catch (Throwable t) {
			t.printStackTrace();
			failures.add("exception: " + t);
		}
		clientThread.shutdownNow();
		System.out.println("ExposureTests: " + passed + " passed, " + failures.size() + " failed");
		failures.forEach(f -> System.out.println("  FAIL " + f));
		System.exit(failures.isEmpty() ? 0 : 1);
	}

	static void check(String name, boolean ok) { if (ok) passed++; else failures.add(name); }

	static void onClient(Runnable r) throws Exception { clientThread.submit(r).get(5, TimeUnit.SECONDS); }

	static void drain() throws Exception { for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> { }); }

	static <T> T edt(java.util.concurrent.Callable<T> c) throws Exception {
		AtomicReference<T> r = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> { try { r.set(c.call()); } catch (Exception e) { throw new RuntimeException(e); } });
		return r.get();
	}

	static WorkbenchPages gated() throws Exception {
		return edt(() -> {
			WorkbenchPages p = new WorkbenchPages(Workbench.context(Paths.get("review-assets")), true);
			p.shell.navigator().navigate(WorkbenchPages.GEAR);
			Offscreen.size(p.shell, 640);
			return p;
		});
	}

	static LifecycleBinding bind(SimulatedClient c, WorkbenchPages p) throws Exception {
		return edt(() -> {
			LifecycleBinding b = new LifecycleBinding(c.lifecycle(), p.shell);
			p.shell.addScopeListener(scope -> b.apply((AccountScope) scope, p::deliverFixture));
			return b;
		});
	}

	static void shot(WorkbenchPages p, String name) throws Exception {
		if (shots == null) return;
		SwingUtilities.invokeAndWait(() -> {
			Offscreen.size(p.shell, 640);
			try { Offscreen.write(Offscreen.paint(p.shell, 1.0), new File(shots, name + ".png")); } catch (Exception e) { throw new RuntimeException(e); }
		});
	}

	/** True if no grid anywhere under the shell or the given old page still holds an item. */
	static boolean noItems(Component c) {
		if (c instanceof EquipmentGrid && !((EquipmentGrid) c).model().items().isEmpty()) return false;
		if (c instanceof InventoryGrid && !((InventoryGrid) c).model().slots().stream().allMatch(Objects::isNull)) return false;
		if (c instanceof Container) for (Component k : ((Container) c).getComponents()) if (!noItems(k)) return false;
		return true;
	}

	static void detachWhileMounted() throws Exception {
		SimulatedClient c = new SimulatedClient();
		WorkbenchPages p = gated();
		LifecycleBinding b = bind(c, p);
		onClient(() -> { c.start(); c.login(SimulatedClient.FIXTURE_A); });
		drain();
		shot(p, "01-bound-logged-in");
		PageHost oldGear = edt(() -> p.shell.host(WorkbenchPages.GEAR));
		check("detach: precondition, account data visible", p.shell.presence() == null && !edt(() -> noItems(oldGear)));
		// aefb1d79 adds AccountLifecycle.subscriberCount(): cancellation is now asserted directly, not inferred.
		check("detach: exactly one live subscription while bound (subscriberCount)", c.lifecycle().subscriberCount() == 1);
		AccountScope scope = edt(() -> b.currentScope().orElse(null));
		// Detach and inspect within the same EDT task: nothing may wait for a later event.
		String[] seen = new String[1];
		boolean[] cleared = new boolean[1];
		SwingUtilities.invokeAndWait(() -> {
			b.dispose();
			RaccoonShell s = p.shell;
			seen[0] = s.currentHost().getName();
			cleared[0] = s.presence() == StateNotice.Presence.UNAVAILABLE && s.scope() == null && s.accountSource() == null
				&& s.host(WorkbenchPages.GEAR) == null && noItems(oldGear) && noItems(s);
		});
		check("detach: same EDT task shows the neutral UNAVAILABLE notice", "notice:UNAVAILABLE".equals(seen[0]));
		check("detach: same EDT task cleared every value, page and scope", cleared[0]);
		check("detach: the subscription is cancelled (subscriberCount 0)", c.lifecycle().subscriberCount() == 0);
		shot(p, "02-detached-neutral");
		int enq = b.enqueued();
		onClient(c::regionChange);
		drain();
		check("detach: lifecycle activity after detach reaches nothing", b.enqueued() == enq && p.shell.presence() == StateNotice.Presence.UNAVAILABLE);
		check("detach: values for the old scope are refused", !edt(() -> b.apply(scope, p::deliverFixture)));
		edt(() -> { p.tick(); p.setGearState(DataState.ACTIVE); return null; });
		check("detach: data source controls cannot resurface data (page not rebuilt while neutral)", edt(() -> p.shell.host(WorkbenchPages.GEAR)) == null
			&& edt(() -> p.shell.currentHost().getName()).equals("notice:UNAVAILABLE"));
		boolean threw = false;
		try { b.dispose(); } catch (IllegalStateException e) { threw = true; }  // called from the test thread
		check("detach: dispose off the EDT is refused (it could not clear immediately)", threw);
		edt(() -> { p.shell.dispose(); return null; });
	}

	static void initialMount() throws Exception {
		WorkbenchPages p = gated();
		check("mount: unbound gated shell is neutral and builds no account page",
			p.shell.presence() == StateNotice.Presence.UNAVAILABLE && edt(() -> p.shell.host(WorkbenchPages.GEAR)) == null);
		shot(p, "03-mounted-unbound");
		SimulatedClient c = new SimulatedClient();
		onClient(() -> { c.start(); c.login(SimulatedClient.FIXTURE_A); });
		boolean[] neutralBeforeFirstSnapshot = new boolean[1];
		LifecycleBinding[] b = new LifecycleBinding[1];
		SwingUtilities.invokeAndWait(() -> {
			b[0] = new LifecycleBinding(c.lifecycle(), p.shell);
			p.shell.addScopeListener(scope -> b[0].apply((AccountScope) scope, p::deliverFixture));
			// The lifecycle is already publishable, but its snapshot is only queued: still neutral, no page.
			neutralBeforeFirstSnapshot[0] = p.shell.presence() == StateNotice.Presence.UNAVAILABLE && p.shell.host(WorkbenchPages.GEAR) == null;
		});
		check("mount: binding mid-session stays neutral until its snapshot is processed", neutralBeforeFirstSnapshot[0]);
		drain();
		check("mount: then publishes the current account", p.shell.presence() == null && edt(() -> p.equipment.model().state()) == DataState.ACTIVE);
		edt(() -> { b[0].dispose(); p.shell.dispose(); return null; });
	}

	static void rebindWithCollidingScopes() throws Exception {
		WorkbenchPages p = gated();
		SimulatedClient c1 = new SimulatedClient(), c2 = new SimulatedClient();
		LifecycleBinding b1 = bind(c1, p);
		onClient(() -> { c1.start(); c1.login(SimulatedClient.FIXTURE_A); });
		drain();
		edt(() -> { p.gearLoadoutSelector.setSelected(1); return null; });
		PageHost firstPage = edt(() -> p.shell.host(WorkbenchPages.GEAR));
		AccountScope s1 = edt(() -> b1.currentScope().orElse(null));
		edt(() -> { b1.dispose(); return null; });
		LifecycleBinding b2 = bind(c2, p);
		onClient(() -> { c2.start(); c2.login(SimulatedClient.FIXTURE_B); });
		drain();
		AccountScope s2 = edt(() -> b2.currentScope().orElse(null));
		// Accepted contract 41eb2ac: scopes are namespaced per lifecycle instance (was a UI-003 finding at 9e625d2).
		check("rebind: scopes from two lifecycle instances are never equal (namespaced at 41eb2ac)",
			s1 != null && s2 != null && !s1.equals(s2) && !s1.sameEpoch(s2) && !s1.sameLogin(s2));
		check("rebind: the new lifecycle's own gate rejects the foreign scope without the UI identity check",
			!edt(() -> { com.feralenterprises.raccoon.core.account.ConsumerScopeGate g = new com.feralenterprises.raccoon.core.account.ConsumerScopeGate(c2.lifecycle());
				g.onSnapshot(c2.lifecycle().snapshot()); return g.accepts(s1); }));
		check("rebind: page state from the first binding is not reused", edt(() -> p.shell.host(WorkbenchPages.GEAR)) != firstPage
			&& edt(() -> p.gearLoadoutSelector.selected()) == 0);
		check("rebind: a leftover value tagged with the old (equal) scope is refused", !edt(() -> b2.apply(s1, () -> { })));
		check("rebind: the new binding's own scope is accepted", edt(() -> b2.apply(s2, () -> { })));
		shot(p, "04-rebound-new-account");
		edt(() -> { b2.dispose(); p.shell.dispose(); return null; });
	}

	static void doubleAttachRefused() throws Exception {
		WorkbenchPages p = gated();
		SimulatedClient c1 = new SimulatedClient(), c2 = new SimulatedClient();
		LifecycleBinding b1 = bind(c1, p);
		boolean refused = edt(() -> {
			try { new LifecycleBinding(c2.lifecycle(), p.shell); return false; } catch (IllegalStateException e) { return true; }
		});
		check("double attach: a second binding is refused", refused);
		onClient(() -> { c2.start(); c2.login(SimulatedClient.FIXTURE_B); });
		drain();
		check("double attach: the refused binding never subscribed or reached the shell", p.shell.presence() == StateNotice.Presence.SIGNED_OUT
			|| p.shell.presence() == StateNotice.Presence.UNAVAILABLE);
		onClient(() -> { c1.start(); c1.login(SimulatedClient.FIXTURE_A); });
		drain();
		check("double attach: the first binding keeps working", p.shell.presence() == null && p.shell.accountSource() == b1);
		edt(() -> { b1.dispose(); p.shell.dispose(); return null; });
	}

	static void queuedSnapshotsAcrossRebind() throws Exception {
		WorkbenchPages p = gated();
		SimulatedClient c1 = new SimulatedClient(), c2 = new SimulatedClient();
		LifecycleBinding b1 = bind(c1, p);
		onClient(() -> { c1.start(); c1.login(SimulatedClient.FIXTURE_A); });
		drain();
		AccountScope s1 = edt(() -> b1.currentScope().orElse(null));
		CountDownLatch holding = new CountDownLatch(1), queued = new CountDownLatch(1);
		LifecycleBinding[] b2 = new LifecycleBinding[1];
		SwingUtilities.invokeLater(() -> {
			holding.countDown();
			try { queued.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { }
			b1.dispose();                                   // b1's region-change snapshots are queued behind this task
			b2[0] = new LifecycleBinding(c2.lifecycle(), p.shell);
		});
		holding.await(5, TimeUnit.SECONDS);
		int ignored = b1.ignoredAfterDispose();
		onClient(c1::regionChange);                         // enqueues b1 snapshots (awaiting identity, then publishable)
		queued.countDown();
		drain();
		check("queued: old binding's queued snapshots were ignored", b1.ignoredAfterDispose() >= ignored + 2);
		check("queued: shell follows only the new binding (its initial UNAUTHENTICATED state)", p.shell.presence() == StateNotice.Presence.SIGNED_OUT
			&& p.shell.accountSource() == b2[0] && edt(() -> p.shell.host(WorkbenchPages.GEAR)) == null);
		check("queued: old scope refused by the new binding", !edt(() -> b2[0].apply(s1, () -> { })));
		// A queued snapshot that lands after the shell is disposed does nothing and throws nothing.
		CountDownLatch h2 = new CountDownLatch(1), q2 = new CountDownLatch(1);
		SwingUtilities.invokeLater(() -> {
			h2.countDown();
			try { q2.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { }
			p.shell.dispose();
		});
		h2.await(5, TimeUnit.SECONDS);
		onClient(() -> { c2.start(); c2.login(SimulatedClient.FIXTURE_B); });
		q2.countDown();
		drain();
		check("queued: snapshots queued before shell disposal are ignored", b2[0].isDisposed() && p.shell.isDisposed() && p.shell.scope() == null);
	}

	/**
	 * Contract 41eb2ac "binding/disposal guard": work queued under one binding must not apply after a rebind even
	 * when its scope is still perfectly current (same lifecycle). Authority checks alone cannot catch this.
	 */
	static void generationGuardSameLifecycle() throws Exception {
		WorkbenchPages p = gated();
		SimulatedClient c = new SimulatedClient();
		LifecycleBinding b1 = bind(c, p);
		onClient(() -> { c.start(); c.login(SimulatedClient.FIXTURE_A); });
		drain();
		LifecycleBinding.Ticket queued = edt(b1::ticket);       // work captured under b1
		edt(() -> { b1.dispose(); return null; });
		LifecycleBinding b2 = bind(c, p);                       // rebind to the SAME lifecycle
		drain();
		check("guard: scope of the queued work is still current after rebind", edt(() -> c.lifecycle().isCurrent(queued.scope())));
		boolean[] ran = {false};
		boolean applied = edt(() -> b2.apply(queued, () -> ran[0] = true));
		check("guard: work queued under the previous binding is refused after rebind (generation changed)", !applied && !ran[0]);
		boolean fresh = edt(() -> b2.apply(b2.ticket(), () -> { }));
		check("guard: work captured under the current binding applies", fresh);
		edt(() -> { b2.dispose(); return null; });
		LifecycleBinding.Ticket afterDispose = queued;
		check("guard: nothing applies after dispose", !edt(() -> b2.apply(afterDispose, () -> { })));
		check("guard: generation increments on bind, detach and dispose", edt(() -> p.shell.accountGeneration()) >= 4);
		edt(() -> { p.shell.dispose(); return null; });
	}

	/** Contract 41eb2ac: cancel() waits for a delivery round in progress; afterwards our listener never runs again. */
	static void cancelWaitsForDeliveryRound() throws Exception {
		WorkbenchPages p = gated();
		SimulatedClient c = new SimulatedClient();
		LifecycleBinding b = bind(c, p);
		onClient(() -> { c.start(); c.login(SimulatedClient.FIXTURE_A); });
		drain();
		CountDownLatch inRound = new CountDownLatch(1), release = new CountDownLatch(1);
		com.feralenterprises.raccoon.core.account.Subscription slow = c.lifecycle().subscribe(s -> {
			if (s.revision() > 0 && inRound.getCount() > 0 && !s.isPublishable()) {
				inRound.countDown();
				try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { }
			}
		});
		java.util.concurrent.Future<?> round = clientThread.submit(c::hop);       // delivery round blocks in "slow"
		check("cancel: a delivery round is in progress", inRound.await(5, TimeUnit.SECONDS));
		long[] took = new long[1];
		Thread releaser = new Thread(() -> { try { Thread.sleep(300); } catch (InterruptedException e) { } release.countDown(); });
		releaser.start();
		int enqBefore = b.enqueued();
		edt(() -> { long t = System.nanoTime(); b.dispose(); took[0] = (System.nanoTime() - t) / 1_000_000; return null; });
		round.get(5, TimeUnit.SECONDS);
		onClient(c::regionChange);
		drain();
		check("cancel: dispose on the EDT waited for the round (" + took[0] + " ms) without deadlock", took[0] >= 200);
		check("cancel: no delivery reached the binding after dispose returned", b.enqueued() <= enqBefore + 1 && b.isDisposed()
			&& p.shell.presence() == StateNotice.Presence.UNAVAILABLE);
		slow.cancel();
		edt(() -> { p.shell.dispose(); return null; });
	}

	static void shellDisposeCancelsBinding() throws Exception {
		WorkbenchPages p = gated();
		SimulatedClient c = new SimulatedClient();
		LifecycleBinding b = bind(c, p);
		onClient(() -> { c.start(); c.login(SimulatedClient.FIXTURE_A); });
		drain();
		edt(() -> { p.shell.dispose(); return null; });
		int enq = b.enqueued();
		onClient(c::hop);
		drain();
		check("shell dispose: binding disposed and unsubscribed", b.isDisposed() && b.enqueued() == enq);
		check("shell dispose: binding dispose afterwards is harmless", edt(() -> { b.dispose(); return true; }));
	}
}
