package com.feralenterprises.raccoon.ui.layout;

/** Components whose height depends on the width they are given (wrapping text, flowing selectors). */
public interface HeightForWidth {
	int heightForWidth(int width);

	/** Narrowest width at which the component renders without clipping; used by the layout audit. */
	default int minimumContentWidth() { return 0; }
}
