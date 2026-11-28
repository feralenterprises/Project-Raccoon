package com.feralenterprises.raccoon.playtest.r02.assembly;

import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.ActivityObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.CaptureState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Coverage;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.Ownership;
import com.feralenterprises.raccoon.playtest.r02.fixture.QuestState;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds {@link FixtureSnapshot}s for the frozen A/B scenarios, following the frozen observation authority
 * ({@code source/r02.js}: {@code quest}, {@code diary}, {@code kc}, {@code read}, {@code r02Capture},
 * {@code showActivity}; baseline {@code bankAuthority}, {@code ffAuthority}). Every rule cites its frozen source in a
 * comment. Nothing is inferred beyond those rules: what they leave unobserved stays UNKNOWN or UNAVAILABLE.
 */
public final class FrozenScenarios {
	/** Frozen bank simulator states. */
	public enum BankState { POPULATED, HISTORICAL, AWAITING, INCOMPLETE, INCOMPLETE_RETAINED, EMPTY }
	/** Frozen Feral Files archive states ({@code ffArchiveStates}). */
	public enum ArchiveState { POPULATED, PARTIAL, EMPTY, UNKNOWN }

	/** One scenario selection; immutable. */
	public static final class Selection {
		public final String account;
		public final int epoch;
		public final BankState bank;
		public final ArchiveState archive;
		/** Frozen {@code qdScenario === 'example'} (quest and diary demo data present). */
		public final boolean questData;
		/** Frozen Pathfinder scenario: quest, seed, craft, done or unknown. */
		public final String route;
		/** Frozen Current Activity key ({@code vorkath}, {@code idle}, {@code unrecognized}, ...), or null for none. */
		public final String activity;
		/** The activity before an idle/banking/travel/paused state, or null. */
		public final String previousActivity;

		private Selection(String account, int epoch, BankState bank, ArchiveState archive, boolean questData, String route,
			String activity, String previousActivity) {
			this.account = account;
			this.epoch = epoch;
			this.bank = bank;
			this.archive = archive;
			this.questData = questData;
			this.route = route;
			this.activity = activity;
			this.previousActivity = previousActivity;
		}

		/** Frozen default for Demo A after {@code r02Sync}: populated bank and archive, quest example, route seed, Vorkath replay. */
		public static Selection demoA(int epoch) { return new Selection("A", epoch, BankState.POPULATED, ArchiveState.POPULATED, true, "seed", "vorkath", null); }
		/** Frozen default for Demo B: populated sample bank and archive, quest example, route unknown, unrecognised activity. */
		public static Selection demoB(int epoch) { return new Selection("B", epoch, BankState.POPULATED, ArchiveState.POPULATED, true, "unknown", "unrecognized", null); }
		public static Selection signedOut(int epoch) { return new Selection(null, epoch, BankState.AWAITING, ArchiveState.UNKNOWN, false, "unknown", null, null); }

		public Selection withBank(BankState b) { return new Selection(account, epoch, b, archive, questData, route, activity, previousActivity); }
		public Selection withArchive(ArchiveState a) { return new Selection(account, epoch, bank, a, questData, route, activity, previousActivity); }
		public Selection withQuestData(boolean q) { return new Selection(account, epoch, bank, archive, q, route, activity, previousActivity); }
		public Selection withRoute(String r) { return new Selection(account, epoch, bank, archive, questData, r, activity, previousActivity); }
		public Selection withActivity(String a) {
			// Frozen showActivity(): idle states keep the current session; 'unrecognized' has none to keep.
			String session = "unrecognized".equals(activity) ? null : activity;
			String prev = isIdle(a) ? (isIdle(activity) ? previousActivity : session) : null;
			return new Selection(account, epoch, bank, archive, questData, route, a, prev);
		}
	}

	private final FrozenDataset data;

	public FrozenScenarios(FrozenDataset data) { this.data = Objects.requireNonNull(data); }

	public FrozenDataset data() { return data; }

	static boolean isIdle(String key) {
		return "idle".equals(key) || "banking".equals(key) || "travel".equals(key) || "paused".equals(key);
	}

