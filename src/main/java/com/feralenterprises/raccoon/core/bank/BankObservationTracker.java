package com.feralenterprises.raccoon.core.bank;

import com.feralenterprises.raccoon.core.account.AccountDataRegistry;
import com.feralenterprises.raccoon.core.account.AccountLifecycleView;
import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.AccountSlot;
import com.feralenterprises.raccoon.core.account.AccountSnapshot;
import com.feralenterprises.raccoon.core.account.ClientPhase;
import com.feralenterprises.raccoon.core.account.Subscription;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Read-only bank observation (FR-003): decides when to capture, validates frames, builds the
 * qualifying streak, and holds current authority. Account state comes only from the FR-002
 * lifecycle; history is a lifecycle login slot (masked when not publishable, cleared on retirement).
 *
 * <p>Inputs ({@link #onTick}, {@link #reset}) run on the client thread. Listeners are called
 * synchronously, in order, while the tracker lock is held: they must not block or call back into
 * tracker inputs.
 */
public final class BankObservationTracker implements BankObservationView, AutoCloseable
{
	/** Matching valid observations needed for a populated bank. */
	public static final int POPULATED_STREAK = 2;
	/** Matching valid observations needed for an empty bank. */
	public static final int EMPTY_STREAK = 3;
	/** Provisional: consecutive failed capture attempts allowed per open-bank session. */
	public static final int MAX_FAILED_ATTEMPTS = 5;
	/** Donor cadence: ticks to wait after a failed capture before the next attempt. */
	public static final int RETRY_TICKS = 3;

	private final AccountLifecycleView lifecycle;
	private final AccountSlot<BankEvidence> history;
	private final CopyOnWriteArrayList<BankObservationListener> listeners = new CopyOnWriteArrayList<>();
	private final Subscription lifecycleSubscription;

	private volatile BankSnapshot snapshot = new BankSnapshot(0, BankStatus.WAITING, BankReason.NONE, null, null, 0);

	// guarded by this
	private BankEvidence current;
	private boolean degraded;
	private BankReason reason = BankReason.NONE;
	private boolean hasLastTick;
	private long lastTick;
	private int streak;
	private long streakFirstTick;
	private long streakLastTick;
	private AccountScope streakScope;
	private TabWitness streakWitness;
	private Map<Integer, Integer> streakContents;
	private java.util.List<RawSlot> streakRawBank;
	private CarriedEvidence streakInventory;
	private CarriedEvidence streakEquipment;
	private int failedAttempts;
	private boolean retryWait;
	private long nextAttemptTick;
	private long generationCounter;
	private long captureAttempts;
	private long revision;

	public BankObservationTracker(AccountLifecycleView lifecycle, AccountDataRegistry registry)
	{
		this.lifecycle = Objects.requireNonNull(lifecycle);
		this.history = registry.loginSlot("bank.history");
		this.lifecycleSubscription = lifecycle.subscribe(this::onAccountSnapshot);
	}

	// ------------------------------------------------------------------ inputs

	/**
	 * One game tick. {@code tickIndex} is the client's actual game-tick counter. At most one
	 * capture is attempted, and only when the account is publishable, the bank is open, no PIN is
	 * shown, the tick is new and the retry budget is not exhausted.
	 */
	public synchronized void onTick(long tickIndex, BankCaptureSource source)
	{
		Objects.requireNonNull(source);
		AccountSnapshot account = lifecycle.snapshot();
		if (!account.isPublishable())
		{
			hasLastTick = false;
			failedAttempts = 0;
			retryWait = false;
			invalidate(BankReason.NOT_PUBLISHABLE);
			return;
		}
		AccountScope scope = account.scope().get();

		if (hasLastTick && tickIndex <= lastTick)
		{
			// duplicate (or backwards) tick index: never a new observation
			invalidate(BankReason.DUPLICATE_TICK);
			return;
		}
		boolean consecutive = hasLastTick && tickIndex == lastTick + 1;
		hasLastTick = true;
		lastTick = tickIndex;

		BankVisibility visibility;
		try
		{
			visibility = source.visibility();
		}
		catch (RuntimeException e)
		{
			visibility = BankVisibility.UNREADABLE;
		}
		switch (visibility)
		{
			case OPEN:
				break;
			case CLOSED:
				failedAttempts = 0;
				retryWait = false;
				invalidate(BankReason.BANK_CLOSED);
				return;
			case PIN:
				invalidate(BankReason.PIN);
				return;
			case ITEMS_NOT_READY:
				invalidate(BankReason.ITEMS_NOT_READY);
				return;
			default:
				invalidate(BankReason.VISIBILITY_UNREADABLE);
				return;
		}

		if (failedAttempts >= MAX_FAILED_ATTEMPTS)
		{
			invalidate(BankReason.RETRY_LIMIT);
			return;
		}
		if (retryWait && tickIndex < nextAttemptTick)
		{
			// donor cadence after a failed capture; nothing is current meanwhile
			return;
		}
		retryWait = false;

		captureAttempts++;
		BankFrame frame;
		try
		{
			frame = source.capture(scope, tickIndex);
		}
		catch (RuntimeException e)
		{
			frame = null;
		}
		if (frame == null)
		{
			fail(BankReason.READ_FAILURE, tickIndex);
			return;
		}
		// identity or scope must not have changed during capture, and the frame must be this tick's
		if (!lifecycle.isCurrent(scope) || !scope.equals(frame.scope()) || frame.tickIndex() != tickIndex)
		{
			invalidate(BankReason.SCOPE_CHANGED);
			return;
		}
		// before/after client phase and identity, confirmed by the central lifecycle authority
		if (frame.phaseBefore() != ClientPhase.LOGGED_IN || frame.phaseAfter() != ClientPhase.LOGGED_IN
			|| !lifecycle.confirms(scope, frame.identityBefore()) || !lifecycle.confirms(scope, frame.identityAfter()))
		{
			fail(BankReason.IDENTITY_CHANGED, tickIndex);
			return;
		}

		BankEvaluator.Evaluation ev = BankEvaluator.evaluate(frame);
		if (!ev.valid())
		{
			fail(ev.failure, tickIndex);
			return;
		}
		failedAttempts = 0;

		// full stability contract: scope authority, exact witness, raw bank slots, bank contents,
		// inventory and equipment maps with their reporting flags, on consecutive ticks
		boolean matches = streak > 0 && consecutive && scope.equals(streakScope) && ev.witness.equals(streakWitness)
			&& ev.rawBank.equals(streakRawBank) && ev.contents.equals(streakContents)
			&& ev.inventory.equals(streakInventory) && ev.equipment.equals(streakEquipment);
		if (matches && current != null)
		{
			// unchanged revalidation of a qualified sample: stays current, not re-announced
			return;
		}
		if (matches)
		{
			// capped: an unchanged degraded sample is not re-announced every tick either
			streak = Math.min(streak + 1, EMPTY_STREAK);
			streakLastTick = tickIndex;
		}
		else
		{
			BankReason why = streak == 0 ? BankReason.STREAK_BUILDING
				: !consecutive ? BankReason.NONCONSECUTIVE_TICK : BankReason.EVIDENCE_CHANGED;
			current = null;
			degraded = false;
			streak = 1;
			streakFirstTick = tickIndex;
			streakLastTick = tickIndex;
			streakScope = scope;
			streakWitness = ev.witness;
			streakContents = ev.contents;
			streakRawBank = ev.rawBank;
			streakInventory = ev.inventory;
			streakEquipment = ev.equipment;
			reason = why;
		}

		boolean populated = !ev.contents.isEmpty();
		if (!ev.witness.isKnown())
		{
			current = null;
			degraded = populated;
			reason = BankReason.WITNESS_UNKNOWN;
			publish();
			return;
		}
		degraded = false;
		int required = populated ? POPULATED_STREAK : EMPTY_STREAK;
		if (streak >= required)
		{
			BankEvidence e = new BankEvidence(++generationCounter, scope, streakFirstTick, streakLastTick, ev.contents, ev.inventory, ev.equipment,
				ev.witness);
			if (history.set(scope, e))
			{
				current = e;
				reason = BankReason.NONE;
			}
			else
			{
				invalidate(BankReason.SCOPE_CHANGED);
				return;
			}
		}
		else if (matches)
		{
			reason = BankReason.STREAK_BUILDING;
		}
		publish();
	}

	/** Plugin start/stop: forget streak and current evidence (history is owned by the lifecycle). */
	public synchronized void reset()
	{
		hasLastTick = false;
		failedAttempts = 0;
		retryWait = false;
		invalidate(BankReason.NONE);
	}

	@Override
	public void close()
	{
		lifecycleSubscription.cancel();
	}

	/** Number of capture attempts so far (diagnostics and tests). */
	public synchronized long captureAttempts()
	{
		return captureAttempts;
	}

	// ------------------------------------------------------------------ internals

	private void onAccountSnapshot(AccountSnapshot s)
	{
		synchronized (this)
		{
			AccountScope now = s.scope().orElse(null);
			boolean currentStale = current != null && !current.scope().equals(now);
			boolean streakStale = streak > 0 && !streakScope.equals(now);
			if (currentStale || streakStale || (!s.isPublishable() && degraded))
			{
				// any scope or authority change revokes at once; history stays with its login
				invalidate(BankReason.SCOPE_CHANGED);
			}
			else
			{
				// even with nothing current, the published history must follow the lifecycle
				// (masked while not publishable, gone after retirement or an account change)
				publish();
			}
		}
	}

	private void fail(BankReason why, long tickIndex)
	{
		failedAttempts++;
		retryWait = true;
		nextAttemptTick = tickIndex + RETRY_TICKS;
		invalidate(why);
	}

	/** Revokes current authority, resets the streak, publishes. */
	private void invalidate(BankReason why)
	{
		current = null;
		degraded = false;
		streak = 0;
		streakScope = null;
		streakWitness = null;
		streakContents = null;
		streakRawBank = null;
		streakInventory = null;
		streakEquipment = null;
		reason = why;
		publish();
	}

	private void publish()
	{
		BankStatus status;
		BankEvidence hist = null;
		if (current != null && lifecycle.isCurrent(current.scope()))
		{
			status = BankStatus.QUALIFIED_CURRENT;
		}
		else
		{
			current = null;
			Optional<BankEvidence> h = history.get();
			if (degraded)
			{
				status = BankStatus.DEGRADED_POPULATED;
			}
			else if (h.isPresent())
			{
				status = BankStatus.HISTORICAL;
				hist = h.get();
			}
			else
			{
				status = BankStatus.WAITING;
			}
		}
		BankSnapshot next = new BankSnapshot(revision + 1, status, reason, current, hist, streak);
		if (next.sameContentAs(snapshot))
		{
			return;
		}
		revision++;
		snapshot = next;
		for (BankObservationListener l : listeners)
		{
			l.onBankSnapshot(next);
		}
	}

	// ------------------------------------------------------------------ view

	@Override
	public BankSnapshot snapshot()
	{
		return snapshot;
	}

	@Override
	public synchronized Subscription subscribe(BankObservationListener listener)
	{
		Objects.requireNonNull(listener);
		listeners.add(listener);
		listener.onBankSnapshot(snapshot);
		return () -> listeners.remove(listener);
	}

	@Override
	public synchronized Ownership bankOwnership(int itemId)
	{
		BankEvidence e = current;
		if (e == null || !lifecycle.isCurrent(e.scope()))
		{
			return Ownership.unknown();
		}
		return Ownership.of(e.contents().getOrDefault(itemId, 0));
	}

	@Override
	public synchronized Ownership combinedOwnership(int itemId)
	{
		BankEvidence e = current;
		if (e == null || !lifecycle.isCurrent(e.scope()))
		{
			return Ownership.unknown();
		}
		CarriedEvidence inv = e.inventory();
		CarriedEvidence worn = e.equipment();
		if (!inv.isReported() || !worn.isReported() || inv.hadExcludedNoted() || worn.hadExcludedNoted())
		{
			return Ownership.unknown();
		}
		// overflow was rejected at evaluation, so this sum fits
		return Ownership.of(e.contents().getOrDefault(itemId, 0) + inv.quantity(itemId) + worn.quantity(itemId));
	}

	@Override
	public synchronized boolean isCurrent(BankEvidence evidence)
	{
		return evidence != null && evidence == current && lifecycle.isCurrent(evidence.scope());
	}

	@Override
	public Runnable fenced(BankEvidence evidence, Runnable work)
	{
		return () ->
		{
			if (isCurrent(evidence))
			{
				work.run();
			}
		};
	}
}
