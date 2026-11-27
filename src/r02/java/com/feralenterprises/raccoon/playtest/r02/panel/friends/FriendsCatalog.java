package com.feralenterprises.raccoon.playtest.r02.panel.friends;

import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

import static com.feralenterprises.raccoon.playtest.r02.panel.friends.LaneJson.array;
import static com.feralenterprises.raccoon.playtest.r02.panel.friends.LaneJson.object;

/** Reference catalog only. Collection observations are supplied by the current RouteContext. */
final class FriendsCatalog {
	private static final String PREFIX = "R02-Lane-Inputs/";
	private static final String LANE_SHA = "1f6a821d37705b7c7acdca9d598c2c5e7f74f7462536c9bf0f9039086545cece";
	private static final String REVIEW_SHA = "7a299ad1d4af98e8f8a9b86a558a230667ff7845c1034a54c8d39867db996899";
	final Map<String, String> categories;
	final List<Pet> roster;
	private final Map<String, Pet> pets;
	private final Map<String, Sprite> sprites;

	private FriendsCatalog(Map<String, String> categories, Map<String, Pet> pets, Map<String, Sprite> sprites, List<String> featured) {
		this.categories = Collections.unmodifiableMap(categories);
		this.pets = Map.copyOf(pets);
		this.sprites = Map.copyOf(sprites);
		List<Pet> ordered = new ArrayList<>(pets.values());
		ordered.sort(Comparator.comparingInt((Pet p) -> featured.contains(p.key) ? featured.indexOf(p.key) : featured.size())
			.thenComparing(p -> p.name, String.CASE_INSENSITIVE_ORDER));
		roster = List.copyOf(ordered);
	}

	static FriendsCatalog load(Path laneInputs, Path frozenReview) throws IOException {
		verified(laneInputs, 5_028_223L, LANE_SHA);
		String html = new String(verified(frozenReview.resolve("Project-Raccoon-R02.html"), 6_844_795L, REVIEW_SHA), StandardCharsets.UTF_8);
		try (ZipFile archive = new ZipFile(laneInputs.toFile())) {
			Map<String, Object> data = LaneJson.read(read(archive, "E/friends.json"));
			Map<String, Object> refs = object(data.get("preparedPetSourceReferences"));
			Map<String, Object> skill = object(data.get("skillLabels"));
			Map<String, Object> methods = object(data.get("methods"));
			Map<String, Object> assets = object(data.get("spriteItems"));
			Map<String, String> categories = new LinkedHashMap<>();
			object(data.get("categories")).forEach((k, v) -> categories.put(k, (String) v));
			Map<String, List<EncounterLink>> links = rewardLinks(archive);
			Map<String, Pet> pets = new LinkedHashMap<>();
			for (String line : read(archive, "E/pet-catalogue.tsv").split("\\R")) {
				if (line.isBlank() || line.startsWith("#") || line.startsWith("version\t")) continue;
				String[] row = line.split("\\t", -1);
				String key = row[0];
				int id = Integer.parseInt(row[3]);
				Map<String, Object> ref = object(refs.get(key));
				if (number(ref, "id") != id || number(object(assets.get(key)), "itemId") != id
					|| !categories.containsKey(row[2]) || !row[5].equals(ref.get("source")))
					throw new IOException("Pet reference identity mismatch: " + key);
				Methods options = methods.containsKey(key) ? new Methods(object(methods.get(key))) : null;
				Pet pet = new Pet(key, row[1], row[2], (String) skill.getOrDefault(key, categories.get(row[2])),
					row[5], options, links.getOrDefault(row[3], List.of()));
				if (pets.put(key, pet) != null) throw new IOException("Duplicate pet " + key);
			}
			if (pets.size() != 72) throw new IOException("Invalid pet roster size: " + pets.size());
			List<String> featured = new ArrayList<>();
			for (Object key : array(data.get("featured"))) featured.add((String) key);
			return new FriendsCatalog(categories, pets, sprites(html, assets), featured);
		} catch (IllegalArgumentException | ClassCastException | IndexOutOfBoundsException | NullPointerException ex) {
			throw new IOException("Malformed canonical Friends inputs", ex);
		}
	}

	Pet pet(String key) { return pets.get(key); }

	BufferedImage image(R02Assets assets, String key) {
		Sprite sprite = sprites.get(key);
		if (sprite == null) return null;
		BufferedImage image = assets.item(sprite.id);
		return image == null ? sprite.image : image;
	}

	int spriteId(String key) { return sprites.get(key).id; }

