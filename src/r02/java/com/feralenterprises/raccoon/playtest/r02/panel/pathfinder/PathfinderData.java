package com.feralenterprises.raccoon.playtest.r02.panel.pathfinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lane D's verified progression inputs ({@code D/quest-and-pathfinder.json}): the 13 grouped Bow of Faerdhinen route
 * milestones and the 215-entry quest catalogue (name, kind, skill requirements, curated notes, dependencies, wiki).
 * Presentation and structure only: completion always comes from the scope's shared quest/skill/container evidence.
 */
public final class PathfinderData {
	/** One route milestone. */
	public static final class Node {
		public final String key, name, kind, detail;
		public final List<String> deps;

		Node(String key, String name, List<String> deps, String kind, String detail) {
			this.key = key;
			this.name = name;
			this.deps = deps;
			this.kind = kind;
			this.detail = detail;
		}
	}

	/** One quest catalogue entry. */
	public static final class Quest {
		public final int id;
		public final String name, kind, skills, why, next, wiki, slug;
		public final boolean curated;
		public final List<String> deps;

		Quest(Object o) {
			id = (int) MiniJson.num(o, "id");
			name = MiniJson.str(o, "name");
			kind = MiniJson.str(o, "kind");
			skills = blank(MiniJson.str(o, "skills"));
			why = blank(MiniJson.str(o, "why"));
			next = blank(MiniJson.str(o, "next"));
			wiki = blank(MiniJson.str(o, "wiki"));
			curated = Boolean.TRUE.equals(((Map<?, ?>) o).get("curated"));
			List<String> d = new ArrayList<>();
			for (Object x : MiniJson.list(o, "deps")) d.add(String.valueOf(x));
			deps = Collections.unmodifiableList(d);
			slug = slug(name);
		}

		private static String blank(String s) { return s == null || s.isEmpty() ? null : s; }
	}

	public final List<Node> nodes;
	public final List<Quest> quests;
	private final Map<String, Quest> bySlug = new LinkedHashMap<>(), byName = new LinkedHashMap<>();
	public final LaneInputs lane;

	private PathfinderData(LaneInputs lane) throws IOException {
		this.lane = lane;
		Object d = lane.json("D/quest-and-pathfinder.json");
		List<Node> ns = new ArrayList<>();
		for (Object o : MiniJson.list(d, "nodes")) {
			List<?> a = (List<?>) o;
			List<String> deps = new ArrayList<>();
			for (Object x : (List<?>) a.get(2)) deps.add(String.valueOf(x));
			ns.add(new Node(String.valueOf(a.get(0)), String.valueOf(a.get(1)), Collections.unmodifiableList(deps), String.valueOf(a.get(3)), String.valueOf(a.get(4))));
		}
		nodes = Collections.unmodifiableList(ns);
		List<Quest> qs = new ArrayList<>();
		for (Object o : MiniJson.list(d, "quests")) {
			Quest q = new Quest(o);
			qs.add(q);
			bySlug.put(q.slug, q);
			byName.put(q.name, q);
		}
		quests = Collections.unmodifiableList(qs);
		if (nodes.size() != 13 || quests.size() != 215) throw new IOException("Unexpected progression data " + nodes.size() + "/" + quests.size());
	}

	public static PathfinderData load(LaneInputs lane) throws IOException { return new PathfinderData(lane); }

	public Node node(String key) { return nodes.stream().filter(n -> n.key.equals(key)).findFirst().orElse(null); }

	public int index(String key) {
		for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).key.equals(key)) return i;
		return -1;
	}

	public Quest questBySlug(String slug) { return slug == null ? null : bySlug.get(slug); }

	public Quest quest(String name) { return byName.get(name); }

	/** Typed milestone ids for quests: lower case, apostrophes dropped, other runs of non-alphanumerics to '-'. */
	public static String slug(String name) {
		return name.toLowerCase(Locale.ROOT).replace("’", "").replace("'", "").replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}
}
