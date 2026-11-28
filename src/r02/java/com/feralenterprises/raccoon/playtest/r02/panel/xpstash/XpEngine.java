package com.feralenterprises.raccoon.playtest.r02.panel.xpstash;

import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.EvidenceStatus;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.SkillObservation;
import com.feralenterprises.raccoon.playtest.r02.panel.xpstash.XpData.Recipe;
import com.feralenterprises.raccoon.playtest.r02.panel.xpstash.XpData.Secondary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;

/**
 * Port of the frozen R02 XP Stash engine ({@code C/reference-xp.js}, a projection of Burrow's Banked Experience):
 * input groups, level-gated method choice, linked-output cascade in topological order, optional secondary limits and
 * Prayer/Sailing/outfit modifiers. Account evidence comes only from the scope's shared snapshot: exact XP is used only
 * when observed (a verified level never invents exact XP); resource quantities only from observed containers.
 */
final class XpEngine {
	/** Page-local options (frozen {@code defaults()}); reset with the page on a scope change. */
	static final class Options {
		String skill = "Fletching", search = "";
		boolean cascade = true, respect = true, limit = false, secondaries = false, catalog = false;
		final Map<String, Boolean> sources = new LinkedHashMap<>();
		int boost = 0;
		final Map<String, String> choices = new HashMap<>(), modifiers = new HashMap<>();
		final Set<String> ignored = new HashSet<>();
		final Map<String, Set<Integer>> pieces = new HashMap<>();

		Options() {
			sources.put("bank", true);
			sources.put("vault", true);
			sources.put("inventory", false);
			sources.put("bag", false);
		}
	}

	/** A secondary requirement line. */
	static final class Need {
		final String label;
		final long wanted;
		final Long available;

		Need(String label, long wanted, Long available) {
			this.label = label;
			this.wanted = wanted;
			this.available = available;
		}
	}

	/** One input group's computed state. */
	static final class Entry {
		final String key;
		final Recipe items;
		final List<Recipe> all, eligible;
		final Recipe chosen;
		final long original;
		final boolean ignored;
		long piped, runs;
		double xp;
		final List<Need> needs = new ArrayList<>();

		Entry(String key, List<Recipe> all, List<Recipe> eligible, Recipe chosen, long original, boolean ignored) {
			this.key = key;
			this.items = all.get(0);
			this.all = all;
			this.eligible = eligible;
			this.chosen = chosen;
			this.original = original;
			this.ignored = ignored;
		}
	}

	/** A calculation result. */
	static final class Result {
		final List<Entry> entries;
		final boolean known, complete, cyclic, bankCurrent;
		final Long currentXp;
		final Integer currentLevel;
		final Double total, projectedXp;

		Result(List<Entry> entries, boolean known, boolean complete, boolean cyclic, boolean bankCurrent, Long currentXp, Integer currentLevel,
			Double total, Double projectedXp) {
			this.entries = entries;
			this.known = known;
			this.complete = complete;
			this.cyclic = cyclic;
			this.bankCurrent = bankCurrent;
			this.currentXp = currentXp;
			this.currentLevel = currentLevel;
			this.total = total;
			this.projectedXp = projectedXp;
		}

		Integer projectedLevel() { return projectedXp == null ? null : level(projectedXp); }
	}

	private final XpData data;

	XpEngine(XpData data) { this.data = data; }

	// ------------------------------------------------------------------ level table

	/** Frozen {@code threshold}: XP for a level. */
	static long threshold(int l) {
		long sum = 0;
		for (int i = 1; i <= l - 1; i++) sum += (long) Math.floor(i + 300 * Math.pow(2, i / 7.0));
		return (long) Math.floor(sum / 4.0);
	}

	static int level(double xp) {
		int i = 1;
		while (i < 99 && threshold(i + 1) <= xp) i++;
		return i;
	}

	// ------------------------------------------------------------------ evidence

	/** Observed items of a source, or null when unavailable (bank: current or last complete capture). */
	static Map<Integer, Long> source(FixtureSnapshot s, String key) {
		if (!s.scope().signedIn()) return null;
		Container c = key.equals("bank") ? Container.BANK : key.equals("vault") ? Container.SEED_VAULT
			: key.equals("inventory") ? Container.INVENTORY : Container.LOOTING_BAG;
		ContainerObservation o = s.container(c);
		if (o == null) return null;
		if (o.state() == ContainerState.CURRENT || o.state() == ContainerState.HISTORICAL) return o.items();
		// Frozen read.bank.snapshot: an incomplete bank keeps only the earlier complete capture it retains (saved).
		if (c == Container.BANK && o.state() == ContainerState.INCOMPLETE && o.retained() != null) return o.retained().items();
		return null; // incomplete (nothing retained) or unavailable: no quantities may be claimed
	}

	static boolean bankCurrent(FixtureSnapshot s) {
		ContainerObservation o = s.scope().signedIn() ? s.container(Container.BANK) : null;
		return o != null && o.state() == ContainerState.CURRENT;
	}

	static String sourceStatus(FixtureSnapshot s, String key) {
		if (source(s, key) == null) return "unavailable";
		return key.equals("bank") && !bankCurrent(s) ? "saved" : "observed";
	}

