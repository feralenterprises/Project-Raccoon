package com.feralenterprises.raccoon.ui.theme;

import java.awt.Font;

/**
 * Supplies fonts for roles. The workbench uses {@link DefaultFontSource}; the production adapter is expected to map
 * roles to RuneLite's FontManager fonts (mapping documented in docs/INTEGRATION-MAPPING.md, not yet verified natively).
 */
public interface FontSource {
	Font font(FontRole role, TextScale scale);
}
