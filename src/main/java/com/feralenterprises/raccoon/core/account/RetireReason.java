package com.feralenterprises.raccoon.core.account;

/** Why the last login was retired. Safe for diagnostics: carries no identity. */
public enum RetireReason
{
	NONE,
	LOGOUT,
	AUTHENTICATOR,
	DIFFERENT_ACCOUNT,
	IDENTITY_LOST,
	PLUGIN_STOPPED,
	UNSUPPORTED_WORLD,
	UNRECOGNIZED_CONTINUITY,
	CONTINUITY_TIMEOUT,
	AUTHENTICATION_TIMEOUT
}
