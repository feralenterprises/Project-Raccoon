package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One saved session record (for example {@code a1}: Vorkath, 12 trip kills). The record's own count is never the
 * account-wide official KC. Every metric is {@link Evidence}: an unobserved metric (a partial record, a progress
 * entry) is UNKNOWN or UNAVAILABLE, never zero. Loot and supplies are by exact item id; an unobserved ledger is
 * UNAVAILABLE, an observed empty one is VERIFIED_ABSENT.
 */
public final class ArchiveRecord {
	/** Frozen record kinds. */
	public enum Kind { COMBAT, SKILLING, PROGRESS }

	private final String id, activityKey, encounterKey, title, unitLabel, notes;
	private final Kind kind;
	private final Instant started;
	private final Evidence<Duration> active, elapsed;
	private final Evidence<Integer> count, deaths;
	private final Evidence<Long> xp;
	private final Evidence<Map<Integer, Long>> loot, supplies;

	/** Compact form for fully observed records (used by {@link TestFixtures}). */
	public ArchiveRecord(String id, String activityKey, String encounterKey, Instant started, Duration active, int units,
		String unitLabel, Evidence<Long> xp, Map<Integer, Long> loot) {
		this(id, activityKey, encounterKey, activityKey, encounterKey != null ? Kind.COMBAT : Kind.SKILLING, started,
			Evidence.verified(Objects.requireNonNull(active), started, "record " + id), Evidence.unknown(),
			Evidence.count(units, started, "record " + id), Evidence.unknown(), unitLabel, xp,
			Evidence.verified(Collections.unmodifiableMap(new LinkedHashMap<>(loot)), started, "record " + id),
			Evidence.unavailable(), null);
	}

	public ArchiveRecord(String id, String activityKey, String encounterKey, String title, Kind kind, Instant started,
		Evidence<Duration> active, Evidence<Duration> elapsed, Evidence<Integer> count, Evidence<Integer> deaths, String unitLabel,
		Evidence<Long> xp, Evidence<Map<Integer, Long>> loot, Evidence<Map<Integer, Long>> supplies, String notes) {
		this.id = Objects.requireNonNull(id);
		this.activityKey = activityKey;
		this.encounterKey = encounterKey;
		this.title = Objects.requireNonNull(title);
		this.kind = Objects.requireNonNull(kind);
		this.started = Objects.requireNonNull(started);
		this.active = Objects.requireNonNull(active);
		this.elapsed = Objects.requireNonNull(elapsed);
		this.count = Objects.requireNonNull(count);
		this.deaths = Objects.requireNonNull(deaths);
		this.unitLabel = Objects.requireNonNull(unitLabel);
		this.xp = Objects.requireNonNull(xp);
		this.loot = immutable(loot);
		this.supplies = immutable(supplies);
		this.notes = notes;
	}

	private static Evidence<Map<Integer, Long>> immutable(Evidence<Map<Integer, Long>> e) {
		Objects.requireNonNull(e);
		if (!e.hasValue()) return e;
		Map<Integer, Long> copy = Collections.unmodifiableMap(new LinkedHashMap<>(e.value()));
		return e.status() == EvidenceStatus.STALE ? Evidence.stale(copy, e.observedAt(), e.source()) : Evidence.verified(copy, e.observedAt(), e.source());
	}

	public String id() { return id; }
	/** Current Activity key this record replays, or null. */
	public String activityKey() { return activityKey; }
	/** The Chronicle encounter this record links to, or null for skilling and progress entries. */
	public String encounterKey() { return encounterKey; }
	public String title() { return title; }
	public Kind kind() { return kind; }
	public Instant started() { return started; }
	public Evidence<Duration> activeTime() { return active; }
	public Evidence<Duration> elapsedTime() { return elapsed; }
	/** The record's own count (kills, chests, fish), in {@link #unitLabel()} units. */
	public Evidence<Integer> count() { return count; }
	public Evidence<Integer> deaths() { return deaths; }
	/** The registry unit ("Kills", "Chests", "Fish caught"). */
	public String unitLabel() { return unitLabel; }
	public Evidence<Long> xp() { return xp; }
	public Evidence<Map<Integer, Long>> lootLedger() { return loot; }
	public Evidence<Map<Integer, Long>> suppliesLedger() { return supplies; }
	/** Frozen record note, or null. */
	public String notes() { return notes; }

	/**
	 * Active time, or null when it was not observed (never an invented zero). Legacy readers already treat null as
	 * unknown; prefer {@link #activeTime()}.
	 */
	public Duration active() { return active.hasValue() ? active.value() : null; }
	/** The record's count; throws when it was not observed (use {@link #count()}). */
	public int units() { return count.value(); }
	/** Observed loot, or an empty map when the ledger was not observed (use {@link #lootLedger()} to tell them apart). */
	public Map<Integer, Long> loot() { return loot.hasValue() ? loot.value() : Collections.emptyMap(); }
}
