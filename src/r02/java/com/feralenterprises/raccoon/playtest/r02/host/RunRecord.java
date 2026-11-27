package com.feralenterprises.raccoon.playtest.r02.host;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Append-only playtest event log ({@code events.log}) in the isolated profile's run folder. It records host events
 * only (start, scope, scenario, phase, teardown, refusals). Nothing from the game client is ever passed in except the
 * GameState name; values are restricted to a safe character set so no free text can smuggle data into the record.
 * {@link #memory()} gives an in-memory record for tests.
 */
public final class RunRecord {
	private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9 _.,:;=+/()\\[\\]#<>'%-]*");
	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

	private final Path dir;
	private final List<String> lines = new ArrayList<>();

	private RunRecord(Path dir) { this.dir = dir; }

	/** A record in {@code dir} (created). */
	public static RunRecord in(Path dir) throws IOException {
		Files.createDirectories(dir);
		return new RunRecord(dir);
	}

	public static RunRecord memory() { return new RunRecord(null); }

	public Path dir() { return dir; }

	public synchronized void event(String name, String detail) {
		String d = detail == null ? "" : detail;
		if (!SAFE.matcher(name).matches() || !SAFE.matcher(d).matches()) { name = "refused-record"; d = "unsafe characters"; }
		String line = TS.format(Instant.now()) + " " + name + (d.isEmpty() ? "" : " " + d);
		lines.add(line);
		if (dir == null) return;
		try (Writer w = Files.newBufferedWriter(dir.resolve("events.log"), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			w.write(line);
			w.write('\n');
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Writes a whole file next to the log (for example the native name audit). Content is caller-produced. */
	public synchronized void file(String name, String content) throws IOException {
		if (!name.matches("[a-z0-9-]+\\.(txt|csv)")) throw new IllegalArgumentException(name);
		if (dir != null) Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
		event("file", name);
	}

	public synchronized List<String> lines() { return Collections.unmodifiableList(new ArrayList<>(lines)); }

	public synchronized boolean has(String name) {
		for (String l : lines) if (l.split(" ", 3).length > 1 && l.split(" ", 3)[1].equals(name)) return true;
		return false;
	}
}
