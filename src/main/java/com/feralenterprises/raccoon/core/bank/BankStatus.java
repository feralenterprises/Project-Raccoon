package com.feralenterprises.raccoon.core.bank;

/** Bank evidence status. Only {@link #QUALIFIED_CURRENT} supports ownership claims. */
public enum BankStatus
{
	QUALIFIED_CURRENT,
	/** Valid populated observation with an unknown tab witness: no ownership claims. */
	DEGRADED_POPULATED,
	/** Waiting or unknown: nothing current and no same-login history. */
	WAITING,
	/** Nothing current; the last qualified evidence of this login is available as provenance only. */
	HISTORICAL
}
