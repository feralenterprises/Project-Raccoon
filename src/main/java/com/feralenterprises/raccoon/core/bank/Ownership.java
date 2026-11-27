package com.feralenterprises.raccoon.core.bank;

/** Answer to "does the account own this item?". {@link Kind#UNKNOWN} unless qualified current. */
public final class Ownership
{
	public enum Kind
	{
		OWNED,
		NOT_OWNED,
		UNKNOWN
	}

	private static final Ownership UNKNOWN = new Ownership(Kind.UNKNOWN, 0);
	private static final Ownership NOT_OWNED = new Ownership(Kind.NOT_OWNED, 0);

	private final Kind kind;
	private final int quantity;

	private Ownership(Kind kind, int quantity)
	{
		this.kind = kind;
		this.quantity = quantity;
	}

	static Ownership of(int quantity)
	{
		return quantity > 0 ? new Ownership(Kind.OWNED, quantity) : NOT_OWNED;
	}

	static Ownership unknown()
	{
		return UNKNOWN;
	}

	public Kind kind()
	{
		return kind;
	}

	/** Quantity when {@link Kind#OWNED}; otherwise 0. */
	public int quantity()
	{
		return quantity;
	}

	@Override
	public String toString()
	{
		return kind == Kind.OWNED ? "OWNED x" + quantity : kind.name();
	}
}
