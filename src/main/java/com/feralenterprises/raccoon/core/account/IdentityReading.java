package com.feralenterprises.raccoon.core.account;

/**
 * One fresh read of the client's account hash. The hash never leaves this package: there is
 * no getter, and {@link #toString()} is redacted.
 */
public final class IdentityReading
{
	private static final long UNKNOWN_HASH = -1L;
	private static final IdentityReading UNKNOWN = new IdentityReading(Kind.UNKNOWN, UNKNOWN_HASH);
	private static final IdentityReading FAILED = new IdentityReading(Kind.READ_FAILED, UNKNOWN_HASH);

	enum Kind
	{
		VERIFIED,
		UNKNOWN,
		READ_FAILED
	}

	private final Kind kind;
	private final long hash;

	private IdentityReading(Kind kind, long hash)
	{
		this.kind = kind;
		this.hash = hash;
	}

	/** A raw client value; {@code -1} means the client has no identity. */
	public static IdentityReading of(long accountHash)
	{
		return accountHash == UNKNOWN_HASH ? UNKNOWN : new IdentityReading(Kind.VERIFIED, accountHash);
	}

	public static IdentityReading unknown()
	{
		return UNKNOWN;
	}

	/** The read threw or was otherwise unusable. */
	public static IdentityReading failed()
	{
		return FAILED;
	}

	public boolean isVerified()
	{
		return kind == Kind.VERIFIED;
	}

	Kind kind()
	{
		return kind;
	}

	long hash()
	{
		return hash;
	}

	@Override
	public String toString()
	{
		return "IdentityReading[" + kind + (kind == Kind.VERIFIED ? ", redacted" : "") + "]";
	}
}
