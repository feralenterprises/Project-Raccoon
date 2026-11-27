package com.feralenterprises.raccoon.ui.model;

/** PROVISIONAL immutable item reference: game item id, quantity and display name supplied by the data layer. */
public final class ItemStack {
	private final int itemId;
	private final int quantity;
	private final String name;

	public ItemStack(int itemId, int quantity, String name) {
		if (itemId < 0) throw new IllegalArgumentException("itemId");
		if (quantity < 1) throw new IllegalArgumentException("quantity");
		this.itemId = itemId;
		this.quantity = quantity;
		this.name = name == null ? ("Item " + itemId) : name;
	}

	public int itemId() { return itemId; }
	public int quantity() { return quantity; }
	public String name() { return name; }

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ItemStack)) return false;
		ItemStack s = (ItemStack) o;
		return s.itemId == itemId && s.quantity == quantity && s.name.equals(name);
	}

	@Override
	public int hashCode() { return (itemId * 31 + quantity) * 31 + name.hashCode(); }
}
