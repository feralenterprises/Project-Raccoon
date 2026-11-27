package com.feralenterprises.raccoon.core.account;

/**
 * Fixed continuity limits (R03 proposal). Suspension ends at whichever expires first. These are
 * constants on purpose: they are never extended at runtime.
 */
public final class ContinuityLimits
{
	/** Monotonic time allowed from suspension until the same login is restored. */
	public static final long SUSPENSION_LIMIT_NANOS = 120L * 1_000_000_000L;

	/** Game ticks allowed after LOGGED_IN for a suspended login to re-authenticate. */
	public static final int AUTHENTICATION_TICK_LIMIT = 20;

	private ContinuityLimits()
	{
	}
}
