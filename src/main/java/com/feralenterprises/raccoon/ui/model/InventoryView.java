package com.feralenterprises.raccoon.ui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** PROVISIONAL immutable read model for the 28-slot inventory component. A null entry is an empty slot. */
public final class InventoryView {
	public static final int SLOTS = 28;

	private final DataState state;
	private final List<ItemStack> slots;
	private final Evidence evidence;

	public InventoryView(DataState state, List<ItemStack> slots, Evidence evidence) {
		this.state = Objects.requireNonNull(state);
		List<ItemStack> copy = new ArrayList<>(Collections.nCopies(SLOTS, (ItemStack) null));
		if (slots != null) {
			if (slots.size() > SLOTS) throw new IllegalArgumentException("more than 28 slots");
			for (int i = 0; i < slots.size(); i++) copy.set(i, slots.get(i));
		}
		this.slots = Collections.unmodifiableList(copy);
		this.evidence = evidence == null ? Evidence.NONE : evidence;
	}

	public static InventoryView of(DataState state) { return new InventoryView(state, null, Evidence.NONE); }

	public DataState state() { return state; }
	public List<ItemStack> slots() { return slots; }
	public ItemStack slot(int i) { return state.showsData() ? slots.get(i) : null; }
	public Evidence evidence() { return evidence; }
}
