package com.feralenterprises.raccoon.core.account;

/**
 * Opaque capability for one observation epoch of one login. Anything produced for an account
 * (writes, callbacks, queued work) must carry the scope it was produced under; it is accepted
 * only while {@link AccountLifecycleView#isCurrent(AccountScope)} holds.
 *
 * <p>{@code accountId} is a per-session ordinal, not the account hash.
 */
public final class AccountScope
{
	private final int accountId;
	private final long loginToken;
	private final long epoch;

	AccountScope(int accountId, long loginToken, long epoch)
	{
		this.accountId = accountId;
		this.loginToken = loginToken;
		this.epoch = epoch;
	}

	/** Stable while the plugin runs, for the same account; never the hash. */
	public int accountId()
	{
		return accountId;
	}

	/** Identifies one login; survives a verified hop or reconnect, changes on any new login. */
	public long loginToken()
	{
		return loginToken;
	}

	/** Unique per activation; changes on every restore and every new login. */
	public long epoch()
	{
		return epoch;
	}

	/** True when both scopes belong to the same login (possibly different epochs). */
	public boolean sameLogin(AccountScope other)
	{
		return other != null && other.accountId == accountId && other.loginToken == loginToken;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof AccountScope))
		{
			return false;
		}
		AccountScope s = (AccountScope) o;
		return s.accountId == accountId && s.loginToken == loginToken && s.epoch == epoch;
	}

	@Override
	public int hashCode()
	{
		return (int) (epoch ^ (epoch >>> 32)) * 31 + accountId;
	}

	@Override
	public String toString()
	{
		return "AccountScope[account#" + accountId + " login#" + loginToken + " epoch#" + epoch + "]";
	}
}
