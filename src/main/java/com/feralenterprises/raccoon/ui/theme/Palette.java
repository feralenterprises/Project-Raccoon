package com.feralenterprises.raccoon.ui.theme;

import java.awt.Color;

/** Colour tokens for one skin. THEMED values are the kit's proposed component tokens. */
public final class Palette {
	public final Color background, panel, panelRaised, text, muted, border, accent, accentDim, progress, goal, trough,
		focus, active, empty, loading, unavailable, stale, shadow;

	private Palette(Color background, Color panel, Color panelRaised, Color text, Color muted, Color border, Color accent,
		Color accentDim, Color progress, Color goal, Color trough, Color focus, Color active, Color empty, Color loading,
		Color unavailable, Color stale, Color shadow) {
		this.background = background; this.panel = panel; this.panelRaised = panelRaised; this.text = text;
		this.muted = muted; this.border = border; this.accent = accent; this.accentDim = accentDim;
		this.progress = progress; this.goal = goal; this.trough = trough; this.focus = focus; this.active = active;
		this.empty = empty; this.loading = loading; this.unavailable = unavailable; this.stale = stale; this.shadow = shadow;
	}

	public static final Palette THEMED = new Palette(
		new Color(0x272623), new Color(0x302e29), new Color(0x3a372f), new Color(0xd9d1c2), new Color(0xaca394),
		new Color(0x81735d), new Color(0xb39a62), new Color(0x6e5f3e), new Color(0x677447), new Color(0x9a7f45),
		new Color(0x1c1b19), new Color(0xd8c48a), new Color(0x8fa65a), new Color(0x9a9183), new Color(0x8b9bb0),
		new Color(0xb26d5c), new Color(0xc99a4a), new Color(0, 0, 0, 110));

	public static final Palette STRUCTURAL = new Palette(
		new Color(0xf4f4f4), new Color(0xffffff), new Color(0xeaeaea), new Color(0x1a1a1a), new Color(0x5c5c5c),
		new Color(0x8a8a8a), new Color(0x1a1a1a), new Color(0xbdbdbd), new Color(0x6a6a6a), new Color(0x9a9a9a),
		new Color(0xe0e0e0), new Color(0x0050c8), new Color(0x2e7d32), new Color(0x6a6a6a), new Color(0x1565c0),
		new Color(0xb71c1c), new Color(0xb26a00), new Color(0, 0, 0, 0));
}
