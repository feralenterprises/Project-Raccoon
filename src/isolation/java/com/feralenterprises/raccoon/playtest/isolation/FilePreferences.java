package com.feralenterprises.raccoon.playtest.isolation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/** One preference node = one folder; its values in {@code prefs.properties}; child folder names are encoded node names. */
final class FilePreferences extends AbstractPreferences {
	private static final String FILE = "prefs.properties";
	private final Path dir;
	private final Properties values = new Properties();

	FilePreferences(AbstractPreferences parent, String name, Path dir) {
		super(parent, name);
		this.dir = dir;
		Path f = dir.resolve(FILE);
		if (Files.isRegularFile(f)) {
			try (InputStream in = Files.newInputStream(f)) { values.load(in); } catch (IOException ignored) {
				// unreadable values start empty; the next flush rewrites them inside the profile
			}
		}
	}

	/** Folder-safe, reversible node name: letters, digits, '.', '-' kept; everything else as _XXXX. */
	static String encode(String name) {
		StringBuilder b = new StringBuilder("n");
		for (char c : name.toCharArray()) {
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '-') b.append(c);
			else b.append('_').append(String.format("%04x", (int) c));
		}
		return b.toString();
	}

	static String decode(String folder) {
		StringBuilder b = new StringBuilder();
		for (int i = 1; i < folder.length(); i++) {
			char c = folder.charAt(i);
			if (c == '_' && i + 5 <= folder.length()) { b.append((char) Integer.parseInt(folder.substring(i + 1, i + 5), 16)); i += 4; }
			else b.append(c);
		}
		return b.toString();
	}

	@Override protected void putSpi(String key, String value) { values.setProperty(key, value); }
	@Override protected String getSpi(String key) { return values.getProperty(key); }
	@Override protected void removeSpi(String key) { values.remove(key); }
	@Override protected String[] keysSpi() { return values.stringPropertyNames().toArray(new String[0]); }

	@Override protected String[] childrenNamesSpi() throws BackingStoreException {
		List<String> out = new ArrayList<>();
		if (!Files.isDirectory(dir)) return new String[0];
		try (DirectoryStream<Path> s = Files.newDirectoryStream(dir)) {
			for (Path p : s) if (Files.isDirectory(p) && p.getFileName().toString().startsWith("n")) out.add(decode(p.getFileName().toString()));
		} catch (IOException e) {
			throw new BackingStoreException(e);
		}
		return out.toArray(new String[0]);
	}

	@Override protected AbstractPreferences childSpi(String name) { return new FilePreferences(this, name, dir.resolve(encode(name))); }

	@Override protected void removeNodeSpi() throws BackingStoreException {
		try {
			Files.deleteIfExists(dir.resolve(FILE));
			if (Files.isDirectory(dir)) try (DirectoryStream<Path> s = Files.newDirectoryStream(dir)) { if (!s.iterator().hasNext()) Files.delete(dir); }
		} catch (IOException e) {
			throw new BackingStoreException(e);
		}
	}

	@Override protected void syncSpi() throws BackingStoreException { flushSpi(); }

	@Override protected void flushSpi() throws BackingStoreException {
		if (isRemoved()) return;
		try {
			Files.createDirectories(dir);
			try (OutputStream out = Files.newOutputStream(dir.resolve(FILE))) {
				values.store(out, null);
			}
		} catch (IOException e) {
			throw new BackingStoreException(e);
		}
	}
}
