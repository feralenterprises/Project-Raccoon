package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.theme.Palette;
import java.awt.Color;

/** Text tone roles. */
public enum Tone {
	TEXT, MUTED, ACCENT;

	public Color color(Palette p) {
		switch (this) {
			case MUTED: return p.muted;
			case ACCENT: return p.accent;
			default: return p.text;
		}
	}
}
