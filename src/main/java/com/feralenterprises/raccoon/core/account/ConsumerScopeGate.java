package com.feralenterprises.raccoon.core.account;

import java.util.Objects;
import java.util.Optional;

/**
 * Hand-off guard for a consumer that renders on its own thread (for example the Swing EDT).
 * Confine one instance to that thread. Forward every snapshot from the lifecycle listener to the
 * consumer thread and call {@link #onSnapshot}; tag any account-derived value with the scope it
 * was produced under and apply it only if {@link #accepts} holds at that moment.
 *
 * <p>{@link #accepts} is authoritative: besides the latest snapshot seen on this thread it asks
 * the lifecycle whether the scope is still current, so a value is rejected even when the
 * retirement snapshot is still queued for this thread. Snapshots handed off out of order are
 * ignored by revision, so the consumer never regresses. Not thread-safe by design.
 */
public final class ConsumerScopeGate
{
	private final AccountLifecycleView authority;
	private AccountSnapshot latest;

	public ConsumerScopeGate(AccountLifecycleView authority)
	{
		this.authority = Objects.requireNonNull(authority);
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

	/**
	 * True only if the latest snapshot seen here is publishable for {@code scope} and the lifecycle
	 * confirms, at this moment, that {@code scope} is still current.
	 */
	public boolean accepts(AccountScope scope)
	{
		return latest != null && scope != null && latest.isPublishable()
			&& latest.scope().map(scope::equals).orElse(false)
			&& authority.isCurrent(scope);
	}
}
