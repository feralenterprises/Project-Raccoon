package com.feralenterprises.raccoon.playtest.r02.panel.bounties;

import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.EvidenceStatus;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.QuestState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bandit's Bounties readiness for one snapshot (frozen R02 {@code bountyStatus}), conditioned only on shared evidence:
 * a verified incomplete gate quest locks; an unverified gate, a missing current bank or a missing archived loadout is
 * Unverified; a verified-absent example weapon is Within reach. Nothing is ever Ready: equipment, carried items, access
 * and supplies are not observed. "Past this stage" needs progression evidence the shared contract does not carry.
 */
final class BountiesModel {
	enum Readiness {
		READY("Ready"), REACH("Within reach"), NOT_READY("Needs preparation"), LOCKED("Locked"), UNKNOWN("Unverified"), OUTGROWN("Past this stage");

		final String label;

		Readiness(String label) { this.label = label; }
	}

	/** Frozen section order. */
	static final Readiness[] SECTIONS = {Readiness.READY, Readiness.REACH, Readiness.NOT_READY, Readiness.LOCKED, Readiness.UNKNOWN, Readiness.OUTGROWN};

	static final class Result {
		final BountiesData.Entry entry;
		final Readiness readiness;
		final String reason;

		Result(BountiesData.Entry entry, Readiness readiness, String reason) {
			this.entry = entry;
			this.readiness = readiness;
			this.reason = reason;
		}
	}

	final String accountLine;
	final List<Result> results = new ArrayList<>();

	private BountiesModel(FixtureSnapshot s, BountiesData data) {
		accountLine = s.scope().signedIn() ? "Account " + s.scope().account() + " · Offline" : "Account unavailable";
		for (BountiesData.Entry e : data.entries) results.add(status(s, data, e));
	}

	static BountiesModel of(FixtureSnapshot s, BountiesData data) { return new BountiesModel(s, data); }

	private static Result status(FixtureSnapshot s, BountiesData data, BountiesData.Entry e) {
		if (!s.scope().signedIn()) return new Result(e, Readiness.UNKNOWN, "Account evidence unavailable.");
		if (e.gate != null) {
			Evidence<QuestState> q = s.quest(e.gate);
			boolean verified = q.status() == EvidenceStatus.VERIFIED && q.value() != null;
			if (verified && q.value() != QuestState.COMPLETE) return new Result(e, Readiness.LOCKED, e.gate + " is incomplete.");
			if (!verified) return new Result(e, Readiness.UNKNOWN, e.gate + " completion is unverified.");
		}
		ContainerObservation bank = s.container(Container.BANK);
		if (bank == null || bank.state() != ContainerState.CURRENT)
			return new Result(e, Readiness.UNKNOWN, "A current bank observation is required to check gear readiness.");
		if (e.exampleWeapon == null) return new Result(e, Readiness.UNKNOWN, "No archived loadout is available for this encounter.");
		EvidenceStatus held = bank.quantity(e.exampleWeapon).status();
		if (held == EvidenceStatus.VERIFIED_ABSENT || held == EvidenceStatus.VERIFIED_ZERO)
			return new Result(e, Readiness.REACH, data.itemName(e.exampleWeapon) + " is absent from the observed bank. Equipped and carried items are unknown.");
		return new Result(e, Readiness.UNKNOWN, "Account equipment, access or supply requirements are incomplete.");
	}

	Result result(String key) { return results.stream().filter(r -> r.entry.key.equals(key)).findFirst().orElse(null); }

	List<Result> matches(Readiness filter, String search) {
		String q = search.toLowerCase(Locale.ROOT).trim();
		List<Result> out = new ArrayList<>();
		for (Result r : results) if ((filter == null || r.readiness == filter) && r.entry.name.toLowerCase(Locale.ROOT).contains(q)) out.add(r);
		return out;
	}
}
