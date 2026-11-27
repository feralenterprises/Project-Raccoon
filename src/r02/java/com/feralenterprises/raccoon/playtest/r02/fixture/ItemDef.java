package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.util.Objects;

/**
 * One registry item: exact OSRS item id, its in-game name, tradeability, the frozen bank category and the frozen
 * illustrative unit price (a sample label, never a live price). Tradeability the frozen data does not establish is
 * {@link Tradeability#UNKNOWN}.
 */
public final class ItemDef {
	public enum Tradeability { TRADEABLE, UNTRADEABLE, UNKNOWN }

	private final int id;
	private final String name;
	private final Tradeability tradeability;
	private final String category;
	private final Long illustrativePrice;

	public ItemDef(int id, String name, boolean tradeable, String category) {
		this(id, name, tradeable ? Tradeability.TRADEABLE : Tradeability.UNTRADEABLE, category, null);
	}

	public ItemDef(int id, String name, Tradeability tradeability, String category, Long illustrativePrice) {
		if (id < 0) throw new IllegalArgumentException("item id");
		if (illustrativePrice != null && illustrativePrice < 0) throw new IllegalArgumentException("price");
		this.id = id;
		this.name = Objects.requireNonNull(name);
		this.tradeability = Objects.requireNonNull(tradeability);
		this.category = Objects.requireNonNull(category);
		this.illustrativePrice = illustrativePrice;
	}

	public int id() { return id; }
	public String name() { return name; }
	/** True only when tradeability is established as TRADEABLE. */
	public boolean tradeable() { return tradeability == Tradeability.TRADEABLE; }
	public Tradeability tradeability() { return tradeability; }
	public String category() { return category; }
	/** Frozen illustrative unit price in gp, or null when unpriced. */
	public Long illustrativePrice() { return illustrativePrice; }
}