	public FixtureSnapshot snapshot(Selection s, int generation, long revision, Instant now) {
		if (s.account == null) {
			return FixtureSnapshot.builder(AccountScope.signedOut(s.epoch)).generation(generation).revision(revision)
				.activity(new ActivityObservation(ActivityObservation.Mode.SIGNED_OUT, null, null, null, null, 0, Evidence.unavailable()))
				.build();
		}
		boolean a = "A".equals(s.account);
		FixtureSnapshot.Builder b = FixtureSnapshot.builder(AccountScope.of(s.account, s.epoch)).generation(generation).revision(revision);
		b.container(bank(s));

		// r02.js dataQuality(): quest and diary observations exist only in the 'example' scenario.
		// r02.js routeStep(): A only, from pfScenarios.
		Integer step = s.questData && a ? data.routeScenarios().get(s.route) : null;
		b.goalProgress("bowfa", step == null ? Evidence.unknown() : Evidence.count(step, now, "frozen Pathfinder scenario " + s.route));
		if (s.questData) quests(b, a, step, now);
		if (s.questData && a) {
			// r02.js diary(): A, example data, region listed for the tier in qdDiaryDemo -> complete; otherwise unknown.
			for (String tierKey : data.diaryTiers()) {
				int sp = tierKey.lastIndexOf(' ');
				List<String> regions = data.diaryDemo().getOrDefault(tierKey.substring(sp + 1), Collections.emptyList());
				if (regions.contains(tierKey.substring(0, sp))) b.diary(tierKey, Evidence.verified(QuestState.COMPLETE, now, "frozen diary demo"));
			}
		}

		// r02.js kc(): official KC only while the archive state is populated.
		if (s.archive == ArchiveState.POPULATED)
			data.officialKc(s.account).forEach((k, n) -> b.officialKc(k, Evidence.count(n, now, "frozen official KC (Demo " + s.account + ")")));

		// ffAuthority.read(): unknown -> null records; empty -> []; partial -> first record with metrics removed.
		List<ArchiveRecord> base = data.archive(s.account);
		switch (s.archive) {
			case UNKNOWN: b.archive(Evidence.unknown(), Collections.<ArchiveRecord>emptyList()); break;
			case EMPTY: b.archive(Evidence.verified(true, now, "frozen archive (empty)"), Collections.<ArchiveRecord>emptyList()); break;
			case PARTIAL: b.archive(Evidence.verified(true, now, "frozen archive (partial)"), base.isEmpty() ? Collections.<ArchiveRecord>emptyList() : Collections.singletonList(partial(base.get(0)))); break;
			default: b.archive(Evidence.verified(true, now, "frozen archive"), base);
		}

		// r02.js read(): skills, inventory, equipment, account type and membership are unobserved (null) for A and B.
		// The snapshot default already gives unknown levels and unavailable exact XP.

		// r02.js r02Capture(id, bank).
		String cap = "frozen capture authority";
		for (String id : new String[] {"skills", "equipment", "inventory", "mode", "slayer"}) b.capture(id, Evidence.verified(CaptureState.PENDING, now, cap));
		b.capture("collection", Evidence.verified("B".equals(s.account) ? CaptureState.PARTIAL : CaptureState.PENDING, now, cap));
		b.capture("quests", Evidence.verified(s.questData ? CaptureState.PARTIAL : CaptureState.PENDING, now, cap));
		b.capture("diaries", Evidence.verified(s.questData && a ? CaptureState.PARTIAL : CaptureState.PENDING, now, cap));
		// Lane D (C-06): the frozen example covers only part of each list, so coverage is PARTIAL where data exists.
		if (s.questData) b.questCoverage(Evidence.verified(Coverage.PARTIAL, now, cap));
		if (s.questData && a) b.diaryCoverage(Evidence.verified(Coverage.PARTIAL, now, cap));
		b.capture("kills", Evidence.verified(s.archive == ArchiveState.POPULATED ? CaptureState.PARTIAL : CaptureState.PENDING, now, cap));

		// r02.js read(): collection observed for B only (referencePetOwnership); 'unknown' rows stay unknown.
		if ("B".equals(s.account)) data.referencePetOwnership().forEach((pet, v) -> {
			if ("obtained".equals(v)) b.ownership(pet, Evidence.verified(Ownership.OBTAINED, now, "frozen reference ownership (Demo B)"));
		});

		b.activity(activity(s, now));
		return b.build();
	}

	/**
	 * Frozen {@code incompleteRetained}: an incomplete bank observation (no quantities of its own) that retains the earlier
	 * complete capture as HISTORICAL evidence, taken at the frozen historical bank time (older than the incomplete one).
	 */
	public static ContainerObservation incompleteRetaining(Map<Integer, Long> complete, String src) {
		ContainerObservation earlier = new ContainerObservation(Container.BANK, ContainerState.HISTORICAL, FrozenDataset.bankTime(true), src + ", saved", complete);
		return new ContainerObservation(Container.BANK, ContainerState.INCOMPLETE, FrozenDataset.bankTime(false), src + ", incomplete",
			Collections.<Integer, Long>emptyMap()).withRetained(earlier);
	}

