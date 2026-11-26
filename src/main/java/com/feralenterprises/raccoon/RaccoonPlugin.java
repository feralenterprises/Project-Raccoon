package com.feralenterprises.raccoon;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Plugin entry. Owns the account lifecycle and forwards client events to it. It has no UI,
 * observers, persistence or networking; later assignments consume the lifecycle through
 * {@link AccountLifecycleService#view()} and {@link AccountLifecycleService#data()}.
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

	public RaccoonPlugin()
	{
	}

	/** Test seam. */
	RaccoonPlugin(AccountLifecycleService accountLifecycle)
	{
		this.accountLifecycle = accountLifecycle;
	}

	@Override
	protected void startUp()
	{
		accountLifecycle.start();
		log.debug("Project Raccoon started");
	}

	@Override
	protected void shutDown()
	{
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
		accountLifecycle.onGameTick();
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		accountLifecycle.onClientTick();
	}
}
