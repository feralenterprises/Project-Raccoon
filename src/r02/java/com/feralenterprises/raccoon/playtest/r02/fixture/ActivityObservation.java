package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.time.Instant;
import java.util.Objects;

/**
 * The current-activity observation. A replay always names the archive record it replays; an unsupported or
 * unwitnessed activity has no count, rate or location (D-07, D-08). Elapsed and active time are derived from the
 * shared clock, never stored as numbers.
 */
public final class ActivityObservation {
	/** Session modes from the frozen Current Activity states. */
	public enum Mode { NONE, REPLAY, UNSUPPORTED, IDLE, BANKING, TRAVEL, PAUSED, SIGNED_OUT }

	private final Mode mode;
	private final String activityKey;
	private final String replayRecordId;
	private final Instant sessionStart;
	private final Instant activeSince;
	private final long activeMillisBefore;
	private final Evidence<String> location;

	public ActivityObservation(Mode mode, String activityKey, String replayRecordId, Instant sessionStart, Instant activeSince,
		long activeMillisBefore, Evidence<String> location) {
		this.mode = Objects.requireNonNull(mode);
		if (mode == Mode.REPLAY && replayRecordId == null) throw new IllegalArgumentException("a replay names its record");
		if (mode != Mode.REPLAY && replayRecordId != null) throw new IllegalArgumentException("only a replay names a record");
		this.activityKey = activityKey;
		this.replayRecordId = replayRecordId;
		this.sessionStart = sessionStart;
		this.activeSince = activeSince;
		this.activeMillisBefore = activeMillisBefore;
		this.location = Objects.requireNonNull(location);
	}

	public static ActivityObservation none() { return new ActivityObservation(Mode.NONE, null, null, null, null, 0, Evidence.unavailable()); }

	public Mode mode() { return mode; }
	public String activityKey() { return activityKey; }
	public String replayRecordId() { return replayRecordId; }
	public Instant sessionStart() { return sessionStart; }
	/** When active time last resumed; null while not active (idle, banking, travel, paused). */
	public Instant activeSince() { return activeSince; }
	public long activeMillisBefore() { return activeMillisBefore; }
	public Evidence<String> location() { return location; }

	public long elapsedMillis(Instant now) { return sessionStart == null ? 0 : Math.max(0, now.toEpochMilli() - sessionStart.toEpochMilli()); }
	public long activeMillis(Instant now) {
		return activeMillisBefore + (activeSince == null ? 0 : Math.max(0, now.toEpochMilli() - activeSince.toEpochMilli()));
	}
}
