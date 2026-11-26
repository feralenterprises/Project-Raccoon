package com.feralenterprises.raccoon.core.account;

import java.util.ArrayList;
import java.util.List;

/** Drives an {@link AccountLifecycle} with a fake monotonic clock, as the simulator would. */
final class LifecycleHarness
{
	static final long HASH_A = 123456789012345L;
	static final long HASH_B = 987654321098765L;
	static final IdentityReading A = IdentityReading.of(HASH_A);
	static final IdentityReading B = IdentityReading.of(HASH_B);
	static final IdentityReading UNKNOWN = IdentityReading.unknown();
	static final IdentityReading FAILED = IdentityReading.failed();
	static final WorldContext WORLD = WorldContext.supported(Membership.UNKNOWN);
	static final WorldContext UNSUPPORTED = WorldContext.unsupported();

	final AccountLifecycle lifecycle = new AccountLifecycle();
	final List<AccountSnapshot> published = new ArrayList<>();
	long now = 1_000_000_000L;

	LifecycleHarness()
	{
		lifecycle.subscribe(published::add);
	}

	LifecycleHarness start()
	{
		lifecycle.start(now);
		return this;
	}

	LifecycleHarness phase(ClientPhase p)
	{
		lifecycle.onClientPhase(p, now);
		return this;
	}

	LifecycleHarness tick(IdentityReading id)
	{
		return tick(id, WORLD);
	}

	LifecycleHarness tick(IdentityReading id, WorldContext world)
	{
		lifecycle.onGameTick(id, world, now);
		return this;
	}

	LifecycleHarness ticks(int n, IdentityReading id)
	{
		for (int i = 0; i < n; i++)
		{
			tick(id);
		}
		return this;
	}

	LifecycleHarness advanceSeconds(double s)
	{
		now += (long) (s * 1_000_000_000L);
		return this;
	}

	LifecycleHarness poll()
	{
		lifecycle.poll(now);
		return this;
	}

	/** Start and log in as {@code id} on a supported world. */
	LifecycleHarness loggedInAs(IdentityReading id)
	{
		return start().phase(ClientPhase.LOGGED_IN).tick(id);
	}

	/** A witnessed hop: HOPPING, LOADING, LOGGED_IN (identity not yet read). */
	LifecycleHarness hop()
	{
		return phase(ClientPhase.HOPPING).phase(ClientPhase.LOADING).phase(ClientPhase.LOGGED_IN);
	}

	AccountSnapshot snap()
	{
		return lifecycle.snapshot();
	}

	AccountScope scope()
	{
		return snap().scope().orElseThrow(() -> new AssertionError("no active scope: " + snap()));
	}
}
