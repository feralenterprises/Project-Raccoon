package com.feralenterprises.raccoon.ui.theme;

import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;

/** Logical-font fallback (Dialog). Sizes follow the kit guidance of 10-13 px live text at Default. */
public final class DefaultFontSource implements FontSource {
	private final Map<TextScale, Map<FontRole, Font>> cache = new EnumMap<>(TextScale.class);

	@Override
	public Font font(FontRole role, TextScale scale) {
		return cache.computeIfAbsent(scale, s -> new EnumMap<>(FontRole.class)).computeIfAbsent(role, r -> create(r, scale));
	}

	private static Font create(FontRole role, TextScale scale) {
		int style;
		float size;
		switch (role) {
			case TITLE: style = Font.BOLD; size = 13f; break;
			case VALUE: style = Font.BOLD; size = 12f; break;
			case SMALL: style = Font.PLAIN; size = 11f; break;
			case QUANTITY: style = Font.BOLD; size = 10f; break;
			case BODY:
			default: style = Font.PLAIN; size = 12f; break;
		}
		// Item quantities mirror native item overlays and do not grow with Large text.
		float factor = role == FontRole.QUANTITY ? 1f : scale.factor();
		return new Font(Font.DIALOG, style, Math.round(size * factor));
	}
}
