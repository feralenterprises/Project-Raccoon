package com.feralenterprises.raccoon.playtest.r02.panel.activity;

import com.feralenterprises.raccoon.playtest.r02.fixture.ActivityObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ActivityObservation.Mode;
import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.panel.activity.ActivityInputs.Entry;
import com.feralenterprises.raccoon.playtest.r02.panel.activity.ActivityInputs.Labels;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Current Activity's view of one snapshot, following the frozen R02 {@code activityRender} states. Current-login scope
 * only: a replay shows a saved record's own values (count with its own unit, saved XP, active time) and is always
 * labelled a saved replay; the shared fixture carries no live counts or XP, so nothing live is ever invented. Null
 * means unavailable, never zero.
 */
final class ActivityModel {
	enum State { ACTIVE, IDLE, UNAVAILABLE }

	static final String DATA_DETAILS_1 = "Offline sample observations only. Adapter registration is based on Burrow main 67b7d848; "
		+ "boss-trip support is conditional on verified encounter observations. Visual-only entries do not establish detection. "
		+ "Rates and loot valuation are estimates where marked. Unknown values are unavailable, not zero. No live account records "
		+ "or tracking services are connected.";
	static final String DATA_DETAILS_2 = "Current-login scope only. Historical sessions belong in Feral Files.";
	private static final Set<Mode> PAUSED = EnumSet.of(Mode.IDLE, Mode.BANKING, Mode.TRAVEL, Mode.PAUSED);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.UK).withZone(ZoneOffset.UTC);

	/** A headline metric: value null means unavailable. */
	static final class Metric {
		final String label, quality;
		final Long value;

		Metric(String label, Long value, String quality) {
			this.label = label;
			this.value = value;
			this.quality = quality;
		}
	}

	/** A label/value line ({@code .activity-line}); value null renders "Unavailable". */
	static final class Line {
		final String label, value;

		Line(String label, String value) {
			this.label = label;
			this.value = value;
		}
	}

	final State state;
	final boolean planned, replay;
	final String environment, kind, title, subtitle, status, heading;
	/** Portrait source: boss key, activity-art key, item id or skill name; null for no portrait. */
	final String portraitBoss, portraitArt, portraitSkill;
	final Integer portraitItem;
	final List<Metric> metrics;
	final List<Line> lines;
	final List<String> notes;
	final String coverage, footer, recordId;

	private ActivityModel(Builder b) {
		state = b.state;
		planned = b.planned;
		replay = b.replay;
		environment = b.environment;
		kind = b.kind;
		title = b.title;
		subtitle = b.subtitle;
		status = b.status;
		heading = b.heading;
		portraitBoss = b.portraitBoss;
		portraitArt = b.portraitArt;
		portraitSkill = b.portraitSkill;
		portraitItem = b.portraitItem;
		metrics = Collections.unmodifiableList(b.metrics);
		lines = Collections.unmodifiableList(b.lines);
		notes = Collections.unmodifiableList(b.notes);
		coverage = b.coverage;
		footer = state == State.UNAVAILABLE ? null : "Session simulator";
		recordId = b.recordId;
	}

	boolean itemPortrait() { return portraitItem != null || portraitSkill != null; }

	static ActivityModel of(FixtureSnapshot s, Registry registry, ActivityInputs in, Instant now) {
		ActivityObservation a = s.activity();
		Builder b = new Builder();
		if (!s.scope().signedIn() || a.mode() == Mode.SIGNED_OUT) return b.unavailable(false).build();
		if (a.mode() == Mode.REPLAY) {
			Entry e = in.entry(a.activityKey());
			if (e == null || e.group.equals("Session")) return b.unavailable(true).build();
			return b.replay(s, registry, in, e, a).build();
		}
		if (PAUSED.contains(a.mode())) return b.paused(in, a, now).build();
		return b.unavailable(true).build(); // NONE or UNSUPPORTED: no supported signal, nothing inferred
	}

	private static final class Builder {
		State state;
		boolean planned, replay;
		String environment = "neutral-activity", kind, title, subtitle, status, heading, portraitBoss, portraitArt, portraitSkill,
			coverage, recordId;
		Integer portraitItem;
		final List<Metric> metrics = new ArrayList<>();
		final List<Line> lines = new ArrayList<>();
		final List<String> notes = new ArrayList<>();

		ActivityModel build() { return new ActivityModel(this); }

		Builder unavailable(boolean unrecognized) {
			state = State.UNAVAILABLE;
			kind = "Unavailable";
			title = unrecognized ? "Unrecognized activity" : "Logged out";
			subtitle = unrecognized ? "No supported signal" : "Account data unavailable";
			status = "Unavailable";
			heading = "Account unavailable";
			notes.add(unrecognized ? "No supported activity signal is available. No progress is inferred."
				: "No account-bound activity is available. A login or supported activity observation is required.");
			lines.add(new Line("Active time", null));
			lines.add(new Line("Session progress", null));
			return this;
		}

		Builder paused(ActivityInputs in, ActivityObservation a, Instant now) {
			state = State.IDLE;
			Entry mode = in.entry(a.mode().name().toLowerCase(Locale.ROOT));
			title = mode != null ? mode.name : "Idle";
			subtitle = "Active time paused";
			kind = "Paused";
			status = "Paused";
			Entry last = in.entry(a.activityKey());
			if (last == null || last.group.equals("Session")) {
				heading = "Waiting for activity";
				notes.add("No previous activity in this simulated session.");
				lines.add(new Line("Active time", null));
				lines.add(new Line("Session progress", null));
				return this;
			}
			heading = "Last activity · " + last.name;
			Labels l = in.labels.get(last.id);
			// the fixture carries no session counts or XP: retained values stay unavailable, never zero
			metrics.add(new Metric(primaryLabel(l, last, null), null, null));
			metrics.add(new Metric(rateLabel(l, last, null), null, "Estimated"));
			metrics.add(new Metric(xpLabel(l, last), null, null));
			metrics.add(new Metric("XP / hr", null, "Estimated"));
			lines.add(new Line("Active time", time(a.activeMillis(now) / 1000)));
			lines.add(new Line("Elapsed session", a.sessionStart() == null ? null : time(a.elapsedMillis(now) / 1000)));
			notes.add("No new actions detected. Last activity retained; active time and rates are frozen.");
			coverage = last.status;
			return this;
		}

		Builder replay(FixtureSnapshot s, Registry registry, ActivityInputs in, Entry e, ActivityObservation a) {
			state = State.ACTIVE;
			environment = e.environment;
			title = e.boss != null ? registry.encounter(e.boss).map(d -> d.name()).orElse(e.name) : e.name;
			subtitle = e.area;
			Labels l = in.labels.get(e.id);
			kind = l != null && l.kind != null ? l.kind : e.boss != null ? "Combat" : e.group.equals("Skills") ? "Skill"
				: e.group.equals("Methods") ? "Method" : e.group.equals("Sailing") ? "Sailing" : "Activity";
			planned = e.adapter == null && e.boss == null;
			portrait(in, e, l);
			heading = "Session progress";
			ArchiveRecord rec = a.replayRecordId() == null ? null : s.record(a.replayRecordId()).orElse(null);
			if (rec == null) {
				// replay requested but the saved record is not in this scope's archive: reference only, no values
				metrics.add(new Metric(primaryLabel(l, e, null), null, null));
				metrics.add(new Metric(rateLabel(l, e, null), null, "Estimated"));
				metrics.add(new Metric(xpLabel(l, e), null, null));
				metrics.add(new Metric("XP / hr", null, "Estimated"));
				lines.add(new Line("Active time", null));
				lines.add(new Line("Elapsed session", null));
				coverage = "Activity reference · account observation unavailable";
				status = planned ? "Preview only" : "Coverage preview";
				return this;
			}
			replay = true;
			recordId = rec.id();
			String unit = rec.unitLabel();
			Long count = (long) rec.units();
			// C-11: a Barrows record is a chest count; a record claiming another unit there establishes nothing
			if (rec.activityKey() != null && rec.activityKey().equals("barrows") && !"chests".equalsIgnoreCase(unit)) {
				count = null;
				unit = "chests";
			}
			long active = rec.active() == null ? 0 : rec.active().getSeconds();
			Long xp = rec.xp().hasValue() ? rec.xp().value() : null;
			metrics.add(new Metric(primaryLabel(l, e, unit), count, null));
			metrics.add(new Metric(rateLabel(l, e, unit), rate(count, active), "Estimated"));
			metrics.add(new Metric(xpLabel(l, e), xp, null));
			metrics.add(new Metric("XP / hr", rate(xp, active), "Estimated"));
			lines.add(new Line("Active time", rec.active() == null ? null : time(active)));
			// saved records carry no elapsed time in the shared contract: unavailable, not invented
			lines.add(new Line("Elapsed session", null));
			coverage = "Replay of a saved activity · " + DATE.format(rec.started());
			status = planned ? "Preview only" : "Saved session replay";
			return this;
		}

		private void portrait(ActivityInputs in, Entry e, Labels l) {
			if (e.boss != null) {
				if (!ActivityInputs.HIDDEN_PORTRAITS.contains(e.boss)) portraitBoss = e.boss;
				return;
			}
			if (l != null && l.art != null) { portraitArt = l.art; return; }
			if (l != null && l.itemIcon != null) { portraitItem = Integer.parseInt(l.itemIcon.substring(1)); return; }
			if (e.icon != null && e.icon.endsWith(".png")) {
				String k = e.icon.substring(0, e.icon.length() - 4);
				if (k.matches("i\\d+")) { portraitItem = Integer.parseInt(k.substring(1)); return; }
				if (k.startsWith("s-")) { portraitSkill = k.substring(2); return; }
			}
			portraitArt = e.art;
		}
	}

	private static boolean kills(String unit) { return unit == null || unit.equalsIgnoreCase("kills"); }

	static String primaryLabel(Labels l, Entry e, String unit) {
		if (!kills(unit)) return sentence(unit);
		if (l != null && l.primary != null) return l.primary;
		return e.metric != null ? e.metric : "Progress";
	}

	static String rateLabel(Labels l, Entry e, String unit) {
		if (l != null && l.rate != null) return l.rate;
		return e.boss != null && kills(unit) ? "Kills / hr" : "Actions / hr";
	}

	static String xpLabel(Labels l, Entry e) {
		if (l != null && l.xp != null) return l.xp;
		return e.skill != null ? e.skill + " XP" : "Activity XP";
	}

	static Long rate(Long value, long activeSeconds) {
		return value == null || activeSeconds <= 0 ? null : Math.round(value * 3600.0 / activeSeconds);
	}

	/** Frozen {@code actTime}: "1h 2m 03s", "31m 00s". */
	static String time(long seconds) {
		long h = seconds / 3600, m = seconds % 3600 / 60, s = seconds % 60;
		return (h > 0 ? h + "h " : "") + m + "m " + String.format(Locale.ROOT, "%02d", s) + "s";
	}

	static String sentence(String unit) {
		return unit.isEmpty() ? unit : Character.toUpperCase(unit.charAt(0)) + unit.substring(1);
	}

	static String number(Long n) { return n == null ? "Unavailable" : String.format(Locale.US, "%,d", n); }
}
