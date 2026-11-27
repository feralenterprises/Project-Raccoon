package com.feralenterprises.raccoon.ui.integration;

import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.AccountState;
import com.feralenterprises.raccoon.ui.component.StateNotice;
import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.shell.PageHost;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.workbench.Offscreen;
import com.feralenterprises.raccoon.workbench.Workbench;
import com.feralenterprises.raccoon.workbench.WorkbenchPages;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * Drives the real FR-002 AccountLifecycle (foundation 9e625d2) on a simulated client thread and checks the shell
 * through {@link LifecycleBinding}: thread hand-off, publishability, scope continuity, stale-value rejection at
 * application time, and disposal. Headless; no RuneLite.
 */
public final class LifecycleBindingTests {
	private static final List<String> failures = new ArrayList<>();
	private static int passed;
	private static final ExecutorService clientThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "simulated-client-thread"));

	static WorkbenchPages pages;
	static RaccoonShell shell;
	static LifecycleBinding binding;
	static SimulatedClient client;

	public static void main(String[] args) throws Exception {
		try {
			run();
		} catch (Throwable t) {
			t.printStackTrace();
			failures.add("exception: " + t);
		}
		clientThread.shutdownNow();
		System.out.println("LifecycleBindingTests: " + passed + " passed, " + failures.size() + " failed");
		failures.forEach(f -> System.out.println("  FAIL " + f));
		System.exit(failures.isEmpty() ? 0 : 1);
	}

	static void check(String name, boolean ok) { if (ok) passed++; else failures.add(name); }

	static void onClient(Runnable r) throws Exception { clientThread.submit(r).get(5, TimeUnit.SECONDS); }

	static void drain() throws Exception {
		for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> { });
	}

	static <T> T edt(java.util.concurrent.Callable<T> c) throws Exception {
		AtomicReference<T> r = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> { try { r.set(c.call()); } catch (Exception e) { throw new RuntimeException(e); } });
		return r.get();
	}

	static void layout() throws Exception { SwingUtilities.invokeAndWait(() -> Offscreen.size(shell, 720)); }

	static String shownName() throws Exception { return edt(() -> shell.currentHost().getName()); }

	static void run() throws Exception {
		client = new SimulatedClient();
		SwingUtilities.invokeAndWait(() -> {
			pages = new WorkbenchPages(Workbench.context(Paths.get("review-assets")), true);
			shell = pages.shell;
			shell.navigator().navigate(WorkbenchPages.GEAR);
			binding = new LifecycleBinding(client.lifecycle(), shell);
			shell.addScopeListener(scope -> binding.apply((AccountScope) scope, pages::deliverFixture));
		});
		layout();
		drain();
		check("initial UNAUTHENTICATED snapshot shows the signed-out notice", shell.presence() == StateNotice.Presence.SIGNED_OUT
			&& shownName().equals("notice:SIGNED_OUT"));

		// Rule 4: the listener only enqueues; the shell changes on the EDT, never on the client thread.
		CountDownLatch edtBlocked = new CountDownLatch(1), release = new CountDownLatch(1);
		SwingUtilities.invokeLater(() -> { edtBlocked.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { } });
		edtBlocked.await(5, TimeUnit.SECONDS);
		int queuedBefore = binding.enqueued();
		onClient(() -> { client.start(); client.login(SimulatedClient.FIXTURE_A); });
		check("rule 4: client thread returned while the EDT was blocked (listener did not wait on the EDT)", binding.enqueued() > queuedBefore);
		check("rule 4: shell untouched until the EDT runs", shell.presence() == StateNotice.Presence.SIGNED_OUT);
		release.countDown();
		drain();
		layout();
		check("login: publishable snapshot shows the account page", shell.presence() == null && shownName().equals("page:" + WorkbenchPages.GEAR));
		check("login: values arrive only through the gate", edt(() -> pages.equipment.model().state()) == DataState.ACTIVE && binding.applied() >= 1);
		AccountScope scopeA1 = edt(() -> binding.currentScope().orElse(null));
		int discards0 = shell.pageDiscards();  // attach resets (one discard) before any publication
		PageHost firstHost = edt(() -> shell.host(WorkbenchPages.GEAR));

		// Region change: ACTIVE awaiting identity is not publishable (rule 1), then same-epoch scope (rule 2).
		AtomicBoolean sawWaiting = new AtomicBoolean();
		SwingUtilities.invokeAndWait(() -> shell.addScopeListener(s -> { }));
		onClient(() -> client.phase(com.feralenterprises.raccoon.core.account.ClientPhase.LOADING));
		drain();
		sawWaiting.set(shell.presence() == StateNotice.Presence.WAITING);
		check("region change: awaiting-identity snapshot hides data with the WAITING notice", sawWaiting.get());
		check("region change: values dropped while not publishable",
			edt(() -> pages.equipment.model().items().isEmpty() && pages.inventory.model().slots().stream().allMatch(Objects::isNull)));
		onClient(() -> { client.phase(com.feralenterprises.raccoon.core.account.ClientPhase.LOGGED_IN); client.tick(); });
		drain();
		layout();
		AccountScope scopeA2 = edt(() -> binding.currentScope().orElse(null));
		check("region change: new scope with the same epoch", scopeA2 != null && !scopeA2.equals(scopeA1) && scopeA2.sameEpoch(scopeA1));
		check("region change: page state kept (same instance, no discard)", edt(() -> shell.host(WorkbenchPages.GEAR)) == firstHost && shell.pageDiscards() == discards0);
		check("region change: values re-delivered for the new scope", edt(() -> pages.equipment.model().state()) == DataState.ACTIVE);
		check("rule 5: a value tagged with the old scope is rejected", !edt(() -> binding.apply(scopeA1, () -> { })));

		// World hop: SUSPENDED_CONTINUITY, then a new epoch -> page discarded.
		onClient(() -> client.phase(com.feralenterprises.raccoon.core.account.ClientPhase.HOPPING));
		drain();
		check("hop: suspended continuity shows RECONNECTING", shell.presence() == StateNotice.Presence.RECONNECTING);
		onClient(() -> { client.phase(com.feralenterprises.raccoon.core.account.ClientPhase.LOADING); client.phase(com.feralenterprises.raccoon.core.account.ClientPhase.LOGGED_IN); client.tick(); });
		drain();
		layout();
		AccountScope scopeA3 = edt(() -> binding.currentScope().orElse(null));
		check("hop: new epoch, same login", scopeA3 != null && !scopeA3.sameEpoch(scopeA2) && scopeA3.sameLogin(scopeA2));
		check("hop: account page discarded and rebuilt", edt(() -> shell.host(WorkbenchPages.GEAR)) != firstHost && shell.pageDiscards() == discards0 + 1);
		check("hop: rebuilt page received data for the new scope", edt(() -> pages.equipment.model().state()) == DataState.ACTIVE);

		// Rule 5 at application time: the retirement snapshot is still queued, yet the old value is rejected.
		CountDownLatch inEdt = new CountDownLatch(1), loggedOut = new CountDownLatch(1);
		AtomicBoolean appliedStale = new AtomicBoolean(true);
		AtomicReference<StateNotice.Presence> presenceAtApply = new AtomicReference<>();
		SwingUtilities.invokeLater(() -> {
			inEdt.countDown();
			try { loggedOut.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { }
			presenceAtApply.set(shell.presence());
			appliedStale.set(binding.apply(scopeA3, pages::deliverFixture));
		});
		inEdt.await(5, TimeUnit.SECONDS);
		onClient(client::logout);
		loggedOut.countDown();
		drain();
		check("rule 5: logout snapshot still queued when the value arrived", presenceAtApply.get() == null);
		check("rule 5: gate consulted the lifecycle and rejected the value", !appliedStale.get());
		check("logout: RETIRED shows the signed-out notice", shell.presence() == StateNotice.Presence.SIGNED_OUT
			&& client.lifecycle().state() == AccountState.RETIRED);

		// A different account: new login, everything from A rejected.
		onClient(() -> client.login(SimulatedClient.FIXTURE_B));
		drain();
		layout();
		AccountScope scopeB = edt(() -> binding.currentScope().orElse(null));
		check("new login: different account scope", scopeB != null && scopeB.accountId() != scopeA3.accountId() && !scopeB.sameLogin(scopeA3));
		check("new login: page discarded again", shell.pageDiscards() == discards0 + 2);
		check("new login: A's scope rejected", !edt(() -> binding.apply(scopeA3, () -> { })));
		check("new login: older revisions never re-applied", binding.staleDropped() == 0 && binding.handled() >= 8);
		check("no account hash shown anywhere", !edt(() -> textOf(shell)).contains(Long.toString(SimulatedClient.FIXTURE_B)));

		// Disposal: subscription cancelled, queued and later snapshots ignored, shell listeners released.
		CountDownLatch holding = new CountDownLatch(1), queued = new CountDownLatch(1);
		SwingUtilities.invokeLater(() -> {
			holding.countDown();
			try { queued.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { }
			binding.dispose();
		});
		holding.await(5, TimeUnit.SECONDS);
		int ignoredBefore = binding.ignoredAfterDispose();
		onClient(client::logout);
		queued.countDown();
		drain();
		check("dispose: a snapshot queued before dispose but delivered after is ignored",
			binding.isDisposed() && binding.ignoredAfterDispose() == ignoredBefore + 1
				&& shell.presence() == StateNotice.Presence.UNAVAILABLE && shell.accountSource() == null);
		int enq = binding.enqueued();
		onClient(() -> client.login(SimulatedClient.FIXTURE_A));
		drain();
		check("dispose: no deliveries after cancel()", binding.enqueued() == enq);
		check("dispose: apply refuses after dispose", !edt(() -> binding.apply(scopeB, () -> { })));
		SwingUtilities.invokeAndWait(shell::dispose);
		check("dispose: shell disposed after binding", shell.isDisposed());
	}

	static String textOf(java.awt.Component c) {
		StringBuilder sb = new StringBuilder();
		if (c instanceof com.feralenterprises.raccoon.ui.component.TextBlock) sb.append(((com.feralenterprises.raccoon.ui.component.TextBlock) c).text()).append('\n');
		if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) sb.append(textOf(k));
		return sb.toString();
	}
}
