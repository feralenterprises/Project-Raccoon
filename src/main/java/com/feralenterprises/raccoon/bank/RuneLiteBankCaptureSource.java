package com.feralenterprises.raccoon.bank;

import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.ClientPhase;
import com.feralenterprises.raccoon.core.account.IdentityReading;
import com.feralenterprises.raccoon.core.bank.BankCaptureSource;
import com.feralenterprises.raccoon.core.bank.BankFrame;
import com.feralenterprises.raccoon.core.bank.BankVisibility;
import com.feralenterprises.raccoon.core.bank.ContainerRead;
import com.feralenterprises.raccoon.core.bank.ItemKind;
import com.feralenterprises.raccoon.core.bank.RawSlot;
import com.feralenterprises.raccoon.core.bank.TabWitness;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/** Read-only RuneLite reads for bank observation. Client thread only; never writes. */
final class RuneLiteBankCaptureSource implements BankCaptureSource
{
	private static final int[] TAB_VARBITS = {
		VarbitID.BANK_TAB_1, VarbitID.BANK_TAB_2, VarbitID.BANK_TAB_3, VarbitID.BANK_TAB_4, VarbitID.BANK_TAB_5,
		VarbitID.BANK_TAB_6, VarbitID.BANK_TAB_7, VarbitID.BANK_TAB_8, VarbitID.BANK_TAB_9};

	private final Client client;

	RuneLiteBankCaptureSource(Client client)
	{
		this.client = client;
	}

	@Override
	public BankVisibility visibility()
	{
		return visibilityOf(visible(InterfaceID.BankpinKeypad.UNIVERSE), visible(InterfaceID.Bankmain.UNIVERSE),
			visible(InterfaceID.Bankmain.ITEMS));
	}

	/** Bank open requires the root and its item widget visible, and no PIN (donor rule). */
	static BankVisibility visibilityOf(boolean pinVisible, boolean rootVisible, boolean itemsVisible)
	{
		if (pinVisible)
		{
			return BankVisibility.PIN;
		}
		if (!rootVisible)
		{
			return BankVisibility.CLOSED;
		}
		return itemsVisible ? BankVisibility.OPEN : BankVisibility.ITEMS_NOT_READY;
	}

	private boolean visible(int componentId)
	{
		Widget w = client.getWidget(componentId);
		return w != null && !w.isHidden();
	}

	@Override
	public BankFrame capture(AccountScope scope, long tickIndex)
	{
		// identity and phase are read before and after the container reads (donor rule); the tracker
		// checks both against the central lifecycle
		ClientPhase phaseBefore = phase();
		IdentityReading before = identity();
		ContainerRead bank = read(InventoryID.BANK);
		ContainerRead inventory = read(InventoryID.INV);
		ContainerRead equipment = read(InventoryID.WORN);
		TabWitness witness = witness();
		ClientPhase phaseAfter = phase();
		IdentityReading after = identity();
		return new BankFrame(scope, tickIndex, phaseBefore, before, bank, inventory, equipment, witness, phaseAfter, after);
	}

	private ClientPhase phase()
	{
		try
		{
			return ClientPhase.fromName(client.getGameState().name());
		}
		catch (RuntimeException e)
		{
			return ClientPhase.UNKNOWN;
		}
	}

	private IdentityReading identity()
	{
		try
		{
			return IdentityReading.of(client.getAccountHash());
		}
		catch (RuntimeException e)
		{
			return IdentityReading.failed();
		}
	}

	private ContainerRead read(int containerId)
	{
		try
		{
			ItemContainer c = client.getItemContainer(containerId);
			if (c == null)
			{
				return ContainerRead.missing();
			}
			Item[] items = c.getItems();
			if (items == null)
			{
				return ContainerRead.unreadable();
			}
			List<RawSlot> slots = new ArrayList<>(items.length);
			for (Item item : items)
			{
				slots.add(item == null ? null : RawSlot.of(item.getId(), item.getQuantity(), kind(item.getId())));
			}
			return ContainerRead.present(slots);
		}
		catch (RuntimeException e)
		{
			return ContainerRead.unreadable();
		}
	}

	private ItemKind kind(int itemId)
	{
		if (itemId < 0)
		{
			return ItemKind.ITEM;
		}
		try
		{
			ItemComposition def = client.getItemDefinition(itemId);
			if (def == null)
			{
				return ItemKind.UNREADABLE;
			}
			return ItemClassifier.classify(itemId, def.getId(), def.getPlaceholderTemplateId(), def.getNote());
		}
		catch (RuntimeException e)
		{
			return ItemKind.UNREADABLE;
		}
	}

	private TabWitness witness()
	{
		try
		{
			int[] counts = new int[TAB_VARBITS.length];
			for (int i = 0; i < counts.length; i++)
			{
				counts[i] = client.getVarbitValue(TAB_VARBITS[i]);
			}
			return TabWitness.of(counts);
		}
		catch (RuntimeException e)
		{
			return TabWitness.unknown();
		}
	}
}
