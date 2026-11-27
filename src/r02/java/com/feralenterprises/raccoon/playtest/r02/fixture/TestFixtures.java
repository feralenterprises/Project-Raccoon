package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny deterministic fixtures with the full contract shape, so one lane can render and test its route with every
 * other panel absent. Values are illustrative and few; the assembled run uses the frozen A/B datasets instead.
 * Item names are RuneLite gameval display names (checked at runelite 9af7b54).
 */
public final class TestFixtures {
	public static final Instant T0 = Instant.parse("2026-11-26T18:15:00Z");

	private TestFixtures() { }

	public static Registry registry() {
		return new Registry(Arrays.asList(
			new ItemDef(995, "Coins", true, "Other"),
			new ItemDef(1511, "Logs", true, "Materials"),
			new ItemDef(359, "Raw tuna", true, "Materials"),
			new ItemDef(21512, "Bird house", true, "Materials"),
			new ItemDef(10828, "Helm of Neitiznot", false, "Gear"),
			new ItemDef(1409, "Iban's staff", false, "Gear"),
			new ItemDef(12494, "Ancient chaps", true, "Gear"),
			new ItemDef(8013, "Teleport to house", true, "Supplies"),
			new ItemDef(153, "Fishing potion(2)", true, "Supplies"),
			new ItemDef(29589, "Emberlight", false, "Gear")),
			Arrays.asList(
				new EncounterDef("scurrius", "Scurrius", "Regular", "Easy", "scurrius"),
				new EncounterDef("vorkath", "Vorkath", "Regular", "Elite", "vorkath"),
				new EncounterDef("tormented-demons", "Tormented Demons", "Demi", "Hard", "tormented-demons"),
				new EncounterDef("brutus", "Brutus", "Regular", "Easy", "brutus"),
				new EncounterDef("dagannoth-rex", "Dagannoth Rex", "Regular", "Hard", "dagannoth-rex"),
				new EncounterDef("dagannoth-prime", "Dagannoth Prime", "Regular", "Hard", "dagannoth-prime"),
				new EncounterDef("dagannoth-supreme", "Dagannoth Supreme", "Regular", "Hard", "dagannoth-supreme"),
				new EncounterDef("hespori", "Hespori", "Demi", null, "hespori")));
	}

	public static FixtureClock clock() { return new FixtureClock(T0); }

	private static Map<Integer, Long> items(long... idQty) {
		Map<Integer, Long> m = new LinkedHashMap<>();
		for (int i = 0; i < idQty.length; i += 2) m.put((int) idQty[i], idQty[i + 1]);
		return m;
	}

	/** Demo A: current bank (no raw tuna), DS II complete, 48 Vorkath / 12 Scurrius KC, record a1, verified levels. */
	public static FixtureSnapshot demoA(int generation, long revision) {
		String src = "Demo A fixture";
		ArchiveRecord a1 = new ArchiveRecord("a1", "vorkath", "vorkath", T0.minus(Duration.ofHours(3)), Duration.ofMinutes(31), 12,
			"kills", Evidence.verified(38_400L, T0, src), items(995, 280_000));
		return FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(revision)
			.container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, src, items(995, 2_350_840, 1511, 1290, 21512, 16, 10828, 1, 29589, 1)))
			.container(new ContainerObservation(Container.INVENTORY, ContainerState.INCOMPLETE, T0, src, items(8013, 1)))
			.quest("Dragon Slayer II", Evidence.verified(QuestState.COMPLETE, T0, src))
			.quest("Song of the Elves", Evidence.verified(QuestState.IN_PROGRESS, T0, src))
			.quest("The Ides of Milk", Evidence.verified(QuestState.NOT_STARTED, T0, src))
			.officialKc("vorkath", Evidence.count(48, T0, src))
			.officialKc("scurrius", Evidence.count(12, T0, src))
			.officialKc("brutus", Evidence.count(0, T0, src))
			.skill("firemaking", new SkillObservation(Evidence.verified(60, T0, src), Evidence.unavailable()))
			.skill("fletching", new SkillObservation(Evidence.verified(1, T0, src), Evidence.unavailable()))
			.capture("skills", Evidence.verified(CaptureState.PENDING, T0, src))
			.capture("bank", Evidence.verified(CaptureState.VERIFIED, T0, src))
			.ownership("beef", Evidence.unknown())
			.archive(Evidence.verified(true, T0, src), Collections.singletonList(a1))
			.activity(new ActivityObservation(ActivityObservation.Mode.REPLAY, "vorkath", "a1", T0, T0, 0, Evidence.unavailable()))
			.build();
	}

	/** Demo B: DS II not started, verified 0 Vorkath KC, 3 Scurrius KC, verified empty bank, no A archive. */
	public static FixtureSnapshot demoB(int generation, long revision) {
		String src = "Demo B fixture";
		return FixtureSnapshot.builder(AccountScope.of("B", 1)).generation(generation).revision(revision)
			.container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, src, Collections.emptyMap()))
			.quest("Dragon Slayer II", Evidence.verified(QuestState.NOT_STARTED, T0, src))
			.officialKc("vorkath", Evidence.count(0, T0, src))
			.officialKc("scurrius", Evidence.count(3, T0, src))
			.ownership("beef", Evidence.verified(Ownership.OBTAINED, T0, src))
			.capture("skills", Evidence.verified(CaptureState.PENDING, T0, src))
			.archive(Evidence.verified(true, T0, src), Collections.emptyList())
			.build();
	}

	/** Signed out: nothing account-derived is observed. */
	public static FixtureSnapshot signedOut(int generation, int epoch) {
		return FixtureSnapshot.builder(AccountScope.signedOut(epoch)).generation(generation)
			.activity(new ActivityObservation(ActivityObservation.Mode.SIGNED_OUT, null, null, null, null, 0, Evidence.unavailable()))
			.build();
	}
}
