package com.feralenterprises.raccoon.core.account;

/**
 * Creates centrally owned account data. Features register slots here instead of keeping their
 * own copies of account state (SR-1).
 */
public interface AccountDataRegistry
{
	/**
	 * Data kept for one login (for example KC totals). Masked while suspended, kept privately
	 * across a verified hop or reconnect, cleared when the login retires.
	 */
	<T> AccountSlot<T> loginSlot(String name);

	/**
	 * Data valid for one observation epoch (for example KC baseline, bank streak, queued
	 * messages). Cleared on every new epoch and on retirement, so nothing gained during a gap is
	 * replayed.
	 */
	<T> AccountSlot<T> epochSlot(String name);
}
