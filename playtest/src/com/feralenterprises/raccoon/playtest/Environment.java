package com.feralenterprises.raccoon.playtest;

/** Machine, toolchain and candidate identity, recorded at the top of every receipt. */
final class Environment
{
	private Environment()
	{
	}

	static void record(Receipt receipt, String step)
	{
		Runtime rt = Runtime.getRuntime();
		receipt.event("environment",
			"step", step,
			"candidate", System.getProperty("raccoon.playtest.candidate", "unknown"),
			"kit", System.getProperty("raccoon.playtest.kit", "unknown"),
			"kind", System.getProperty("raccoon.playtest.kind", "unknown"),
			"os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"),
			"cpus", rt.availableProcessors(),
			"cpu", System.getenv("PROCESSOR_IDENTIFIER") == null ? "n/a" : System.getenv("PROCESSOR_IDENTIFIER"),
			"jvm", System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"),
			"java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")",
			"maxHeapMiB", rt.maxMemory() / (1024 * 1024),
			"userHome", path(System.getProperty("user.home")),
			"tmpDir", path(System.getProperty("java.io.tmpdir")),
			"workingDir", path(System.getProperty("user.dir")));
	}

	/** Paths without the tester's user name: the kit folder and profile folder are replaced. */
	static String path(String p)
	{
		if (p == null)
		{
			return null;
		}
		String kit = System.getProperty("raccoon.playtest.kit.dir");
		String profile = System.getenv("USERPROFILE");
		if (kit != null && !kit.isEmpty() && p.regionMatches(true, 0, kit, 0, kit.length()))
		{
			return "<kit>" + p.substring(kit.length());
		}
		if (profile != null && !profile.isEmpty() && p.regionMatches(true, 0, profile, 0, profile.length()))
		{
			return "%USERPROFILE%" + p.substring(profile.length());
		}
		return p;
	}
}
