package com.feralenterprises.raccoon.ui.asset;

/**
 * Semantic keys for original R02 artwork. Components ask for a role ("frame stone"), never for a file. Every key is
 * PROVISIONAL: the bound review asset is pending individual owner approval and is NOT packaged in the framework.
 * The candidate filename records which kit asset the workbench binds; production binding is decided at approval.
 */
public enum ArtKey {
	FRAME_STONE_NINE_SLICE("stone_nine_slice.png", "Resizable stone frame atlas (24 px slices)"),
	DIVIDER_PAW("stone_paw_divider.png", "Section divider, paw"),
	DIVIDER_DIAMOND("PR-061_diamond_divider.png", "Section divider, diamond"),
	SURFACE_DARK_STONE("PR-030_dark_dungeon_stone.png", "Full-height base texture"),
	SURFACE_TIMBER("PR-032_rough_timber.png", "Header bar texture"),
	SURFACE_FIELDSTONE("PR-028_weathered_fieldstone.png", "Navigation rail texture"),
	SCENE_HEADER("PR-026_adventurer_camp.png", "Environmental header scene"),
	SCENE_FOOTER("SIG-03_burrow_entrance.png", "Environmental footer (Home burrow entrance)"),
	CHARACTER_BRAND("PR-001_signature_guard.png", "Raccoon brand character"),
	DECOR_TOP_RIGHT("PR-041_hanging_vines.png", "Transparent decoration, top edge"),
	DECOR_FOOT_LEFT("PR-040_moss_rocks.png", "Transparent decoration, footer left"),
	DECOR_FOOT_RIGHT("PR-046_grass_and_weeds.png", "Transparent decoration, footer right"),
	ICON_PREPOT("SIG-01_prepot_raccoon.png", "Action icon, Prepot"),
	ICON_BANK("SIG-02_raccoon_bank_chest.png", "Action icon, Bank");

	private final String candidateFile;
	private final String role;

	ArtKey(String candidateFile, String role) {
		this.candidateFile = candidateFile;
		this.role = role;
	}

	public String candidateFile() { return candidateFile; }
	public String role() { return role; }
}
