package com.feralenterprises.raccoon.core.account;

/**
 * Opaque capability for one authority period of one observation epoch of one login. A new
 * authority is issued on every activation and on every revalidation after a phase change (for
 * example LOADING); a scope from an earlier authority is never current again, even in the same
 * epoch. Anything produced for an account
 * (writes, callbacks, queued work) must carry the scope it was produced under; it is accepted
 * only while {@link AccountLifecycleView#isCurrent(AccountScope)} holds.
 *
 * <p>{@code accountId} is a per-session ordinal, not the account hash. Every scope belongs to the
 * namespace of the {@link AccountLifecycle} instance that issued it: scopes from different
 * instances never compare equal and never carry authority in another instance, whatever their
 * counters. The namespace is an opaque in-memory object; it is never printed or persisted.
 */
public final class AccountScope
{
	private final Object namespace;
	private final int accountId;
	private final long loginToken;
	private final long epoch;
	private final long authority;

	AccountScope(Object namespace, int accountId, long loginToken, long epoch, long authority)
	{
		this.namespace = java.util.Objects.requireNonNull(namespace);
		this.accountId = accountId;
		this.loginToken = loginToken;
		this.epoch = epoch;
		this.authority = authority;
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

	/** Unique per activation or revalidation; the part of the scope that confers authority. */
	public long authority()
	{
		return authority;
	}

	/**
	 * True when both scopes belong to the same epoch (epoch data such as baselines carries over),
	 * even if one of them no longer has authority.
	 */
	public boolean sameEpoch(AccountScope other)
	{
		return sameLogin(other) && other.epoch == epoch;
	}

	/** True when both scopes belong to the same login (possibly different epochs). */
	public boolean sameLogin(AccountScope other)
	{
		return other != null && other.namespace == namespace && other.accountId == accountId && other.loginToken == loginToken;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof AccountScope))
		{
			return false;
		}
		AccountScope s = (AccountScope) o;
		return s.namespace == namespace && s.accountId == accountId && s.loginToken == loginToken && s.epoch == epoch
			&& s.authority == authority;
	}

	@Override
	public int hashCode()
	{
		return (((int) (authority ^ (authority >>> 32)) * 31 + (int) (epoch ^ (epoch >>> 32))) * 31 + accountId) * 31
			+ System.identityHashCode(namespace);
	}

	@Override
	public String toString()
	{
		return "AccountScope[account#" + accountId + " login#" + loginToken + " epoch#" + epoch + " auth#" + authority + "]";
	}
}
