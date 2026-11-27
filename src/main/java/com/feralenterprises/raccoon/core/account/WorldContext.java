package com.feralenterprises.raccoon.core.account;

import java.util.Objects;

/** What the adapter knows about the current world, already reduced to policy. */
public final class WorldContext
{
	private static final WorldContext UNSUPPORTED = new WorldContext(false, Membership.UNKNOWN);

	private final boolean supported;
	private final Membership membership;

	private WorldContext(boolean supported, Membership membership)
	{
		this.supported = supported;
		this.membership = Objects.requireNonNull(membership);
	}

	public static WorldContext supported(Membership membership)
	{
		return new WorldContext(true, membership);
	}

	/** Temporary or unsupported worlds: no observation may happen there. */
	public static WorldContext unsupported()
	{
		return UNSUPPORTED;
	}

	public boolean isSupported()
	{
		return supported;
	}

	public Membership membership()
	{
		return membership;
	}

	@Override
	public String toString()
	{
		return "WorldContext[" + (supported ? "supported" : "unsupported") + ", " + membership + "]";
	}
}
