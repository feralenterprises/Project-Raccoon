package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.util.Objects;

/**
 * One registry encounter, keyed by the stable payload id used in route requests ({@code vorkath},
 * {@code dagannoth-rex}). {@code kcKey} names its official kill count; Rex, Prime and Supreme each have their own.
 * Difficulty is null when unranked (shown as unknown, never guessed).
 */
public final class EncounterDef {
	private final String key, name, category, difficulty, kcKey;

	public EncounterDef(String key, String name, String category, String difficulty, String kcKey) {
		this.key = Objects.requireNonNull(key);
		this.name = Objects.requireNonNull(name);
		this.category = Objects.requireNonNull(category);
		this.difficulty = difficulty;
		this.kcKey = kcKey;
	}

	public String key() { return key; }
	public String name() { return name; }
	public String category() { return category; }
	public String difficulty() { return difficulty; }
	/** The official KC key, or null when the encounter has no official kill count. */
	public String kcKey() { return kcKey; }
}
