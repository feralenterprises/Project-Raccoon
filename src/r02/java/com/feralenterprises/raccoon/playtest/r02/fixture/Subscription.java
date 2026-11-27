package com.feralenterprises.raccoon.playtest.r02.fixture;

/** A listener registration; closing it twice is harmless. */
@FunctionalInterface
public interface Subscription extends AutoCloseable {
	@Override
	void close();
}
