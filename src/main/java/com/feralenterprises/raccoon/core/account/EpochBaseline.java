package com.feralenterprises.raccoon.core.account;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Turns absolute counters (for example a kill count) into per-epoch deltas. The first value seen
 * in an epoch is the baseline and yields a delta of zero, so nothing gained during a hop,
 * reconnect or account switch is ever counted.
 */
public final class EpochBaseline
{
	private final AccountSlot<Long> last;

	public EpochBaseline(AccountDataRegistry registry, String name)
	{
		this.last = registry.epochSlot(name);
	}

	/** Delta since the previous value in this epoch, 0 for the baseline, empty if stale. */
	public OptionalLong observe(AccountScope scope, long value)
	{
		Optional<Long> previous = last.get();
		if (!last.set(scope, value))
		{
			return OptionalLong.empty();
		}
		return OptionalLong.of(previous.isPresent() ? value - previous.get() : 0L);
	}
}
