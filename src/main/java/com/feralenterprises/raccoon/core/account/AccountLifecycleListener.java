package com.feralenterprises.raccoon.core.account;

/** Receives every published snapshot, in order, on the thread that drove the change. */
public interface AccountLifecycleListener
{
	void onAccountSnapshot(AccountSnapshot snapshot);
}
