package com.feralenterprises.raccoon.workbench;

import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/**
 * FIXTURE provider for the simulator only. Reads unapproved R02 review artwork and previously captured RuneLite
 * item-cache images from an EXTERNAL folder at runtime; nothing is bundled in any jar. Native interface sprites are
 * not available outside RuneLite, so {@link #nativeSprite} returns null and components show labelled fallbacks.
 */
public final class FixtureAssetProvider implements AssetProvider {
	private final Path artDir, itemDir;
	private final Map<ArtKey, BufferedImage> art = new EnumMap<>(ArtKey.class);
	private final Map<Integer, BufferedImage> items = new HashMap<>();
	private final AtomicInteger artRequests = new AtomicInteger();
	private final boolean disabled;
	private volatile int asyncDelayMs;
	private final java.util.Set<Integer> loaded = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public FixtureAssetProvider(Path artDir, Path itemDir) {
		this(artDir, itemDir, false);
	}

	private FixtureAssetProvider(Path artDir, Path itemDir, boolean disabled) {
		this.artDir = artDir;
		this.itemDir = itemDir;
		this.disabled = disabled;
	}

	/** A provider with nothing available, used to prove every component degrades without artwork. */
	public static FixtureAssetProvider empty() { return new FixtureAssetProvider(null, null, true); }

	public int artRequests() { return artRequests.get(); }

	/**
	 * Simulates RuneLite's AsyncBufferedImage: the first request for an item returns null and {@code onLoaded} fires
	 * later on a background thread. 0 (default) loads synchronously.
	 */
	public void setAsyncDelayMs(int ms) { asyncDelayMs = ms; loaded.clear(); }

	@Override
	public synchronized BufferedImage art(ArtKey key) {
		artRequests.incrementAndGet();
		if (disabled || artDir == null) return null;
		return art.computeIfAbsent(key, k -> read(artDir.resolve(k.candidateFile())));
	}

	@Override
	public BufferedImage nativeSprite(NativeSprite sprite, Runnable onLoaded) { return null; }

	@Override
	public synchronized BufferedImage item(int itemId, int quantity, Runnable onLoaded) {
		if (disabled || itemDir == null) return null;
		int delay = asyncDelayMs;
		if (delay > 0 && !loaded.contains(itemId)) {
			Thread t = new Thread(() -> {
				try { Thread.sleep(delay); } catch (InterruptedException e) { return; }
				loaded.add(itemId);
				if (onLoaded != null) onLoaded.run();
			}, "fixture-item-" + itemId);
			t.setDaemon(true);
			t.start();
			return null;
		}
		return items.computeIfAbsent(itemId, id -> read(itemDir.resolve(id + ".png")));
	}

	@Override
	public String provenance() {
		return disabled ? "FIXTURE: no assets (degradation test)"
			: "FIXTURE: unapproved R02 review art + prior RuneLite item-cache captures (simulator only)";
	}

	private static BufferedImage read(Path p) {
		try {
			return Files.isRegularFile(p) ? ImageIO.read(p.toFile()) : null;
		} catch (IOException e) {
			return null;
		}
	}
}
