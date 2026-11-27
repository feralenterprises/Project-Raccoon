package com.feralenterprises.raccoon.ui.shell;

/**
 * Implemented by a page body that accepts typed route payloads. The shell calls it on the EDT each time the page is
 * shown. {@code returning} is true when the page is reached through Back: the page should then keep its own current
 * selection, filters and detail instead of re-applying the request.
 */
public interface RouteAware {
	void routeShown(RouteRequest request, boolean returning);
}
