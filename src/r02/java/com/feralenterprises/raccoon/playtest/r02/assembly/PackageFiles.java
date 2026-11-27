package com.feralenterprises.raccoon.playtest.r02.assembly;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Reads files of the extracted frozen package only after checking them against the package's SHA256SUMS.txt. */
final class PackageFiles {
	private final Path root;
	private final Map<String, String> sums;

	PackageFiles(Path root) throws IOException {
		this.root = root;
		Map<String, String> m = new HashMap<>();
		for (String line : Files.readAllLines(root.resolve("SHA256SUMS.txt"), StandardCharsets.UTF_8)) {
			String[] p = line.trim().split("\\s+", 2);
			if (p.length == 2) m.put(p[1], p[0]);
		}
		if (m.isEmpty()) throw new IOException("SHA256SUMS.txt is empty");
		sums = Collections.unmodifiableMap(m);
	}

	/** The verified bytes of a package file; throws when it is unlisted, missing or altered (fail closed). */
	byte[] read(String rel) throws IOException {
		String want = sums.get(rel);
		if (want == null) throw new IOException(rel + " is not listed in SHA256SUMS.txt");
		byte[] b = Files.readAllBytes(root.resolve(rel));
		if (!want.equals(FrozenPackageAssets.sha256(b))) throw new IOException(rel + ": SHA-256 mismatch");
		return b;
	}

	String text(String rel) throws IOException { return new String(read(rel), StandardCharsets.UTF_8); }
}
