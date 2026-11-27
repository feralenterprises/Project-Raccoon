package com.feralenterprises.raccoon.playtest.r02.panel.outfit;

import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

import static com.feralenterprises.raccoon.playtest.r02.panel.outfit.LaneJson.array;
import static com.feralenterprises.raccoon.playtest.r02.panel.outfit.LaneJson.object;

/** Frozen catalog data only. All account observations are supplied separately by the shared RouteContext. */
public final class CombatCatalog {
	public static final String INPUT_SHA256 = "1f6a821d37705b7c7acdca9d598c2c5e7f74f7462536c9bf0f9039086545cece";
	private static final String PREFIX = "R02-Lane-Inputs/";
	private final Map<String, Encounter> encounters;
	private final Map<Integer, BufferedImage> extraSprites;
	private final FrozenCombatArt art;

	private CombatCatalog(Map<String, Encounter> encounters, Map<Integer, BufferedImage> extraSprites, FrozenCombatArt art) {
		this.encounters = Collections.unmodifiableMap(encounters);
		this.extraSprites = Collections.unmodifiableMap(extraSprites);
		this.art = art;
	}

	/** Loads the verified private lane archive before constructing any Swing pages. No assets are copied into Git. */
	public static CombatCatalog load(Path archive) throws IOException {
		return load(archive, null);
	}

