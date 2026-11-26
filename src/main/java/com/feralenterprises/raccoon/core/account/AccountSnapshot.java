package com.feralenterprises.raccoon.core.account;

import java.util.Optional;

/**
 * Immutable, UI-safe view of the lifecycle. Safe to read from any thread. Contains no account
 * hash and no account data; data is read through {@link AccountSlot}s.
 */
public final class AccountSnapshot
{
	private final long revision;
	private final AccountState state;
	private final AccountScope scope;
	private final Membership membership;
	private final RetireReason lastRetireReason;

	AccountSnapshot(long revision, AccountState state, AccountScope scope, Membership membership, RetireReason lastRetireReason)
	{
		this.revision = revision;
		this.state = state;
		this.scope = state == AccountState.ACTIVE ? scope : null;
		this.membership = state == AccountState.ACTIVE ? membership : Membership.UNKNOWN;
		this.lastRetireReason = lastRetireReason;
	}

	/** Increases on every published change. */
	public long revision()
	{
		return revision;
	}

	public AccountState state()
	{
		return state;
	}

	/** Present only when {@link #isPublishable()}. */
	public Optional<AccountScope> scope()
	{
		return Optional.ofNullable(scope);
	}

	/** Only ACTIVE with a fresh verified identity may publish account data. */
	public boolean isPublishable()
	{
		return state == AccountState.ACTIVE;
	}

	/** {@link Membership#UNKNOWN} unless ACTIVE and explicitly observed in this epoch. */
	public Membership membership()
	{
		return membership;
	}

	public RetireReason lastRetireReason()
	{
		return lastRetireReason;
	}

	@Override
	public String toString()
	{
		return "AccountSnapshot[rev " + revision + ", " + state + (scope == null ? "" : ", " + scope)
			+ ", " + membership + ", lastRetire " + lastRetireReason + "]";
	}
}
