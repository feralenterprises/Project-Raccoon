package com.feralenterprises.raccoon.playtest.r02.runelite;

import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.FontSource;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.ui.FontManager;

/** Framework font roles on RuneLite's own fonts (FontManager, 1.13.8), as in the UI-004 probe. */
final class RuneLiteFonts implements FontSource {
	private final Map<TextScale, Map<FontRole, Font>> cache = new EnumMap<>(TextScale.class);

	@Override
	public Font font(FontRole role, TextScale scale) {
		return cache.computeIfAbsent(scale, s -> new EnumMap<>(FontRole.class)).computeIfAbsent(role, r -> {
			Font base;
			switch (r) {
				case TITLE: case VALUE: base = FontManager.getRunescapeBoldFont(); break;
				case SMALL: case QUANTITY: base = FontManager.getRunescapeSmallFont(); break;
				default: base = FontManager.getRunescapeFont(); break;
			}
			float f = r == FontRole.QUANTITY ? 1f : scale.factor();
			return f == 1f ? base : base.deriveFont(base.getSize2D() * f);
		});
	}
}
