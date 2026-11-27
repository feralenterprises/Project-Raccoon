package com.feralenterprises.raccoon.playtest.r02.panel.pathfinder;

import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.EvidenceStatus;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.QuestState;
import com.feralenterprises.raccoon.playtest.r02.fixture.SkillObservation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pathfinder's view of one snapshot. The frozen R02 route is driven by one step count {@code n} (milestones before
 * {@code n} complete, the milestone at {@code n} current). Here {@code n} comes only from the scope's shared evidence:
 * Song of the Elves complete gives 9 (13 when a current bank verifies the Bow of Faerdhinen), in progress gives 8;
 * otherwise the Elf quest chain gives {@code k+1} only when quest {@code k} is complete and quest {@code k+1} is
 * verified not complete. Anything less is Unknown: unknown never implies unmet or complete (C-02).
 */
final class PathfinderModel {
	static final int TOTAL = 13, SEED = 9, BOW_ITEM = 25865;
	static final String[] REQ_SKILLS = {"Agility", "Construction", "Farming", "Herblore", "Hunter", "Mining", "Smithing", "Woodcutting"};

	enum State {
		COMPLETE("Complete"), CURRENT("Current"), AVAILABLE("Available"), BLOCKED("Blocked"), UNKNOWN("Unknown");

		final String label;

		State(String label) { this.label = label; }
	}

	/** A quest's evidence as a label: Complete, In progress, Not started or Unknown (unverified). */
	enum QuestLabel {
		COMPLETE("Complete"), IN_PROGRESS("In progress"), NOT_STARTED("Not started"), UNKNOWN("Unknown");

		final String label;

		QuestLabel(String label) { this.label = label; }
	}

	final PathfinderData data;
	/** Completed milestone count, or null when the evidence cannot establish it. */
	final Integer step;
	final boolean signedIn;
	private final FixtureSnapshot snapshot;

	private PathfinderModel(FixtureSnapshot s, PathfinderData data) {
		this.data = data;
		this.snapshot = s;
		signedIn = s.scope().signedIn();
		step = signedIn ? step(s, data) : null;
	}

	static PathfinderModel of(FixtureSnapshot s, PathfinderData data) { return new PathfinderModel(s, data); }

	static QuestLabel quest(FixtureSnapshot s, String name) {
		if (!s.scope().signedIn()) return QuestLabel.UNKNOWN;
		Evidence<QuestState> e = s.quest(name);
		if (e.status() != EvidenceStatus.VERIFIED || e.value() == null) return QuestLabel.UNKNOWN;
		switch (e.value()) {
			case COMPLETE: return QuestLabel.COMPLETE;
			case IN_PROGRESS: return QuestLabel.IN_PROGRESS;
			default: return QuestLabel.NOT_STARTED;
		}
	}

	private static Integer step(FixtureSnapshot s, PathfinderData data) {
		QuestLabel sote = quest(s, "Song of the Elves");
		if (sote == QuestLabel.COMPLETE) return bowVerified(s) ? TOTAL : SEED;
		if (sote == QuestLabel.IN_PROGRESS) return data.index("sote");
		// Elf quest chain (milestones before the grouped requirements checkpoint)
		int chainEnd = data.index("reqs");
		int lastComplete = -1;
		for (int i = 0; i < chainEnd; i++) if (quest(s, data.nodes.get(i).name) == QuestLabel.COMPLETE) lastComplete = i;
		int next = lastComplete + 1;
		if (next >= chainEnd) return null; // grouped requirements cannot be verified from quest evidence
		QuestLabel frontier = quest(s, data.nodes.get(next).name);
		return frontier == QuestLabel.NOT_STARTED || frontier == QuestLabel.IN_PROGRESS ? next : null;
	}

	private static boolean bowVerified(FixtureSnapshot s) {
		ContainerObservation b = s.container(Container.BANK);
		return b != null && b.state() == ContainerState.CURRENT && b.items().getOrDefault(BOW_ITEM, 0L) > 0;
	}

	/** Frozen {@code pfState}. */
	State state(String key) {
		if (step == null) return State.UNKNOWN;
		int i = data.index(key);
		if (i < step) return State.COMPLETE;
		PathfinderData.Node current = current();
		if (current != null && current.key.equals(key)) return State.CURRENT;
		return data.node(key).deps.stream().allMatch(d -> data.index(d) < step) ? State.AVAILABLE : State.BLOCKED;
	}

	PathfinderData.Node current() { return step == null || step >= TOTAL ? null : data.nodes.get(step); }

	String progressText() { return step == null ? "Unknown" : percent() + " (" + step + "/" + TOTAL + ")"; }

	String overallText() { return step == null ? "Unknown" : step + "/" + TOTAL + " · " + percent(); }

	String percent() { return step == null ? "Unknown" : Math.round(step * 100.0 / TOTAL) + "%"; }

	String nextTitle() {
		PathfinderData.Node n = current();
		return n != null ? n.name : step != null && step == TOTAL ? "Goal complete" : "Evidence unavailable";
	}

	String nextSubtitle() {
		PathfinderData.Node n = current();
		if (n != null) return n.key.equals("sote") ? "Complete the quest" : "View milestone";
		return step != null && step == TOTAL ? "All milestones complete" : "Await account capture";
	}

	/** Visible route in compact mode: the frozen window around the current step. */
	List<PathfinderData.Node> compactRoute() {
		int from = Math.max(0, Math.min(6, (step == null ? 8 : step) - 2));
		return data.nodes.subList(from, TOTAL);
	}

	/** Reqs tab skill rows: verified level against 70, otherwise Unknown (never "met" without evidence). */
	List<String[]> skillRows() {
		List<String[]> rows = new ArrayList<>();
		for (String skill : REQ_SKILLS) {
			SkillObservation o = signedIn ? snapshot.skill(skill.toLowerCase(Locale.ROOT)) : SkillObservation.unobserved();
			Evidence<Integer> l = o.level();
			String v = l.status() == EvidenceStatus.VERIFIED && l.value() != null
				? (l.value() >= 70 ? "70 · Met (" + l.value() + ")" : "70 · Level " + l.value()) : "70 · Unknown";
			rows.add(new String[] {skill, v});
		}
		return Collections.unmodifiableList(rows);
	}

	QuestLabel questState(String name) { return quest(snapshot, name); }
}
