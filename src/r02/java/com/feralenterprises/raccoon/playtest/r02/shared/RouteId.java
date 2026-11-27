package com.feralenterprises.raccoon.playtest.r02.shared;

import com.feralenterprises.raccoon.ui.shell.Destination;
import java.util.Locale;

/**
 * The 14 R02 routes (dispatch Assignment 0). Declaration order of the 12 rail routes is the frozen rail order of
 * {@code Project-Raccoon-R02.html}. Tools has no rail entry (it opens from Settings, Interface); Settings is the header
 * utility. Labels are the frozen panel titles; {@link #railName} is the frozen rail accessible name.
 */
public enum RouteId {
	HOME("home", "Home", "Home", "HO"),
	PATHFINDER("pathfinder", "Pathfinder", "Pathfinder", "PF"),
	OUTFIT("outfit", "Outfitter", "Outfitter", "OF"),
	ACTIVITY("activity", "Current Activity", "Current Activity", "CA"),
	FILES("files", "Feral Files", "Feral Files", "FF"),
	BANK("bank", "Bank Memory", "Bank Memory", "BM"),
	QUESTS("quests", "Quests & Diaries", "Quests", "QD"),
	CHRONICLE("chronicle", "Boss Chronicle", "Boss Chronicle", "BC"),
	BOUNTIES("bounties", "Bandit’s Bounties", "Bandit’s Bounties", "BB"),
	FRIENDS("friends", "Feral Friends", "Feral Friends", "FR"),
	CAPTURE("capture", "Account Capture", "Account Capture", "AC"),
	XPSTASH("xpstash", "XP Stash", "XP Stash", "XP"),
	TOOLS("tools", "Tools", "Tools", "TL"),
	SETTINGS("settings", "Settings", "Settings", "SE");

	private final String id, label, railName, monogram;

	RouteId(String id, String label, String railName, String monogram) {
		this.id = id;
		this.label = label;
		this.railName = railName;
		this.monogram = monogram;
	}

	public String id() { return id; }
	public String label() { return label; }
	public String railName() { return railName; }
	public String monogram() { return monogram; }
	public boolean inRail() { return this != TOOLS && this != SETTINGS; }

	/** The framework destination. Every R02 route is account-scoped so a scope change discards its page state. */
	public Destination destination() {
		if (this == SETTINGS) return new Destination(id, label, monogram, Destination.Group.UTILITY, false, true);
		return new Destination(id, label, monogram, Destination.Group.PRODUCT, false, true, inRail());
	}

	public static RouteId of(String id) {
		for (RouteId r : values()) if (r.id.equals(id)) return r;
		throw new IllegalArgumentException("unknown R02 route " + id);
	}

	@Override
	public String toString() { return id.toLowerCase(Locale.ROOT); }
}
