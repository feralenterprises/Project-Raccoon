package com.feralenterprises.raccoon.ui.model;

import com.feralenterprises.raccoon.ui.asset.NativeSprite;

/**
 * The 11 OSRS worn-equipment slots in the native 3x5 arrangement. {@code runeliteIndex} is the intended
 * EquipmentInventorySlot index (to be verified against the pinned RuneLite API before production use).
 */
public enum EquipmentSlot {
	HEAD(0, 1, "Head", "Head", 0, NativeSprite.EQUIP_HEAD),
	CAPE(1, 0, "Cape", "Cape", 1, NativeSprite.EQUIP_CAPE),
	NECK(1, 1, "Neck", "Neck", 2, NativeSprite.EQUIP_NECK),
	AMMO(1, 2, "Ammo", "Ammo", 13, NativeSprite.EQUIP_AMMO),
	WEAPON(2, 0, "Weapon", "Wpn", 3, NativeSprite.EQUIP_WEAPON),
	BODY(2, 1, "Body", "Body", 4, NativeSprite.EQUIP_BODY),
	SHIELD(2, 2, "Shield", "Shld", 5, NativeSprite.EQUIP_SHIELD),
	LEGS(3, 1, "Legs", "Legs", 7, NativeSprite.EQUIP_LEGS),
	HANDS(4, 0, "Hands", "Hand", 9, NativeSprite.EQUIP_HANDS),
	FEET(4, 1, "Feet", "Feet", 10, NativeSprite.EQUIP_FEET),
	RING(4, 2, "Ring", "Ring", 12, NativeSprite.EQUIP_RING);

	private final int row, col, runeliteIndex;
	private final String label, shortLabel;
	private final NativeSprite emptySprite;

	EquipmentSlot(int row, int col, String label, String shortLabel, int runeliteIndex, NativeSprite emptySprite) {
		this.row = row; this.col = col; this.label = label; this.shortLabel = shortLabel; this.runeliteIndex = runeliteIndex; this.emptySprite = emptySprite;
	}

	public int row() { return row; }
	public int col() { return col; }
	public String label() { return label; }
	/** Four-character label for the text fallback when the native slot icon is unavailable. */
	public String shortLabel() { return shortLabel; }
	public int runeliteIndex() { return runeliteIndex; }
	public NativeSprite emptySprite() { return emptySprite; }
}
