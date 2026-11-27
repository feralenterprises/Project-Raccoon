package com.feralenterprises.raccoon.ui.test;

import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.model.EquipmentSlot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies NativeSprite ids and EquipmentSlot indices against pinned RuneLite sources by parsing them (the client
 * jar cannot be downloaded in the build environment). Argument: root of a runelite checkout at the pinned tag.
 */
public final class SpriteIdCheck {
	private static final Pattern CLASS = Pattern.compile("public static final class (\\w+)");
	private static final Pattern CONST = Pattern.compile("public static final int (\\w+) = (\\w+);");
	private static final Pattern ENUM = Pattern.compile("^\\s*(\\w+)\\((\\d+)\\)[,;]", Pattern.MULTILINE);

	public static void main(String[] args) throws Exception {
		Path root = Paths.get(args[0]);
		Path gameval = root.resolve("runelite-api/src/main/java/net/runelite/api/gameval/SpriteID.java");
		Path legacy = root.resolve("runelite-api/src/main/java/net/runelite/api/SpriteID.java");
		Path slots = root.resolve("runelite-api/src/main/java/net/runelite/api/EquipmentInventorySlot.java");
		System.out.println("Source identity:");
		if (!SourceIdentity.verifyRunelite(root, SourceIdentity.pins())) {
			System.out.println("SpriteIdCheck: REFUSED - sources are not the pinned RuneLite " + SourceIdentity.pins().getProperty("runelite.tag"));
			System.exit(1);
		}
		Map<String, Integer> g = parseGameval(read(gameval));
		Map<String, Integer> l = new HashMap<>();
		Matcher m = CONST.matcher(read(legacy));
		while (m.find()) l.put(m.group(1), Integer.parseInt(m.group(2)));
		Map<String, Integer> s = new HashMap<>();
		Matcher e = ENUM.matcher(read(slots));
		while (e.find()) s.put(e.group(1), Integer.parseInt(e.group(2)));

		System.out.println("Pinned sources:");
		for (Path p : new Path[]{gameval, legacy, slots}) System.out.println("  sha256 " + sha(p) + "  " + root.relativize(p));
		int bad = 0, verified = 0, unresolved = 0;
		System.out.println("NativeSprite, id, gameval, legacy, result");
		for (NativeSprite n : NativeSprite.values()) {
			if (!n.resolved()) { unresolved++; System.out.println(n + ", -1, -, -, UNRESOLVED"); continue; }
			Integer gv = g.get(n.gamevalConstant()), lv = l.get(n.legacyConstant());
			boolean ok = gv != null && gv == n.spriteId() && lv != null && lv == n.spriteId();
			if (ok) verified++; else bad++;
			System.out.println(n + ", " + n.spriteId() + ", " + n.gamevalConstant() + "=" + gv + ", " + n.legacyConstant() + "=" + lv
				+ ", " + (ok ? "SOURCE_VERIFIED" : "MISMATCH"));
		}
		String[] rlName = {"HEAD", "CAPE", "AMULET", "AMMO", "WEAPON", "BODY", "SHIELD", "LEGS", "GLOVES", "BOOTS", "RING"};
		System.out.println("EquipmentSlot, runeliteIndex, EquipmentInventorySlot, result");
		int i = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			Integer v = s.get(rlName[i++]);
			boolean ok = v != null && v == slot.runeliteIndex();
			if (!ok) bad++;
			System.out.println(slot + ", " + slot.runeliteIndex() + ", " + rlName[i - 1] + "=" + v + ", " + (ok ? "SOURCE_VERIFIED" : "MISMATCH"));
		}
		System.out.println("SpriteIdCheck: " + verified + " sprites verified, " + unresolved + " unresolved, " + bad + " mismatches");
		System.exit(bad == 0 ? 0 : 1);
	}

	/** Resolves "Group.NAME" including aliases such as {@code HEAD = _0} inside nested classes. */
	static Map<String, Integer> parseGameval(String src) {
		Map<String, Integer> out = new HashMap<>();
		String cls = "";
		for (String line : src.split("\n")) {
			Matcher c = CLASS.matcher(line);
			if (c.find()) { cls = c.group(1); continue; }
			Matcher k = CONST.matcher(line);
			if (!k.find()) continue;
			String v = k.group(2);
			Integer n = v.matches("\\d+") ? Integer.valueOf(v) : out.get(cls + "." + v);
			if (n != null) out.put(cls + "." + k.group(1), n);
		}
		return out;
	}

	static String read(Path p) throws Exception { return new String(Files.readAllBytes(p), StandardCharsets.UTF_8); }

	static String sha(Path p) throws Exception {
		byte[] d = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));
		StringBuilder sb = new StringBuilder();
		for (byte b : d) sb.append(String.format("%02x", b));
		return sb.toString();
	}
}
