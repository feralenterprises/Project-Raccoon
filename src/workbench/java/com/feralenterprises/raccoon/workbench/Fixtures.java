package com.feralenterprises.raccoon.workbench;

import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.model.EquipmentSlot;
import com.feralenterprises.raccoon.ui.model.EquipmentView;
import com.feralenterprises.raccoon.ui.model.Evidence;
import com.feralenterprises.raccoon.ui.model.InventoryView;
import com.feralenterprises.raccoon.ui.model.ItemStack;
import com.feralenterprises.raccoon.ui.model.ProgressView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * FIXTURE read models. Synthetic example values only; no player account, bank or capture data. Item ids are public
 * game item ids; 1712 and 995 deliberately have no fixture image so the missing-image fallback is visible.
 */
public final class Fixtures {
	private Fixtures() { }

	public static final ItemStack HELM = new ItemStack(10828, 1, "Helm of neitiznot");
	public static final ItemStack FIRE_CAPE = new ItemStack(6570, 1, "Fire cape");
	public static final ItemStack GLORY = new ItemStack(1712, 1, "Amulet of glory(4)");
	public static final ItemStack SCIMITAR = new ItemStack(4587, 1, "Dragon scimitar");
	public static final ItemStack TORSO = new ItemStack(10551, 1, "Fighter torso");
	public static final ItemStack BOOTS = new ItemStack(11840, 1, "Dragon boots");
	public static final ItemStack SUPER_COMBAT = new ItemStack(12695, 1, "Super combat potion(4)");
	public static final ItemStack COINS = new ItemStack(995, 125_000, "Coins");

	public static Evidence evidence(DataState s) {
		switch (s) {
			case ACTIVE: return new Evidence("FIXTURE observation", "just now");
			case STALE: return new Evidence("FIXTURE observation", "3 h ago; may have changed");
			case LOADING: return new Evidence("Waiting for the game", "");
			case UNAVAILABLE: return new Evidence("Not available while signed out", "");
			default: return new Evidence("FIXTURE observation", "nothing equipped");
		}
	}

	public static EquipmentView equipment(DataState state, int loadout) {
		Map<EquipmentSlot, ItemStack> m = new EnumMap<>(EquipmentSlot.class);
		if (state.showsData()) {
			m.put(EquipmentSlot.HEAD, HELM);
			m.put(EquipmentSlot.CAPE, FIRE_CAPE);
			m.put(EquipmentSlot.NECK, GLORY);
			m.put(EquipmentSlot.BODY, TORSO);
			m.put(EquipmentSlot.FEET, BOOTS);
			if (loadout == 0) m.put(EquipmentSlot.WEAPON, SCIMITAR);
		}
		return new EquipmentView(state, m, evidence(state));
	}

	public static InventoryView inventory(DataState state, int loadout) {
		List<ItemStack> s = new ArrayList<>();
		if (state.showsData()) {
			for (int i = 0; i < 4 - loadout; i++) s.add(SUPER_COMBAT);
			s.add(COINS);
			if (loadout == 1) s.add(SCIMITAR);
		}
		return new InventoryView(state, s, evidence(state));
	}

	public static ProgressView progress(String label, long cur, long target, DataState s) {
		return new ProgressView(label, cur, target, s, evidence(s));
	}
}
