package com.feralenterprises.raccoon.ui.asset;

import java.awt.image.BufferedImage;

/**
 * The single asset interface shared by the simulator and the plugin. Every method may return {@code null}; components
 * must render a labelled fallback instead of failing. Implementations: workbench FixtureAssetProvider (review files
 * from an external folder) and, later, a RuneLite provider (SpriteManager, ItemManager, approved packaged art).
 */
public interface AssetProvider {
	/** Original approved artwork for a semantic role, or null if unavailable or not approved. */
	BufferedImage art(ArtKey key);

	/** Authentic interface sprite, or null if not (yet) loaded or unavailable. */
	BufferedImage nativeSprite(NativeSprite sprite, Runnable onLoaded);

	/**
	 * Item image for an item id and quantity, or null while unavailable. RuneLite's ItemManager returns an image that
	 * fills in asynchronously; {@code onLoaded} is invoked (on any thread) when a later call would return more.
	 */
	BufferedImage item(int itemId, int quantity, Runnable onLoaded);

	/** Short provenance label, e.g. "FIXTURE: review assets (not approved)". Shown in the workbench. */
	String provenance();
}
