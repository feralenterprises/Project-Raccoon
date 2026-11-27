package com.feralenterprises.raccoon.playtest.r02.host;

/**
 * Account-scope guard input. Simulated data may be shown only while no game account can be signed in. The host maps
 * the client's {@code GameState} by name (so this class has no RuneLite dependency): the start-up and login-screen
 * states are OPEN; logging in, loading, logged in, hopping, connection lost and any state not listed here (including
 * UNKNOWN and states added by a later client) are SUSPENDED. Fail closed.
 */
public enum ClientPhase {
	/** No account session exists: fixtures may be shown. */
	OPEN,
	/** A real account may be signed in: every simulated value is hidden. */
	SUSPENDED;

	public static ClientPhase ofGameState(String gameStateName) {
		if (gameStateName == null) return SUSPENDED;
		switch (gameStateName) {
			case "STARTING":
			case "LOGIN_SCREEN":
			case "LOGIN_SCREEN_AUTHENTICATOR":
				return OPEN;
			default:
				return SUSPENDED;
		}
	}
}
