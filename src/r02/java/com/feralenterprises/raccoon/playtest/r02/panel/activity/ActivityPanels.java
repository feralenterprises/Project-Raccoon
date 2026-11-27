package com.feralenterprises.raccoon.playtest.r02.panel.activity;

import com.feralenterprises.raccoon.playtest.r02.panel.home.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.io.IOException;
import java.nio.file.Path;

/** Lane A Current Activity entry point. Load off the EDT; the shared composition installs it on the EDT. */
public final class ActivityPanels {
	private ActivityPanels() { }

	public static RouteRegistration registration(ActivityInputs inputs) {
		return new RouteRegistration(RouteId.ACTIVITY, Lane.ADVENTURE.name(), context -> new ActivityPanel(context, inputs));
	}

	/** {@code laneInputs}: the accepted Lane Inputs ZIP; {@code frozenReview}: extracted {@code Project-Raccoon-R02/}. */
	public static RouteRegistration registration(Path laneInputs, Path frozenReview) throws IOException {
		return registration(ActivityInputs.load(LaneInputs.open(laneInputs), frozenReview));
	}
}
