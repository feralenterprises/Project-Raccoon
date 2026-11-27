package com.feralenterprises.raccoon.playtest.r02.panel.bounties;

import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.MiniJson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bandit's Bounties inputs: the archived bounty board ({@code D/bounties.json}), the archived example loadouts
 * ({@code B/outfitter.json}, read only for each encounter's example weapon) and item names. Archived templates are not
 * owned gear, current observations or recommendations; the board's own status and detail copy is reference only.
 */
public final class BountiesData {
	/** Bounty key to archived Outfitter encounter key (frozen {@code aliases}; others use the slug of the name). */
	static final Map<String, String> OUTFIT_ALIASES = Map.of("Dagannoth_Prime", "dagannoth-kings", "Dagannoth_Rex", "dagannoth-kings",
		"Dagannoth_Supreme", "dagannoth-kings", "Dharok_the_Wretched", "barrows");
	/** Quest gates named by the frozen board ({@code preparedGate}). */
	static final Map<String, String> GATES = Map.of("Brutus", "The Ides of Milk", "Vorkath", "Dragon Slayer II",
		"Phantom_Muspah", "Secrets of the North", "Zulrah", "Regicide");

	static final class Entry {
		final String key, name, gear, referenceStatus, wiki, outfitKey, gate;
		/** The archived example weapon (Weapon slot of the encounter's example kit), or null when none is archived. */
		final Integer exampleWeapon;

		Entry(String key, String name, String gear, String referenceStatus, String wiki, String outfitKey, Integer exampleWeapon) {
			this.key = key;
			this.name = name;
			this.gear = gear;
			this.referenceStatus = referenceStatus;
			this.wiki = wiki;
			this.outfitKey = outfitKey;
			this.exampleWeapon = exampleWeapon;
			this.gate = GATES.get(key);
		}
	}

	final List<Entry> entries;
	private final Map<Integer, String> itemNames;

	private BountiesData(List<Entry> entries, Map<Integer, String> itemNames) {
		this.entries = entries;
		this.itemNames = itemNames;
	}

	public static BountiesData load(LaneInputs lane) throws IOException {
		Object outfitter = lane.json("B/outfitter.json");
		int weaponSlot = MiniJson.list(MiniJson.obj(outfitter, "paperdoll"), "gearNames").indexOf("Weapon");
		if (weaponSlot < 0) throw new IOException("Outfitter paperdoll has no Weapon slot");
		Map<String, Map<String, Object>> encounters = new HashMap<>();
		for (Object e : MiniJson.list(outfitter, "encounters")) encounters.put(MiniJson.str(e, "key"), castMap(e));
		Map<Integer, String> names = new HashMap<>();
		@SuppressWarnings("unchecked")
		Map<String, Object> raw = (Map<String, Object>) lane.json("shared/item-names.json");
		raw.forEach((k, v) -> names.put(Integer.valueOf(k), String.valueOf(v)));
		List<Entry> list = new ArrayList<>();
		@SuppressWarnings("unchecked")
		Map<String, Object> board = (Map<String, Object>) lane.json("D/bounties.json");
		for (Map.Entry<String, Object> b : board.entrySet()) {
			Object v = b.getValue();
			String name = MiniJson.str(v, "name");
			String outfitKey = OUTFIT_ALIASES.getOrDefault(b.getKey(), slug(name));
			Map<String, Object> enc = encounters.get(outfitKey);
			list.add(new Entry(b.getKey(), name, MiniJson.str(v, "gear"), MiniJson.str(v, "status"), MiniJson.str(v, "wiki"),
				enc == null ? null : outfitKey, enc == null ? null : weapon(enc, weaponSlot)));
		}
		if (list.size() != 11) throw new IOException("Expected 11 archived bounties, found " + list.size());
		return new BountiesData(Collections.unmodifiableList(list), names);
	}

	String itemName(int id) { return itemNames.getOrDefault(id, "Archived example weapon"); }

	static String slug(String name) {
		return name.toLowerCase(java.util.Locale.ROOT).replace("'", "").replace("’", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
	}

	private static Integer weapon(Map<String, Object> enc, int slot) {
		String style = MiniJson.str(enc, "kitStyle");
		Object kits = enc.get("kits");
		if (style == null || !(kits instanceof Map) || !(castMap(kits).get(style) instanceof Map)) return null;
		Object g0 = castMap(castMap(kits).get(style)).get("gear");
		List<?> gear = g0 instanceof List ? (List<?>) g0 : null;
		if (gear == null || gear.size() <= slot) return null;
		Object g = gear.get(slot);
		if (g instanceof List && !((List<?>) g).isEmpty()) g = ((List<?>) g).get(0);
		return g instanceof Number ? ((Number) g).intValue() : null;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> castMap(Object o) { return (Map<String, Object>) o; }
}
