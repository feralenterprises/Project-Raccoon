package com.feralenterprises.raccoon.playtest.r02.panel.files;

import com.feralenterprises.raccoon.playtest.r02.fixture.ActivityObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.EvidenceStatus;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.panel.home.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.home.MiniJson;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Feral Files' view of one snapshot, following the frozen R02 {@code ffRender}. Every count, time, XP and item comes
 * from the shared {@code ArchiveRecord}s of this scope. The verified {@code shared/saved-records.json} supplies
 * presentation only (title, kind, icon) and, when its own count and unit agree exactly with the shared record, the
 * record note and notable item. Trip counts never stand in for account-wide KC (C-10); a Barrows record counts chests
 * and one claiming another unit has no count (C-11). Unknown is unavailable, never zero.
 */
final class FilesModel {
	enum Archive { LOGOUT, UNKNOWN, EMPTY, POPULATED }

	static final String[] FILTERS = {"All", "Combat", "Skilling", "Progress"};
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.UK).withZone(ZoneOffset.UTC);

	/** Presentation for saved records, keyed {@code account/id}. Display only; never evidence. */
	static final class Presentation {
		private final Map<String, Map<String, Object>> byKey = new HashMap<>();

		@SuppressWarnings("unchecked")
		Presentation(LaneInputs lane) throws IOException {
			Map<String, Object> saved = (Map<String, Object>) lane.json("shared/saved-records.json");
			for (Map.Entry<String, Object> account : saved.entrySet())
				for (Object rec : (List<?>) account.getValue())
					byKey.put(account.getKey() + "/" + MiniJson.str(rec, "id"), (Map<String, Object>) rec);
		}

		Map<String, Object> get(String account, String id) { return account == null ? null : byKey.get(account + "/" + id); }
	}

	static final class Item {
		final int id;
		final String name;
		final long quantity;

		Item(int id, String name, long quantity) {
			this.id = id;
			this.name = name;
			this.quantity = quantity;
		}
	}

	/** One saved record as Feral Files shows it. */
	static final class Rec {
		final String id, title, kind, icon, date, encounterKey, activityKey, countLabel, rowMetric, rowTime, note;
		/** Count in the record's own unit; null when unknown or contradicted. */
		final Long count, activeMinutes, xp;
		/** Observed loot; empty means not observed (the shared record cannot distinguish an empty verified ledger). */
		final List<Item> loot;
		final Item notable;

		Rec(ArchiveRecord r, Registry registry, Map<String, Object> p) {
			id = r.id();
			encounterKey = r.encounterKey();
			activityKey = r.activityKey();
			String key = encounterKey != null ? encounterKey : activityKey;
			title = key != null && registry.encounter(key).isPresent() ? registry.encounter(key).get().name()
				: p != null && MiniJson.str(p, "title") != null ? MiniJson.str(p, "title") : titleCase(key);
			kind = p != null && MiniJson.str(p, "kind") != null ? MiniJson.str(p, "kind") : encounterKey != null ? "Combat" : "Skilling";
			icon = p != null && MiniJson.str(p, "icon") != null ? MiniJson.str(p, "icon") : encounterKey != null ? encounterKey + ".png" : null;
			date = DATE.format(r.started());
			String unit = r.unitLabel() == null ? "" : r.unitLabel();
			Long c = r.count().hasValue() ? Long.valueOf(r.count().value()) : null; // unknown count stays unknown (a11)
			if ("barrows".equals(activityKey) && !"chests".equalsIgnoreCase(unit)) {
				c = null;
				unit = "chests";
			}
			count = c;
			countLabel = sentence(unit);
			rowMetric = count == null ? "Count unknown" : count + " " + unit.toLowerCase(Locale.ROOT);
			activeMinutes = r.active() == null ? null : r.active().toMinutes();
			rowTime = activeMinutes == null ? "Time unknown" : activeMinutes + "m active";
			xp = r.xp().hasValue() ? r.xp().value() : null;
			List<Item> items = new ArrayList<>();
			for (Map.Entry<Integer, Long> e : r.loot().entrySet()) items.add(new Item(e.getKey(), registry.itemName(e.getKey()), e.getValue()));
			loot = Collections.unmodifiableList(items);
			boolean agrees = p != null && count != null && agrees(p, count, unit);
			note = agrees ? MiniJson.str(p, "notes") : null;
			Item n = null;
			if (agrees && p.get("notable") instanceof Number) {
				int nid = ((Number) p.get("notable")).intValue();
				n = loot.stream().filter(i -> i.id == nid).findFirst().orElse(null);
			}
			notable = n;
		}

		boolean combat() { return kind.equals("Combat"); }

		boolean matches(String query, String filter) {
			if (!filter.equals("All") && !filter.equals(kind)) return false;
			String q = query.trim().toLowerCase(Locale.ROOT);
			return q.isEmpty() || String.join(" ", title, date, kind, rowMetric).toLowerCase(Locale.ROOT).contains(q);
		}

		/** The presentation record describes the same count and unit as the shared record. */
		@SuppressWarnings("unchecked")
		private static boolean agrees(Map<String, Object> p, long count, String unit) {
			Object m = p.get("metric");
			if (m instanceof Map) {
				Object v = ((Map<String, Object>) m).get("value"), u = ((Map<String, Object>) m).get("unit");
				return v instanceof Number && ((Number) v).longValue() == count && unit.equalsIgnoreCase(String.valueOf(u));
			}
			Object kills = p.get("kills"), metric = p.get("metricCount");
			if (unit.equalsIgnoreCase("kills")) return kills instanceof Number && ((Number) kills).longValue() == count;
			return metric instanceof Number && ((Number) metric).longValue() == count
				&& unit.equalsIgnoreCase(String.valueOf(p.get("metricLabel")));
		}
	}

	/** The current-login card in Sessions: a replay is shown as a replay of a saved record (C-08). */
	static final class CurrentLogin {
		final String activity, countLabel;
		final Long xp, count, activeMinutes;

		CurrentLogin(String activity, String countLabel, Long xp, Long count, Long activeMinutes) {
			this.activity = activity;
			this.countLabel = countLabel;
			this.xp = xp;
			this.count = count;
			this.activeMinutes = activeMinutes;
		}
	}

	final Archive archive;
	final String account, status;
	final List<Rec> records;
	final CurrentLogin current;
	private final FixtureSnapshot snapshot;

	private FilesModel(FixtureSnapshot s, Registry registry, Presentation p) {
		snapshot = s;
		account = s.scope().signedIn() ? s.scope().account() : null;
		Evidence<Boolean> available = s.archiveAvailable();
		if (account == null) archive = Archive.LOGOUT;
		else if (!available.hasValue() || !Boolean.TRUE.equals(available.value())) archive = Archive.UNKNOWN;
		else archive = s.archive().isEmpty() ? Archive.EMPTY : Archive.POPULATED;
		switch (archive) {
			case LOGOUT: status = "Account unavailable"; break;
			case UNKNOWN: status = "Awaiting verification"; break;
			case EMPTY: status = "Verified empty archive"; break;
			default: status = "Saved observations";
		}
		List<Rec> rs = new ArrayList<>();
		if (archive == Archive.POPULATED) for (ArchiveRecord r : s.archive()) rs.add(new Rec(r, registry, p.get(account, r.id())));
		records = archive == Archive.LOGOUT || archive == Archive.UNKNOWN ? null : Collections.unmodifiableList(rs);
		current = account == null ? null : current(s, rs);
	}

	static FilesModel of(FixtureSnapshot s, Registry registry, Presentation p) { return new FilesModel(s, registry, p); }

	private static CurrentLogin current(FixtureSnapshot s, List<Rec> records) {
		ActivityObservation a = s.activity();
		if (a.mode() != ActivityObservation.Mode.REPLAY || a.replayRecordId() == null) return null;
		Rec r = records.stream().filter(x -> x.id.equals(a.replayRecordId())).findFirst().orElse(null);
		if (r == null) return null;
		return new CurrentLogin(r.title + " (saved replay)", r.countLabel, r.xp, r.count, r.activeMinutes);
	}

	/** Account-wide KC from the shared kill log, never from trip counts (C-10). */
	String accountKc(Rec r) {
		if (r.encounterKey == null) return null;
		Evidence<Integer> e = snapshot.officialKc(r.encounterKey);
		return e.status() == EvidenceStatus.VERIFIED || e.status() == EvidenceStatus.VERIFIED_ZERO ? number((long) e.value()) : null;
	}

	Rec record(String id) {
		return records == null || id == null ? null : records.stream().filter(r -> r.id.equals(id)).findFirst().orElse(null);
	}

	Rec latestSpoil() { return records == null ? null : records.stream().filter(r -> r.notable != null).findFirst().orElse(null); }

	List<Rec> matching(String query, String filter) {
		return records == null ? Collections.emptyList() : records.stream().filter(r -> r.matches(query, filter)).collect(Collectors.toList());
	}

	static String number(Long n) { return n == null ? "Unavailable" : String.format(Locale.US, "%,d", n); }

	static String sentence(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

	static String titleCase(String key) {
		if (key == null) return "Saved session";
		StringBuilder b = new StringBuilder();
		for (String w : key.split("[-_ ]+")) if (!w.isEmpty()) b.append(b.length() > 0 ? " " : "").append(sentence(w));
		return b.toString();
	}
}
