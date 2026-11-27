package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The frame's one immutable account observation set. Every route reads only this (plus the scope-independent
 * {@link Registry}); a new revision is a new snapshot. {@code generation} is the binding generation at publication;
 * {@code revision} increases with every publication in a scope. Anything not supplied reads as UNKNOWN or UNAVAILABLE,
 * never as zero or empty.
 */
public final class FixtureSnapshot {
	private final AccountScope scope;
	private final int generation;
	private final long revision;
	private final Map<Container, ContainerObservation> containers;
	private final Map<String, Evidence<QuestState>> quests;
	private final Map<String, Evidence<QuestState>> diaries;
	private final Map<String, Evidence<Integer>> officialKc;
	private final Map<String, SkillObservation> skills;
	private final Map<String, Evidence<CaptureState>> captures;
	private final Map<String, Evidence<Ownership>> collection;
	private final List<ArchiveRecord> archive;
	private final ActivityObservation activity;
	private final Evidence<Boolean> archiveAvailable;
	private final Map<String, Evidence<Integer>> goalProgress;
	private final Evidence<Coverage> questCoverage;
	private final Evidence<Coverage> diaryCoverage;

	private FixtureSnapshot(Builder b) {
		scope = Objects.requireNonNull(b.scope, "scope");
		generation = b.generation;
		revision = b.revision;
		Map<Container, ContainerObservation> c = new EnumMap<>(Container.class);
		for (Container k : Container.values()) c.put(k, b.containers.getOrDefault(k, ContainerObservation.unavailable(k)));
		containers = Collections.unmodifiableMap(c);
		quests = Collections.unmodifiableMap(new LinkedHashMap<>(b.quests));
		diaries = Collections.unmodifiableMap(new LinkedHashMap<>(b.diaries));
		officialKc = Collections.unmodifiableMap(new LinkedHashMap<>(b.officialKc));
		skills = Collections.unmodifiableMap(new LinkedHashMap<>(b.skills));
		captures = Collections.unmodifiableMap(new LinkedHashMap<>(b.captures));
		collection = Collections.unmodifiableMap(new LinkedHashMap<>(b.collection));
		archive = Collections.unmodifiableList(new ArrayList<>(b.archive));
		activity = b.activity;
		archiveAvailable = b.archiveAvailable;
		goalProgress = Collections.unmodifiableMap(new LinkedHashMap<>(b.goalProgress));
		questCoverage = b.questCoverage;
		diaryCoverage = b.diaryCoverage;
		if (!scope.signedIn() && (activity.mode() != ActivityObservation.Mode.SIGNED_OUT && activity.mode() != ActivityObservation.Mode.NONE))
			throw new IllegalArgumentException("a signed-out snapshot has no current activity");
	}

	public static Builder builder(AccountScope scope) { return new Builder(scope); }

	public AccountScope scope() { return scope; }
	public int generation() { return generation; }
	public long revision() { return revision; }
	public ContainerObservation container(Container c) { return containers.get(c); }
	public Evidence<QuestState> quest(String name) { return quests.getOrDefault(name, Evidence.unknown()); }
	/** Diary tier progress keyed "Region Tier" ("Ardougne Easy"). */
	public Evidence<QuestState> diary(String regionTier) { return diaries.getOrDefault(regionTier, Evidence.unknown()); }
	/** Official KC by registry kcKey; UNKNOWN when not observed, VERIFIED_ZERO when observed as 0. */
	public Evidence<Integer> officialKc(String kcKey) { return officialKc.getOrDefault(kcKey, Evidence.unknown()); }
	public SkillObservation skill(String skill) { return skills.getOrDefault(skill, SkillObservation.unobserved()); }
	/**
	 * Capture evidence by source. {@code "bank"} is derived from the BANK container so the two can never disagree (Lane C):
	 * CURRENT is VERIFIED, HISTORICAL is a stale VERIFIED, INCOMPLETE is PARTIAL, UNAVAILABLE is unavailable. A
	 * builder value for "bank" is ignored.
	 */
	public Evidence<CaptureState> capture(String sourceId) {
		if (BANK_SOURCE.equals(sourceId)) return bankCapture(containers.get(Container.BANK));
		return captures.getOrDefault(sourceId, Evidence.unknown());
	}

	public static final String BANK_SOURCE = "bank";

