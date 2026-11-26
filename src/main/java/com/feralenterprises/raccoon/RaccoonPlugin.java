package com.feralenterprises.raccoon;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import com.feralenterprises.raccoon.bank.BankObservationService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Plugin entry. Owns the account lifecycle and read-only bank observation and forwards client
 * events to them. No UI, persistence, networking or game writes; later assignments consume
 * {@link AccountLifecycleService#view()} and {@link BankObservationService#view()}.
 */
@Slf4j
@PluginDescriptor(
	name = "Project Raccoon",
	description = "Foundation scaffold. No features yet."
)
public class RaccoonPlugin extends Plugin
{
	@Inject
	private AccountLifecycleService accountLifecycle;

	@Inject
	private BankObservationService bankObservation;

	public RaccoonPlugin()
	{
	}

	/** Test seam. */
	RaccoonPlugin(AccountLifecycleService accountLifecycle, BankObservationService bankObservation)
	{
		this.accountLifecycle = accountLifecycle;
		this.bankObservation = bankObservation;
	}

	@Override
	protected void startUp()
	{
		accountLifecycle.start();
		bankObservation.reset();
		log.debug("Project Raccoon started");
	}

	@Override
	protected void shutDown()
	{
		bankObservation.reset();
		accountLifecycle.stop();
		log.debug("Project Raccoon stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		accountLifecycle.onGameState(event.getGameState().name());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// lifecycle first, so bank observation sees this tick's fresh scope
		accountLifecycle.onGameTick();
		bankObservation.onGameTick();
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		accountLifecycle.onClientTick();
	}
}
