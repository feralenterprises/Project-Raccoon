package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One registry encounter, keyed by the stable payload id used in route requests ({@code vorkath}). Official kill
 * counts are keyed separately: most encounters have one KC key; Dagannoth Kings has three (Rex, Prime, Supreme), each
 * its own count. Difficulty is null when unranked (shown as unknown, never guessed).
 */
public final class EncounterDef {
	private final String key, name, category, difficulty;
	private final List<String> kcKeys;

	public EncounterDef(String key, String name, String category, String difficulty, String kcKey) {
		this(key, name, category, difficulty, kcKey == null ? Collections.<String>emptyList() : Collections.singletonList(kcKey));
	}

	public EncounterDef(String key, String name, String category, String difficulty, List<String> kcKeys) {
		this.key = Objects.requireNonNull(key);
		this.name = Objects.requireNonNull(name);
		this.category = Objects.requireNonNull(category);
		this.difficulty = difficulty;
		this.kcKeys = Collections.unmodifiableList(Arrays.asList(kcKeys.toArray(new String[0])));
	}

	public String key() { return key; }
	public String name() { return name; }
	public String category() { return category; }
	public String difficulty() { return difficulty; }
	/** The single official KC key, or null when there is none or more than one (see {@link #kcKeys()}). */
	public String kcKey() { return kcKeys.size() == 1 ? kcKeys.get(0) : null; }
	/** Every official KC key for this encounter, in display order. */
	public List<String> kcKeys() { return kcKeys; }
}
