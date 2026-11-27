package com.feralenterprises.raccoon.playtest.r02.fixture;

/** Independent container observation states (dispatch: current/historical/incomplete/unavailable). */
public enum ContainerState {
	/** Complete and current: presence is VERIFIED, absence is VERIFIED_ABSENT. */
	CURRENT,
	/** A complete earlier observation (saved snapshot): presence is STALE, absence cannot be claimed (UNKNOWN). */
	HISTORICAL,
	/** Partly observed: what was seen is VERIFIED, absence cannot be claimed (UNKNOWN). */
	INCOMPLETE,
	/** Never observed in this scope. */
	UNAVAILABLE
}
