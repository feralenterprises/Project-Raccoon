package com.feralenterprises.raccoon.core.bank;

import com.feralenterprises.raccoon.core.account.AccountScope;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Qualified bank evidence: contents of the BANK container plus separately reported carried items,
 * with its provenance (scope and tick range). Immutable. Being current is decided by the tracker
 * and the lifecycle, never by holding this object.
 */
public final class BankEvidence
{
	private final AccountScope scope;
	private final long firstTick;
	private final long lastTick;
	private final Map<Integer, Integer> contents;
	private final CarriedEvidence inventory;
	private final CarriedEvidence equipment;
	private final TabWitness witness;
	private final long generation;

	BankEvidence(long generation, AccountScope scope, long firstTick, long lastTick, Map<Integer, Integer> contents, CarriedEvidence inventory,
		CarriedEvidence equipment, TabWitness witness)
	{
		this.scope = scope;
		this.firstTick = firstTick;
		this.lastTick = lastTick;
		this.contents = Collections.unmodifiableMap(new TreeMap<>(contents));
		this.inventory = inventory;
		this.equipment = equipment;
		this.witness = witness;
		this.generation = generation;
	}

	/**
	 * Unique per qualification. Bank results derived from this evidence are valid only while
	 * {@link BankObservationView#isCurrent(BankEvidence)} holds; account scope alone is not enough.
	 */
	public long generation()
	{
		return generation;
	}

	/** Scope (including authority) this evidence was qualified under. */
	public AccountScope scope()
	{
		return scope;
	}

	/** Tick of the first observation in the qualifying streak. */
	public long firstTick()
	{
		return firstTick;
	}

	/**
	 * Tick on which the streak <em>qualified</em>. Not the latest successful revalidation: unchanged
	 * revalidation keeps the same evidence and does not move this tick. Do not present it as the
	 * latest observed tick.
	 */
	public long lastTick()
	{
		return lastTick;
	}

	/** Item id → quantity in the bank (placeholders, filler and noted variants excluded). */
	public Map<Integer, Integer> contents()
	{
		return contents;
	}

	public boolean isEmptyBank()
	{
		return contents.isEmpty();
	}

	public CarriedEvidence inventory()
	{
		return inventory;
	}

	public CarriedEvidence equipment()
	{
		return equipment;
	}

	public TabWitness witness()
	{
		return witness;
	}

	@Override
	public String toString()
	{
		return "BankEvidence[gen " + generation + ", " + scope + ", ticks " + firstTick + ".." + lastTick + ", " + contents.size() + " items, inv "
			+ inventory + ", worn " + equipment + "]";
	}
}
