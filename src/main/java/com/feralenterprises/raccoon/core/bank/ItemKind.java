package com.feralenterprises.raccoon.core.bank;

/** Classification of a slot's item definition, resolved by the adapter at capture time. */
public enum ItemKind
{
	ITEM,
	PLACEHOLDER,
	FILLER,
	NOTED,
	/** The definition could not be read; invalidates the observation. */
	UNREADABLE
}
