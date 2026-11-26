package com.feralenterprises.raccoon.core.account;

import java.util.Optional;

/**
 * Hand-off guard for a consumer that renders on its own thread (for example the Swing EDT).
 * Confine one instance to that thread. Forward every snapshot from the lifecycle listener to the
 * consumer thread and call {@link #onSnapshot}; tag any account-derived value with the scope it
 * was produced under and apply it only if {@link #accepts} holds at that moment.
 *
 * <p>Because hand-offs to one thread run in order, a value produced before a retirement is either
 * applied before the retirement snapshot arrives (and then cleared by it) or rejected after it.
 * Snapshots handed off out of order are ignored by revision, so the consumer never regresses.
 * Not thread-safe by design.
 */
public final class ConsumerScopeGate
{
	private AccountSnapshot latest;

	public ConsumerScopeGate()
	{
	}

	/** Not yet used (reproduction commit); the repair makes this the only constructor. */
	public ConsumerScopeGate(AccountLifecycleView authority)
	{
	}

	/**
	 * Records a snapshot. Returns true if it is newer than any seen before (the consumer should
	 * re-render, clearing everything if it is not publishable); false if stale and ignored.
	 */
	public boolean onSnapshot(AccountSnapshot snapshot)
	{
		if (latest != null && snapshot.revision() <= latest.revision())
		{
			return false;
		}
		latest = snapshot;
		return true;
	}

	/** Latest snapshot seen on this thread. */
	public Optional<AccountSnapshot> latest()
	{
		return Optional.ofNullable(latest);
	}

	/** True only if the latest snapshot is publishable and belongs to {@code scope}. */
	public boolean accepts(AccountScope scope)
	{
		return latest != null && scope != null && latest.isPublishable() && latest.scope().map(scope::equals).orElse(false);
	}
}
