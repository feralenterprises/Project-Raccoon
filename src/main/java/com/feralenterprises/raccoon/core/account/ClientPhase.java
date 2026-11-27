package com.feralenterprises.raccoon.core.account;

/** Client connection phases, mirroring RuneLite game states without depending on RuneLite. */
public enum ClientPhase
{
	UNKNOWN,
	STARTING,
	LOGIN_SCREEN,
	LOGIN_SCREEN_AUTHENTICATOR,
	LOGGING_IN,
	LOADING,
	LOGGED_IN,
	CONNECTION_LOST,
	HOPPING;

	/** Maps a game-state name to a phase; unrecognised names become {@link #UNKNOWN}. */
	public static ClientPhase fromName(String name)
	{
		if (name != null)
		{
			for (ClientPhase p : values())
			{
				if (p.name().equals(name))
				{
					return p;
				}
			}
		}
		return UNKNOWN;
	}
}
