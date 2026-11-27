package com.feralenterprises.raccoon.playtest.r02.host;

import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The framework asset provider for the playtest host. Approved R02 art comes only from the verified frozen package
 * (by its asset id); keys whose candidate file is not a frozen asset id resolve to null (labelled fallback). Interface
 * sprites and item images come from the native delegate (RuneLite SpriteManager / ItemManager, i.e. the game cache)
 * when one is given, otherwise item images fall back to the frozen authentic sprites.
 */
public final class HostAssets implements AssetProvider {
	private static final Pattern ASSET_ID = Pattern.compile("^((?:PR|SIG)-\\d+)_.*");

	private final R02Assets frozen;
	private final AssetProvider nativeDelegate;

	public HostAssets(R02Assets frozen, AssetProvider nativeDelegate) {
		this.frozen = Objects.requireNonNull(frozen);
		this.nativeDelegate = nativeDelegate;
	}

	/** The frozen asset id a key binds, or null when the key's candidate file is not a frozen asset. */
	public static String assetId(ArtKey key) {
		Matcher m = ASSET_ID.matcher(key.candidateFile());
		return m.matches() ? m.group(1) : null;
	}

	@Override public BufferedImage art(ArtKey key) {
		String id = assetId(key);
		return id == null ? null : frozen.art(id);
	}

	@Override public BufferedImage nativeSprite(NativeSprite sprite, Runnable onLoaded) {
		return nativeDelegate == null ? null : nativeDelegate.nativeSprite(sprite, onLoaded);
	}

	@Override public BufferedImage item(int itemId, int quantity, Runnable onLoaded) {
		return nativeDelegate != null ? nativeDelegate.item(itemId, quantity, onLoaded) : frozen.item(itemId);
	}

	@Override public String provenance() {
		return nativeDelegate == null ? "R02 PLAYTEST: frozen package (verified), no native sprites"
			: "R02 PLAYTEST: frozen package art (verified) + " + nativeDelegate.provenance();
	}
}
