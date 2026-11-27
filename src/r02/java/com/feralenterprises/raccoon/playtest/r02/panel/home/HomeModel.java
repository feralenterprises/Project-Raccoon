package com.feralenterprises.raccoon.playtest.r02.panel.home;

import com.feralenterprises.raccoon.playtest.r02.fixture.ActivityObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.CaptureState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.EvidenceStatus;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Home's evidence-to-copy rules, one immutable value per snapshot. Mirrors the frozen R02 {@code home()} behaviour
 * exactly, and every value is conditioned on its own evidence: official KC (never archived trip kills, C-04/C-10), a
 * current bank observation (C-05), saved versus current-login activity (C-08/C-09) and partial quest coverage (C-06).
 * Nothing here is inferred from another account or another scope.
 */
final class HomeModel {
	static final String SAVED_REPLAY = "Saved session replay", CURRENT = "Current activity", SAVED = "Saved session";
	private static final Set<ActivityObservation.Mode> LIVE = EnumSet.of(ActivityObservation.Mode.IDLE,
		ActivityObservation.Mode.BANKING, ActivityObservation.Mode.TRAVEL, ActivityObservation.Mode.PAUSED);

	/** A Recent Activity row and its typed destination. */
	static final class Recent {
		/** {@code recordId}: the saved record this row shows (replay or archive), for its frozen icon; null for live rows. */
		final String title, subtitle, end, encounterKey, activityKey, recordId;
		final RouteId route;
		final RouteRequest.Param param;
		final String payload;

		Recent(String title, String subtitle, String end, String encounterKey, String activityKey, String recordId,
			RouteId route, RouteRequest.Param param, String payload) {
			this.title = title;
			this.subtitle = subtitle;
			this.end = end;
			this.encounterKey = encounterKey;
			this.activityKey = activityKey;
			this.recordId = recordId;
			this.route = route;
			this.param = param;
			this.payload = payload;
		}
	}

	final String goalTitle, goalValue, goalRemaining, reason, primaryLabel;
	/** Official KC, or null when no verified kill-log observation exists for this scope. */
	final Integer kc;
	final int target;
	final RouteId primaryRoute;
	final String encounterKey;
	final String tuna;
	final List<Recent> recent;
	final String totalLevel, combatLevel, questProgress;

	private HomeModel(FixtureSnapshot s, Registry r, HomeInputs in) {
		boolean signedIn = s.scope().signedIn();
		encounterKey = in.milestoneEncounter;
		target = in.milestoneTarget;
		goalTitle = r.encounter(encounterKey).map(e -> e.name()).orElse(titleCase(encounterKey)) + " KC goal";
		kc = signedIn ? verifiedCount(s.officialKc(encounterKey)) : null;
		goalValue = kc == null ? "Kill count unavailable" : fmt(kc) + " / " + target + " kills · demo goal";
		goalRemaining = kc == null ? "Waiting for a kill-log observation"
			: kc >= target ? "Goal complete" : (target - kc) + " kills remaining";
		reason = "This personal demo milestone uses your observed " + goalTitle.replace(" KC goal", "") + " kill count.";
		primaryRoute = kc == null || kc >= target ? RouteId.CHRONICLE : RouteId.OUTFIT;
		primaryLabel = primaryRoute == RouteId.OUTFIT ? "Prepare loadout ›" : "View boss record ›";
		tuna = signedIn ? tuna(s.container(Container.BANK), in) : "Raw tuna availability unknown";
		recent = signedIn ? Collections.unmodifiableList(recent(s, r)) : Collections.emptyList();
		totalLevel = "Unavailable";
		combatLevel = "Unavailable";
		questProgress = signedIn && observedQuestCoverage(s.capture("quests")) ? "Partial" : "Unknown";
	}

	static HomeModel of(FixtureSnapshot s, Registry r, HomeInputs in) { return new HomeModel(s, r, in); }

	double fraction() { return kc == null ? 0 : Math.min(1.0, kc / (double) target); }

