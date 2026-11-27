package com.feralenterprises.raccoon.ui.component;

/**
 * Controls whose label can be cut short report the narrowest width at which their current text still shows in full,
 * so a generic clipping audit can detect truncation (for example at Large text) without each lane measuring its own.
 * Reporting only: it never changes layout.
 */
public interface TextFit {
	/** Narrowest width, in pixels, at which the current text is shown without truncation. */
	int requiredTextWidth();

	/** True when the control is at least as wide as its text needs (false before it has a size). */
	default boolean textFits(int actualWidth) { return actualWidth >= requiredTextWidth(); }
}