	public static CombatCatalog load(Path archive, Path frozenReview) throws IOException {
		if (Files.size(archive) != 5_028_223) throw new IOException("Unexpected Lane Inputs archive length");
		byte[] input = Files.readAllBytes(archive);
		if (!INPUT_SHA256.equals(sha256(input))) throw new IOException("Lane Inputs archive SHA-256 mismatch");
		Map<String, byte[]> entries = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(input))) {
			for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
				String name = entry.getName();
				if (name.equals(PREFIX + "B/outfitter.json") || name.equals(PREFIX + "B/boss-chronicle.json")
					|| name.matches(PREFIX + "shared/osrs-items/[0-9]+\\.png")) {
					entries.put(name.substring(PREFIX.length()), zip.readAllBytes());
				}
			}
		}
		try {
			Map<String, Object> outfitter = LaneJson.read(new String(entries.get("B/outfitter.json"), StandardCharsets.UTF_8));
			Map<String, Object> chronicle = LaneJson.read(new String(entries.get("B/boss-chronicle.json"), StandardCharsets.UTF_8));
			Map<String, Map<String, Object>> references = new LinkedHashMap<>();
			for (Object raw : array(chronicle.get("encounters"))) {
				Map<String, Object> row = object(raw);
				references.put(string(row, "encounterKey"), row);
			}
			List<String> ranks = strings(chronicle.get("difficultyLabels"));
			Map<String, Encounter> encounters = new LinkedHashMap<>();
			for (Object raw : array(outfitter.get("encounters"))) {
				Map<String, Object> row = object(raw);
				String key = string(row, "key");
				Map<String, Object> details = references.remove(key);
				if (details == null || encounters.put(key, new Encounter(row, details, ranks)) != null)
					throw new IOException("Encounter registry mismatch: " + key);
			}
			if (!references.isEmpty() || encounters.size() != 60) throw new IOException("Incomplete encounter registry");

			Map<Integer, BufferedImage> sprites = new LinkedHashMap<>();
			for (Object raw : array(outfitter.get("kitItemSprites"))) {
				Map<String, Object> row = object(raw);
				String offline = string(row, "offlineSpritePath");
				if (offline.isEmpty()) continue;
				byte[] png = entries.get(offline);
				BufferedImage image = png == null ? null : ImageIO.read(new ByteArrayInputStream(png));
				if (image == null) throw new IOException("Invalid offline item sprite: " + offline);
				sprites.put(integer(row.get("itemId")), image);
			}
			return new CombatCatalog(encounters, sprites, frozenReview == null ? FrozenCombatArt.NONE : FrozenCombatArt.load(frozenReview));
		} catch (IllegalArgumentException | ClassCastException | NullPointerException | IndexOutOfBoundsException e) {
			throw new IOException("Invalid combat data in the pinned archive", e);
		}
	}

	BufferedImage art(String environment, boolean scene) { return art.image(environment, scene); }

	public List<Encounter> encounters() { return List.copyOf(encounters.values()); }
	public Encounter encounter(String key) { return encounters.get(key); }

	/** Prefer the shared authentic asset resolver; the authenticated lane pack covers additional offline item IDs. */
	public BufferedImage itemImage(R02Assets assets, int id) {
		BufferedImage image = assets.item(id);
		return image == null ? extraSprites.get(id) : image;
	}

	public static String styleName(String style) {
		return style.isEmpty() ? "Unknown" : style.substring(0, 1).toUpperCase(Locale.ROOT) + style.substring(1);
	}

	static String sha256(byte[] data) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
			StringBuilder hex = new StringBuilder();
			for (byte b : hash) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int integer(Object value) { return Math.toIntExact(((Number) value).longValue()); }

	private static String string(Map<String, Object> row, String key) {
		Object value = row.get(key);
		return value == null ? "" : (String) value;
	}

	private static List<String> strings(Object value) {
		List<String> out = new ArrayList<>();
		if (value != null) for (Object entry : array(value)) out.add((String) entry);
		return List.copyOf(out);
	}

	public static final class Slot {
		public enum State { SUPPLIED, EMPTY, UNKNOWN }
		static final Slot UNKNOWN = new Slot(State.UNKNOWN, 0, 0);
		static final Slot EMPTY = new Slot(State.EMPTY, 0, 0);
		public final State state;
		public final int id, quantity;

		private Slot(State state, int id, int quantity) {
			this.state = state;
			this.id = id;
			this.quantity = quantity;
		}

		private static Slot read(Object entry) {
			if (entry == null) return EMPTY;
			if (entry instanceof Map) {
				if (!Boolean.TRUE.equals(object(entry).get("unverified"))) throw new IllegalArgumentException("unknown slot flag");
				return UNKNOWN;
			}
			if (entry instanceof List) {
				List<Object> stack = array(entry);
				if (stack.size() != 2) throw new IllegalArgumentException("slot stack shape");
				return supplied(integer(stack.get(0)), integer(stack.get(1)));
			}
			return supplied(integer(entry), 1);
		}

		private static Slot supplied(int id, int quantity) {
			if (id <= 0 || quantity <= 0) throw new IllegalArgumentException("invalid item stack");
			return new Slot(State.SUPPLIED, id, quantity);
		}
	}

	public static final class Kit {
		public final String label, check, checkIcon, note, evidence, source;
		public final List<Slot> gear, inventory;
		public final List<String> issues;

		private Kit(Map<String, Object> row) {
			label = string(row, "label");
			check = string(row, "check");
			checkIcon = string(row, "checkIcon");
			note = string(row, "note");
			evidence = string(row, "evidence");
			source = string(row, "sourceUrl");
			gear = slots(row.get("gear"), 11);
			inventory = slots(row.get("inv"), 28);
			issues = strings(row.get("issues"));
		}

		private static List<Slot> slots(Object value, int size) {
			List<Object> entries = array(value);
			if (entries.size() != size) throw new IllegalArgumentException("invalid slot count");
			List<Slot> out = new ArrayList<>();
			for (Object entry : entries) out.add(Slot.read(entry));
			return List.copyOf(out);
		}

		public boolean packed(int id) { return inventory.stream().anyMatch(slot -> slot.id == id); }
	}

	public static final class Reward {
		public final String name, unit, condition, note, source;
		public final Integer id;

		private Reward(Map<String, Object> row) {
			name = string(row, "name");
			unit = row.containsKey("rewardType") ? string(row, "rewardType") : string(row, "kcUnit");
			condition = string(row, "condition");
			note = string(row, "note");
			source = string(row, "source");
			id = row.get("id") == null ? null : integer(row.get("id"));
		}
	}

	static final class SwitchSupplies {
		final String role;
		final List<Integer> itemIds;
		SwitchSupplies(Map<String, Object> row) {
			role = string(row, "role");
			List<Integer> ids = new ArrayList<>();
			for (Object id : array(row.get("inventory_item_ids"))) ids.add(integer(id));
			itemIds = List.copyOf(ids);
		}
	}

	public static final class Encounter {
		public final String key, name, category, area, environment, wiki, defaultStyle, strategy, strategySource,
			strategyQuality, accessReference, difficulty, rewardSource;
		public final int rank;
		public final List<String> aliases, approaches, rewardGaps;
		public final Map<String, Kit> kits;
		public final List<Reward> rewards;
		final String switchMode, switchPrimary;
		final List<String> switchTargets;
		final List<SwitchSupplies> switchSupplies;
		private final String search;

		private Encounter(Map<String, Object> row, Map<String, Object> details, List<String> ranks) {
			key = string(row, "key");
			name = string(row, "name");
			category = string(row, "group");
			area = string(row, "area");
			environment = string(row, "environment");
			wiki = string(row, "wiki_reference");
			defaultStyle = string(row, "kitStyle");
			aliases = strings(row.get("aliases"));
			search = (name + " " + string(row, "canonical_id") + " " + key + " " + String.join(" ", aliases)).toLowerCase(Locale.ROOT);
			Map<String, Kit> kitsByStyle = new LinkedHashMap<>();
			object(row.get("kits")).forEach((style, raw) -> kitsByStyle.put(style, new Kit(object(raw))));
			kits = Collections.unmodifiableMap(kitsByStyle);
			rank = details.get("difficulty") == null ? 0 : integer(details.get("difficulty"));
			difficulty = ranks.get(rank);
			Map<String, Object> reference = object(details.get("strategyReference"));
			approaches = strings(reference.get("approaches"));
			strategy = string(reference, "strategy");
			strategySource = string(reference, "url");
			strategyQuality = string(reference, "quality");
			accessReference = string(reference, "access");
			Map<String, Object> archive = details.get("rewardArchive") == null ? Map.of() : object(details.get("rewardArchive"));
			List<Reward> rows = new ArrayList<>();
			if (archive.containsKey("items"))
				for (Object raw : array(archive.get("items"))) rows.add(new Reward(object(raw)));
			rewards = List.copyOf(rows);
			rewardGaps = strings(archive.get("gaps"));
			rewardSource = string(archive, "wiki");

			Map<String, Object> switchPlan = row.containsKey("switchPlan") ? object(row.get("switchPlan")) : Map.of();
			switchMode = string(switchPlan, "mode");
			switchPrimary = string(switchPlan, "primary");
			switchTargets = strings(switchPlan.get("targets"));
			List<SwitchSupplies> supplies = new ArrayList<>();
			if (row.containsKey("packedSwitches"))
				for (Object raw : array(row.get("packedSwitches"))) supplies.add(new SwitchSupplies(object(raw)));
			switchSupplies = List.copyOf(supplies);
		}

		public boolean matches(String query) { return search.contains(query.trim().toLowerCase(Locale.ROOT)); }
	}
}
