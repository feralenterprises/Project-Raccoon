package com.feralenterprises.raccoon.ui.model;

/**
 * PROVISIONAL fixture contract. Presentation states every data-bearing component supports. Mapping to production
 * read models (e.g. UNKNOWN vs EMPTY vs STALE vs VERIFIED in Burrow, AccountSnapshot publishability in FR-002) is in
 * docs/INTEGRATION-MAPPING.md.
 */
public enum DataState {
	/** Current, trusted data. */
	ACTIVE("Active"),
	/** Data is known and there is nothing to show (e.g. no items equipped). Not the same as unknown. */
	EMPTY("Empty"),
	/** Data is being gathered; nothing trustworthy to show yet. */
	LOADING("Loading"),
	/** Data cannot be shown (signed out, unsupported world, source missing). Never rendered as empty. */
	UNAVAILABLE("Unavailable"),
	/** Last known data shown with its age; may no longer match the game. */
	STALE("Stale");

	private final String label;

	DataState(String label) { this.label = label; }

	public String label() { return label; }

	public boolean showsData() { return this == ACTIVE || this == STALE; }
}
