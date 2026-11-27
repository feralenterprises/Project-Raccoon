package com.feralenterprises.raccoon.ui.shell;

import java.util.Objects;

/**
 * An immutable navigation target: a route id plus at most one typed payload id (R02 dispatch: "route plus optional
 * encounter/session/milestone ID"). Payloads are stable ids, never objects, so no route compiles against another.
 */
public final class RouteRequest {
	/** The payload kinds a route may receive. */
	public enum Param { ENCOUNTER, SESSION, MILESTONE }

	private final String route;
	private final Param param;
	private final String paramId;

	private RouteRequest(String route, Param param, String paramId) {
		this.route = Objects.requireNonNull(route, "route");
		if ((param == null) != (paramId == null)) throw new IllegalArgumentException("param and id must be given together");
		if (paramId != null && paramId.isEmpty()) throw new IllegalArgumentException("empty param id");
		this.param = param;
		this.paramId = paramId;
	}

	public static RouteRequest of(String route) { return new RouteRequest(route, null, null); }

	public static RouteRequest of(String route, Param param, String id) {
		return new RouteRequest(route, Objects.requireNonNull(param, "param"), Objects.requireNonNull(id, "id"));
	}

	public String route() { return route; }
	/** The payload kind, or null for a bare route. */
	public Param param() { return param; }
	/** The payload id, or null for a bare route. */
	public String paramId() { return paramId; }
	public boolean hasParam() { return param != null; }

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RouteRequest)) return false;
		RouteRequest r = (RouteRequest) o;
		return route.equals(r.route) && param == r.param && Objects.equals(paramId, r.paramId);
	}

	@Override
	public int hashCode() { return Objects.hash(route, param, paramId); }

	@Override
	public String toString() { return param == null ? route : route + "?" + param.name().toLowerCase(java.util.Locale.ROOT) + "=" + paramId; }
}
