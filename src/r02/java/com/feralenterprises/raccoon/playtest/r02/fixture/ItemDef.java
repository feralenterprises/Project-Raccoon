package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.util.Objects;

/** One registry item: exact OSRS item id, its in-game name, tradeability and the frozen bank category. */
public final class ItemDef {
	private final int id;
	private final String name;
	private final boolean tradeable;
	private final String category;

	public ItemDef(int id, String name, boolean tradeable, String category) {
		if (id < 0) throw new IllegalArgumentException("item id");
		this.id = id;
		this.name = Objects.requireNonNull(name);
		this.tradeable = tradeable;
		this.category = Objects.requireNonNull(category);
	}

	public int id() { return id; }
	public String name() { return name; }
	public boolean tradeable() { return tradeable; }
	public String category() { return category; }
}
