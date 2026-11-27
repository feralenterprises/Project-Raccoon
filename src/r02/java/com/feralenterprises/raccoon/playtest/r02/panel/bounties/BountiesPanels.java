package com.feralenterprises.raccoon.playtest.r02.panel.bounties;

import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;

/** Lane D Bandit's Bounties entry point. Load {@link BountiesData} off the EDT; the shared composition installs it on the EDT. */
public final class BountiesPanels {
	private BountiesPanels() { }

	public static RouteRegistration registration(BountiesData data) {
		return new RouteRegistration(RouteId.BOUNTIES, Lane.PROGRESSION.name(), context -> new BountiesPanel(context, data));
	}
}
