package com.feralenterprises.raccoon.playtest.r02.acceptance;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenDataset;
import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios;
import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios.ArchiveState;
import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios.BankState;
import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios.Selection;
import com.feralenterprises.raccoon.playtest.r02.assembly.JsLiteral;
import com.feralenterprises.raccoon.playtest.r02.fixture.ActivityObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.CaptureState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.EncounterDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.EvidenceStatus;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureClock;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.ItemDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.Ownership;
import com.feralenterprises.raccoon.playtest.r02.fixture.QuestState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;

/** Frozen A/B dataset fixtures, checked against the frozen package. Called from {@link ContractTests}. */
final class FrozenDataTests {
	private FrozenDataTests() { }

	static void literals(BiConsumer<String, Boolean> check) {
		Object v = JsLiteral.parse("{a:1, 'b':'x\\'y', \"c\":[true,null,-2.5e1,], // note\n d:Object.freeze({e:[]}), f:g(1,'h',[i(2)])}");
		@SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) v;
		check.accept("literal: keys, quotes, numbers, comments", m.get("a").equals(1.0) && "x'y".equals(m.get("b"))
			&& ((List<?>) m.get("c")).equals(Arrays.asList(true, null, -25.0)));
		check.accept("literal: Object.freeze unwrapped", m.get("d") instanceof Map);
		check.accept("literal: calls kept uninterpreted", m.get("f") instanceof JsLiteral.Call && "g".equals(((JsLiteral.Call) m.get("f")).name)
			&& ((JsLiteral.Call) m.get("f")).args.size() == 3);
		check.accept("literal: functions refused", ContractTests.throwsIt(() -> JsLiteral.parse("{a:()=>1}")));
		check.accept("literal: identifiers refused", ContractTests.throwsIt(() -> JsLiteral.parse("[x]")));
		check.accept("literal: duplicate keys refused", ContractTests.throwsIt(() -> JsLiteral.parse("{a:1,a:2}")));
		check.accept("literal: trailing content refused", ContractTests.throwsIt(() -> JsLiteral.parse("[1] 2")));
		check.accept("literal: declaration stops at the literal", JsLiteral.parse("[1]") .equals(JsLiteral.declaration("const x = [1].map(f);", "x")));
	}

	static void run(Path pkg, BiConsumer<String, Boolean> check) throws Exception {
		FrozenDataset d = FrozenDataset.load(pkg);
		FrozenScenarios sc = new FrozenScenarios(d);
		Registry r = d.registry();
		Instant now = Instant.parse("2026-11-27T03:00:00Z");

		// ---- registry ----
		check.accept("registry: 60 encounters", r.encounters().size() == 60);
		Set<String> cats = r.encounters().stream().map(EncounterDef::category).collect(Collectors.toSet());
		check.accept("registry: frozen category vocabulary", cats.equals(new HashSet<>(Arrays.asList("Regular", "Wilderness", "Wilderness access", "Moons", "Demi"))));
		EncounterDef dk = r.encounter("dagannoth-kings").get();
		check.accept("registry: three separate King counts (D-18)", dk.kcKeys().equals(FrozenDataset.DAGANNOTH_KC_KEYS) && dk.kcKey() == null);
		check.accept("registry: unranked difficulty unknown (D-18)", r.encounter("hespori").get().difficulty() == null
			&& r.encounter("lizardman-shamans").map(e -> e.difficulty() == null).orElse(true));
		check.accept("registry: ranks from frozen bcRanks", "Elite".equals(r.encounter("vorkath").get().difficulty())
			&& "Easy".equals(r.encounter("scurrius").get().difficulty()) && "Master".equals(r.encounter("the-nightmare").get().difficulty()));
		check.accept("registry: D-09 Bird house", "Bird house".equals(r.itemName(21512)));
		check.accept("registry: D-15 names", "Iban's staff".equals(r.itemName(1409)) && "Ancient chaps".equals(r.itemName(12494))
			&& "Teleport to house".equals(r.itemName(8013)) && "Fishing potion(2)".equals(r.itemName(153)));
		ItemDef helm = r.item(10828).get();
		check.accept("registry: D-10 Helm of Neitiznot tradeable, illustrative 53,000", helm.tradeable() && helm.illustrativePrice() == 53_000L);
		// Demo A's bank may be personal data: checks compare against the external package, never against committed holdings.
		boolean categories = true;
		for (FrozenDataset.BankRow row : d.bankCatalog()) categories &= r.item(row.id).isPresent() && row.category.equals(r.item(row.id).get().category());
		check.accept("registry: unestablished tradeability is unknown", r.item(995).get().tradeability() == ItemDef.Tradeability.UNKNOWN);
		check.accept("registry: bank categories carried for every catalogue row", categories && "Logs".equals(r.itemName(1511)));
		check.accept("registry: archive loot names present", "Guthan's platebody".equals(r.itemName(4728)));
		check.accept("dataset: 37 bank catalogue rows", d.bankCatalog().size() == 37);
		check.accept("dataset: 215 quests in prepared order", d.questNames().size() == 215 && "Learning the Ropes".equals(d.questNames().get(0)));
		check.accept("dataset: 48 diary tiers", d.diaryTiers().size() == 48 && d.diaryTiers().contains("Ardougne Easy"));
		check.accept("dataset: lane catalogs readable", ((List<?>) d.literal("activityCatalog")).size() == 123 && d.jsonBlock("r02-xp-data") != null);

		// ---- Demo A ----
		FixtureSnapshot a = sc.snapshot(Selection.demoA(1), 3, 1, now);
		check.accept("A: scope, generation, revision", "A".equals(a.scope().account()) && a.generation() == 3 && a.revision() == 1);
		check.accept("A: current bank, 37 items", a.container(Container.BANK).state() == ContainerState.CURRENT && a.container(Container.BANK).items().size() == 37);
		boolean held = true;
		for (FrozenDataset.BankRow row : d.bankCatalog()) held &= a.container(Container.BANK).quantity(row.id).value() == row.quantity;
		check.accept("A: every catalogue quantity held exactly", held);
		check.accept("A: no raw tuna is verified absence (D-05 suppress)", a.container(Container.BANK).quantity(359).status() == EvidenceStatus.VERIFIED_ABSENT);
		check.accept("A: other containers unavailable", a.container(Container.INVENTORY).state() == ContainerState.UNAVAILABLE
			&& a.container(Container.SEED_VAULT).state() == ContainerState.UNAVAILABLE);
		check.accept("C-01 A: DS II complete, Vorkath 48", a.quest("Dragon Slayer II").value() == QuestState.COMPLETE && a.officialKc("vorkath").value() == 48);
		check.accept("C-02 A seed: Song of the Elves complete, route 9", a.quest("Song of the Elves").value() == QuestState.COMPLETE && a.goalProgress("bowfa").value() == 9);
		FixtureSnapshot aq = sc.snapshot(Selection.demoA(1).withRoute("quest"), 3, 1, now);
		check.accept("C-02 A quest: Song of the Elves in progress, route 8", aq.quest("Song of the Elves").value() == QuestState.IN_PROGRESS && aq.goalProgress("bowfa").value() == 8);
		FixtureSnapshot au = sc.snapshot(Selection.demoA(1).withRoute("unknown"), 3, 1, now);
		check.accept("C-02 A unknown: Song of the Elves unknown, route unknown", au.quest("Song of the Elves").status() == EvidenceStatus.UNKNOWN
			&& au.goalProgress("bowfa").status() == EvidenceStatus.UNKNOWN);
		check.accept("A: route quests before the step complete", a.quest("Plague City").value() == QuestState.COMPLETE && a.quest("Mourning’s End Part II").value() == QuestState.COMPLETE);
		check.accept("A: The Ides of Milk not started (C-03 input)", a.quest("The Ides of Milk").value() == QuestState.NOT_STARTED);
		check.accept("A: unlisted quest unknown", a.quest("Cook's Assistant").status() == EvidenceStatus.UNKNOWN);
		check.accept("A: demo quest states", a.quest("A Kingdom Divided").value() == QuestState.IN_PROGRESS && a.quest("Monkey Madness II").value() == QuestState.COMPLETE);
		check.accept("A: diaries from the demo table", a.diary("Ardougne Easy").value() == QuestState.COMPLETE && a.diary("Wilderness Easy").status() == EvidenceStatus.UNKNOWN);
		check.accept("A: Scurrius 12 crosses the 10 milestone (Home join)", a.officialKc("scurrius").value() == 12);
		check.accept("A: observed zero kept (D-11)", a.officialKc("brutus").status() == EvidenceStatus.VERIFIED_ZERO && a.officialKc("zulrah").status() == EvidenceStatus.UNKNOWN);
		check.accept("A: Kings counts unknown, not fabricated", a.officialKc("dagannoth-rex").status() == EvidenceStatus.UNKNOWN);
		ArchiveRecord a1 = a.record("a1").get();
		check.accept("D-13 A: a1 has 12 trip kills and links Vorkath", a1.count().value() == 12 && "vorkath".equals(a1.encounterKey()) && "Kills".equals(a1.unitLabel()));
		check.accept("D-13 A: a1 time and XP", a1.activeTime().value().equals(Duration.ofMinutes(31)) && a1.xp().value() == 38_400L
			&& a1.started().equals(Instant.parse("2026-11-26T23:42:00Z")));
		check.accept("A: a1 loot 280,000 coins, supplies observed", a1.lootLedger().value().equals(java.util.Collections.singletonMap(995, 280_000L))
			&& a1.suppliesLedger().value().get(385) == 18L);
		ArchiveRecord a2 = a.record("a2").get();
		check.accept("C-11: Barrows counted in Chests", "Chests".equals(a2.unitLabel()) && a2.count().value() == 8);
		ArchiveRecord a3 = a.record("a3").get();
		check.accept("A: fishing record counts fish, no encounter, loot unavailable", "Fish caught".equals(a3.unitLabel()) && a3.count().value() == 96
			&& a3.encounterKey() == null && a3.lootLedger().status() == EvidenceStatus.UNAVAILABLE && "fishing".equals(a3.activityKey()));
		ArchiveRecord a7 = a.record("a7").get();
		check.accept("A: progress record keeps unobserved time unknown", a7.activeTime().status() == EvidenceStatus.UNKNOWN && a7.kind() == ArchiveRecord.Kind.PROGRESS);
		check.accept("A: a10 observed empty loot", a.record("a10").get().lootLedger().status() == EvidenceStatus.VERIFIED_ABSENT);
		check.accept("A: 11 archive records", a.archive().size() == 11);
		ActivityObservation act = a.activity();
		check.accept("A: Vorkath activity replays a1", act.mode() == ActivityObservation.Mode.REPLAY && "a1".equals(act.replayRecordId())
			&& act.activeMillis(now) == Duration.ofMinutes(31).toMillis() && act.elapsedMillis(now) == Duration.ofMinutes(37).toMillis());
		check.accept("D-08: activity location unavailable", act.location().status() == EvidenceStatus.UNAVAILABLE);
		check.accept("A: skills pending, exact XP unavailable", a.skill("firemaking").level().status() == EvidenceStatus.UNKNOWN
			&& a.skill("firemaking").exactXp().status() == EvidenceStatus.UNAVAILABLE && a.capture("skills").value() == CaptureState.PENDING);
		check.accept("A: captures per frozen authority", a.capture("quests").value() == CaptureState.PARTIAL && a.capture("diaries").value() == CaptureState.PARTIAL
			&& a.capture("kills").value() == CaptureState.PARTIAL && a.capture("collection").value() == CaptureState.PENDING);
		check.accept("A: collection unobserved", a.ownership("beef").status() == EvidenceStatus.UNKNOWN);

		// ---- Demo B ----
		FixtureSnapshot b = sc.snapshot(Selection.demoB(1), 3, 1, now);
		check.accept("C-01 B: DS II not started, verified 0 Vorkath", b.quest("Dragon Slayer II").value() == QuestState.NOT_STARTED
			&& b.officialKc("vorkath").status() == EvidenceStatus.VERIFIED_ZERO);
		check.accept("B: other quests unknown", b.quest("Song of the Elves").status() == EvidenceStatus.UNKNOWN && b.goalProgress("bowfa").status() == EvidenceStatus.UNKNOWN);
		check.accept("B: Scurrius 3 leaves the milestone open, Brutus 2", b.officialKc("scurrius").value() == 3 && b.officialKc("brutus").value() == 2);
		check.accept("B: sample bank of five", b.container(Container.BANK).items().size() == 5 && b.container(Container.BANK).quantity(995).value() == 42_500L);
		check.accept("B: never sees A's records", !b.record("a1").isPresent() && b.archive().size() == 1 && "b1".equals(b.archive().get(0).id()));
		check.accept("B: no Vorkath archive row", b.archive().stream().noneMatch(x -> "vorkath".equals(x.encounterKey())));
		check.accept("B: Beef obtained (consistent with Brutus KC)", b.ownership("beef").value() == Ownership.OBTAINED && b.ownership("rocky").status() == EvidenceStatus.UNKNOWN);
		check.accept("B: collection capture partial, diaries pending", b.capture("collection").value() == CaptureState.PARTIAL && b.capture("diaries").value() == CaptureState.PENDING);
		check.accept("B: diaries unknown", b.diary("Ardougne Easy").status() == EvidenceStatus.UNKNOWN);
		check.accept("B: unrecognised activity has no counts", b.activity().mode() == ActivityObservation.Mode.UNSUPPORTED && b.activity().replayRecordId() == null);
		FixtureSnapshot bc = sc.snapshot(Selection.demoB(1).withActivity("cooking"), 3, 1, now);
		check.accept("B: cooking replays b1, never a6", "b1".equals(bc.activity().replayRecordId()));
		FixtureSnapshot bv = sc.snapshot(Selection.demoB(1).withActivity("vorkath"), 3, 1, now);
		check.accept("B: Vorkath is reference only", bv.activity().mode() == ActivityObservation.Mode.REFERENCE && bv.activity().replayRecordId() == null);

		// ---- states ----
		FixtureSnapshot so = sc.snapshot(Selection.signedOut(2), 3, 1, now);
		check.accept("signed out: nothing account-derived", !so.scope().signedIn() && so.container(Container.BANK).state() == ContainerState.UNAVAILABLE
			&& so.officialKc("vorkath").status() == EvidenceStatus.UNKNOWN && so.archive().isEmpty() && so.activity().mode() == ActivityObservation.Mode.SIGNED_OUT);
		check.accept("bank historical: stale values, unknown absence", sc.snapshot(Selection.demoA(1).withBank(BankState.HISTORICAL), 3, 1, now).container(Container.BANK).quantity(1511).status() == EvidenceStatus.STALE
			&& sc.snapshot(Selection.demoA(1).withBank(BankState.HISTORICAL), 3, 1, now).container(Container.BANK).quantity(359).status() == EvidenceStatus.UNKNOWN);
		check.accept("bank empty: verified empty", sc.snapshot(Selection.demoA(1).withBank(BankState.EMPTY), 3, 1, now).container(Container.BANK).verifiedEmpty());
		{
			com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation ir =
				sc.snapshot(Selection.demoA(1).withBank(BankState.valueOf("INCOMPLETE_RETAINED")), 3, 1, now).container(Container.BANK);
			com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation full =
				sc.snapshot(Selection.demoA(1).withBank(BankState.HISTORICAL), 3, 1, now).container(Container.BANK);
			check.accept("bank incomplete-retained: incomplete, claims nothing, retains the frozen complete capture as historical",
				ir.state() == com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState.INCOMPLETE && ir.items().isEmpty()
				&& ir.retained() != null && ir.retained().state() == com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState.HISTORICAL
				&& ir.retained().items().equals(full.items()));
		}
		check.accept("bank incomplete: no ownership claim", sc.snapshot(Selection.demoA(1).withBank(BankState.INCOMPLETE), 3, 1, now).container(Container.BANK).quantity(1511).status() == EvidenceStatus.UNKNOWN);
		check.accept("bank awaiting: unavailable", sc.snapshot(Selection.demoA(1).withBank(BankState.AWAITING), 3, 1, now).container(Container.BANK).state() == ContainerState.UNAVAILABLE);
		FixtureSnapshot ap = sc.snapshot(Selection.demoA(1).withArchive(ArchiveState.PARTIAL), 3, 1, now);
		check.accept("archive partial: one record, metrics unknown, KC unavailable", ap.archive().size() == 1 && ap.archive().get(0).count().status() == EvidenceStatus.UNKNOWN
			&& ap.archive().get(0).xp().status() == EvidenceStatus.UNKNOWN && ap.officialKc("vorkath").status() == EvidenceStatus.UNKNOWN
			&& ap.capture("kills").value() == CaptureState.PENDING);
		check.accept("archive partial: no replay", ap.activity().mode() == ActivityObservation.Mode.REFERENCE);
		FixtureSnapshot ae = sc.snapshot(Selection.demoA(1).withArchive(ArchiveState.EMPTY), 3, 1, now);
		check.accept("archive empty: observed, no records", ae.archiveAvailable().hasValue() && ae.archive().isEmpty());
		FixtureSnapshot ak = sc.snapshot(Selection.demoA(1).withArchive(ArchiveState.UNKNOWN), 3, 1, now);
		check.accept("archive unknown: not observed", ak.archiveAvailable().status() == EvidenceStatus.UNKNOWN && ak.officialKc("scurrius").status() == EvidenceStatus.UNKNOWN);
		FixtureSnapshot nq = sc.snapshot(Selection.demoA(1).withQuestData(false), 3, 1, now);
		check.accept("quest data unknown: quests and diaries unknown, captures pending", nq.quest("Dragon Slayer II").status() == EvidenceStatus.UNKNOWN
			&& nq.diary("Ardougne Easy").status() == EvidenceStatus.UNKNOWN && nq.capture("quests").value() == CaptureState.PENDING);

		// ---- D-19 with the shared clock ----
		FixtureClock clock = new FixtureClock(now);
		Selection idleSel = Selection.demoA(1).withActivity("idle");
		FixtureSnapshot idle = sc.snapshot(idleSel, 3, 1, clock.now());
		long e0 = idle.activity().elapsedMillis(clock.now()), a0 = idle.activity().activeMillis(clock.now());
		clock.advance(Duration.ofSeconds(30));
		check.accept("D-19: idle keeps the Vorkath record, elapsed +30 s, active unchanged", idle.activity().mode() == ActivityObservation.Mode.IDLE
			&& "vorkath".equals(idle.activity().activityKey()) && idle.activity().elapsedMillis(clock.now()) - e0 == 30_000 && idle.activity().activeMillis(clock.now()) == a0);
		check.accept("D-19: banking after idle keeps the record", "vorkath".equals(sc.snapshot(idleSel.withActivity("banking"), 3, 1, now).activity().activityKey()));
		check.accept("D-19: logout retires the record", sc.snapshot(Selection.signedOut(2), 3, 1, now).activity().activityKey() == null);
		check.accept("idle with no session fabricates none", sc.snapshot(Selection.demoB(1).withActivity("idle"), 3, 1, now).activity().activityKey() == null);

		// ---- through the dispatcher: A -> B -> logout ----
		FixtureDispatcher fx = new FixtureDispatcher(r, new FixtureClock(now));
		boolean[] ok = new boolean[1];
		SwingUtilities.invokeAndWait(() -> {
			int g = fx.bind();
			ok[0] = fx.publish(sc.snapshot(Selection.demoA(1), g, 1, now)) && fx.publish(sc.snapshot(Selection.demoA(1).withBank(BankState.HISTORICAL), g, 2, now))
				&& !fx.publish(sc.snapshot(Selection.demoA(1), g, 2, now)) && fx.publish(sc.snapshot(Selection.demoB(2), g, 1, now))
				&& fx.current().record("a1").isPresent() == false && fx.publish(sc.snapshot(Selection.signedOut(3), g, 1, now));
		});
		check.accept("dispatcher: frozen A -> stale guard -> B -> logout", ok[0] && !fx.current().scope().signedIn());

		// ---- fail closed ----
		Path tmp = Files.createTempDirectory("r02page");
		Files.copy(pkg.resolve("SHA256SUMS.txt"), tmp.resolve("SHA256SUMS.txt"));
		byte[] page = Files.readAllBytes(pkg.resolve(FrozenDataset.PAGE));
		page[page.length / 2] ^= 1;
		Files.write(tmp.resolve(FrozenDataset.PAGE), page);
		boolean refused = false;
		try { FrozenDataset.load(tmp); } catch (java.io.IOException e) { refused = e.getMessage().contains("SHA-256 mismatch"); }
		check.accept("dataset: altered page refused", refused);
	}
}
