package com.feralenterprises.raccoon.ui.asset;

/**
 * Semantic keys for authentic game interface sprites. The production provider fetches them at runtime with
 * {@code SpriteManager.getSpriteAsync(spriteId(), file(), ...)}; they are never bundled or redrawn.
 *
 * <p>Ids are checked against the pinned RuneLite source (runelite-parent-1.13.8, commit 8397ec71) by
 * {@code SpriteIdCheck}: each id must equal both the {@code net.runelite.api.gameval.SpriteID} constant and the
 * deprecated {@code net.runelite.api.SpriteID} constant named here. SOURCE_VERIFIED means the name and number
 * match the pinned source; the image itself has not been visually captured from a live client.
 */
public enum NativeSprite {
	EQUIP_HEAD(156, "Wornicons.HEAD", "EQUIPMENT_SLOT_HEAD"),
	EQUIP_CAPE(157, "Wornicons.CAPE", "EQUIPMENT_SLOT_CAPE"),
	EQUIP_NECK(158, "Wornicons.NECK", "EQUIPMENT_SLOT_NECK"),
	EQUIP_WEAPON(159, "Wornicons.WEAPON", "EQUIPMENT_SLOT_WEAPON"),
	EQUIP_RING(160, "Wornicons.RING", "EQUIPMENT_SLOT_RING"),
	EQUIP_BODY(161, "Wornicons.TORSO", "EQUIPMENT_SLOT_TORSO"),
	EQUIP_SHIELD(162, "Wornicons.SHIELD", "EQUIPMENT_SLOT_SHIELD"),
	EQUIP_LEGS(163, "Wornicons.LEGS", "EQUIPMENT_SLOT_LEGS"),
	EQUIP_HANDS(164, "Wornicons.HANDS", "EQUIPMENT_SLOT_HANDS"),
	EQUIP_FEET(165, "Wornicons.FEET", "EQUIPMENT_SLOT_FEET"),
	EQUIP_AMMO(166, "Wornicons.AMMUNITION", "EQUIPMENT_SLOT_AMMUNITION"),
	EQUIP_SLOT_TILE(170, "Miscgraphics.EQUIPMENT_SLOT_TILE", "EQUIPMENT_SLOT_TILE"),
	/** A bright red tile in the OSRS cache (rev243): a selection marker, not a hover highlight. */
	EQUIP_SLOT_SELECTED(179, "Miscgraphics.EQUIPMENT_SLOT_SELECTED", "EQUIPMENT_SLOT_SELECTED"),
	/**
	 * UNRESOLVED. UI-004 cache candidate: 173 (36x6, legacy IRON_RIVETS_HORIZONTAL) renders like the native connector,
	 * but which sprite the equipment interface uses needs the RuneLite widget inspector, so it is not adopted here.
	 */
	EQUIP_CONNECTOR_H(-1, null, null),
	/** UNRESOLVED. UI-004 cache candidate: 172 (6x36, legacy IRON_RIVETS_VERTICAL); see above. */
	EQUIP_CONNECTOR_V(-1, null, null),
	/**
	 * UNRESOLVED: legacy RS2_SIDE_PANEL_BACKGROUND = 14 has no matching gameval name, and UI-004 decoded id 14 from the
	 * OSRS cache: it is a 34x34 spell icon, so the legacy name is stale. The real background needs the widget inspector.
	 */
	INVENTORY_BACKGROUND(-1, null, null);

	private final int spriteId;
	private final String gamevalConstant;
	private final String legacyConstant;

	NativeSprite(int spriteId, String gamevalConstant, String legacyConstant) {
		this.spriteId = spriteId;
		this.gamevalConstant = gamevalConstant;
		this.legacyConstant = legacyConstant;
	}

	/** Sprite archive id, or -1 when unresolved (providers must return null). */
	public int spriteId() { return spriteId; }
	/** Sprite file index within the archive. */
	public int file() { return 0; }
	public boolean resolved() { return spriteId >= 0; }
	/** Nested constant under {@code net.runelite.api.gameval.SpriteID}, e.g. "Wornicons.HEAD"; null if unresolved. */
	public String gamevalConstant() { return gamevalConstant; }
	/** Constant under the deprecated {@code net.runelite.api.SpriteID}; null if unresolved. */
	public String legacyConstant() { return legacyConstant; }
}
