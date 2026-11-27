package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One saved session record (for example {@code a1}: Vorkath, 12 trip kills). Trip kills are this record's own count
 * and are never the account-wide official KC. Loot is by exact item id.
 */
public final class ArchiveRecord {
	private final String id, activityKey, encounterKey;
	private final Instant started;
	private final Duration active;
	private final int units;
	private final String unitLabel;
	private final Evidence<Long> xp;
	private final Map<Integer, Long> loot;

	public ArchiveRecord(String id, String activityKey, String encounterKey, Instant started, Duration active, int units,
		String unitLabel, Evidence<Long> xp, Map<Integer, Long> loot) {
		this.id = Objects.requireNonNull(id);
		this.activityKey = Objects.requireNonNull(activityKey);
		this.encounterKey = encounterKey;
		this.started = Objects.requireNonNull(started);
		this.active = Objects.requireNonNull(active);
		if (units < 0) throw new IllegalArgumentException("units");
		this.units = units;
		this.unitLabel = Objects.requireNonNull(unitLabel);
		this.xp = Objects.requireNonNull(xp);
		this.loot = Collections.unmodifiableMap(new LinkedHashMap<>(loot));
	}

	public String id() { return id; }
	public String activityKey() { return activityKey; }
	/** The Chronicle encounter this record links to, or null for a skilling session. */
	public String encounterKey() { return encounterKey; }
	public Instant started() { return started; }
	public Duration active() { return active; }
	public int units() { return units; }
	/** The registry unit for this activity ("kills", "chests", "lobsters"). */
	public String unitLabel() { return unitLabel; }
	public Evidence<Long> xp() { return xp; }
	public Map<Integer, Long> loot() { return loot; }
}
