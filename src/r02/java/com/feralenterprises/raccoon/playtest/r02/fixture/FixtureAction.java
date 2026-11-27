package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.util.Objects;

/**
 * Typed fixture-only actions for controls the frozen UI has (simulator account/state switches, capture refresh,
 * dismissals). They change fixture state only; there is no game, account or network effect.
 */
public final class FixtureAction {
	public enum Kind { SWITCH_SCENARIO, REFRESH_CAPTURE, DISMISS_OPPORTUNITY, RESTORE_OPPORTUNITIES, RESET_OBSERVATIONS }

	private final Kind kind;
	private final String target;

	private FixtureAction(Kind kind, String target) {
		this.kind = Objects.requireNonNull(kind);
		this.target = target;
	}

	public static FixtureAction of(Kind kind) { return new FixtureAction(kind, null); }
	public static FixtureAction of(Kind kind, String target) { return new FixtureAction(kind, Objects.requireNonNull(target)); }

	public Kind kind() { return kind; }
	/** The stable id the action applies to (scenario id, capture source id, opportunity id), or null. */
	public String target() { return target; }

	@Override
	public String toString() { return target == null ? kind.name() : kind + ":" + target; }
}
