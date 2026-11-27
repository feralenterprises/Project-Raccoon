package com.feralenterprises.raccoon.ui.theme;

/** Default and Large text. Large is a proposal: the factor is subject to native RuneLite font verification. */
public enum TextScale {
	DEFAULT(1.0f), LARGE(1.25f);

	private final float factor;

	TextScale(float factor) { this.factor = factor; }

	public float factor() { return factor; }
}