	/** Official KC only when verified for this scope; stale, unknown and unavailable are never shown as a count. */
	static Integer verifiedCount(Evidence<Integer> e) {
		return e.status() == EvidenceStatus.VERIFIED || e.status() == EvidenceStatus.VERIFIED_ZERO ? e.value() : null;
	}

	/** Frozen raw-tuna wording; "fewer than" only from a current bank observation (absent counts as verified zero). */
	static String tuna(ContainerObservation bank, HomeInputs in) {
		String unknown = "Raw tuna availability unknown";
		if (bank == null || bank.state() == ContainerState.UNAVAILABLE) return unknown;
		Evidence<Long> q = bank.quantity(in.tunaItem);
		// a complete current observation without the item is a verified zero (C-05); anything else without a value is unknown
		boolean verifiedAbsent = q.status() == EvidenceStatus.VERIFIED_ABSENT && bank.state() == ContainerState.CURRENT;
		if (!q.hasValue() && !verifiedAbsent) return unknown;
		if (bank.state() == ContainerState.HISTORICAL) return "Raw tuna · only a saved observation";
		boolean enough = !verifiedAbsent && q.value() >= in.tunaRequired;
		if (enough) return "Cook " + in.tunaRequired + " tuna · " + fmt(in.tunaXpTotal) + " XP";
		return bank.state() == ContainerState.CURRENT ? "Verified bank has fewer than " + in.tunaRequired + " raw tuna" : unknown;
	}

	private static boolean observedQuestCoverage(Evidence<CaptureState> e) {
		return e.hasValue() && (e.value() == CaptureState.VERIFIED || e.value() == CaptureState.PARTIAL);
	}

	private static List<Recent> recent(FixtureSnapshot s, Registry r) {
		List<Recent> rows = new ArrayList<>();
		ActivityObservation a = s.activity();
		String replayId = null;
		if (a.mode() == ActivityObservation.Mode.REPLAY || LIVE.contains(a.mode())) {
			boolean replay = a.mode() == ActivityObservation.Mode.REPLAY;
			ArchiveRecord rec = replay && a.replayRecordId() != null ? s.record(a.replayRecordId()).orElse(null) : null;
			replayId = replay ? a.replayRecordId() : null;
			String key = rec != null && rec.encounterKey() != null ? rec.encounterKey() : a.activityKey();
			// a current-login row carries no XP here: the fixture has no live XP observation (C-09)
			String xp = rec != null ? xp(rec.xp()) : "—";
			rows.add(new Recent(name(r, key), replay ? SAVED_REPLAY : CURRENT, xp + " XP",
				rec != null ? rec.encounterKey() : null, a.activityKey(), rec != null ? rec.id() : null, RouteId.ACTIVITY, null, null));
		}
		Evidence<Boolean> available = s.archiveAvailable();
		if (available.hasValue() && Boolean.TRUE.equals(available.value())) {
			int added = 0;
			for (ArchiveRecord rec : s.archive()) {
				if (rec.id().equals(replayId)) continue;
				if (added++ == 2) break;
				String key = rec.encounterKey() != null ? rec.encounterKey() : rec.activityKey();
				rows.add(new Recent(name(r, key), SAVED, xp(rec.xp()) + " XP", rec.encounterKey(), rec.activityKey(), rec.id(),
					RouteId.FILES, RouteRequest.Param.SESSION, rec.id()));
			}
		}
		return rows;
	}

	private static String xp(Evidence<Long> e) { return e.hasValue() ? fmt(e.value()) : "—"; }

	static String name(Registry r, String key) {
		if (key == null) return "Activity";
		return r.encounter(key).map(e -> e.name()).orElse(titleCase(key));
	}

	static String titleCase(String key) {
		StringBuilder b = new StringBuilder();
		for (String w : key.split("[-_ ]+")) {
			if (w.isEmpty()) continue;
			if (b.length() > 0) b.append(' ');
			b.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
		}
		return b.toString();
	}

	static String fmt(long n) { return String.format(Locale.US, "%,d", n); }
}
