package com.feralenterprises.raccoon.playtest.r02.panel.pathfinder;

import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;

/** Lane D Pathfinder entry point. Load inputs off the EDT; the shared composition installs it on the EDT. */
public final class PathfinderPanels {
	private PathfinderPanels() { }

	public static RouteRegistration registration(PathfinderData data) {
		return new RouteRegistration(RouteId.PATHFINDER, Lane.PROGRESSION.name(), context -> new PathfinderPanel(context, data));
	}
}
