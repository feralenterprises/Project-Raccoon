package com.feralenterprises.raccoon.playtest.r02.shared;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** The five implementation lanes and their exclusive routes (dispatch ownership contract). */
public enum Lane {
	ADVENTURE("Adventure and sessions", "home.AdventurePanels", RouteId.HOME, RouteId.ACTIVITY, RouteId.FILES, RouteId.TOOLS),
	COMBAT("Combat and encounters", "outfit.OutfitPanels", RouteId.OUTFIT, RouteId.CHRONICLE),
	ACCOUNT("Account and supplies", "bank.AccountPanels", RouteId.BANK, RouteId.CAPTURE, RouteId.XPSTASH),
	PROGRESSION("Progression and bounties", "pathfinder.ProgressionPanels", RouteId.PATHFINDER, RouteId.QUESTS, RouteId.BOUNTIES),
	COLLECTION("Collection and preferences", "friends.CollectionPanels", RouteId.FRIENDS, RouteId.SETTINGS);

	/** Package of every lane entry class; {@link #entryClass()} is relative to it. */
	public static final String PANEL_PACKAGE = "com.feralenterprises.raccoon.playtest.r02.panel.";

	private final String title;
	private final String entry;
	private final Set<RouteId> routes;

	Lane(String title, String entry, RouteId... routes) {
		this.title = title;
		this.entry = entry;
		this.routes = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(routes)));
	}

	public String title() { return title; }
	public Set<RouteId> routes() { return routes; }

	/**
	 * The lane's single host entry class (inside its own first route's package), with
	 * {@code public static List<RouteRegistration> registrations(Path laneInputsZip, Path frozenReview) throws IOException}.
	 * The host calls it off the EDT with the verified private inputs and installs the result on the EDT. Lanes A and B
	 * already use this shape (AdventurePanels, OutfitPanels).
	 */
	public String entryClass() { return PANEL_PACKAGE + entry; }

	/** The source path prefix this lane may write, relative to the dev module root. */
	public String writeScope(RouteId r) {
		if (!routes.contains(r)) throw new IllegalArgumentException(r + " is not owned by " + this);
		return "com/feralenterprises/raccoon/playtest/r02/panel/" + r.id() + "/";
	}

	public static Lane owning(RouteId r) {
		for (Lane l : values()) if (l.routes.contains(r)) return l;
		throw new IllegalStateException("no lane owns " + r);
	}
}
