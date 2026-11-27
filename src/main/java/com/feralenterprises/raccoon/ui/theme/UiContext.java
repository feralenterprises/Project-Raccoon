package com.feralenterprises.raccoon.ui.theme;

import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;

/**
 * One instance per plugin (or simulator) holding skin, text scale, fonts and assets. Not a global singleton, so the
 * framework never mutates RuneLite's look-and-feel or UIManager defaults.
 */
public final class UiContext {
	private final FontSource fonts;
	private final AssetProvider assets;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private final BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
	private volatile Skin skin = Skin.THEMED;
	private volatile TextScale textScale = TextScale.DEFAULT;

	public UiContext(FontSource fonts, AssetProvider assets) {
		this.fonts = Objects.requireNonNull(fonts, "fonts");
		this.assets = Objects.requireNonNull(assets, "assets");
	}

	public Skin skin() { return skin; }
	public boolean themed() { return skin == Skin.THEMED; }
	public TextScale textScale() { return textScale; }
	public AssetProvider assets() { return assets; }
	public Palette palette() { return themed() ? Palette.THEMED : Palette.STRUCTURAL; }
	public Font font(FontRole role) { return fonts.font(role, textScale); }

	/** Font metrics usable before a component is displayable (layout runs offscreen in tests). */
	public FontMetrics metrics(FontRole role) {
		return scratch.getGraphics().getFontMetrics(font(role));
	}

	public void setSkin(Skin s) { if (s != skin) { skin = s; fire(); } }
	public void setTextScale(TextScale t) { if (t != textScale) { textScale = t; fire(); } }

	/** Listener runs on the EDT after a skin or text change; shells revalidate and repaint their whole tree. */
	public void addChangeListener(Runnable r) { listeners.add(r); }
	public void removeChangeListener(Runnable r) { listeners.remove(r); }

	/** Registered change listeners; a disposed shell must leave this unchanged (leak check). */
	public int listenerCount() { return listeners.size(); }

	private void fire() {
		Runnable all = () -> listeners.forEach(Runnable::run);
		if (SwingUtilities.isEventDispatchThread()) all.run(); else SwingUtilities.invokeLater(all);
	}
}
