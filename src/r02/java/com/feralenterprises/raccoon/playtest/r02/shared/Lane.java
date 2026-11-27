package com.feralenterprises.raccoon.playtest.r02.shared;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** The five implementation lanes and their exclusive routes (dispatch ownership contract). */
public enum Lane {
	ADVENTURE("Adventure and sessions", RouteId.HOME, RouteId.ACTIVITY, RouteId.FILES, RouteId.TOOLS),
	COMBAT("Combat and encounters", RouteId.OUTFIT, RouteId.CHRONICLE),
	ACCOUNT("Account and supplies", RouteId.BANK, RouteId.CAPTURE, RouteId.XPSTASH),
	PROGRESSION("Progression and bounties", RouteId.PATHFINDER, RouteId.QUESTS, RouteId.BOUNTIES),
	COLLECTION("Collection and preferences", RouteId.FRIENDS, RouteId.SETTINGS);

	private final String title;
	private final Set<RouteId> routes;

	Lane(String title, RouteId... routes) {
		this.title = title;
		this.routes = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(routes)));
	}

	public String title() { return title; }
	public Set<RouteId> routes() { return routes; }

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