	// ------------------------------------------------------------------ modifiers

	double gain(Recipe r, Options o) {
		String mode = o.modifiers.get(r.skill);
		Set<Integer> pieces = o.pieces.getOrDefault(r.skill, new HashSet<>());
		double outfit = 0;
		if (pieces.size() == 4) outfit = data.outfitXp[4];
		else for (int p : pieces) outfit += data.outfitXp[p];
		double value = r.xp, consumption = 1;
		if ((r.skill.equals("Farming") || r.skill.equals("Construction") || r.skill.equals("Firemaking")) && !data.constructionBones.contains(r.key))
			value *= 1 + outfit;
		if (r.skill.equals("Sailing") && "lure".equals(mode) && data.salvage.contains(r.key)) value *= 1.025;
		if (r.skill.equals("Prayer")) {
			boolean bone = data.bones.contains(r.key), ash = data.ashes.contains(r.key);
			if (bone) {
				if ("sinister".equals(mode)) value *= 3;
				if ("gilded".equals(mode) || "wild".equals(mode)) value *= 3.5;
				if ("ecto".equals(mode) && !r.key.equals("STRYKEWYRM_BONES_BURY")) value *= 4;
				if ("wild".equals(mode)) consumption *= 0.5;
			}
			if (ash && "demonic".equals(mode)) value *= 3;
			if (!data.zealotsExcluded.contains(r.key)) consumption *= 1 - data.zealotsSave * pieces.size();
		}
		return Math.round(value / consumption * 100) / 100.0;
	}

	// ------------------------------------------------------------------ secondaries

	private static final class Spec {
		final int[] ids, weights;
		final LongUnaryOperator need;
		final String label;

		Spec(int[] ids, int[] weights, LongUnaryOperator need, String label) {
			this.ids = ids;
			this.weights = weights;
			this.need = need;
			this.label = label;
		}

		int weight(int i) { return weights == null ? 1 : weights[i]; }
	}

	private List<Spec> specs(Recipe r) {
		List<Spec> out = new ArrayList<>();
		if ("degrime".equals(r.specialType)) {
			out.add(new Spec(r.specialIds, null, n -> (long) Math.ceil(n / 27.0) * 2, data.displayId(r.specialIds[0])));
			return out;
		}
		if ("dose".equals(r.specialType)) {
			int[] w = new int[r.specialIds.length];
			for (int i = 0; i < w.length; i++) w[i] = i + 1;
			out.add(new Spec(r.specialIds, w, n -> n, data.displayId(r.specialIds[0]) + " doses"));
			return out;
		}
		if ("crush".equals(r.specialType)) {
			out.add(new Spec(r.specialIds, null, n -> n, data.displayId(r.specialIds[r.specialIds.length - 1])));
			return out;
		}
		for (Secondary s : r.secondaries) out.add(new Spec(new int[] {s.id}, null, n -> (long) Math.ceil(n * s.qty - 1e-9), data.displayId(s.id)));
		return out;
	}

	// ------------------------------------------------------------------ calculation

	Result calculate(FixtureSnapshot s, Options o) {
		SkillObservation skill = s.scope().signedIn() ? s.skill(o.skill.toLowerCase(Locale.ROOT)) : SkillObservation.unobserved();
		Long currentXp = skill.exactXp().hasValue() ? skill.exactXp().value() : null;
		EvidenceStatus ls = skill.level().status();
		Integer currentLevel = ls == EvidenceStatus.VERIFIED || ls == EvidenceStatus.VERIFIED_ZERO ? skill.level().value()
			: currentXp == null ? null : level(currentXp);

		List<Map<Integer, Long>> available = new ArrayList<>();
		for (Map.Entry<String, Boolean> e : o.sources.entrySet()) if (e.getValue()) available.add(source(s, e.getKey()));
		boolean known = available.stream().anyMatch(x -> x != null);
		boolean complete = !available.isEmpty() && available.stream().allMatch(x -> x != null);
		Map<Integer, Long> balances = new HashMap<>();
		for (Map<Integer, Long> items : available)
			if (items != null) for (Map.Entry<Integer, Long> i : items.entrySet()) balances.merge(i.getKey(), i.getValue(), Long::sum);
		Map<Integer, Long> secondaryBalance = new HashMap<>(balances);

		List<Entry> entries = new ArrayList<>();
		for (Map.Entry<String, List<Recipe>> g : data.groupsFor(o.skill)) {
			List<Recipe> all = g.getValue();
			Recipe items = all.get(0);
			boolean levelKnown = o.respect && currentLevel != null;
			List<Recipe> eligible = new ArrayList<>();
			for (Recipe r : all) if (!levelKnown || r.level <= currentLevel + o.boost) eligible.add(r);
			String choice = o.choices.get(g.getKey());
			Recipe chosen = eligible.stream().filter(r -> r.key.equals(choice)).findFirst().orElse(eligible.isEmpty() ? null : eligible.get(0));
			long original = 0;
			for (int i = 0; i < items.ids.length; i++) original += balances.getOrDefault(items.ids[i], 0L) * (items.byDose ? i + 1 : 1);
			entries.add(new Entry(g.getKey(), all, eligible, chosen, original, o.ignored.contains(g.getKey())));
		}

		Map<Integer, Entry> selected = new HashMap<>();
		for (Entry e : entries) if (!e.ignored && e.chosen != null) for (int id : e.items.ids) selected.put(id, e);
		Map<Entry, Entry> targets = new IdentityHashMap<>();
		Map<Entry, Integer> indegree = new IdentityHashMap<>();
		for (Entry e : entries) indegree.put(e, 0);
		if (o.cascade) for (Entry e : entries) {
			Entry target = e.chosen == null || e.chosen.outputId == null ? null : selected.get(e.chosen.outputId);
			if (!e.ignored && target != null && target != e) {
				targets.put(e, target);
				indegree.put(target, indegree.get(target) + 1);
			}
		}
		List<Entry> ready = new ArrayList<>();
		for (Entry e : entries) if (indegree.get(e) == 0) ready.add(e);
		Set<Entry> done = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		for (int i = 0; i < ready.size(); i++) {
			Entry e = ready.get(i);
			process(e, true, o, complete, known, secondaryBalance, targets, done);
			Entry t = targets.get(e);
			if (t != null) {
				indegree.put(t, indegree.get(t) - 1);
				if (indegree.get(t) == 0) ready.add(t);
			}
		}
		for (Entry e : entries) if (!done.contains(e)) process(e, false, o, complete, known, secondaryBalance, targets, done);
		boolean cyclic = ready.size() != entries.size();
		Double total = known ? entries.stream().mapToDouble(e -> e.xp).sum() : null;
		Double projected = total != null && currentXp != null ? Math.min(200_000_000d, currentXp + total) : null;
		return new Result(entries, known, complete, cyclic, bankCurrent(s), currentXp, currentLevel, total, projected);
	}

