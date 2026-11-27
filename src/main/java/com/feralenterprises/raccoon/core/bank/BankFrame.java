package com.feralenterprises.raccoon.core.bank;

import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.ClientPhase;
import com.feralenterprises.raccoon.core.account.IdentityReading;
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
	private final ClientPhase phaseBefore;
	private final IdentityReading identityBefore;
	private final ClientPhase phaseAfter;
	private final IdentityReading identityAfter;

	/** Full capture: client phase and identity read before and after the container reads. */
	public BankFrame(AccountScope scope, long tickIndex, ClientPhase phaseBefore, IdentityReading identityBefore,
		ContainerRead bank, ContainerRead inventory, ContainerRead equipment, TabWitness witness,
		ClientPhase phaseAfter, IdentityReading identityAfter)
	{
		this.scope = scope;
		this.tickIndex = tickIndex;
		this.phaseBefore = phaseBefore;
		this.identityBefore = identityBefore;
		this.bank = Objects.requireNonNull(bank);
		this.inventory = Objects.requireNonNull(inventory);
		this.equipment = Objects.requireNonNull(equipment);
		this.witness = Objects.requireNonNull(witness);
		this.phaseAfter = phaseAfter;
		this.identityAfter = identityAfter;
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

	public ClientPhase phaseBefore()
	{
		return phaseBefore;
	}

	public IdentityReading identityBefore()
	{
		return identityBefore;
	}

	public ClientPhase phaseAfter()
	{
		return phaseAfter;
	}

	public IdentityReading identityAfter()
	{
		return identityAfter;
	}
}
