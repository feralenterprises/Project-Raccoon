package com.feralenterprises.raccoon.ui.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** PROVISIONAL immutable read model for the equipment component. */
public final class EquipmentView {
	private final DataState state;
	private final Map<EquipmentSlot, ItemStack> items;
	private final Evidence evidence;

	public EquipmentView(DataState state, Map<EquipmentSlot, ItemStack> items, Evidence evidence) {
		this.state = Objects.requireNonNull(state);
		EnumMap<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
		if (items != null) copy.putAll(items);
		this.items = Collections.unmodifiableMap(copy);
		this.evidence = evidence == null ? Evidence.NONE : evidence;
	}

	public static EquipmentView of(DataState state) { return new EquipmentView(state, null, Evidence.NONE); }

	public DataState state() { return state; }
	public Map<EquipmentSlot, ItemStack> items() { return items; }
	public ItemStack item(EquipmentSlot slot) { return state.showsData() ? items.get(slot) : null; }
	public Evidence evidence() { return evidence; }
}
