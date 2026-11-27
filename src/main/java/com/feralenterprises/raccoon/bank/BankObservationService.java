package com.feralenterprises.raccoon.bank;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import com.feralenterprises.raccoon.core.bank.BankCaptureSource;
import com.feralenterprises.raccoon.core.bank.BankObservationTracker;
import com.feralenterprises.raccoon.core.bank.BankObservationView;
import java.util.function.IntSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;

/**
 * RuneLite adapter for read-only bank observation. Run {@link #onGameTick()} after the account
 * lifecycle's tick so the scope is fresh. Later features consume {@link #view()}.
 */
@Singleton
public class BankObservationService
{
	private final BankObservationTracker tracker;
	private final BankCaptureSource source;
	private final IntSupplier tickCount;

	@Inject
	BankObservationService(AccountLifecycleService accounts, Client client)
	{
		this(accounts, new RuneLiteBankCaptureSource(client), client::getTickCount);
	}

	/** Seam for tests and the simulator. */
	public BankObservationService(AccountLifecycleService accounts, BankCaptureSource source, IntSupplier tickCount)
	{
		this.tracker = new BankObservationTracker(accounts.view(), accounts.data());
		this.source = source;
		this.tickCount = tickCount;
	}

	public BankObservationView view()
	{
		return tracker;
	}

	public void reset()
	{
		tracker.reset();
	}

	public void onGameTick()
	{
		tracker.onTick(tickCount.getAsInt(), source);
	}
}
