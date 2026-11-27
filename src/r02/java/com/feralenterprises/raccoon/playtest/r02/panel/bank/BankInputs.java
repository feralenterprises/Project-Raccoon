package com.feralenterprises.raccoon.playtest.r02.panel.bank;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bank Memory's verified presentation inputs ({@code shared/bank-and-capture.json}): the frozen item catalog (name,
 * category, illustrative unit price per exact item ID) and the frozen state copy. Never a source of quantities or
 * ownership: those come only from the shared bank observation of the current scope.
 */
public final class BankInputs {
	/** Frozen catalog entry for one exact item ID. */
	static final class CatalogItem {
		final int id;
		final String name, category;
		final Long unitPrice;

		CatalogItem(int id, String name, String category, Long unitPrice) {
			this.id = id;
			this.name = name;
			this.category = category;
			this.unitPrice = unitPrice;
		}
	}

	final Map<Integer, CatalogItem> catalog;
	/** Frozen {@code bankStateInfo}: state id to {label, note}. */
	final Map<String, String[]> states;
	final LaneInputs lane;

	private BankInputs(LaneInputs lane) throws IOException {
		this.lane = lane;
		Object data = lane.json("shared/bank-and-capture.json");
		Map<Integer, CatalogItem> c = new HashMap<>();
		for (Object o : MiniJson.list(data, "bankCatalog")) {
			int id = (int) MiniJson.num(o, "id");
			Object price = ((Map<?, ?>) o).get("unitPrice");
			c.put(id, new CatalogItem(id, MiniJson.str(o, "name"), MiniJson.str(o, "category"), price instanceof Number ? ((Number) price).longValue() : null));
		}
		catalog = Collections.unmodifiableMap(c);
		Map<String, String[]> s = new HashMap<>();
		for (Map.Entry<String, Object> e : MiniJson.obj(data, "bankStates").entrySet()) {
			List<?> v = (List<?>) e.getValue();
			s.put(e.getKey(), new String[] {String.valueOf(v.get(0)), String.valueOf(v.get(1))});
		}
		for (String k : new String[] {"populated", "historical", "awaiting", "incomplete", "empty", "logout"})
			if (!s.containsKey(k)) throw new IOException("Frozen bank state copy missing: " + k);
		states = Collections.unmodifiableMap(s);
	}

	public static BankInputs load(LaneInputs lane) throws IOException { return new BankInputs(lane); }

	private final Map<Integer, java.awt.image.BufferedImage> sprites = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.awt.image.BufferedImage NONE = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);

	/**
	 * The verified OSRS item sprite for this exact ID ({@code shared/osrs-items/<id>.png}, RuneLite cache provenance in
	 * {@code shared/osrs-sprite-provenance.json}); null when the pack has none. Never another item's sprite.
	 */
	public java.awt.image.BufferedImage sprite(int id) {
		java.awt.image.BufferedImage img = sprites.computeIfAbsent(id, k -> {
			String rel = "shared/osrs-items/" + k + ".png";
			if (!lane.has(rel)) return NONE;
			try {
				java.awt.image.BufferedImage b = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(lane.bytes(rel)));
				return b == null ? NONE : b;
			} catch (IOException e) { return NONE; }
		});
		return img == NONE ? null : img;
	}

	public LaneInputs lane() { return lane; }
}
