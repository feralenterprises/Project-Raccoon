package com.feralenterprises.raccoon.ui.integration;

import com.feralenterprises.raccoon.core.account.AccountLifecycleView;
import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.AccountSnapshot;
import com.feralenterprises.raccoon.core.account.ConsumerScopeGate;
import com.feralenterprises.raccoon.core.account.Subscription;
import com.feralenterprises.raccoon.ui.component.StateNotice;
import com.feralenterprises.raccoon.ui.lifecycle.Disposable;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

/**
 * Connects the FR-002 account lifecycle (foundation 9e625d2) to a {@link RaccoonShell}, following
 * docs/ui-integration.md of that commit:
 * <ul>
 * <li>Rule 4: the listener runs on the driving thread under the delivery lock, so it only enqueues to the EDT.</li>
 * <li>On the EDT, {@link ConsumerScopeGate#onSnapshot} drops older revisions; publishable snapshots show account data
 * for their scope, everything else (including ACTIVE awaiting a fresh identity) hides it (rules 1, 9).</li>
 * <li>Rule 2: scope continuity is {@link AccountScope#sameEpoch}: a region change keeps page state, a hop, reconnect
 * or new login discards it.</li>
 * <li>Rules 3 and 5: values are applied only through {@link #apply}, which asks the gate (and through it the lifecycle,
 * at application time) whether the value's scope is still current.</li>
 * <li>Rule 10: {@link #dispose} cancels the subscription; snapshots already queued are ignored.</li>
 * <li>UI-003: the binding is the shell's single attached account source. Attaching resets the shell to the neutral
 * state; {@link #dispose} detaches and immediately clears every account value and page (neutral UNAVAILABLE notice).
 * Disposing the shell disposes the binding. Construction and disposal must run on the EDT.</li>
 * <li>UI-STALE-SNAPSHOT: a publishable snapshot is revalidated against the live lifecycle when it reaches the EDT.
 * If its authority was retired while it was queued (logout, account switch, hop, revalidation), it is treated as not
 * publishable: account data stays hidden, with the notice for the live state, and the newer snapshot already queued
 * publishes the live scope. {@link #currentScope} reports only a live scope of an attached binding.</li>
 * </ul>
 * Never displays scope, account id or hash.
 */
public final class LifecycleBinding implements Disposable {
	private final RaccoonShell shell;
	private final AccountLifecycleView view;
	private final ConsumerScopeGate gate;
	private final Subscription subscription;
	private final AtomicInteger enqueued = new AtomicInteger();
	private int handled, stale, applied, rejected, ignoredAfterDispose, retiredOnArrival;
	/** Shell binding generation when this binding attached. */
	private final int generation;
	/** The exact scope instance this binding last published to the shell (null while not publishable). */
	private AccountScope published;
	private volatile boolean disposed;

	public LifecycleBinding(AccountLifecycleView view, RaccoonShell shell) {
		if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("bind on the EDT");
		this.shell = shell;
		this.view = view;
		this.gate = new ConsumerScopeGate(view);
		shell.attachAccountSource(this, this::dispose);
		shell.setScopeContinuity((a, b) -> a instanceof AccountScope && b instanceof AccountScope && ((AccountScope) a).sameEpoch((AccountScope) b));
		this.generation = shell.accountGeneration();   // after attach: this binding's generation
		this.subscription = view.subscribe(s -> {
			if (disposed) return;
			int queuedAt = shell.accountGeneration();     // captured when the work is queued
			enqueued.incrementAndGet();
			SwingUtilities.invokeLater(() -> onEdt(s, queuedAt));
		});
	}

