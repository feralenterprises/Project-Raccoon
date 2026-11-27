package com.feralenterprises.raccoon.playtest.r02.panel.home;

import com.feralenterprises.raccoon.playtest.r02.panel.activity.ActivityInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.activity.ActivityPanels;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lane A (Adventure) single host entry point: every Lane A route implemented so far. The shared owner calls this off
 * the EDT with the accepted Lane Inputs ZIP and the extracted frozen review, then installs the registrations into its
 * {@code R02Composition} on the EDT.
 */
public final class AdventurePanels {
	private AdventurePanels() { }

	public static List<RouteRegistration> registrations(Path laneInputsZip, Path frozenReview) throws IOException {
		LaneInputs lane = LaneInputs.open(laneInputsZip);
		List<RouteRegistration> out = new ArrayList<>();
		out.add(HomePanels.registration(HomeInputs.load(lane)));
		out.add(ActivityPanels.registration(ActivityInputs.load(lane, frozenReview)));
		return Collections.unmodifiableList(out);
	}
}
