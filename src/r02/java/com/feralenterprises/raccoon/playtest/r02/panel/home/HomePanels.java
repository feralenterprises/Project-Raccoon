package com.feralenterprises.raccoon.playtest.r02.panel.home;

import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Lane A (Adventure) host entry point. The shared owner loads the private inputs off the EDT and installs the returned
 * registrations into its {@code R02Composition} on the EDT. Current Activity, Feral Files and Tools join this list as
 * they land.
 */
public final class HomePanels {
	private HomePanels() { }

	public static RouteRegistration registration(HomeInputs inputs) {
		return new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), context -> new HomePanel(context, inputs));
	}

	/** {@code laneInputs}: the accepted Lane Inputs ZIP. Images otherwise come from the host's shared R02 assets. */
	public static List<RouteRegistration> registrations(Path laneInputs) throws IOException {
		return Collections.singletonList(registration(HomeInputs.load(laneInputs)));
	}
}
