package com.feralenterprises.raccoon.playtest.r02.panel.xpstash;

import com.feralenterprises.raccoon.playtest.r02.panel.bank.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.bank.MiniJson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * XP Stash's verified inputs: the Burrow Banked Experience recipe catalogue ({@code C/xp-recipes.json}, 801 recipes
 * and cache keys), the modifier rules ({@code C/xp-modifiers.json}) and item names ({@code shared/item-names.json}).
 * Banked Experience is BSD-2-Clause ({@code C/BANKED-EXPERIENCE-BSD-2-CLAUSE.txt}).
 */
public final class XpData {
	/** A secondary ingredient. */
	static final class Secondary {
		final int id;
		final double qty;

		Secondary(int id, double qty) {
			this.id = id;
			this.qty = qty;
		}
	}

	/** One recipe (method) for an input group. */
	static final class Recipe {
		final String key, name, skill, input, specialType;
		final int level;
		final double xp;
		final boolean rng, byDose;
		final int[] ids, specialIds;
		final List<Secondary> secondaries;
		final Integer outputId;
		final double outputQty;

		@SuppressWarnings("unchecked")
		Recipe(Object o) {
			key = MiniJson.str(o, "key");
			name = MiniJson.str(o, "name");
			skill = MiniJson.str(o, "skill");
			input = MiniJson.str(o, "input");
			level = (int) MiniJson.num(o, "level");
			xp = ((Number) ((Map<String, Object>) o).get("xp")).doubleValue();
			rng = Boolean.TRUE.equals(((Map<String, Object>) o).get("rng"));
			byDose = Boolean.TRUE.equals(((Map<String, Object>) o).get("byDose"));
			ids = ints(MiniJson.list(o, "ids"));
			List<Secondary> sec = new ArrayList<>();
			for (Object s : MiniJson.list(o, "secondaries"))
				sec.add(new Secondary((int) MiniJson.num(s, "id"), ((Number) ((Map<String, Object>) s).get("qty")).doubleValue()));
			secondaries = Collections.unmodifiableList(sec);
			Object special = ((Map<String, Object>) o).get("special");
			specialType = special == null ? null : MiniJson.str(special, "type");
			specialIds = special == null ? null : ints(MiniJson.list(special, "ids"));
			Object out = ((Map<String, Object>) o).get("output");
			outputId = out == null ? null : (int) MiniJson.num(out, "id");
			outputQty = out == null ? 0 : ((Number) ((Map<String, Object>) out).get("qty")).doubleValue();
		}

		private static int[] ints(List<Object> l) {
			int[] a = new int[l.size()];
			for (int i = 0; i < a.length; i++) a[i] = ((Number) l.get(i)).intValue();
			return a;
		}
	}

	final List<Recipe> recipes;
	/** Input groups in catalogue order, keyed {@code skill|input}. */
	final Map<String, List<Recipe>> groups;
	final List<String> skills;
	final Set<String> bones, ashes, constructionBones, salvage, zealotsExcluded;
	final double zealotsSave;
	final double[] outfitXp;
	private final Map<Integer, String> itemNames = new HashMap<>(), cacheKeys = new HashMap<>();

	@SuppressWarnings("unchecked")
	private XpData(LaneInputs lane) throws IOException {
		Object data = lane.json("C/xp-recipes.json");
		List<Recipe> rs = new ArrayList<>();
		for (Object o : MiniJson.list(data, "recipes")) rs.add(new Recipe(o));
		recipes = Collections.unmodifiableList(rs);
		Map<String, List<Recipe>> g = new LinkedHashMap<>();
		for (Recipe r : rs) g.computeIfAbsent(r.skill + "|" + r.input, k -> new ArrayList<>()).add(r);
		groups = Collections.unmodifiableMap(g);
		Set<String> sk = new TreeSet<>();
		for (Recipe r : rs) sk.add(r.skill);
		skills = Collections.unmodifiableList(new ArrayList<>(sk));
		for (Map.Entry<String, Object> e : MiniJson.obj(data, "cacheKeys").entrySet()) cacheKeys.put(Integer.parseInt(e.getKey()), String.valueOf(e.getValue()));
		Map<String, Object> names = (Map<String, Object>) lane.json("shared/item-names.json");
		for (Map.Entry<String, Object> e : names.entrySet()) itemNames.put(Integer.parseInt(e.getKey()), String.valueOf(e.getValue()));
		Object m = lane.json("C/xp-modifiers.json");
		bones = set(MiniJson.list(m, "BONES"));
		ashes = set(MiniJson.list(m, "ASHES"));
		constructionBones = set(MiniJson.list(m, "CONSTRUCTION_BONES"));
		salvage = set(MiniJson.list(m, "SALVAGE"));
		zealotsExcluded = set(MiniJson.list(m, "ZEALOTS_EXCLUDED"));
		zealotsSave = ((Number) ((Map<String, Object>) m).get("zealotsSave")).doubleValue();
		List<Object> outfit = MiniJson.list(m, "outfitXp");
		outfitXp = new double[outfit.size()];
		for (int i = 0; i < outfitXp.length; i++) outfitXp[i] = ((Number) outfit.get(i)).doubleValue();
		if (recipes.size() != 801) throw new IOException("Unexpected recipe count " + recipes.size());
	}

	public static XpData load(LaneInputs lane) throws IOException { return new XpData(lane); }

	private static Set<String> set(List<Object> l) {
		Set<String> s = new HashSet<>();
		for (Object o : l) s.add(String.valueOf(o));
		return Collections.unmodifiableSet(s);
	}

	/** Frozen {@code displayID}: item name, else the humanised cache key, else "Item N". Never another item's name. */
	String displayId(int id) {
		String n = itemNames.get(id);
		if (n != null) return n;
		String k = cacheKeys.get(id);
		if (k != null) {
			String s = k.toLowerCase(Locale.ROOT).replace('_', ' ');
			return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
		}
		return "Item " + id;
	}

	List<Map.Entry<String, List<Recipe>>> groupsFor(String skill) {
		List<Map.Entry<String, List<Recipe>>> out = new ArrayList<>();
		for (Map.Entry<String, List<Recipe>> e : groups.entrySet()) if (e.getValue().get(0).skill.equals(skill)) out.add(e);
		return out;
	}
}
