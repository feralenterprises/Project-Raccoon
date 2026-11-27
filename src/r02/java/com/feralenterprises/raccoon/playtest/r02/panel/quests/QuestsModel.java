package com.feralenterprises.raccoon.playtest.r02.panel.quests;

import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.EvidenceStatus;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.QuestState;
import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.PathfinderData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Quests & Diaries' view of one snapshot (frozen R02 {@code qdRender}). Quest and diary states come only from the
 * scope's shared evidence ({@code quest(name)}, {@code diary("<Region> <Tier>")}); only verified evidence is a state,
 * anything else is Unknown (never not started or complete). Coverage is Partial while any observation exists, never
 * complete (C-06). The catalogue is an archived quest order, not a certified list; diary task completion is unobserved.
 */
final class QuestsModel {
	static final String[] REGIONS = {"Ardougne", "Desert", "Falador", "Fremennik", "Kandarin", "Karamja", "Kourend & Kebos", "Lumbridge & Draynor",
		"Morytania", "Varrock", "Western Provinces", "Wilderness"};
	static final String[] TIERS = {"Easy", "Medium", "Hard", "Elite"};

	enum State {
		COMPLETE("Complete"), PROGRESS("In progress"), NOT_STARTED("Not started"), UNKNOWN("Unknown");

		final String label;

		State(String label) { this.label = label; }
	}

	/** One archived diary task: text, skill requirements, other named requirements (quests or items, as archived). */
	static final class Task {
		final String text, skills, other;

		Task(String text, String skills, String other) {
			this.text = text;
			this.skills = skills;
			this.other = other;
		}
	}

	/** The archived diary task lists ({@code D/diary-tasks.tsv}), keyed {@code "<Region> <Tier>"}. */
	static final class Diaries {
		final Map<String, List<Task>> tasks;

		Diaries(LaneInputs lane) throws IOException {
			Map<String, List<Task>> m = new LinkedHashMap<>();
			List<Task> current = null;
			for (String line : lane.text("D/diary-tasks.tsv").split("\n")) {
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] f = line.split("\t", -1);
				if (f[0].equals("@")) {
					current = new ArrayList<>();
					m.put(f[1], current);
				} else if (current != null) current.add(new Task(f[0], f.length > 1 ? blank(f[1]) : null, f.length > 2 ? blank(f[2]) : null));
			}
			if (m.size() != 48) throw new IOException("Expected 48 diary tiers, found " + m.size());
			tasks = Collections.unmodifiableMap(m);
		}

		private static String blank(String s) { return s.trim().isEmpty() ? null : s.trim(); }
	}

	final PathfinderData data;
	final boolean signedIn;
	final String coverage, countText;
	private final FixtureSnapshot snapshot;

	private QuestsModel(FixtureSnapshot s, PathfinderData data) {
		this.snapshot = s;
		this.data = data;
		signedIn = s.scope().signedIn();
		long complete = signedIn ? data.quests.stream().filter(q -> quest(q.name) == State.COMPLETE).count() : 0;
		boolean any = signedIn && data.quests.stream().anyMatch(q -> quest(q.name) != State.UNKNOWN);
		coverage = !signedIn ? "Account evidence unavailable" : any ? "Partial quest observations" : "Quest observations unknown";
		countText = any ? complete + " verified complete" : "No verified total";
	}

	static QuestsModel of(FixtureSnapshot s, PathfinderData data) { return new QuestsModel(s, data); }

	static State state(Evidence<QuestState> e) {
		if (e.status() != EvidenceStatus.VERIFIED || e.value() == null) return State.UNKNOWN;
		switch (e.value()) {
			case COMPLETE: return State.COMPLETE;
			case IN_PROGRESS: return State.PROGRESS;
			default: return State.NOT_STARTED;
		}
	}

	State quest(String name) { return signedIn ? state(snapshot.quest(name)) : State.UNKNOWN; }

	State diary(String region, String tier) { return signedIn ? state(snapshot.diary(region + " " + tier)) : State.UNKNOWN; }

	/** Diary tier count: "n/m complete" only when every listed region has verified evidence for the tier, else Unknown. */
	String tierCount(String tier, List<String> regions) {
		if (!signedIn || regions.stream().anyMatch(r -> diary(r, tier) == State.UNKNOWN)) {
			long n = regions.stream().filter(r -> diary(r, tier) == State.COMPLETE).count();
			return n == 0 ? "Unknown" : "At least " + n + "/" + regions.size() + " complete";
		}
		return regions.stream().filter(r -> diary(r, tier) == State.COMPLETE).count() + "/" + regions.size() + " complete";
	}

	List<PathfinderData.Quest> quests(String search, String status, String kind) {
		String q = search.toLowerCase(Locale.ROOT);
		return data.quests.stream().filter(r -> (kind.equals("all") || r.kind.equals(kind)) && (status.equals("all") || quest(r.name).name().equals(status))
			&& r.name.toLowerCase(Locale.ROOT).contains(q)).sorted((a, b) -> a.id - b.id).collect(Collectors.toList());
	}

	List<String> regions(String search, String region) {
		String q = search.toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		for (String r : REGIONS) if ((region.equals("all") || r.equals(region)) && r.toLowerCase(Locale.ROOT).contains(q)) out.add(r);
		return out;
	}
}
