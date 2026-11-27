package com.feralenterprises.raccoon.playtest.r02.assembly;

import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.EncounterDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.ItemDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The frozen R02 datasets, read from the verified {@code Project-Raccoon-R02.html} of the frozen review package
 * (fail closed: the page must match the package SHA256SUMS.txt). Nothing is copied into the repository. Builds the
 * shared {@link Registry} and exposes the A/B scenario inputs the frozen observation authority uses; lanes can read
 * their own catalogs through {@link #literal} and {@link #jsonBlock}.
 *
 * <p>Registry corrections applied to the frozen names (D-09, D-15; checked against RuneLite gameval at 9af7b54):
 * {@link #NAME_CORRECTIONS}. Tradeability is UNKNOWN unless established; 10828 Helm of Neitiznot is TRADEABLE (OSRS
 * Wiki item infobox, checked 27 Nov 2026).
 */
public final class FrozenDataset {
	public static final String PAGE = "Project-Raccoon-R02.html";
	public static final ZoneId FIXTURE_ZONE = ZoneId.of("America/Los_Angeles");
	public static final Map<Integer, String> NAME_CORRECTIONS;
	static {
		Map<Integer, String> m = new LinkedHashMap<>();
		m.put(21512, "Bird house");
		m.put(1409, "Iban's staff");
		m.put(12494, "Ancient chaps");
		m.put(8013, "Teleport to house");
		m.put(153, "Fishing potion(2)");
		// Owner decision 27 Nov 2026: native item naming. The frozen data carries the wiki page title; the game name is
		// "Bullseye lantern" (OSRS Wiki infobox, ids 4548/4550). The native host re-checks every name against ItemManager.
		m.put(4550, "Bullseye lantern");
		NAME_CORRECTIONS = Collections.unmodifiableMap(m);
	}
	private static final String[] RANKS = {null, "Easy", "Medium", "Hard", "Elite", "Master"};
	/** The three Dagannoth Kings counts (D-18): one encounter, three official KC keys. */
	public static final List<String> DAGANNOTH_KC_KEYS = Collections.unmodifiableList(Arrays.asList("dagannoth-rex", "dagannoth-prime", "dagannoth-supreme"));

	/** One frozen bank catalogue row. */
	public static final class BankRow {
		public final int id;
		public final String category;
		public final long quantity;
		public final Long unitPrice;
		BankRow(int id, String category, long quantity, Long unitPrice) { this.id = id; this.category = category; this.quantity = quantity; this.unitPrice = unitPrice; }
	}

	private final String page;
	private final Map<String, String> jsonBlocks = new LinkedHashMap<>();
	private final Registry registry;
	private final List<BankRow> bank;
	private final Map<Integer, Long> bankSampleB;
	private final Map<String, Map<String, Integer>> officialKc;
	private final Map<String, List<ArchiveRecord>> archive;
	private final Map<String, Map<String, String>> archivedActivity;
	private final Map<String, String> questDemo;
	private final Map<String, List<String>> diaryDemo;
	private final List<String> routeQuests;
	private final Map<String, Integer> routeScenarios;
	private final Map<String, String> referencePetOwnership;
	private final List<String> questNames;
	private final List<String> diaryTiers;

	private FrozenDataset(String page) {
		this.page = page;
		Matcher m = Pattern.compile("<script type=\"application/json\" id=\"([^\"]+)\">(.*?)</script>", Pattern.DOTALL).matcher(page);
		while (m.find()) jsonBlocks.put(m.group(1), m.group(2));
		Map<String, Object> of = map(JsLiteral.parse(require(jsonBlocks.get("of-fixtures"), "of-fixtures")));
		Map<String, Object> model = map(JsLiteral.parse(require(jsonBlocks.get("r02-model-data"), "r02-model-data")));

		List<BankRow> rows = new ArrayList<>();
		for (Object o : list(literal("bankCatalog"))) {
			List<Object> r = list(o);
			rows.add(new BankRow(i(r.get(0)), (String) r.get(1), l(r.get(2)), r.get(3) == null ? null : l(r.get(3))));
		}
		bank = Collections.unmodifiableList(rows);
		// Frozen bankAuthority.sampleFor(): account B holds five of the catalogue items in these quantities.
		Map<Integer, Long> b = new LinkedHashMap<>();
		b.put(995, 42_500L); b.put(385, 12L); b.put(1511, 85L); b.put(1333, 1L); b.put(952, 1L);
		bankSampleB = Collections.unmodifiableMap(b);

		registry = buildRegistry(of, rows);
		officialKc = nested(map(literal("bcOfficialKcFixtures")));
		archivedActivity = new LinkedHashMap<>();
		map(literal("archivedActivity")).forEach((acct, v) -> {
			Map<String, String> x = new LinkedHashMap<>();
			map(v).forEach((k, id) -> x.put(k, (String) id));
			archivedActivity.put(acct, Collections.unmodifiableMap(x));
		});
		Map<String, List<ArchiveRecord>> arch = new LinkedHashMap<>();
		arch.put("A", records(list(literal("ffA")), archivedActivity.getOrDefault("A", Collections.emptyMap())));
		arch.put("B", records(list(literal("ffB")), archivedActivity.getOrDefault("B", Collections.emptyMap())));
		archive = Collections.unmodifiableMap(arch);
		Map<String, String> qd = new LinkedHashMap<>();
		map(literal("qdQuestDemo")).forEach((k, v) -> qd.put(k, (String) v));
		questDemo = Collections.unmodifiableMap(qd);
		Map<String, List<String>> dd = new LinkedHashMap<>();
		map(literal("qdDiaryDemo")).forEach((tier, regions) -> {
			List<String> rs = new ArrayList<>();
			for (Object r : list(regions)) rs.add((String) r);
			dd.put(tier, Collections.unmodifiableList(rs));
		});
		diaryDemo = Collections.unmodifiableMap(dd);
		List<String> route = new ArrayList<>();
		for (Object n : list(literal("pfNodes"))) { List<Object> node = list(n); route.add("quest".equals(node.get(3)) ? (String) node.get(1) : null); }
		routeQuests = Collections.unmodifiableList(route);
		Map<String, Integer> sc = new LinkedHashMap<>();
		map(literal("pfScenarios")).forEach((k, v) -> sc.put(k, v == null ? null : i(v)));
		routeScenarios = Collections.unmodifiableMap(sc);
		Map<String, String> pets = new LinkedHashMap<>();
		map(model.get("referencePetOwnership")).forEach((k, v) -> pets.put(k, (String) v));
		referencePetOwnership = Collections.unmodifiableMap(pets);
		List<String> qn = new ArrayList<>();
		for (Object q : list(literal("qdCatalog"))) qn.add((String) map(q).get("name"));
		questNames = Collections.unmodifiableList(qn);
		diaryTiers = Collections.unmodifiableList(new ArrayList<>(map(model.get("diaries")).keySet()));
	}

	/** Loads and verifies the frozen page from an extracted package root. */
	public static FrozenDataset load(Path packageRoot) throws IOException {
		return new FrozenDataset(new PackageFiles(packageRoot).text(PAGE));
	}

	/** Raw parsed literal of a frozen declaration ({@code const NAME = ...}) for lane-owned catalogs. */
	public Object literal(String name) { return JsLiteral.declaration(page, name); }
	/** Raw JSON text of a frozen {@code <script type="application/json" id=...>} block, or null. */
	public String jsonBlock(String id) { return jsonBlocks.get(id); }

	public Registry registry() { return registry; }
	public List<BankRow> bankCatalog() { return bank; }
	public Map<Integer, Long> bankSampleB() { return bankSampleB; }
	/** Frozen official KC by account and kc key ({@code bcOfficialKcFixtures}). */
	public Map<String, Integer> officialKc(String account) { return officialKc.getOrDefault(account, Collections.emptyMap()); }
	public List<ArchiveRecord> archive(String account) { return archive.getOrDefault(account, Collections.emptyList()); }
	/** Current Activity key to archived record id, per account ({@code archivedActivity}). */
	public Map<String, String> archivedActivity(String account) { return archivedActivity.getOrDefault(account, Collections.emptyMap()); }
	public Map<String, String> questDemo() { return questDemo; }
	public Map<String, List<String>> diaryDemo() { return diaryDemo; }
	/** Pathfinder node order; quest nodes give the quest name, other nodes null. */
	public List<String> routeQuests() { return routeQuests; }
	/** Pathfinder scenario to completed-milestone step (null = unknown). */
	public Map<String, Integer> routeScenarios() { return routeScenarios; }
	public Map<String, String> referencePetOwnership() { return referencePetOwnership; }
	/** The 215 frozen quest names in prepared order. */
	public List<String> questNames() { return questNames; }
	/** The 48 frozen diary tiers ("Ardougne Easy"). */
	public List<String> diaryTiers() { return diaryTiers; }

	// ---- registry ---------------------------------------------------------------------------------------------

	private Registry buildRegistry(Map<String, Object> of, List<BankRow> rows) {
		Map<Integer, String> names = new LinkedHashMap<>();
		map(of.get("items")).forEach((k, v) -> names.put(Integer.valueOf(k), (String) v));
		names.putIfAbsent(1511, "Logs"); // frozen bankCatalog fallback name
		Map<Integer, BankRow> byId = new LinkedHashMap<>();
		for (BankRow r : rows) byId.put(r.id, r);
		for (Object rec : list(literal("ffA"))) collectNames(rec, names);
		for (Object rec : list(literal("ffB"))) collectNames(rec, names);
		names.putAll(NAME_CORRECTIONS);
		List<ItemDef> items = new ArrayList<>();
		for (Map.Entry<Integer, String> e : names.entrySet()) {
			BankRow r = byId.get(e.getKey());
			ItemDef.Tradeability t = e.getKey() == 10828 ? ItemDef.Tradeability.TRADEABLE : ItemDef.Tradeability.UNKNOWN;
			items.add(new ItemDef(e.getKey(), e.getValue(), t, r == null ? "Unclassified" : r.category, r == null ? null : r.unitPrice));
		}
		Map<String, Object> ranks = map(literal("bcRanks"));
		List<EncounterDef> enc = new ArrayList<>();
		for (Object o : list(of.get("registry"))) {
			Map<String, Object> e = map(o);
			String key = (String) e.get("key");
			Object rank = ranks.get(key);
			String difficulty = rank == null ? null : RANKS[i(rank)];
			List<String> kc = key.equals("dagannoth-kings") ? DAGANNOTH_KC_KEYS : Collections.singletonList(key);
			enc.add(new EncounterDef(key, (String) e.get("name"), (String) e.get("group"), difficulty, kc));
		}
		return new Registry(items, enc);
	}

	private static void collectNames(Object rec, Map<Integer, String> names) {
		JsLiteral.Call c = (JsLiteral.Call) rec;
		for (int a : new int[] {10, 11}) {
			Object ledger = c.arg(a);
			if (ledger == null) continue;
			for (Object it : list(ledger)) {
				JsLiteral.Call item = (JsLiteral.Call) it;
				if (item.arg(2) != null) names.putIfAbsent(i(item.arg(0)), (String) item.arg(2));
			}
		}
	}

	// ---- archive (frozen ffRecord(id,title,kind,icon,date,active,elapsed,xp,kills,deaths,loot,supplies,extra)) -----

	private List<ArchiveRecord> records(List<Object> calls, Map<String, String> activityToRecord) {
		Map<String, String> recordToActivity = new LinkedHashMap<>();
		activityToRecord.forEach((act, rec) -> recordToActivity.put(rec, act));
		List<ArchiveRecord> out = new ArrayList<>();
		for (Object o : calls) {
			JsLiteral.Call c = (JsLiteral.Call) o;
			if (!c.name.equals("ffRecord")) throw new IllegalArgumentException("unexpected " + c.name);
			String id = (String) c.arg(0), title = (String) c.arg(1);
			ArchiveRecord.Kind kind = ArchiveRecord.Kind.valueOf(((String) c.arg(2)).toUpperCase(Locale.ROOT));
			String icon = (String) c.arg(3);
			Instant at = fixtureTime((String) c.arg(4));
			Map<String, Object> extra = c.arg(12) == null ? Collections.emptyMap() : map(c.arg(12));
			String src = "frozen archive record " + id;
			String encounter = null;
			if (kind == ArchiveRecord.Kind.COMBAT && icon.endsWith(".png")) {
				String k = icon.substring(0, icon.length() - 4);
				if (registry.encounter(k).isPresent()) encounter = k;
			}
			Integer kills = c.arg(8) == null ? null : i(c.arg(8));
			Object metric = extra.get("metricCount");
			Evidence<Integer> count = kills != null ? Evidence.count(kills, at, src) : metric != null ? Evidence.count(i(metric), at, src) : Evidence.unknown();
			String unit = extra.get("countLabel") != null ? (String) extra.get("countLabel")
				: kills != null ? "Kills" : extra.get("metricLabel") != null ? (String) extra.get("metricLabel") : "Count";
			out.add(new ArchiveRecord(id, recordToActivity.get(id), encounter, title, kind, at,
				minutes(c.arg(5), at, src), minutes(c.arg(6), at, src), count,
				c.arg(9) == null ? Evidence.unknown() : Evidence.count(i(c.arg(9)), at, src), unit,
				c.arg(7) == null ? Evidence.unknown() : Evidence.verified(l(c.arg(7)), at, src),
				ledger(c.arg(10), at, src), ledger(c.arg(11), at, src), (String) extra.get("notes")));
		}
		return Collections.unmodifiableList(out);
	}

	private static Evidence<Duration> minutes(Object v, Instant at, String src) {
		return v == null ? Evidence.unknown() : Evidence.verified(Duration.ofMinutes(l(v)), at, src);
	}

	private static Evidence<Map<Integer, Long>> ledger(Object v, Instant at, String src) {
		if (v == null) return Evidence.unavailable();
		Map<Integer, Long> m = new LinkedHashMap<>();
		for (Object it : list(v)) { JsLiteral.Call c = (JsLiteral.Call) it; m.merge(i(c.arg(0)), l(c.arg(1)), Long::sum); }
		return m.isEmpty() ? Evidence.absent(at, src) : Evidence.verified(m, at, src);
	}

	/** Frozen archive time ("26 Nov · 15:42", year 2026, Pacific). */
	static Instant fixtureTime(String s) {
		String t = s.replace('·', ' ').replaceAll("\\s+", " ").trim();
		LocalDateTime d = LocalDateTime.parse("2026 " + t, DateTimeFormatter.ofPattern("yyyy d MMM HH:mm", Locale.ENGLISH));
		return d.atZone(FIXTURE_ZONE).toInstant();
	}

	/** Frozen bank capture times (bankFixtureTimes: current 18:15, historical 16:42 PST, 26 Nov 2026). */
	public static Instant bankTime(boolean historical) { return fixtureTime(historical ? "26 Nov · 16:42" : "26 Nov · 18:15"); }

	// ---- literal helpers ---------------------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	static Map<String, Object> map(Object o) { if (!(o instanceof Map)) throw new IllegalArgumentException("object expected"); return (Map<String, Object>) o; }
	@SuppressWarnings("unchecked")
	static List<Object> list(Object o) { if (!(o instanceof List)) throw new IllegalArgumentException("array expected"); return (List<Object>) o; }
	static int i(Object o) {
		double d = (Double) o;
		if (d != Math.rint(d) || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) throw new IllegalArgumentException("integer expected: " + o);
		return (int) d;
	}
	static long l(Object o) {
		double d = (Double) o;
		if (d != Math.rint(d)) throw new IllegalArgumentException("integer expected: " + o);
		return (long) d;
	}
	private static <T> T require(T v, String what) { if (v == null) throw new IllegalArgumentException("frozen page has no " + what); return v; }

	private static Map<String, Map<String, Integer>> nested(Map<String, Object> m) {
		Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
		m.forEach((k, v) -> {
			Map<String, Integer> x = new LinkedHashMap<>();
			map(v).forEach((kk, vv) -> x.put(kk, i(vv)));
			out.put(k, Collections.unmodifiableMap(x));
		});
		return Collections.unmodifiableMap(out);
	}
}
