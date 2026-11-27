package com.feralenterprises.raccoon.core.bank;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One carried container (inventory or equipment), reported separately from the bank. Missing
 * means <em>unreported</em>, never empty.
 */
public final class CarriedEvidence
{
	private static final CarriedEvidence UNREPORTED = new CarriedEvidence(null, false);

	private final Map<Integer, Integer> items;
	private final boolean excludedNoted;

	private CarriedEvidence(Map<Integer, Integer> items, boolean excludedNoted)
	{
		this.items = items;
		this.excludedNoted = excludedNoted;
	}

	static CarriedEvidence unreported()
	{
		return UNREPORTED;
	}

	static CarriedEvidence reported(Map<Integer, Integer> items, boolean excludedNoted)
	{
		return new CarriedEvidence(Collections.unmodifiableMap(new TreeMap<>(items)), excludedNoted);
	}

	public boolean isReported()
	{
		return items != null;
	}

	/** Item id → quantity, present only when reported. Excludes placeholders, filler, noted variants. */
	public Optional<Map<Integer, Integer>> items()
	{
		return Optional.ofNullable(items);
	}

	/** True if noted variants were present and excluded: combined counts are then not claimable. */
	public boolean hadExcludedNoted()
	{
		return excludedNoted;
	}

	int quantity(int itemId)
	{
		return items == null ? 0 : items.getOrDefault(itemId, 0);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof CarriedEvidence))
		{
			return false;
		}
		CarriedEvidence c = (CarriedEvidence) o;
		return c.excludedNoted == excludedNoted && (c.items == null ? items == null : c.items.equals(items));
	}

	@Override
	public int hashCode()
	{
		return (items == null ? 0 : items.hashCode()) * 2 + (excludedNoted ? 1 : 0);
	}

	@Override
	public String toString()
	{
		return isReported() ? "Carried[" + items.size() + " items" + (excludedNoted ? ", noted excluded" : "") + "]" : "Carried[unreported]";
	}
}
