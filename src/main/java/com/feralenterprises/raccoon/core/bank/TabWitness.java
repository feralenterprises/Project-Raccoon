package com.feralenterprises.raccoon.core.bank;

import java.util.Arrays;

/**
 * Bank tab sizes (tabs 1–9) used to check that the BANK container is complete. Known only when
 * every count was read and is nonnegative.
 */
public final class TabWitness
{
	private static final TabWitness UNKNOWN = new TabWitness(null);

	private final int[] counts;

	private TabWitness(int[] counts)
	{
		this.counts = counts;
	}

	/** Any negative count makes the witness unknown. */
	public static TabWitness of(int... counts)
	{
		if (counts == null)
		{
			return UNKNOWN;
		}
		for (int c : counts)
		{
			if (c < 0)
			{
				return UNKNOWN;
			}
		}
		return new TabWitness(counts.clone());
	}

	public static TabWitness unknown()
	{
		return UNKNOWN;
	}

	public boolean isKnown()
	{
		return counts != null;
	}

	/** Sum of the tab counts; throws ArithmeticException on overflow. Known witnesses only. */
	int total()
	{
		int sum = 0;
		for (int c : counts)
		{
			sum = Math.addExact(sum, c);
		}
		return sum;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof TabWitness && Arrays.equals(((TabWitness) o).counts, counts);
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(counts);
	}

	@Override
	public String toString()
	{
		return isKnown() ? "TabWitness" + Arrays.toString(counts) : "TabWitness[unknown]";
	}
}
