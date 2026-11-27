package com.feralenterprises.raccoon.core.kc;

import com.feralenterprises.raccoon.core.account.AccountDataRegistry;
import com.feralenterprises.raccoon.core.account.AccountLifecycleView;
import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.AccountSlot;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * The single Brutus KC service (FR-004). All state lives in lifecycle slots: the current-login
 * total in a login slot (masked while suspended, kept over a verified hop, cleared on retirement);
 * the last count with the scope authority it was read under, and the quarantine flag, in epoch
 * slots. Client thread.
 */
public final class BrutusKcTracker implements BrutusKcView
{
	/** Last absolute count and the exact scope (authority) it was observed under. */
	private static final class Last
	{
		final AccountScope scope;
		final int count;

		Last(AccountScope scope, int count)
		{
			this.scope = scope;
			this.count = count;
		}
	}

	private final AccountLifecycleView lifecycle;
	private final AccountSlot<Long> total;
	private final AccountSlot<Last> last;
	private final AccountSlot<Boolean> quarantined;
	private volatile long revision;

	public BrutusKcTracker(AccountLifecycleView lifecycle, AccountDataRegistry registry)
	{
		this.lifecycle = Objects.requireNonNull(lifecycle);
		this.total = registry.loginSlot("kc.brutus.total");
		this.last = registry.epochSlot("kc.brutus.last");
		this.quarantined = registry.epochSlot("kc.brutus.quarantined");
	}

	/**
	 * One chat message, with the account scope captured when the message arrived. Only a
	 * verified increase under the current scope adds to the total.
	 */
	public synchronized KcResult onChatMessage(AccountScope scope, ChatKind kind, String message)
	{
		OptionalInt parsed = BrutusKcParser.parse(kind, message);
		if (!parsed.isPresent())
		{
			return KcResult.NOT_KC;
		}
		if (scope == null || !lifecycle.isCurrent(scope))
		{
			return KcResult.REJECTED_SCOPE;
		}
		if (quarantined.get(scope).orElse(false))
		{
			return KcResult.QUARANTINED;
		}
		int count = parsed.getAsInt();
		Optional<Last> previous = last.get(scope).filter(l -> l.scope.equals(scope));
		if (!previous.isPresent())
		{
			// first count under this epoch and authority (also after LOADING revalidation)
			if (!last.set(scope, new Last(scope, count)))
			{
				return KcResult.REJECTED_SCOPE;
			}
			if (!total.get(scope).isPresent())
			{
				total.set(scope, 0L);
			}
			revision++;
			return KcResult.BASELINED;
		}
		int before = previous.get().count;
		if (count < before)
		{
			quarantined.set(scope, true);
			revision++;
			return KcResult.REGRESSION_QUARANTINED;
		}
		if (count == before)
		{
			return KcResult.UNCHANGED;
		}
		long updated;
		try
		{
			updated = Math.addExact(total.get(scope).orElse(0L), (long) count - before);
		}
		catch (ArithmeticException e)
		{
			quarantined.set(scope, true);
			revision++;
			return KcResult.OVERFLOW_QUARANTINED;
		}
		// conservative order: if the scope stops being current in between, the gain is lost, never doubled
		if (!last.set(scope, new Last(scope, count)) || !total.set(scope, updated))
		{
			return KcResult.REJECTED_SCOPE;
		}
		revision++;
		return KcResult.GAIN;
	}

	@Override
	public synchronized OptionalLong currentLoginTotal()
	{
		Optional<Long> t = total.get();
		return t.isPresent() ? OptionalLong.of(t.get()) : OptionalLong.empty();
	}

	@Override
	public synchronized OptionalLong currentLoginTotal(AccountScope scope)
	{
		Optional<Long> t = total.get(scope);
		return t.isPresent() ? OptionalLong.of(t.get()) : OptionalLong.empty();
	}

	@Override
	public synchronized boolean isQuarantined()
	{
		return quarantined.get().orElse(false);
	}

	@Override
	public long revision()
	{
		return revision;
	}
}
