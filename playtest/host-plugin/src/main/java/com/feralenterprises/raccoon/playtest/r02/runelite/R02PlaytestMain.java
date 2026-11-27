package com.feralenterprises.raccoon.playtest.r02.runelite;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Child-JVM entry point used by the playtest launcher: registers the host as a built-in external plugin (the
 * runelite/example-plugin pattern) and starts RuneLite. Developer mode requires {@code -ea} and a non-launcher start.
 */
public final class R02PlaytestMain {
	private R02PlaytestMain() { }

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		if (System.getProperty("raccoon.r02.profile") == null) {
			System.err.println("Start this through the R02 playtest launcher (it creates the isolated profile).");
			System.exit(2);
		}
		ExternalPluginManager.loadBuiltin(R02PlaytestPlugin.class);
		RuneLite.main(args);
	}
}
