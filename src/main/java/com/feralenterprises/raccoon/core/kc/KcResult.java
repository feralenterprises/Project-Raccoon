package com.feralenterprises.raccoon.core.kc;

/** Outcome of one chat message. Safe for diagnostics: carries no identity or totals. */
public enum KcResult
{
	/** Not a Brutus KC line (other type, other boss, malformed, out of range). */
	NOT_KC,
	/** No current account scope, or the message's scope is stale or foreign. */
	REJECTED_SCOPE,
	/** First count under this epoch and authority: baseline, zero delta. */
	BASELINED,
	UNCHANGED,
	GAIN,
	/** Count went down: quarantined until a new lifecycle epoch. */
	REGRESSION_QUARANTINED,
	/** Already quarantined in this epoch: ignored. */
	QUARANTINED,
	/** The total would overflow: quarantined, nothing added. */
	OVERFLOW_QUARANTINED
}