	private void process(Entry e, boolean allowOutput, Options o, boolean complete, boolean known, Map<Integer, Long> balance,
		Map<Entry, Entry> targets, Set<Entry> done) {
		Recipe r = e.chosen;
		if (r != null && !e.ignored) {
			long potential = Math.max(0, (long) Math.floor(e.original + e.piped));
			long runs = potential;
			List<Spec> specs = specs(r);
			for (Spec spec : specs) {
				long avail = 0;
				for (int i = 0; i < spec.ids.length; i++) avail += balance.getOrDefault(spec.ids[i], 0L) * spec.weight(i);
				if (o.limit && complete && spec.need.applyAsLong(runs) > avail) {
					long lo = 0, hi = runs;
					while (lo < hi) {
						long mid = (lo + hi + 1) / 2;
						if (spec.need.applyAsLong(mid) <= avail) lo = mid;
						else hi = mid - 1;
					}
					runs = lo;
				}
				e.needs.add(new Need(spec.label, spec.need.applyAsLong(potential), known ? avail : null));
			}
			if (o.limit && complete) for (Spec spec : specs) {
				long used = spec.need.applyAsLong(runs);
				for (int i = 0; i < spec.ids.length; i++) {
					long bal = balance.getOrDefault(spec.ids[i], 0L);
					int w = spec.weight(i);
					long deduct = Math.min(bal, (long) Math.ceil(used / (double) w));
					balance.put(spec.ids[i], bal - deduct);
					used = Math.max(0, used - deduct * w);
				}
			}
			e.runs = runs;
			e.xp = runs * gain(r, o);
			Entry t = targets.get(e);
			if (t != null && allowOutput) t.piped += (long) (runs * r.outputQty);
		}
		done.add(e);
	}

	/** Frozen list filter: observed (or linked) resources, or the whole catalogue; search over names. */
	List<Entry> visible(Result res, Options o) {
		String q = o.search.trim().toLowerCase(Locale.ROOT);
		List<Entry> out = new ArrayList<>();
		for (Entry e : res.entries) {
			boolean available = e.original > 0 || e.piped > 0;
			if (!o.catalog && !available) continue;
			boolean match = q.isEmpty() || data.displayId(e.items.ids[0]).toLowerCase(Locale.ROOT).contains(q)
				|| e.all.stream().anyMatch(r -> r.name.toLowerCase(Locale.ROOT).contains(q));
			if (match) out.add(e);
		}
		return out;
	}

	/** Frozen projection warning, or null. */
	static String warning(Result r, Options o) {
		if (!r.known) return "Resource observations unavailable.";
		if (!r.complete) return "Estimate covers observed sources only.";
		if (!r.bankCurrent && Boolean.TRUE.equals(o.sources.get("bank"))) return "Bank quantities are from the last complete capture.";
		return null;
	}

	/** Frozen {@code money}: up to two decimals, grouped; null is Unavailable. */
	static String money(Number n) {
		if (n == null) return "Unavailable";
		java.text.DecimalFormat f = new java.text.DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols.getInstance(Locale.US));
		f.setRoundingMode(java.math.RoundingMode.HALF_UP); // matches the frozen toLocaleString (half away from zero)
		return f.format(n.doubleValue());
	}
}