	private ContainerObservation bank(Selection s) {
		// bankAuthority: populated/empty/historical keep a complete snapshot; awaiting and (non-retained) incomplete have none;
		// incomplete-retained shows only the earlier complete capture, as saved (frozen incompleteRetained).
		Map<Integer, Long> items = new LinkedHashMap<>();
		if ("A".equals(s.account)) for (FrozenDataset.BankRow r : data.bankCatalog()) items.merge(r.id, r.quantity, Long::sum);
		else items.putAll(data.bankSampleB());
		String src = "frozen bank (Demo " + s.account + ")";
		switch (s.bank) {
			case POPULATED: return new ContainerObservation(Container.BANK, ContainerState.CURRENT, FrozenDataset.bankTime(false), src, items);
			case EMPTY: return new ContainerObservation(Container.BANK, ContainerState.CURRENT, FrozenDataset.bankTime(false), src, Collections.<Integer, Long>emptyMap());
			case HISTORICAL: return new ContainerObservation(Container.BANK, ContainerState.HISTORICAL, FrozenDataset.bankTime(true), src + ", saved", items);
			case INCOMPLETE: return new ContainerObservation(Container.BANK, ContainerState.INCOMPLETE, FrozenDataset.bankTime(false), src + ", incomplete", Collections.<Integer, Long>emptyMap());
			case INCOMPLETE_RETAINED: return incompleteRetaining(items, src);
			default: return ContainerObservation.unavailable(Container.BANK);
		}
	}

	private void quests(FixtureSnapshot.Builder b, boolean a, Integer step, Instant now) {
		String src = "frozen quest authority";
		if (!a) {
			// r02.js quest(): B -> Dragon Slayer II not started; everything else unknown.
			b.quest("Dragon Slayer II", Evidence.verified(QuestState.NOT_STARTED, now, src));
			return;
		}
		// r02.js quest() for A, in its order: Pathfinder route nodes before the step, then the demo table; the three
		// named quests are set explicitly and override both.
		for (Map.Entry<String, String> e : data.questDemo().entrySet()) b.quest(e.getKey(), Evidence.verified(state(e.getValue()), now, src));
		List<String> route = data.routeQuests();
		if (step != null) for (int n = 0; n < route.size() && n < step; n++) if (route.get(n) != null) b.quest(route.get(n), Evidence.verified(QuestState.COMPLETE, now, src));
		b.quest("Dragon Slayer II", Evidence.verified(QuestState.COMPLETE, now, src));
		b.quest("The Ides of Milk", Evidence.verified(QuestState.NOT_STARTED, now, src));
		if (step == null) b.quest("Song of the Elves", Evidence.unknown());
		else b.quest("Song of the Elves", Evidence.verified(step >= 9 ? QuestState.COMPLETE : QuestState.IN_PROGRESS, now, src));
	}

	private static QuestState state(String frozen) {
		switch (frozen) {
			case "complete": return QuestState.COMPLETE;
			case "progress": return QuestState.IN_PROGRESS;
			case "notstarted": return QuestState.NOT_STARTED;
			default: throw new IllegalArgumentException("frozen quest state " + frozen);
		}
	}

	private static ArchiveRecord partial(ArchiveRecord r) {
		// ffAuthority partial: first record with active, xp, kills, deaths, actions, supplies and estimate removed.
		return new ArchiveRecord(r.id(), r.activityKey(), r.encounterKey(), r.title(), r.kind(), r.started(), Evidence.unknown(),
			r.elapsedTime(), Evidence.unknown(), Evidence.unknown(), r.unitLabel(), Evidence.unknown(), r.lootLedger(), Evidence.unknown(),
			"Partial saved evidence; unobserved metrics remain unknown.");
	}

	private ActivityObservation activity(Selection s, Instant now) {
		if (s.activity == null) return ActivityObservation.none();
		if ("unrecognized".equals(s.activity)) return new ActivityObservation(ActivityObservation.Mode.UNSUPPORTED, null, null, null, null, 0, Evidence.unavailable());
		boolean idle = isIdle(s.activity);
		String key = idle ? s.previousActivity : s.activity;
		if (key == null) {
			// An idle-type state with no session before it: the frozen page keeps no session (no fabricated history).
			return new ActivityObservation(mode(s.activity), null, null, null, null, 0, Evidence.unavailable());
		}
		// r02.js showActivity(): replay only when the account's archive has the mapped record with active and elapsed.
		String recordId = s.archive == ArchiveState.POPULATED ? data.archivedActivity(s.account).get(key) : null;
		ArchiveRecord rec = null;
		for (ArchiveRecord r : data.archive(s.account)) if (r.id().equals(recordId)) rec = r;
		if (rec != null && rec.activeTime().hasValue() && rec.elapsedTime().hasValue()) {
			Instant start = now.minus(rec.elapsedTime().value());
			long active = rec.activeTime().value().toMillis();
			ActivityObservation.Mode m = idle ? mode(s.activity) : ActivityObservation.Mode.REPLAY;
			return new ActivityObservation(m, key, idle ? null : rec.id(), start, null, active, Evidence.unavailable());
		}
		// Recognised but unobserved: reference only (no count, time or location).
		return new ActivityObservation(idle ? mode(s.activity) : ActivityObservation.Mode.REFERENCE, key, null, null, null, 0, Evidence.unavailable());
	}

	private static ActivityObservation.Mode mode(String idleKey) {
		switch (idleKey) {
			case "idle": return ActivityObservation.Mode.IDLE;
			case "banking": return ActivityObservation.Mode.BANKING;
			case "travel": return ActivityObservation.Mode.TRAVEL;
			default: return ActivityObservation.Mode.PAUSED;
		}
	}
}
