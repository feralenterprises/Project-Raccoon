package com.feralenterprises.raccoon.bank;

import com.feralenterprises.raccoon.core.bank.ItemKind;
import net.runelite.api.gameval.ItemID;

/** Classifies an item definition (values read from {@code ItemComposition}). Pure, testable. */
final class ItemClassifier
{
	/** {@code getPlaceholderTemplateId()} value for placeholders (RuneLite 1.13.8 javadoc). */
	static final int PLACEHOLDER_TEMPLATE = 14401;
	/** {@code getNote()} value for noted items (RuneLite 1.13.8 javadoc). */
	static final int NOTED = 799;

	private ItemClassifier()
	{
	}

	/** The definition must describe the slot's own item id (donor rule), else unreadable. */
	static ItemKind classify(int itemId, int definitionId, int placeholderTemplateId, int note)
	{
		if (definitionId != itemId)
		{
			return ItemKind.UNREADABLE;
		}
		return classify(itemId, placeholderTemplateId, note);
	}

	static ItemKind classify(int itemId, int placeholderTemplateId, int note)
	{
		if (placeholderTemplateId != -1)
		{
			return placeholderTemplateId == PLACEHOLDER_TEMPLATE ? ItemKind.PLACEHOLDER : ItemKind.UNREADABLE;
		}
		if (itemId == ItemID.BANK_FILLER)
		{
			return ItemKind.FILLER;
		}
		if (note != -1)
		{
			// provisional: every noted variant is excluded (donor-defined list not supplied)
			return note == NOTED ? ItemKind.NOTED : ItemKind.UNREADABLE;
		}
		return ItemKind.ITEM;
	}
}
