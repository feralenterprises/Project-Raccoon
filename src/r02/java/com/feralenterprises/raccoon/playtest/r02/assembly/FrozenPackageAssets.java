package com.feralenterprises.raccoon.playtest.r02.assembly;

import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Resolves R02 assets from the extracted frozen review package ({@code Project-Raccoon-R02/}, archive SHA-256
 * ff4a67ab…f1d3ba). Fail-closed per file: a file is decoded only if its SHA-256 matches the package's own
 * {@code SHA256SUMS.txt}; otherwise it resolves to null (accessible fallback) and is recorded in {@link #refused()}.
 * Encounter portraits and the Outfitter/Bank item sprites come from the package's embedded {@code of-sprites} set; the Tormented Demon portrait must hash
 * to {@link R02Assets#TORMENTED_DEMON_PORTRAIT_SHA256}. Nothing is fetched from the network. The package stays outside
 * the repository; pass its location to the host. Decoding happens on first use and is cached: call {@link #warm}
 * from a background thread before showing pages so the EDT never decodes.
 */
public final class FrozenPackageAssets implements R02Assets {
	private static final Pattern SPRITE = Pattern.compile("\"([A-Za-z0-9_'\\-]+)\\.png\"\\s*:\\s*\"data:image/png;base64,([A-Za-z0-9+/=]+)\"");

	private static final Pattern CSS_IMAGE = Pattern.compile("url\\(\"data:image/png;base64,([A-Za-z0-9+/=]+)\"\\)");

	private final Path root;
	private final Map<String, String> sums;
	private final Map<String, Optional<BufferedImage>> cache = new ConcurrentHashMap<>();
	private final Map<String, String> refused = new ConcurrentHashMap<>();
	private volatile Map<String, byte[]> portraits;

	public FrozenPackageAssets(Path packageRoot) throws IOException {
		this.root = packageRoot;
		Map<String, String> m = new HashMap<>();
		for (String line : Files.readAllLines(packageRoot.resolve("SHA256SUMS.txt"), StandardCharsets.UTF_8)) {
			String[] p = line.trim().split("\\s+", 2);
			if (p.length == 2) m.put(p[1], p[0]);
		}
		if (m.isEmpty()) throw new IOException("SHA256SUMS.txt is empty");
		sums = Collections.unmodifiableMap(m);
	}

	/** Files or portraits that failed verification or decoding, with the reason. */
	public Map<String, String> refused() { return Collections.unmodifiableMap(refused); }

	/**
	 * Frozen art by id: the packaged file when there is one; otherwise the stylesheet's embedded copy whose SHA-256 is
	 * exactly the id's {@code production_sha256} in the verified {@code frozen-manifest.csv} (PR-028, the {@code --stone}
	 * texture, ships only embedded). No other binding is attempted.
	 */
	@Override public BufferedImage art(String artId) {
		return cached("art:" + artId, () -> {
			try (java.util.stream.Stream<Path> s = Files.list(root.resolve("source/frozen-assets"))) {
				Optional<Path> f = s.filter(p -> p.getFileName().toString().startsWith(artId + "_")).findFirst();
				if (f.isPresent()) return verified("source/frozen-assets/" + f.get().getFileName());
			}
			String want = manifestSha().get(artId);
			byte[] b = want == null ? null : embeddedCss().get(want);
			return b == null ? null : decode(b);
		});
	}

	private volatile Map<String, String> manifest;
	private volatile Map<String, byte[]> cssImages;

	/** id -> production_sha256 from the verified frozen manifest. */
	private Map<String, String> manifestSha() {
		Map<String, String> m = manifest;
		if (m != null) return m;
		Map<String, String> out = new HashMap<>();
		byte[] b = verifiedBytes("source/frozen-manifest.csv");
		if (b != null) {
			String[] lines = new String(b, StandardCharsets.UTF_8).split("\r?\n");
			java.util.List<String> head = java.util.Arrays.asList(lines[0].split(","));
			int id = head.indexOf("id"), sha = head.indexOf("production_sha256");
			for (int i = 1; i < lines.length && id >= 0 && sha >= 0; i++) {
				String[] c = lines[i].split(",");
				if (c.length > Math.max(id, sha) && c[sha].matches("[0-9a-f]{64}")) out.put(c[id], c[sha]);
			}
		}
		manifest = Collections.unmodifiableMap(out);
		return manifest;
	}

	/** sha256 -> bytes of every {@code url("data:image/png;base64,...")} in the verified page. */
	private Map<String, byte[]> embeddedCss() {
		Map<String, byte[]> m = cssImages;
		if (m != null) return m;
		Map<String, byte[]> out = new HashMap<>();
		byte[] html = verifiedBytes("Project-Raccoon-R02.html");
		if (html != null) {
			Matcher x = CSS_IMAGE.matcher(new String(html, StandardCharsets.UTF_8));
			while (x.find()) { byte[] b = Base64.getDecoder().decode(x.group(1)); out.put(sha256(b), b); }
		}
		cssImages = Collections.unmodifiableMap(out);
		return cssImages;
	}

	private volatile LaneInputsArchive lanePack;

	/**
	 * Adds the verified lane item-sprite pack ({@code shared/osrs-items/<id>.png}) as the last item source, for frozen
	 * bank items the package has no sprite for (Lane C: 12788, 23971, 23975, 23979, 25867). Same exact ID only.
	 */
	public FrozenPackageAssets withLaneSprites(LaneInputsArchive pack) {
		lanePack = pack;
		cache.keySet().removeIf(k -> k.startsWith("item:"));
		return this;
	}

	/** Packaged sprite file first (XP Stash set), then the embedded Outfitter/Bank set ({@code i<id>.png}), then the lane pack. */
	@Override public BufferedImage item(int itemId) {
		if (itemId < 0) return null;
		return cached("item:" + itemId, () -> {
			String rel = "source/osrs-item-sprites/" + itemId + ".png";
			if (sums.containsKey(rel)) return verified(rel);
			byte[] b = embedded().get("i" + itemId);
			if (b == null && lanePack != null) b = lanePack.bytes("shared/osrs-items/" + itemId + ".png");
			return b == null ? null : decode(b);
		});
	}

	@Override public BufferedImage skillIcon(String skill) {
		if (!skill.matches("[a-z]+")) return null;
		return cached("skill:" + skill, () -> verified("source/osrs-skill-icons/" + skill + ".png"));
	}

	@Override public BufferedImage portrait(String encounterKey) {
		return cached("portrait:" + encounterKey, () -> {
			byte[] b = embedded().get(encounterKey);
			if (b == null) return null;
			if (encounterKey.equals("tormented-demons") && !TORMENTED_DEMON_PORTRAIT_SHA256.equals(sha256(b))) {
				refused.put("portrait:" + encounterKey, "hash differs from the owner-supplied portrait");
				return null;
			}
			return decode(b);
		});
	}

	/** Decodes the portraits and the given assets now, on the calling (background) thread. */
	public void warm(Iterable<String> artIds) {
		for (String k : embedded().keySet()) if (!k.matches("i\\d+")) portrait(k);
		for (String id : artIds) art(id);
	}

	/** The package's embedded {@code of-sprites} set (portraits and {@code i<id>} item sprites), from the verified HTML. */
	private Map<String, byte[]> embedded() {
		Map<String, byte[]> p = portraits;
		if (p != null) return p;
		synchronized (this) {
			if (portraits != null) return portraits;
			Map<String, byte[]> out = new HashMap<>();
			byte[] html = verifiedBytes("Project-Raccoon-R02.html");
			if (html != null) {
				String s = new String(html, StandardCharsets.UTF_8);
				int at = s.indexOf("id=\"of-sprites\"");
				int end = at < 0 ? -1 : s.indexOf("</script>", at);
				if (end > at) {
					Matcher m = SPRITE.matcher(s.substring(at, end));
					while (m.find()) out.put(m.group(1), Base64.getDecoder().decode(m.group(2)));
				}
			}
			portraits = Collections.unmodifiableMap(out);
			return portraits;
		}
	}

	private interface Load { BufferedImage get() throws IOException; }

	private BufferedImage cached(String key, Load load) {
		return cache.computeIfAbsent(key, k -> {
			try { return Optional.ofNullable(load.get()); }
			catch (IOException | RuntimeException e) { refused.put(k, e.toString()); return Optional.empty(); }
		}).orElse(null);
	}

	private BufferedImage verified(String rel) throws IOException {
		byte[] b = verifiedBytes(rel);
		return b == null ? null : decode(b);
	}

	private byte[] verifiedBytes(String rel) {
		String want = sums.get(rel);
		if (want == null) { refused.put(rel, "not listed in SHA256SUMS.txt"); return null; }
		try {
			byte[] b = Files.readAllBytes(root.resolve(rel));
			if (!want.equals(sha256(b))) { refused.put(rel, "SHA-256 mismatch"); return null; }
			return b;
		} catch (IOException e) {
			refused.put(rel, e.toString());
			return null;
		}
	}

	private static BufferedImage decode(byte[] b) throws IOException {
		BufferedImage i = ImageIO.read(new ByteArrayInputStream(b));
		if (i == null) throw new IOException("not a decodable image");
		return i;
	}

	public static String sha256(byte[] b) {
		try {
			StringBuilder s = new StringBuilder();
			for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) s.append(String.format("%02x", x));
			return s.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
