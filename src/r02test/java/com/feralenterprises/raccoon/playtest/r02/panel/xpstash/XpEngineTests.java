package com.feralenterprises.raccoon.playtest.r02.panel.xpstash;

import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.SkillObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.panel.bank.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.bank.MiniJson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * XP Stash engine regressions: the three frozen arithmetic vectors ({@code C/xp-arithmetic-vectors.json}) and, when a
 * reference file is given, a differential comparison against the frozen engine run by {@code reference-diff.js}.
 * Args: the accepted Lane Inputs ZIP, optional reference JSON.
 */
public final class XpEngineTests {
	private static int checks, failures;
	private static final Instant T0 = TestFixtures.T0;

	public static void main(String[] args) throws Exception {
		LaneInputs lane = LaneInputs.open(Paths.get(args[0]));
		XpData data = XpData.load(lane);
		XpEngine engine = new XpEngine(data);
		check("801 recipes, 12 skills, BSD notice present", data.recipes.size() == 801 && data.skills.size() == 12
			&& lane.has("C/BANKED-EXPERIENCE-BSD-2-CLAUSE.txt"));
		check("OSRS level table: 13,034,431 XP is level 99; 83 is level 2; 0 is level 1", XpEngine.threshold(99) == 13_034_431
			&& XpEngine.threshold(2) == 83 && XpEngine.level(0) == 1 && XpEngine.level(83) == 2 && XpEngine.level(82) == 1);
		vectors(lane, engine);
		semantics(engine);
		if (args.length > 1) differential(engine, Paths.get(args[1]));
		System.out.println("XP Stash engine: " + (checks - failures) + "/" + checks + " checks passed");
		if (failures > 0) System.exit(1);
	}

	static Map<Integer, Long> frozenBank(LaneInputs lane) throws java.io.IOException {
		Map<Integer, Long> items = new LinkedHashMap<>();
		for (Object o : MiniJson.list(lane.json("shared/bank-and-capture.json"), "bankCatalog")) items.put((int) MiniJson.num(o, "id"), MiniJson.num(o, "quantity"));
		return items;
	}

	private static FixtureSnapshot.Builder a() { return FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(1).revision(1); }

	private static XpEngine.Entry entry(XpEngine.Result r, String key) {
		return r.entries.stream().filter(e -> e.key.equals(key)).findFirst().orElse(null);
	}

