package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** The single injectable clock. Tests advance it; nothing in R02 reads the system clock directly. */
public final class FixtureClock {
	private Instant now;

	public FixtureClock(Instant start) { this.now = Objects.requireNonNull(start); }

	public synchronized Instant now() { return now; }

	public synchronized void advance(Duration d) {
		if (d.isNegative()) throw new IllegalArgumentException("clock cannot go back");
		now = now.plus(d);
	}
}
