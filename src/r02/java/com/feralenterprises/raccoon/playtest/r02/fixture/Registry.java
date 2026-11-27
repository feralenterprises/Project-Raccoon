package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The one shared item and encounter registry. Scope-independent; every panel resolves names through it so an item id
 * correction (D-09, D-15) reaches every view at once.
 */
public final class Registry {
	private final Map<Integer, ItemDef> items;
	private final Map<String, EncounterDef> encounters;

	public Registry(Collection<ItemDef> items, Collection<EncounterDef> encounters) {
		Map<Integer, ItemDef> i = new LinkedHashMap<>();
		for (ItemDef d : items) if (i.put(d.id(), d) != null) throw new IllegalArgumentException("duplicate item " + d.id());
		Map<String, EncounterDef> e = new LinkedHashMap<>();
		for (EncounterDef d : encounters) if (e.put(d.key(), d) != null) throw new IllegalArgumentException("duplicate encounter " + d.key());
		this.items = Collections.unmodifiableMap(i);
		this.encounters = Collections.unmodifiableMap(e);
	}

	public Optional<ItemDef> item(int id) { return Optional.ofNullable(items.get(id)); }
	public Optional<EncounterDef> encounter(String key) { return Optional.ofNullable(encounters.get(key)); }
	public Collection<ItemDef> items() { return items.values(); }
	/** Encounters in registry order. */
	public Collection<EncounterDef> encounters() { return encounters.values(); }
	/** Display name for an item id: the registry name, or "Item &lt;id&gt;" when unregistered (never a guess). */
	public String itemName(int id) { ItemDef d = items.get(id); return d != null ? d.name() : "Item " + id; }
}