	private static void vectors(LaneInputs lane, XpEngine engine) throws java.io.IOException {
		Map<Integer, Long> bank = frozenBank(lane);
		FixtureSnapshot current = a().container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "A/current", bank)).build();
		XpEngine.Options fm = new XpEngine.Options();
		fm.skill = "Firemaking";
		fm.sources.put("vault", false);
		check("vector firemaking-a: 51,600 XP (A/current, seed vault excluded)", Objects.equals(engine.calculate(current, fm).total, 51_600d));

		XpEngine.Options fl = new XpEngine.Options();
		fl.sources.put("vault", false);
		fl.limit = true;
		fl.choices.put("Fletching|F_LOGS", "F_LONGBOW_U");
		XpEngine.Result r = engine.calculate(current, fl);
		XpEngine.Entry logs = entry(r, "Fletching|F_LOGS");
		XpEngine.Entry target = r.entries.stream().filter(e -> e.piped > 0).findFirst().orElse(null);
		check("vector fletching-a-linked: 1,290 linked unstrung longbows", logs.chosen.key.equals("F_LONGBOW_U") && target != null && target.piped == 1290);
		XpEngine.Need bow = target.needs.get(0);
		check("vector fletching-a-linked: bowstring requested 1,290, available 0, limited runs 0", bow.wanted == 1290
			&& Objects.equals(bow.available, 0L) && target.runs == 0);

		FixtureSnapshot l1 = a().container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "A/current", bank))
			.skill("fletching", new SkillObservation(Evidence.verified(1, T0, "local"), Evidence.verified(0L, T0, "local"))).build();
		check("vector fletching-preview-level-one: F_ARROW_SHAFT", entry(engine.calculate(l1, new XpEngine.Options()), "Fletching|F_LOGS").chosen.key.equals("F_ARROW_SHAFT"));
	}

	private static void semantics(XpEngine engine) {
		Map<Integer, Long> logs = new LinkedHashMap<>();
		logs.put(1511, 100L);
		FixtureSnapshot levelOnly = a().container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "x", logs))
			.skill("firemaking", new SkillObservation(Evidence.verified(60, T0, "x"), Evidence.unavailable())).build();
		XpEngine.Options o = new XpEngine.Options();
		o.skill = "Firemaking";
		o.sources.put("vault", false);
		XpEngine.Result r = engine.calculate(levelOnly, o);
		check("verified level without exact XP: level known, account XP and projections unavailable", r.currentLevel == 60 && r.currentXp == null
			&& r.total != null && r.projectedXp == null && r.projectedLevel() == null);
		FixtureSnapshot exact = a().container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "x", logs))
			.skill("firemaking", new SkillObservation(Evidence.verified(60, T0, "x"), Evidence.verified(300_000L, T0, "x"))).build();
		XpEngine.Result e = engine.calculate(exact, o);
		check("exact XP observed: projection = account XP + banked XP", e.currentXp == 300_000 && e.projectedXp == 300_000 + e.total);
		FixtureSnapshot stale = a().container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "x", logs))
			.skill("firemaking", new SkillObservation(Evidence.stale(60, T0, "x"), Evidence.unavailable())).build();
		check("a stale level is not a verified level", engine.calculate(stale, o).currentLevel == null);
		FixtureSnapshot inc = a().container(new ContainerObservation(Container.BANK, ContainerState.INCOMPLETE, T0, "x", logs)).build();
		XpEngine.Result ri = engine.calculate(inc, o);
		check("incomplete bank gives no quantities", !ri.known && ri.total == null && "Resource observations unavailable.".equals(XpEngine.warning(ri, o)));
		FixtureSnapshot hist = a().container(new ContainerObservation(Container.BANK, ContainerState.HISTORICAL, T0, "x", logs)).build();
		check("historical bank: quantities from the last complete capture, flagged", "Bank quantities are from the last complete capture."
			.equals(XpEngine.warning(engine.calculate(hist, o), o)) && XpEngine.sourceStatus(hist, "bank").equals("saved"));
		XpEngine.Options withVault = new XpEngine.Options();
		withVault.skill = "Firemaking";
		check("an included unobserved source makes the estimate partial", "Estimate covers observed sources only."
			.equals(XpEngine.warning(engine.calculate(exact, withVault), withVault)));
		check("signed out: nothing known", !engine.calculate(TestFixtures.signedOut(1, 1), o).known);
		check("money format matches the frozen page (Node toLocaleString): 12.35, 0.13, 1,234.57, 6,450.01, 19,350, Unavailable",
			XpEngine.money(12.345).equals("12.35") && XpEngine.money(0.125).equals("0.13") && XpEngine.money(1234.565).equals("1,234.57")
			&& XpEngine.money(6450.005).equals("6,450.01") && XpEngine.money(19350d).equals("19,350") && XpEngine.money(2.5).equals("2.5")
			&& XpEngine.money(null).equals("Unavailable"));
	}

	@SuppressWarnings("unchecked")
	private static void differential(XpEngine engine, Path ref) throws Exception {
		Object root = MiniJson.parse(new String(Files.readAllBytes(ref), StandardCharsets.UTF_8));
		List<Object> scenarios = MiniJson.list(root, "scenarios");
		int mismatched = 0, compared = 0;
		String first = null;
		for (Object s : scenarios) {
			Map<String, Object> m = (Map<String, Object>) s;
			String skill = (String) m.get("skill");
			FixtureSnapshot.Builder b = a();
			Object obs = m.get("skillObs");
			if (obs != null) {
				Object lv = ((Map<String, Object>) obs).get("level"), xp = ((Map<String, Object>) obs).get("xp");
				b.skill(skill.toLowerCase(), new SkillObservation(lv == null ? Evidence.unknown() : Evidence.verified(((Number) lv).intValue(), T0, "ref"),
					xp == null ? Evidence.unavailable() : Evidence.verified(((Number) xp).longValue(), T0, "ref")));
			}
			String bankState = (String) m.get("bankState");
			if (m.get("bank") != null) b.container(new ContainerObservation(Container.BANK, bankState.equals("current") ? ContainerState.CURRENT
				: ContainerState.HISTORICAL, T0, "ref", items(m.get("bank"))));
			if (m.get("vault") != null) b.container(new ContainerObservation(Container.SEED_VAULT, ContainerState.CURRENT, T0, "ref", items(m.get("vault"))));
			if (m.get("inventory") != null) b.container(new ContainerObservation(Container.INVENTORY, ContainerState.CURRENT, T0, "ref", items(m.get("inventory"))));
			if (m.get("bag") != null) b.container(new ContainerObservation(Container.LOOTING_BAG, ContainerState.CURRENT, T0, "ref", items(m.get("bag"))));
			Map<String, Object> op = (Map<String, Object>) m.get("options");
			XpEngine.Options o = new XpEngine.Options();
			o.skill = skill;
			o.cascade = (Boolean) op.get("cascade");
			o.respect = (Boolean) op.get("respect");
			o.limit = (Boolean) op.get("limit");
			o.boost = ((Number) op.get("boost")).intValue();
			for (Map.Entry<String, Object> e : ((Map<String, Object>) op.get("sources")).entrySet()) o.sources.put(e.getKey(), (Boolean) e.getValue());
			for (Map.Entry<String, Object> e : ((Map<String, Object>) op.get("choices")).entrySet()) o.choices.put(e.getKey(), (String) e.getValue());
			for (Object g : (List<Object>) op.get("ignored")) o.ignored.add((String) g);
			o.modifiers.put(skill, (String) op.get("modifier"));
			HashSet<Integer> pieces = new HashSet<>();
			for (Object p : (List<Object>) op.get("pieces")) pieces.add(((Number) p).intValue());
			o.pieces.put(skill, pieces);
			XpEngine.Result r = engine.calculate(b.build(), o);
			String diff = compare(r, (Map<String, Object>) m.get("expected"));
			compared++;
			if (diff != null) {
				mismatched++;
				if (first == null) first = skill + ": " + diff;
			}
		}
		check("differential: " + compared + " seeded scenarios match the frozen reference engine exactly" + (first == null ? "" : " (first: " + first + ")"),
			mismatched == 0 && compared == scenarios.size() && compared >= 600);
	}

	private static Map<Integer, Long> items(Object list) {
		Map<Integer, Long> m = new LinkedHashMap<>();
		for (Object i : (List<?>) list) m.merge((int) MiniJson.num(i, "id"), MiniJson.num(i, "quantity"), Long::sum);
		return m;
	}

	@SuppressWarnings("unchecked")
	private static String compare(XpEngine.Result r, Map<String, Object> x) {
		if (r.known != (Boolean) x.get("known") || r.complete != (Boolean) x.get("complete") || r.cyclic != (Boolean) x.get("cyclic")) return "flags";
		if (!same(r.currentXp, x.get("currentXp")) || !same(r.currentLevel, x.get("currentLevel"))) return "level/xp";
		if (!same(r.total, x.get("total")) || !same(r.projectedXp, x.get("projectedXp"))) return "total " + r.total + " vs " + x.get("total");
		List<Object> ex = (List<Object>) x.get("entries");
		if (ex.size() != r.entries.size()) return "entry count";
		for (int i = 0; i < ex.size(); i++) {
			Map<String, Object> e = (Map<String, Object>) ex.get(i);
			XpEngine.Entry a = r.entries.get(i);
			if (!a.key.equals(e.get("key"))) return "order";
			if (!same(a.chosen == null ? null : a.chosen.key, e.get("chosen"))) return a.key + " chosen";
			if (!same(a.original, e.get("original")) || !same(a.piped, e.get("piped")) || !same(a.runs, e.get("runs")) || !same(a.xp, e.get("xp")))
				return a.key + " values " + a.original + "/" + a.piped + "/" + a.runs + "/" + a.xp + " vs " + e.get("original") + "/" + e.get("piped") + "/" + e.get("runs") + "/" + e.get("xp");
			List<Object> needs = (List<Object>) e.get("needs");
			if (needs.size() != a.needs.size()) return a.key + " needs";
			for (int j = 0; j < needs.size(); j++) {
				List<Object> n = (List<Object>) needs.get(j);
				XpEngine.Need an = a.needs.get(j);
				if (!an.label.equals(n.get(0)) || !same(an.wanted, n.get(1)) || !same(an.available, n.get(2))) return a.key + " need " + an.label;
			}
		}
		return null;
	}

	private static boolean same(Object a, Object b) {
		if (a == null || b == null) return a == null && b == null;
		if (a instanceof Number && b instanceof Number) return Math.abs(((Number) a).doubleValue() - ((Number) b).doubleValue()) < 1e-6;
		return a.equals(b);
	}

	private static void check(String name, boolean ok) {
		checks++;
		if (!ok) failures++;
		System.out.println((ok ? "  PASS " : "  FAIL ") + name);
	}
}
