package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.util.Objects;

/**
 * A skill's observations. Level and exact XP are independent: a verified level is kept and shown even while exact XP
 * is unavailable (Solis, 27 Nov), and neither is derived from the other.
 */
public final class SkillObservation {
	private final Evidence<Integer> level;
	private final Evidence<Long> exactXp;

	public SkillObservation(Evidence<Integer> level, Evidence<Long> exactXp) {
		this.level = Objects.requireNonNull(level);
		this.exactXp = Objects.requireNonNull(exactXp);
		if (level.hasValue() && (level.value() < 1 || level.value() > 99)) throw new IllegalArgumentException("level out of range");
	}

	public static SkillObservation unobserved() { return new SkillObservation(Evidence.unknown(), Evidence.unavailable()); }

	public Evidence<Integer> level() { return level; }
	public Evidence<Long> exactXp() { return exactXp; }
}