	private void onEdt(AccountSnapshot s, int queuedAt) {
		if (disposed || queuedAt != generation || generation != shell.accountGeneration()) { ignoredAfterDispose++; return; }
		if (!gate.onSnapshot(s)) { stale++; return; }
		handled++;
		if (s.isPublishable()) {
			AccountScope scope = s.scope().orElseThrow(IllegalStateException::new);
			// The gate has just recorded s, so accepts() asks the live lifecycle whether this scope is still current.
			if (gate.accepts(scope)) {
				published = scope;
				shell.showAccount(this, published);
				return;
			}
			retiredOnArrival++;
			published = null;
			AccountSnapshot live = view.snapshot();
			// A live publishable state means its own (newer) snapshot is queued behind this one and will publish it.
			shell.hideAccount(this, live.isPublishable() ? StateNotice.Presence.WAITING : presenceFor(live));
		} else {
			published = null;
			shell.hideAccount(this, presenceFor(s));
		}
	}

	/** Shared notice wording for a non-publishable snapshot. */
	public static StateNotice.Presence presenceFor(AccountSnapshot s) {
		if (s.isAwaitingFreshIdentity()) return StateNotice.Presence.WAITING;
		switch (s.state()) {
			case SUSPENDED_CONTINUITY: return StateNotice.Presence.RECONNECTING;
			case ACTIVE: return StateNotice.Presence.WAITING;
			default: return StateNotice.Presence.SIGNED_OUT;
		}
	}

	/**
	 * Rule 5. Runs {@code applyValue} on the EDT only if {@code scope} is still current. Call on the EDT with the scope
	 * the value was tagged with, which must be the instance this binding published (delivered through the shell's scope
	 * listener). Identity is required in addition to the gate because {@link AccountScope#equals} is value-based: a
	 * second AccountLifecycle instance (e.g. after a service restart) issues numerically equal scopes, and a value
	 * left over from the old session must not pass. Returns whether it was applied.
	 */
	public boolean apply(AccountScope scope, Runnable applyValue) {
		if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("apply must run on the EDT");
		if (disposed || generation != shell.accountGeneration() || scope == null || scope != published || !gate.accepts(scope)) { rejected++; return false; }
		applyValue.run();
		applied++;
		return true;
	}

	/**
	 * Captured when work is queued: the scope the value belongs to and the shell's binding generation at that moment.
	 * Applied only if both are still valid (contract 41eb2ac: generation guard in addition to authority checks).
	 */
	public static final class Ticket {
		private final AccountScope scope;
		private final int generation;
		Ticket(AccountScope scope, int generation) { this.scope = scope; this.generation = generation; }
		public AccountScope scope() { return scope; }
		public int generation() { return generation; }
	}

	/** Capture on the EDT when scheduling work for the currently published scope. */
	public Ticket ticket() { return new Ticket(published, shell.accountGeneration()); }

	/** Applies {@code applyValue} only if the ticket's generation is current and its scope passes {@link #apply}. */
	public boolean apply(Ticket t, Runnable applyValue) {
		if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("apply must run on the EDT");
		if (t == null || t.generation() != shell.accountGeneration() || generation != shell.accountGeneration()) { rejected++; return false; }
		return apply(t.scope(), applyValue);
	}

	/**
	 * The scope this binding currently publishes, only while the lifecycle confirms it is current and the binding is
	 * attached. Never reports a retired authority, even while its retirement snapshot is still queued. EDT only.
	 */
	public Optional<AccountScope> currentScope() {
		if (disposed || generation != shell.accountGeneration()) return Optional.empty();
		return Optional.ofNullable(published).filter(gate::accepts);
	}

	/** Unsubscribes and detaches; the shell clears every account value and page at once. EDT only. Idempotent. */
	@Override
	public void dispose() {
		if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("dispose on the EDT");
		if (disposed) return;
		disposed = true;
		published = null;
		subscription.cancel();
		shell.detachAccountSource(this);
	}

	public boolean isDisposed() { return disposed; }
	public int enqueued() { return enqueued.get(); }
	public int handled() { return handled; }
	public int staleDropped() { return stale; }
	public int applied() { return applied; }
	public int rejected() { return rejected; }
	public int ignoredAfterDispose() { return ignoredAfterDispose; }
	/** Publishable snapshots whose authority had been retired by the time they reached the EDT (not shown). */
	public int retiredOnArrival() { return retiredOnArrival; }
}