	private static Map<String, List<EncounterLink>> rewardLinks(ZipFile archive) throws IOException {
		Map<String, String> names = new LinkedHashMap<>();
		for (Object entry : array(LaneJson.read(read(archive, "B/outfitter.json")).get("encounters"))) {
			Map<String, Object> row = object(entry);
			names.put(string(row, "key"), string(row, "name"));
		}
		Map<String, List<EncounterLink>> byId = new LinkedHashMap<>();
		for (Object entry : array(LaneJson.read(read(archive, "B/boss-chronicle.json")).get("encounters"))) {
			Map<String, Object> row = object(entry);
			String key = string(row, "encounterKey");
			Map<String, Object> rewards = object(row.get("rewardArchive"));
			if (rewards == null) continue;
			for (Object raw : array(rewards.get("items"))) {
				Map<String, Object> item = object(raw);
				if (!(item.get("id") instanceof Number)) continue;
				String condition = string(item, "condition");
				if (condition.isEmpty()) condition = string(item, "note");
				byId.computeIfAbsent(Integer.toString(number(item, "id")), ignored -> new ArrayList<>())
					.add(new EncounterLink(key, names.getOrDefault(key, key), condition));
			}
		}
		return byId;
	}

	private static Map<String, Sprite> sprites(String html, Map<String, Object> assets) throws IOException {
		String marker = "  const sprites = ";
		int declaration = html.indexOf(marker);
		if (declaration < 0) throw new IOException("Missing frozen Friends sprite registry");
		int start = declaration + marker.length();
		int end = html.indexOf(";\n", start);
		Map<String, Object> frozen = LaneJson.read(html.substring(start, end));
		Map<String, Sprite> result = new LinkedHashMap<>();
		String mime = "data:image/png;base64,";
		for (Map.Entry<String, Object> entry : assets.entrySet()) {
			String key = entry.getKey();
			int id = number(object(entry.getValue()), "itemId");
			Map<String, Object> record = object(frozen.get(key));
			if (record == null || number(record, "id") != id) throw new IOException("Mismatched frozen sprite ID: " + key);
			String source = string(record, "src");
			if (!source.startsWith(mime)) throw new IOException("Non-PNG frozen sprite: " + key);
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(source.substring(mime.length()))));
			if (image == null) throw new IOException("Unable to decode frozen sprite: " + key);
			result.put(key, new Sprite(id, image));
		}
		return result;
	}

	private static String read(ZipFile archive, String name) throws IOException {
		java.util.zip.ZipEntry entry = archive.getEntry(PREFIX + name);
		if (entry == null) throw new IOException("Missing " + name);
		try (InputStream in = archive.getInputStream(entry)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static byte[] verified(Path path, long length, String expected) throws IOException {
		if (Files.size(path) != length) throw new IOException("Unexpected input length: " + path.getFileName());
		byte[] bytes = Files.readAllBytes(path);
		try {
			byte[] actual = MessageDigest.getInstance("SHA-256").digest(bytes);
			if (!expected.equals(String.format("%064x", new java.math.BigInteger(1, actual))))
				throw new IOException("SHA-256 mismatch: " + path.getFileName());
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
		return bytes;
	}

	private static String string(Map<String, Object> row, String key) {
		Object value = row.get(key);
		return value == null ? "" : (String) value;
	}

	private static int number(Map<String, Object> row, String key) { return ((Number) row.get(key)).intValue(); }

	static final class Pet {
		final String key, name, category, type, source;
		final Methods methods;
		final List<EncounterLink> links;

		Pet(String key, String name, String category, String type, String source, Methods methods, List<EncounterLink> links) {
			this.key = key;
			this.name = name;
			this.category = category;
			this.type = type;
			this.source = source;
			this.methods = methods;
			this.links = List.copyOf(links);
		}
	}

	static final class EncounterLink {
		final String key, name, condition;
		EncounterLink(String key, String name, String condition) {
			this.key = key;
			this.name = name;
			this.condition = condition;
		}
	}

	static final class Methods {
		final String skill, wiki;
		final List<Method> options;

		Methods(Map<String, Object> row) {
			skill = string(row, "skill");
			wiki = string(row, "wiki");
			List<Method> values = new ArrayList<>();
			for (Object option : array(row.get("options"))) values.add(new Method(object(option), skill));
			options = List.copyOf(values);
		}
	}

	static final class Method {
		final String name, place, requirement;
		final int base;
		final Integer listed;
		Method(Map<String, Object> row, String skill) {
			name = string(row, "name");
			place = string(row, "place");
			String requirement = string(row, "requirement");
			this.requirement = requirement.isEmpty() ? number(row, "level") + " " + skill : requirement;
			base = number(row, "base");
			listed = row.get("listed") == null ? null : number(row, "listed");
		}
	}

	private static final class Sprite {
		final int id;
		final BufferedImage image;
		Sprite(int id, BufferedImage image) { this.id = id; this.image = image; }
	}
}
