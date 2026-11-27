package com.feralenterprises.raccoon.playtest.r02.panel.outfit;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Resolves the fixed R02 environment scene and surface assignments from the private frozen review input. */
final class FrozenCombatArt {
	private static final String HTML_SHA256 = "7a299ad1d4af98e8f8a9b86a558a230667ff7845c1034a54c8d39867db996899";
	private static final Pattern PNG = Pattern.compile("(--[a-z0-9-]+):url\\(\"data:image/png;base64,([a-zA-Z0-9+/=]+)\"\\)");
	private static final Pattern ENVIRONMENT = Pattern.compile("\\[data-environment=\"([a-z0-9-]+)\"\\]\\{([^{}]+)\\}");
	private static final Pattern ROLE = Pattern.compile("--(encounter-scene|surface):var\\((--[a-z0-9-]+)\\)");
	static final FrozenCombatArt NONE = new FrozenCombatArt(Map.of(), Map.of());
	private final Map<String, BufferedImage> scenes;
	private final Map<String, BufferedImage> surfaces;

	private FrozenCombatArt(Map<String, BufferedImage> scenes, Map<String, BufferedImage> surfaces) {
		this.scenes = Map.copyOf(scenes);
		this.surfaces = Map.copyOf(surfaces);
	}

	static FrozenCombatArt load(Path root) throws IOException {
		Path file = root.resolve("Project-Raccoon-R02.html");
		if (Files.size(file) != 6_844_795L) throw new IOException("Unexpected frozen R02 review length");
		byte[] bytes = Files.readAllBytes(file);
		if (!HTML_SHA256.equals(CombatCatalog.sha256(bytes))) throw new IOException("Frozen R02 review SHA-256 mismatch");
		String html = new String(bytes, StandardCharsets.UTF_8);

		Map<String, String> values = new HashMap<>();
		Matcher png = PNG.matcher(html);
		while (png.find()) values.put(png.group(1), png.group(2));

		Map<String, String> sceneKeys = new HashMap<>(), surfaceKeys = new HashMap<>();
		Matcher env = ENVIRONMENT.matcher(html);
		while (env.find()) {
			Matcher role = ROLE.matcher(env.group(2));
			while (role.find()) {
				Map<String, String> target = role.group(1).equals("encounter-scene") ? sceneKeys : surfaceKeys;
				target.put(env.group(1), role.group(2));
			}
		}
		Map<String, BufferedImage> decoded = new HashMap<>();
		return new FrozenCombatArt(resolve(sceneKeys, values, decoded), resolve(surfaceKeys, values, decoded));
	}

	private static Map<String, BufferedImage> resolve(Map<String, String> keys, Map<String, String> values,
		Map<String, BufferedImage> decoded) throws IOException {
		Map<String, BufferedImage> result = new HashMap<>();
		for (Map.Entry<String, String> entry : keys.entrySet()) {
			String key = entry.getValue();
			if (!values.containsKey(key)) continue;
			if (!decoded.containsKey(key)) {
				byte[] png = Base64.getDecoder().decode(values.get(key));
				BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
				if (image == null) throw new IOException("Unsupported frozen arena image " + key);
				decoded.put(key, image);
			}
			result.put(entry.getKey(), decoded.get(key));
		}
		return result;
	}

	BufferedImage image(String environment, boolean scene) {
		return (scene ? scenes : surfaces).get(environment);
	}
}
