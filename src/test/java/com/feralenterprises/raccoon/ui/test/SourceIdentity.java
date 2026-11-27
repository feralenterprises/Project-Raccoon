package com.feralenterprises.raccoon.ui.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Properties;

/**
 * Recomputes git object ids (blob and flat tree) from files on disk, so a check can prove which exact source it
 * consumed, independent of how the files were obtained. CRLF is normalised to LF first, matching what git stores.
 * Usage: SourceIdentity foundation-tree DIR PIN_KEY | SourceIdentity runelite ROOT (pins read from ui-pins.properties).
 */
public final class SourceIdentity {
	public static void main(String[] args) throws Exception {
		Properties pins = pins();
		boolean ok;
		if ("foundation-tree".equals(args[0])) {
			Path dir = Paths.get(args[1]).resolve("com/feralenterprises/raccoon/core/account");
			String expected = pins.getProperty("foundation.coreAccountTree");
			String actual = treeId(dir);
			ok = expected.equals(actual);
			System.out.println("foundation core.account tree " + actual + " (pinned " + expected + ", foundation.commit "
				+ pins.getProperty("foundation.commit") + "): " + (ok ? "IDENTITY VERIFIED" : "MISMATCH"));
		} else {
			ok = verifyRunelite(Paths.get(args[1]), pins);
		}
		System.exit(ok ? 0 : 1);
	}

	public static Properties pins() throws IOException {
		Properties p = new Properties();
		try (FileInputStream in = new FileInputStream(new File("ui-pins.properties"))) { p.load(in); }
		return p;
	}

	/** Verifies the three RuneLite files the sprite check reads, and the checkout HEAD when it is a git work tree. */
	public static boolean verifyRunelite(Path root, Properties pins) throws Exception {
		String[][] files = {
			{"runelite.blob.gamevalSpriteID", "runelite-api/src/main/java/net/runelite/api/gameval/SpriteID.java"},
			{"runelite.blob.legacySpriteID", "runelite-api/src/main/java/net/runelite/api/SpriteID.java"},
			{"runelite.blob.EquipmentInventorySlot", "runelite-api/src/main/java/net/runelite/api/EquipmentInventorySlot.java"}};
		boolean ok = true;
		for (String[] f : files) {
			String actual = blobId(Files.readAllBytes(root.resolve(f[1])));
			String expected = pins.getProperty(f[0]);
			boolean match = expected.equals(actual);
			ok &= match;
			System.out.println("  blob " + actual + " " + f[1] + " (pinned " + expected + "): " + (match ? "IDENTITY VERIFIED" : "MISMATCH"));
		}
		String head = gitHead(root);
		String pinned = pins.getProperty("runelite.commit");
		if (head != null) {
			boolean match = pinned.equals(head);
			ok &= match;
			System.out.println("  checkout HEAD " + head + " (pinned " + pinned + " = " + pins.getProperty("runelite.tag") + "): "
				+ (match ? "IDENTITY VERIFIED" : "MISMATCH"));
		} else {
			System.out.println("  checkout is not a git work tree; file blob ids above are the identity");
		}
		return ok;
	}

	static String gitHead(Path root) {
		if (!Files.exists(root.resolve(".git"))) return null;
		try {
			Process p = new ProcessBuilder("git", "-C", root.toString(), "rev-parse", "HEAD").redirectErrorStream(true).start();
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			return p.waitFor() == 0 ? out : "unreadable: " + out;
		} catch (Exception e) {
			return "unreadable: " + e.getMessage();
		}
	}

	static byte[] lf(byte[] in) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
		for (int i = 0; i < in.length; i++) if (!(in[i] == '\r' && i + 1 < in.length && in[i + 1] == '\n')) out.write(in[i]);
		return out.toByteArray();
	}

	public static String blobId(byte[] content) throws Exception { return hex(rawBlobId(lf(content))); }

	static byte[] rawBlobId(byte[] c) throws Exception {
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		sha1.update(("blob " + c.length + "\0").getBytes(StandardCharsets.US_ASCII));
		sha1.update(c);
		return sha1.digest();
	}

	/** Git tree id of a directory containing only regular (mode 100644) files, as git would record it. */
	public static String treeId(Path dir) throws Exception {
		File[] files = dir.toFile().listFiles();
		if (files == null) throw new IOException("missing " + dir);
		Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
		ByteArrayOutputStream entries = new ByteArrayOutputStream();
		for (File f : files) {
			if (!f.isFile()) throw new IOException("unexpected non-file " + f);
			entries.write(("100644 " + f.getName() + "\0").getBytes(StandardCharsets.UTF_8));
			entries.write(rawBlobId(lf(Files.readAllBytes(f.toPath()))));
		}
		byte[] body = entries.toByteArray();
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		sha1.update(("tree " + body.length + "\0").getBytes(StandardCharsets.US_ASCII));
		sha1.update(body);
		return hex(sha1.digest());
	}

	static String hex(byte[] d) {
		StringBuilder sb = new StringBuilder();
		for (byte b : d) sb.append(String.format("%02x", b));
		return sb.toString();
	}
}
