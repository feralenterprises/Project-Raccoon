package com.feralenterprises.raccoon.playtest.r02.assembly;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The private Lane Inputs ZIP, read the same way Lanes A and C read it: the archive must hash exactly, and a file is
 * returned only when it matches the archive's own {@code SHA256SUMS}. Shared so the asset provider can use its verified
 * item sprites ({@code shared/osrs-items/<id>.png}, Lane C finding). Nothing from it is committed.
 */
public final class LaneInputsArchive {
	/** SHA-256 of the accepted Lane Inputs ZIP (Lanes A and C pin the same value). */
	public static final String ACCEPTED_SHA256 = "1f6a821d37705b7c7acdca9d598c2c5e7f74f7462536c9bf0f9039086545cece";
	private static final String ROOT = "R02-Lane-Inputs/";

	private final Map<String, byte[]> files;

	private LaneInputsArchive(Map<String, byte[]> files) { this.files = files; }

	public static LaneInputsArchive open(Path zip) throws IOException { return open(zip, ACCEPTED_SHA256); }

	/** As {@link #open(Path)} against an explicit accepted digest (tests use stand-in archives). */
	public static LaneInputsArchive open(Path zip, String acceptedSha256) throws IOException {
		String d = FrozenPackageAssets.sha256(Files.readAllBytes(zip));
		if (!acceptedSha256.equals(d)) throw new IOException("lane inputs ZIP is not the accepted one (" + d.substring(0, 12) + ")");
		Map<String, byte[]> raw = new HashMap<>();
		try (ZipFile z = new ZipFile(zip.toFile())) {
			for (ZipEntry e : Collections.list(z.entries())) {
				if (e.isDirectory() || !e.getName().startsWith(ROOT) || e.getName().contains("..")) continue;
				try (InputStream in = z.getInputStream(e)) { raw.put(e.getName().substring(ROOT.length()), in.readAllBytes()); }
			}
		}
		byte[] sums = raw.get("SHA256SUMS");
		if (sums == null) throw new IOException("lane inputs ZIP has no SHA256SUMS");
		Map<String, byte[]> verified = new HashMap<>();
		for (String line : new String(sums, StandardCharsets.UTF_8).split("\n")) {
			String[] p = line.trim().split("\\s+", 2);
			if (p.length != 2) continue;
			String rel = p[1].startsWith("*") ? p[1].substring(1) : p[1];
			byte[] b = raw.get(rel);
			if (b != null && p[0].equals(FrozenPackageAssets.sha256(b))) verified.put(rel, b);
		}
		return new LaneInputsArchive(Collections.unmodifiableMap(verified));
	}

	/** Verified bytes, or null when the file is absent or does not match its sum. */
	public byte[] bytes(String rel) { return files.get(rel); }

	public int verifiedCount() { return files.size(); }
}
