package com.feralenterprises.raccoon.ui.shell;

/**
 * Implemented by a page body with local detail levels (a selected encounter, an open record, a slot detail). Back
 * unwinds local detail first: while {@link #hasLocalBack()} is true the shell calls {@link #localBack()} instead of
 * leaving the route. The page restores its own opener, scroll and filters when it closes a detail, and calls the
 * listener given to {@link #setLocalBackListener} whenever {@link #hasLocalBack()} may have changed.
 */
public interface LocalBack {
	boolean hasLocalBack();

	/** Closes one local level. Called on the EDT only while {@link #hasLocalBack()} is true. */
	void localBack();

	/** The shell installs one listener per page; a page with no changing local state may ignore it. */
	default void setLocalBackListener(Runnable onChange) { }
}
