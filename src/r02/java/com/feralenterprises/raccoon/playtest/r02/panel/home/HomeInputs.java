package com.feralenterprises.raccoon.playtest.r02.panel.home;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Home's verified private inputs: the milestone and raw-tuna parameters ({@code A/home-and-tools.json}), saved-record
 * icon keys, and the exact frozen R02 rasters Home's cards draw (named by the SHA-256 the frozen
 * {@code Project-Raccoon-R02.html} embeds). The page environment and burrow-entrance footer art belong to the shared
 * {@code PageHost}. A raster that cannot be verified is null and is not drawn (decoration) or falls back to text.
 */
public final class HomeInputs {
	static final String STONE = "f59f1f64e06671e9bb7edd3ab32b6a2cff444d1df24a7850cecabc291a7e8b3f";
	/** {@code --wood}: byte-identical to frozen PR-032 rough timber. */
	static final String WOOD = "ca9f1e985729b635b4e7d6a39017da120f67d60c9dcd1cdb984824e3199bc135";
	static final String PLASTER = "ddfec5cd2ba812c9cecadf186dceb37fa100cd854e43505d9861701646bed6ee";
	static final String IVY_LEFT = "a694a3abe566e9c76cfe42884b18e9e6456c044f439ee3ae4a99f79b01223cb1";
	static final String OPPORTUNITIES_ICON = "cf28ec8ca0d4d9f27a99315c4b0d5051f91e37157a96f4e9d51ba0156a0b0802";
	static final String SNAPSHOT_ICON = "e495cfbeac54c4ea3a47e41769e6bae0dc298f5c9a134a030e9de5dd09da96ec";

	final String milestoneEncounter;
	final int milestoneTarget;
	final int tunaItem, tunaRequired;
	final long tunaXpTotal;
	/** {@code ivyLeft}: the frozen {@code .feature .foliage} hanging vines on the Next Opportunity card. */
	final BufferedImage stone, wood, plaster, ivyLeft, opportunitiesIcon, snapshotIcon;

	/** Frozen {@code --stone} and {@code --wood} (PR-032) textures, for other Lane A pages. */
	public BufferedImage stone() { return stone; }
	public BufferedImage wood() { return wood; }
	final LaneInputs lane;
	/** Frozen presentation icon key per account and saved-record id ({@code shared/saved-records.json}); display only. */
	private final java.util.Map<String, String> recordIcons = new java.util.HashMap<>();

	private HomeInputs(LaneInputs in) throws IOException {
		lane = in;
		Object home = in.json("A/home-and-tools.json");
		Object goal = MiniJson.obj(home, "scurriusMilestone"), tuna = MiniJson.obj(home, "rawTuna");
		milestoneEncounter = MiniJson.str(goal, "encounterKey");
		milestoneTarget = (int) MiniJson.num(goal, "target");
		tunaItem = (int) MiniJson.num(tuna, "itemId");
		tunaRequired = (int) MiniJson.num(tuna, "required");
		tunaXpTotal = MiniJson.num(tuna, "xpTotal");
		if (!Boolean.TRUE.equals(((java.util.Map<?, ?>) tuna).get("needsCurrentCompleteBank")))
			throw new IOException("raw tuna input no longer requires a current complete bank; review before use");
		stone = in.frozenArt(STONE);
		wood = in.frozenArt(WOOD);
		plaster = in.frozenArt(PLASTER);
		ivyLeft = in.frozenArt(IVY_LEFT);
		opportunitiesIcon = in.frozenArt(OPPORTUNITIES_ICON);
		snapshotIcon = in.frozenArt(SNAPSHOT_ICON);
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> saved = (java.util.Map<String, Object>) in.json("shared/saved-records.json");
		for (java.util.Map.Entry<String, Object> account : saved.entrySet())
			for (Object rec : (java.util.List<?>) account.getValue())
				recordIcons.put(account.getKey() + "/" + MiniJson.str(rec, "id"), MiniJson.str(rec, "icon"));
	}

	/** The frozen icon key ({@code vorkath.png}, {@code i379.png}, {@code s-cooking.png}) for this scope's record, or null. */
	String recordIcon(String account, String recordId) {
		return account == null || recordId == null ? null : recordIcons.get(account + "/" + recordId);
	}

	/** Resolves a frozen icon key through the shared asset resolver; null when unresolved (accessible fallback). */
	static java.awt.image.BufferedImage icon(com.feralenterprises.raccoon.playtest.r02.shared.R02Assets assets, String key) {
		if (key == null || !key.endsWith(".png")) return null;
		String k = key.substring(0, key.length() - 4);
		if (k.matches("i\\d+")) return assets.item(Integer.parseInt(k.substring(1)));
		if (k.startsWith("s-")) return assets.skillIcon(k.substring(2));
		return assets.portrait(k);
	}

	/** Loads off the EDT. */
	public static HomeInputs load(Path laneInputsZip) throws IOException { return new HomeInputs(LaneInputs.open(laneInputsZip)); }

	/** Loads from an already opened (verified) lane-input archive. */
	public static HomeInputs load(LaneInputs lane) throws IOException { return new HomeInputs(lane); }

	public LaneInputs lane() { return lane; }
}