	private static Evidence<CaptureState> bankCapture(ContainerObservation b) {
		switch (b.state()) {
			case CURRENT: return Evidence.verified(CaptureState.VERIFIED, b.observedAt(), b.source());
			case HISTORICAL: return Evidence.stale(CaptureState.VERIFIED, b.observedAt(), b.source());
			case INCOMPLETE: return Evidence.verified(CaptureState.PARTIAL, b.observedAt(), b.source());
			default: return Evidence.unavailable();
		}
	}

	/** Whether the quest observations cover the whole quest list (Lane D, C-06); UNKNOWN when not observed. */
	public Evidence<Coverage> questCoverage() { return questCoverage; }
	/** Whether the diary observations cover every region and tier; UNKNOWN when not observed. */
	public Evidence<Coverage> diaryCoverage() { return diaryCoverage; }
	public Evidence<Ownership> ownership(String petKey) { return collection.getOrDefault(petKey, Evidence.unknown()); }
	/** Whether the archive was observed at all; when not, an empty {@link #archive()} means unknown, not "no sessions". */
	public Evidence<Boolean> archiveAvailable() { return archiveAvailable; }
	public List<ArchiveRecord> archive() { return archive; }
	public Optional<ArchiveRecord> record(String id) { return archive.stream().filter(r -> r.id().equals(id)).findFirst(); }
	public ActivityObservation activity() { return activity; }
	/** Observed milestone progress for a goal (for example {@code bowfa}: completed milestones); UNKNOWN when unobserved. */
	public Evidence<Integer> goalProgress(String goal) { return goalProgress.getOrDefault(goal, Evidence.unknown()); }

	/** Builder; one per publication. Not thread-safe; the built snapshot is. */
	public static final class Builder {
		private final AccountScope scope;
		private int generation;
		private long revision;
		private final Map<Container, ContainerObservation> containers = new EnumMap<>(Container.class);
		private final Map<String, Evidence<QuestState>> quests = new LinkedHashMap<>();
		private final Map<String, Evidence<QuestState>> diaries = new LinkedHashMap<>();
		private final Map<String, Evidence<Integer>> officialKc = new LinkedHashMap<>();
		private final Map<String, SkillObservation> skills = new LinkedHashMap<>();
		private final Map<String, Evidence<CaptureState>> captures = new LinkedHashMap<>();
		private final Map<String, Evidence<Ownership>> collection = new LinkedHashMap<>();
		private final List<ArchiveRecord> archive = new ArrayList<>();
		private ActivityObservation activity = ActivityObservation.none();
		private Evidence<Boolean> archiveAvailable = Evidence.unknown();
		private final Map<String, Evidence<Integer>> goalProgress = new LinkedHashMap<>();
		private Evidence<Coverage> questCoverage = Evidence.unknown();
		private Evidence<Coverage> diaryCoverage = Evidence.unknown();

		private Builder(AccountScope scope) { this.scope = scope; }

		public Builder generation(int g) { generation = g; return this; }
		public Builder revision(long r) { revision = r; return this; }
		public Builder container(ContainerObservation o) { containers.put(o.container(), o); return this; }
		public Builder quest(String name, Evidence<QuestState> e) { quests.put(name, e); return this; }
		public Builder diary(String regionTier, Evidence<QuestState> e) { diaries.put(regionTier, e); return this; }
		public Builder officialKc(String kcKey, Evidence<Integer> e) { officialKc.put(kcKey, e); return this; }
		public Builder skill(String skill, SkillObservation o) { skills.put(skill, o); return this; }
		public Builder capture(String sourceId, Evidence<CaptureState> e) { captures.put(sourceId, e); return this; }
		public Builder ownership(String petKey, Evidence<Ownership> e) { collection.put(petKey, e); return this; }
		public Builder archive(Evidence<Boolean> available, List<ArchiveRecord> records) {
			archiveAvailable = Objects.requireNonNull(available);
			if (!available.hasValue() && !records.isEmpty()) throw new IllegalArgumentException("records need an observed archive");
			archive.clear();
			archive.addAll(records);
			return this;
		}
		public Builder activity(ActivityObservation a) { activity = Objects.requireNonNull(a); return this; }
		public Builder goalProgress(String goal, Evidence<Integer> e) { goalProgress.put(goal, Objects.requireNonNull(e)); return this; }
		public Builder questCoverage(Evidence<Coverage> e) { questCoverage = Objects.requireNonNull(e); return this; }
		public Builder diaryCoverage(Evidence<Coverage> e) { diaryCoverage = Objects.requireNonNull(e); return this; }
		public FixtureSnapshot build() { return new FixtureSnapshot(this); }
	}
}
