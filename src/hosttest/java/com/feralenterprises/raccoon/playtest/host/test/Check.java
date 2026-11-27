package com.feralenterprises.raccoon.playtest.host.test;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free assertion counter (JUnit is not reachable offline), as in the other suites. */
public final class Check {
	private final String suite;
	private int passed;
	private final List<String> failures = new ArrayList<>();

	public Check(String suite) { this.suite = suite; }

	public void that(String name, boolean ok) {
		if (ok) passed++;
		else { failures.add(name); System.out.println("FAIL " + name); }
	}

	public void throwsOn(String name, Runnable r, String expectedFragment) {
		try {
			r.run();
			that(name + " (no exception)", false);
		} catch (RuntimeException e) {
			boolean ok = expectedFragment == null || String.valueOf(e.getMessage()).contains(expectedFragment);
			if (!ok) System.out.println("  got: " + e);
			that(name, ok);
		}
	}

	public int finish() {
		System.out.println(suite + ": " + passed + " passed, " + failures.size() + " failed");
		return failures.isEmpty() ? 0 : 1;
	}
}
