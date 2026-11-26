package com.feralenterprises.raccoon.core.bank;

import com.feralenterprises.raccoon.core.account.AccountScope;
import java.util.Objects;

/** One raw capture on one game tick, under the scope it was captured for. */
public final class BankFrame
{
	private final AccountScope scope;
	private final long tickIndex;
	private final ContainerRead bank;
	private final ContainerRead inventory;
	private final ContainerRead equipment;
	private final TabWitness witness;

	public BankFrame(AccountScope scope, long tickIndex, ContainerRead bank, ContainerRead inventory, ContainerRead equipment,
		TabWitness witness)
	{
		this.scope = scope;
		this.tickIndex = tickIndex;
		this.bank = Objects.requireNonNull(bank);
		this.inventory = Objects.requireNonNull(inventory);
		this.equipment = Objects.requireNonNull(equipment);
		this.witness = Objects.requireNonNull(witness);
	}

	public AccountScope scope()
	{
		return scope;
	}

	public long tickIndex()
	{
		return tickIndex;
	}

	public ContainerRead bank()
	{
		return bank;
	}

	public ContainerRead inventory()
	{
		return inventory;
	}

	public ContainerRead equipment()
	{
		return equipment;
	}

	public TabWitness witness()
	{
		return witness;
	}
}
