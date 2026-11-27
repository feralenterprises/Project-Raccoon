package com.feralenterprises.raccoon.playtest.r02.panel.activity;

import com.feralenterprises.raccoon.playtest.r02.panel.home.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.home.MiniJson;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Current Activity's verified private inputs: the 123-entry catalog ({@code A/activity-catalog.json}) and, from the
 * frozen {@code Project-Raccoon-R02.html} (SHA-256 {@value #FROZEN_HTML_SHA256}), the environment presets
 * ({@code :is(#route,#activity)[data-environment]} custom properties in cascade order), the {@code #activity-art}
 * portraits and the five sample-session label sets. Only presentation is taken from the frozen page: every count, time
 * and XP value comes from the shared fixture snapshot.
 */
public final class ActivityInputs {
	public static final String FROZEN_HTML_SHA256 = "7a299ad1d4af98e8f8a9b86a558a230667ff7845c1034a54c8d39867db996899";
	/** Boss portraits the frozen page deliberately hides. */
	static final List<String> HIDDEN_PORTRAITS = List.of("artio", "callisto", "calvar-ion", "spindel", "venenatis", "vet-ion");

	/** One catalog entry ({@code A/activity-catalog.json}). */
	static final class Entry {
		final String id, name, group, status, adapter, environment, art, metric, secondary, skill, icon, area, boss;

		Entry(Object o) {
			id = MiniJson.str(o, "id");
			name = MiniJson.str(o, "name");
			group = MiniJson.str(o, "group");
			status = MiniJson.str(o, "status");
			adapter = blank(MiniJson.str(o, "adapter"));
			environment = MiniJson.str(o, "environment");
			art = blank(MiniJson.str(o, "art"));
			metric = blank(MiniJson.str(o, "metric"));
			secondary = blank(MiniJson.str(o, "secondary"));
			skill = blank(MiniJson.str(o, "skill"));
			icon = blank(MiniJson.str(o, "icon"));
			area = blank(MiniJson.str(o, "area"));
			boss = blank(MiniJson.str(o, "boss"));
		}

		private static String blank(String s) { return s == null || s.isEmpty() ? null : s; }
	}

	/** Frozen sample-session labels and portrait choice for the five fixture keys. */
	static final class Labels {
		final String kind, primary, rate, xp, art, itemIcon;

		Labels(String kind, String primary, String rate, String xp, String art, String itemIcon) {
			this.kind = kind;
			this.primary = primary;
			this.rate = rate;
			this.xp = xp;
			this.art = art;
			this.itemIcon = itemIcon;
		}
	}

	/** A resolved environment preset: the frozen palette and its scene, surface and prop rasters. */
	public static final class Environment {
		public final String name;
		public final Color ink, face, edge, light, shade, tint, metal, text, glint;
		public final BufferedImage scene, surface, propA, propB;

		Environment(String name, Map<String, String> vars, ActivityInputs in) {
			this.name = name;
			ink = in.color(vars, "--ink", new Color(0x151b1c));
			face = in.color(vars, "--face", new Color(0x30383a));
			edge = in.color(vars, "--edge", new Color(0x8b9a91));
			light = in.color(vars, "--light", new Color(0xc6e5e6));
			shade = in.color(vars, "--shade", new Color(0x22323e));
			tint = in.color(vars, "--tint", new Color(0x426f8c));
			metal = in.color(vars, "--metal", new Color(0xaac9d8));
			text = in.color(vars, "--text", new Color(0xc5dce7));
			glint = in.color(vars, "--glint", new Color(0xbdeeff));
			scene = in.image(vars, "--encounter-scene");
			surface = in.image(vars, "--surface");
			propA = in.image(vars, "--prop-a");
			propB = in.image(vars, "--prop-b");
		}
	}

	private static final Pattern STYLE = Pattern.compile("<style[^>]*>(.*?)</style>", Pattern.DOTALL);
	private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");
	private static final Pattern ROOT = Pattern.compile("^(?::is\\(#route,#activity\\)|#activity)(?:\\[data-environment=\"([a-z0-9-]+)\"\\])?$");
	private static final Pattern VAR = Pattern.compile("var\\((--[a-z0-9-]+)(?:,([^()]*(?:\\([^()]*\\))?))?\\)");
	private static final Pattern DATA_PNG = Pattern.compile("url\\(\"?data:image/(?:png|jpeg|webp);base64,([A-Za-z0-9+/=]+)\"?\\)");
	private static final Pattern ART_JSON = Pattern.compile("id=\"activity-art\"[^>]*>(.*?)</script>", Pattern.DOTALL);

	final Map<String, Entry> catalog = new LinkedHashMap<>();
	final Map<String, Labels> labels = new HashMap<>();
	final Map<String, BufferedImage> activityArt = new HashMap<>();
	private final Map<String, String> baseVars = new LinkedHashMap<>();
	private final Map<String, Map<String, String>> envVars = new HashMap<>();
	private final Map<String, Environment> environments = new ConcurrentHashMap<>();
	private final Map<String, BufferedImage> decoded = new ConcurrentHashMap<>();

	private ActivityInputs(LaneInputs lane, String html) throws IOException {
		for (Object o : (List<?>) lane.json("A/activity-catalog.json")) {
			Entry e = new Entry(o);
			catalog.put(e.id, e);
		}
		if (catalog.size() != 123) throw new IOException("Unexpected activity catalog size " + catalog.size());
		parseEnvironments(html);
		Matcher art = ART_JSON.matcher(html);
		if (!art.find()) throw new IOException("Frozen activity art missing");
		@SuppressWarnings("unchecked")
		Map<String, Object> artMap = (Map<String, Object>) MiniJson.parse(art.group(1));
		for (Map.Entry<String, Object> a : artMap.entrySet()) {
			BufferedImage img = dataImage(String.valueOf(a.getValue()));
			if (img != null) activityArt.put(a.getKey(), img);
		}
		parseLabels(html);
	}

	/** Loads off the EDT: the accepted Lane Inputs ZIP and the extracted frozen review ({@code Project-Raccoon-R02/}). */
	public static ActivityInputs load(LaneInputs lane, Path frozenReview) throws IOException {
		byte[] html = Files.readAllBytes(frozenReview.resolve("Project-Raccoon-R02.html"));
		if (!FROZEN_HTML_SHA256.equals(LaneInputs.sha256(html))) throw new IOException("Frozen R02 review SHA-256 mismatch");
		return new ActivityInputs(lane, new String(html, StandardCharsets.UTF_8));
	}

	Entry entry(String id) { return id == null ? null : catalog.get(id); }

	/** The preset for an environment name (unknown names fall back to {@code neutral-activity}). */
	public Environment environment(String name) {
		String key = name != null && envVars.containsKey(name) ? name : "neutral-activity";
		return environments.computeIfAbsent(key, k -> {
			Map<String, String> vars = new HashMap<>(baseVars);
			Map<String, String> env = envVars.get(k);
			if (env != null) vars.putAll(env);
			return new Environment(k, vars, this);
		});
	}

	int environmentCount() { return envVars.size(); }

	// ------------------------------------------------------------------ frozen CSS cascade

	private void parseEnvironments(String html) {
		StringBuilder css = new StringBuilder();
		Matcher st = STYLE.matcher(html);
		while (st.find()) css.append(st.group(1)).append('\n');
		Matcher r = RULE.matcher(css);
		while (r.find()) {
			for (String sel : splitTop(r.group(1).replaceAll("/\\*.*?\\*/", "").trim(), ',')) {
				Matcher root = ROOT.matcher(sel.trim());
				if (!root.matches()) continue;
				Map<String, String> target = root.group(1) == null ? baseVars : envVars.computeIfAbsent(root.group(1), k -> new LinkedHashMap<>());
				for (String decl : splitTop(r.group(2), ';')) {
					int c = decl.indexOf(':');
					if (c < 0) continue;
					String name = decl.substring(0, c).trim();
					if (name.startsWith("--")) target.put(name, decl.substring(c + 1).trim());
				}
			}
		}
		envVars.computeIfAbsent("neutral-activity", k -> new LinkedHashMap<>());
	}

	/** Splits on {@code sep} outside parentheses and quotes (data URIs contain ';' and ','). */
	static List<String> splitTop(String s, char sep) {
		List<String> out = new ArrayList<>();
		int depth = 0, start = 0;
		boolean quote = false;
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == '"') quote = !quote;
			else if (!quote && ch == '(') depth++;
			else if (!quote && ch == ')') depth--;
			else if (!quote && depth == 0 && ch == sep) { out.add(s.substring(start, i)); start = i + 1; }
		}
		out.add(s.substring(start));
		return out;
	}

	private String resolve(Map<String, String> vars, String value, int depth) {
		if (value == null || depth > 8) return null;
		Matcher m = VAR.matcher(value);
		if (!m.find()) return value.trim();
		String ref = vars.get(m.group(1));
		if (ref == null) ref = m.group(2);
		String inner = resolve(vars, ref, depth + 1);
		if (inner == null) return null;
		return resolve(vars, value.substring(0, m.start()) + inner + value.substring(m.end()), depth + 1);
	}

	Color color(Map<String, String> vars, String name, Color fallback) {
		String v = resolve(vars, vars.get(name), 0);
		if (v == null || !v.matches("#[0-9a-fA-F]{3,8}")) return fallback;
		String h = v.substring(1);
		if (h.length() == 3 || h.length() == 4) {
			StringBuilder b = new StringBuilder();
			for (char ch : h.toCharArray()) b.append(ch).append(ch);
			h = b.toString();
		}
		int rgb = Integer.parseInt(h.substring(0, 6), 16);
		int a = h.length() == 8 ? Integer.parseInt(h.substring(6), 16) : 255;
		return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
	}

	BufferedImage image(Map<String, String> vars, String name) {
		String v = resolve(vars, vars.get(name), 0);
		return v == null ? null : dataImage(v);
	}

	private BufferedImage dataImage(String v) {
		Matcher m = DATA_PNG.matcher(v.startsWith("url(") ? v : "url(" + v + ")");
		if (!m.find()) return null;
		String b64 = m.group(1);
		return decoded.computeIfAbsent(LaneInputs.sha256(b64.getBytes(StandardCharsets.US_ASCII)), k -> {
			try {
				BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
				return img == null ? new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB) : img;
			} catch (IOException | IllegalArgumentException e) {
				return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			}
		});
	}

	// ------------------------------------------------------------------ sample-session labels

	private void parseLabels(String html) throws IOException {
		int start = html.indexOf("const activityFixtures={");
		int end = html.indexOf("\n};", start);
		if (start < 0 || end < 0) throw new IOException("Frozen activity sample labels missing");
		String block = html.substring(start, end);
		Matcher line = Pattern.compile("\\n\\s*\"?([a-z-]+)\"?:\\{(.*)").matcher(block);
		while (line.find()) {
			String body = line.group(2);
			labels.put(line.group(1), new Labels(find(body, "kind:'([^']+)'"), find(body, "primary:\\['([^']+)'"),
				find(body, "rate:'([^']+)'"), find(body, "xp:\\['([^']+)'"), find(body, "portrait:activityArt\\.([a-z]+)"),
				find(body, "portrait:ofAssets\\['(i\\d+)\\.png'\\]")));
		}
		if (labels.size() != 5) throw new IOException("Expected 5 frozen activity samples, found " + labels.size());
	}

	private static String find(String s, String regex) {
		Matcher m = Pattern.compile(regex).matcher(s);
		return m.find() ? m.group(1) : null;
	}

	Map<String, Labels> labels() { return Collections.unmodifiableMap(labels); }
}
