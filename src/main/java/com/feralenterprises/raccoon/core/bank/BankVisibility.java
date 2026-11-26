package com.feralenterprises.raccoon.core.bank;

/** What the client shows, read before any capture. */
public enum BankVisibility
{
	OPEN,
	CLOSED,
	/** The bank PIN keypad is visible. */
	PIN,
	/** Visibility could not be read. */
	UNREADABLE
}
