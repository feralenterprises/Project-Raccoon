package com.feralenterprises.raccoon.playtest;

import com.feralenterprises.raccoon.RaccoonPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * R2/R3 client: the pinned RuneLite 1.13.8 developer client with the candidate plugin (from the
 * candidate's reproducible development JAR) and the playtest witness loaded as built-in plugins.
 * Started only by the playtest launcher with an isolated {@code user.home}.
 */
public final class PlaytestClient
{
	private PlaytestClient()
	{
	}

	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RaccoonPlugin.class, WitnessPlugin.class);
		RuneLite.main(args);
	}
}
