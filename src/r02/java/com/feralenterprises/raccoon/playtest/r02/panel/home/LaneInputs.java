package com.feralenterprises.raccoon.playtest.r02.panel.home;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Lane A's read-only view of the accepted private {@code Project-Raccoon-R02-Lane-Inputs.zip} (Solis review
 * 2026-11-27: 5,028,223 B, SHA-256 {@value #ARCHIVE_SHA256}). Fail-closed: the archive must hash exactly, and every
 * file is returned only if it matches the archive's own {@code SHA256SUMS}. Nothing from the archive is committed or
 * fetched from the network; hosts pass its location. Load off the EDT.
 */
public final class LaneInputs {
	public static final String ARCHIVE_SHA256 = "1f6a821d37705b7c7acdca9d598c2c5e7f74f7462536c9bf0f9039086545cece";
	private static final String ROOT = "R02-Lane-Inputs/";

	private final Map<String, byte[]> files;

	private LaneInputs(Map<String, byte[]> files) { this.files = files; }

	public static LaneInputs open(Path archive) throws IOException {
		byte[] zip = Files.readAllBytes(archive);
		if (!ARCHIVE_SHA256.equals(sha256(zip))) throw new IOException("Lane inputs archive SHA-256 mismatch: " + archive.getFileName());
		Map<String, byte[]> raw = new HashMap<>();
		try (ZipFile z = new ZipFile(archive.toFile())) {
			for (ZipEntry e : Collections.list(z.entries())) {
				if (e.isDirectory() || !e.getName().startsWith(ROOT)) continue;
				try (InputStream in = z.getInputStream(e)) { raw.put(e.getName().substring(ROOT.length()), in.readAllBytes()); }
			}
		}
		byte[] sums = raw.get("SHA256SUMS");
		if (sums == null) throw new IOException("Lane inputs archive has no SHA256SUMS");
		Map<String, byte[]> verified = new HashMap<>();
		for (String line : new String(sums, StandardCharsets.UTF_8).split("\n")) {
			String[] p = line.trim().split("\\s+", 2);
			if (p.length != 2) continue;
			String rel = p[1].startsWith("*") ? p[1].substring(1) : p[1];
			byte[] b = raw.get(rel);
			if (b != null && p[0].equals(sha256(b))) verified.put(rel, b);
		}
		return new LaneInputs(Collections.unmodifiableMap(verified));
	}

	/** Verified bytes, or an exception naming the missing or mismatched file. */
	public byte[] bytes(String rel) throws IOException {
		byte[] b = files.get(rel);
		if (b == null) throw new IOException("Lane input missing or unverified: " + rel);
		return b;
	}

	public String text(String rel) throws IOException { return new String(bytes(rel), StandardCharsets.UTF_8); }

	public Object json(String rel) throws IOException { return MiniJson.parse(text(rel)); }

	/** A frozen R02 raster by its SHA-256 ({@code shared/frozen-art/<sha>.png}); null when absent, so callers fall back. */
	public BufferedImage frozenArt(String sha256) {
		byte[] b = files.get("shared/frozen-art/" + sha256 + ".png");
		return b == null ? null : decode(b);
	}

	public boolean has(String rel) { return files.containsKey(rel); }

	public int verifiedCount() { return files.size(); }

	static BufferedImage decode(byte[] b) {
		try { return ImageIO.read(new ByteArrayInputStream(b)); } catch (IOException e) { return null; }
	}

	public static String sha256(byte[] b) {
		try {
			StringBuilder s = new StringBuilder(64);
			for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) s.append(String.format("%02x", x));
			return s.toString();
		} catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}
}
