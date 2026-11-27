package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.util.Objects;

/**
 * Who the fixture data belongs to: a demo account id (or none when signed out) and an epoch. A different account or
 * epoch is a different scope: the shell discards every route page and Back history.
 */
public final class AccountScope {
	private final String account;
	private final int epoch;

	private AccountScope(String account, int epoch) {
		if (account != null && account.isEmpty()) throw new IllegalArgumentException("empty account");
		this.account = account;
		this.epoch = epoch;
	}

	public static AccountScope of(String account, int epoch) { return new AccountScope(Objects.requireNonNull(account), epoch); }
	public static AccountScope signedOut(int epoch) { return new AccountScope(null, epoch); }

	/** Demo account id ("A", "B"), or null when signed out. */
	public String account() { return account; }
	public boolean signedIn() { return account != null; }
	public int epoch() { return epoch; }

	@Override
	public boolean equals(Object o) {
		return o instanceof AccountScope && Objects.equals(account, ((AccountScope) o).account) && epoch == ((AccountScope) o).epoch;
	}

	@Override
	public int hashCode() { return Objects.hash(account, epoch); }

	@Override
	public String toString() { return (account == null ? "logout" : account) + ":" + epoch; }
}
