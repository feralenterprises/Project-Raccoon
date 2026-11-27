package com.feralenterprises.raccoon.core.account;

/** Lifecycle states. Only {@link #ACTIVE} may publish account data. */
public enum AccountState
{
	/** No verified identity yet (signed out, enabling, or awaiting a first fresh identity). */
	UNAUTHENTICATED,
	/** Fresh verified identity on a supported world; the current scope may publish. */
	ACTIVE,
	/** A witnessed hop or connection loss; all data masked, callbacks fenced, login held privately. */
	SUSPENDED_CONTINUITY,
	/** The previous login ended; its data was cleared. A new login starts from a fresh identity. */
	RETIRED
}
