package com.feralenterprises.raccoon;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Minimal plugin entry for the FR-000 foundation. It has no UI, observers,
 * persistence or networking; later assignments add behaviour behind
 * tools/verify architecture rules.
 */
@Slf4j
@PluginDescriptor(
	name = "Project Raccoon",
	description = "Foundation scaffold. No features yet."
)
public class RaccoonPlugin extends Plugin
{
	@Override
	protected void startUp()
	{
		log.debug("Project Raccoon foundation started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("Project Raccoon foundation stopped");
	}
}
