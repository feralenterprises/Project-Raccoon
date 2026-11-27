package com.feralenterprises.raccoon.playtest.r02.shared;

import java.awt.image.BufferedImage;

/**
 * The shared R02 asset resolver lanes call. Every method returns null when the asset is unresolved; callers then draw
 * {@link #fallbackLabel} as an accessible text fallback in the image's place. Nothing is invented and nothing is
 * fetched from the network. Item sprites and skill icons are the frozen authentic OSRS sprites (or, in a RuneLite
 * host, ItemManager/SkillIconManager); encounter portraits are the frozen embedded set.
 */
public interface R02Assets {
	/** The owner-supplied Tormented Demon portrait; must be served byte-identical. */
	String TORMENTED_DEMON_PORTRAIT_SHA256 = "bee7861992a997be7fb24aac25f2c91de5d1ff037142c7f75bffd629365965ab";

	/** Frozen approved art by asset id (for example {@code PR-032}). */
	BufferedImage art(String artId);

	/** Authentic OSRS item sprite by exact item id. */
	BufferedImage item(int itemId);

	/** Authentic OSRS skill icon by lower-case skill name (for example {@code firemaking}). */
	BufferedImage skillIcon(String skill);

	/** Encounter portrait by registry key (for example {@code tormented-demons}). */
	BufferedImage portrait(String encounterKey);

	/** Short accessible text to draw when an image is unresolved: the item, skill or encounter name. */
	static String fallbackLabel(String name) { return name == null || name.isEmpty() ? "Image unavailable" : name; }

	/** A resolver with nothing: every lane can render and test with no asset files at all. */
	static R02Assets none() {
		return new R02Assets() {
			@Override public BufferedImage art(String artId) { return null; }
			@Override public BufferedImage item(int itemId) { return null; }
			@Override public BufferedImage skillIcon(String skill) { return null; }
			@Override public BufferedImage portrait(String encounterKey) { return null; }
		};
	}
}
