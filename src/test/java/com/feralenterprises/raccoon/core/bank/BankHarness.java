package com.feralenterprises.raccoon.core.bank;

import com.feralenterprises.raccoon.core.account.AccountLifecycle;
import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.ClientPhase;
import com.feralenterprises.raccoon.core.account.IdentityReading;
import com.feralenterprises.raccoon.core.account.Membership;
import com.feralenterprises.raccoon.core.account.WorldContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Drives the real FR-002 lifecycle plus a scripted bank source, tick by tick. */
final class BankHarness implements BankCaptureSource
{
	static final IdentityReading A = IdentityReading.of(123456789012345L);
	static final IdentityReading B = IdentityReading.of(987654321098765L);
	static final WorldContext WORLD = WorldContext.supported(Membership.UNKNOWN);
	static final int COINS = 995;
	static final int LOBSTER = 379;

	final AccountLifecycle lifecycle = new AccountLifecycle();
	final BankObservationTracker tracker = new BankObservationTracker(lifecycle, lifecycle);
	final List<BankSnapshot> published = new ArrayList<>();

	long tick = 100;
	long now = 1_000_000_000L;
	IdentityReading identity = A;

	// scripted client state
	BankVisibility visibility = BankVisibility.OPEN;
	boolean visibilityThrows;
	boolean captureThrows;
	List<RawSlot> bankSlots = slots(RawSlot.item(COINS, 1000), RawSlot.item(LOBSTER, 20), RawSlot.empty());
	ContainerRead bankOverride;
	ContainerRead inventory = ContainerRead.present(Arrays.asList(RawSlot.item(COINS, 5)));
	ContainerRead equipment = ContainerRead.present(new ArrayList<>());
	TabWitness witness = TabWitness.of(1, 0, 0, 0, 0, 0, 0, 0, 0);
	Runnable duringCapture;

	BankHarness()
	{
		tracker.subscribe(published::add);
		lifecycle.start(now);
		lifecycle.onClientPhase(ClientPhase.LOGGED_IN, now);
		lifecycle.onGameTick(identity, WORLD, now);
	}

	static List<RawSlot> slots(RawSlot... s)
	{
		return new ArrayList<>(Arrays.asList(s));
	}

	/** One game tick: lifecycle first (as the plugin does), then bank observation. */
	BankHarness tick()
	{
		tick++;
		now += 600_000_000L;
		lifecycle.onGameTick(identity, WORLD, now);
		tracker.onTick(tick, this);
		return this;
	}

	BankHarness ticks(int n)
	{
		for (int i = 0; i < n; i++)
		{
			tick();
		}
		return this;
	}

	/** Re-run the bank step for the same tick index (duplicate tick). */
	BankHarness sameTick()
	{
		tracker.onTick(tick, this);
		return this;
	}

	BankHarness skipTick()
	{
		tick++;
		return this;
	}

	BankHarness phase(ClientPhase p)
	{
		lifecycle.onClientPhase(p, now);
		return this;
	}

	BankStatus status()
	{
		return tracker.snapshot().status();
	}

	BankReason reason()
	{
		return tracker.snapshot().reason();
	}

	AccountScope scope()
	{
		return lifecycle.snapshot().scope().orElseThrow(AssertionError::new);
	}

	@Override
	public BankVisibility visibility()
	{
		if (visibilityThrows)
		{
			throw new IllegalStateException("widget read failed");
		}
		return visibility;
	}

	@Override
	public BankFrame capture(AccountScope scope, long tickIndex)
	{
		if (duringCapture != null)
		{
			Runnable r = duringCapture;
			duringCapture = null;
			r.run();
		}
		if (captureThrows)
		{
			throw new IllegalStateException("container read failed");
		}
		ContainerRead bank = bankOverride != null ? bankOverride : ContainerRead.present(bankSlots);
		return new BankFrame(scope, tickIndex, bank, inventory, equipment, witness);
	}
}
