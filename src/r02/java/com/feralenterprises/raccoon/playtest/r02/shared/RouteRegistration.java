package com.feralenterprises.raccoon.playtest.r02.shared;

import com.feralenterprises.raccoon.ui.shell.Environment;
import java.util.Objects;

/**
 * A lane's registration entry, keyed by the existing route id. Lanes publish these from their own package; only the
 * shared owner's composition root ({@code assembly}) installs them.
 */
public final class RouteRegistration {
	private final RouteId route;
	private final PanelFactory factory;
	private final String owner;

	public RouteRegistration(RouteId route, String owner, PanelFactory factory) {
		this.route = Objects.requireNonNull(route);
		this.owner = Objects.requireNonNull(owner);
		this.factory = Objects.requireNonNull(factory);
	}

	public RouteId route() { return route; }
	public PanelFactory factory() { return factory; }
	/** The lane that owns this route (dispatch ownership table), for receipts and the ownership check. */
	public String owner() { return owner; }
	public Environment environment() { return R02Environment.of(route).frameworkEnvironment(); }
}
