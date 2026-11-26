package com.feralenterprises.raccoon.core.bank;

import java.util.Objects;

/** One raw container slot as read from the client. Empty slots are {@code id -1, quantity 0}. */
public final class RawSlot
{
	private final int itemId;
	private final int quantity;
	private final ItemKind kind;

	private RawSlot(int itemId, int quantity, ItemKind kind)
	{
		this.itemId = itemId;
		this.quantity = quantity;
		this.kind = Objects.requireNonNull(kind);
	}

	public static RawSlot of(int itemId, int quantity, ItemKind kind)
	{
		return new RawSlot(itemId, quantity, kind);
	}

	public static RawSlot item(int itemId, int quantity)
	{
		return new RawSlot(itemId, quantity, ItemKind.ITEM);
	}

	public static RawSlot empty()
	{
		return new RawSlot(-1, 0, ItemKind.ITEM);
	}

	public int itemId()
	{
		return itemId;
	}

	public int quantity()
	{
		return quantity;
	}

	public ItemKind kind()
	{
		return kind;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof RawSlot))
		{
			return false;
		}
		RawSlot r = (RawSlot) o;
		return r.itemId == itemId && r.quantity == quantity && r.kind == kind;
	}

	@Override
	public int hashCode()
	{
		return (itemId * 31 + quantity) * 31 + kind.hashCode();
	}

	@Override
	public String toString()
	{
		return "RawSlot[" + itemId + " x" + quantity + " " + kind + "]";
	}
}
