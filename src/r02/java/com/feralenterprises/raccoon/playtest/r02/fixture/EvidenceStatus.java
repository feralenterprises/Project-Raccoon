package com.feralenterprises.raccoon.playtest.r02.fixture;

/**
 * Observation states kept separate everywhere (dispatch: "Mark complete absence/verified empty, verified zero,
 * unknown, not applicable and stale separately"). Never collapse one into another for display.
 */
public enum EvidenceStatus {
	/** Observed now, with a value. */
	VERIFIED,
	/** Observed now as a count of zero (for example official KC 0). */
	VERIFIED_ZERO,
	/** A complete observation that verifies the thing is not there (verified empty / verified absence). */
	VERIFIED_ABSENT,
	/** A value from an earlier observation; carries its observation time and may no longer be true. */
	STALE,
	/** A source exists but this value has not been observed (yet). */
	UNKNOWN,
	/** No source can provide this value in this build (for example exact skill XP). */
	UNAVAILABLE,
	/** The question does not apply (for example a members boss on a verified F2P world). */
	NOT_APPLICABLE;

	/** True when a value may be shown (VERIFIED, VERIFIED_ZERO, STALE with its age). */
	public boolean hasValue() { return this == VERIFIED || this == VERIFIED_ZERO || this == STALE; }
}
