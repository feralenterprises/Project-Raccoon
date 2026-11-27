package com.feralenterprises.raccoon.playtest.r02.runelite;

import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Authentic game-cache images for the framework: interface sprites via SpriteManager.getSpriteAsync(id, file, consumer)
 * and item images via ItemManager.getImage(id, quantity, stackable), which fills in asynchronously. Art is never
 * answered here (HostAssets takes it from the verified frozen package). Same pattern as the UI-004 probe provider.
 */
final class NativeAssets implements AssetProvider {
	private final SpriteManager sprites;
	private final ItemManager items;
	private final Map<Integer, BufferedImage> loaded = new ConcurrentHashMap<>();
	private final Map<Integer, Boolean> requested = new ConcurrentHashMap<>();

	NativeAssets(SpriteManager sprites, ItemManager items) {
		this.sprites = sprites;
		this.items = items;
	}

	@Override public BufferedImage art(ArtKey key) { return null; }

	@Override
	public BufferedImage nativeSprite(NativeSprite sprite, Runnable onLoaded) {
		if (!sprite.resolved()) return null;
		BufferedImage img = loaded.get(sprite.spriteId());
		if (img == null && requested.putIfAbsent(sprite.spriteId(), Boolean.TRUE) == null) {
			sprites.getSpriteAsync(sprite.spriteId(), sprite.file(), i -> {
				if (i != null) loaded.put(sprite.spriteId(), i);
				if (onLoaded != null) onLoaded.run();
			});
		}
		return img;
	}

	@Override
	public BufferedImage item(int itemId, int quantity, Runnable onLoaded) {
		AsyncBufferedImage img = items.getImage(itemId, quantity, quantity > 1);
		if (img != null && onLoaded != null) img.onLoaded(onLoaded);
		return img;
	}

	@Override
	public String provenance() { return "RuneLite SpriteManager/ItemManager (game cache)"; }
}
