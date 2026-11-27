package com.feralenterprises.raccoon.ui.lifecycle;

/**
 * Implemented by components that cache context-derived styling (fonts, colours). The shell calls this when the
 * skin or text scale changes, so components need no listener of their own on the shared UiContext.
 */
public interface ContextAware {
	void contextChanged();
}
