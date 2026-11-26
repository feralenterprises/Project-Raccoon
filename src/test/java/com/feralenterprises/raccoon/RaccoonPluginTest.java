package com.feralenterprises.raccoon;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Development launcher (RuneLite template convention); not a unit test. */
public class RaccoonPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RaccoonPlugin.class);
		RuneLite.main(args);
	}
}
