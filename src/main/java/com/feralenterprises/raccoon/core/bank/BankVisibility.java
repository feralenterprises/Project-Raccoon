package com.feralenterprises.raccoon.core.bank;

/** What the client shows, read before any capture. */
public enum BankVisibility
{
	OPEN,
	CLOSED,
	/** The bank PIN keypad is visible. */
	PIN,
	/** Bank root visible but its item widget hidden or missing (e.g. Bank Tags rebuilding). */
	ITEMS_NOT_READY,
	/** Visibility could not be read. */
	UNREADABLE
}
