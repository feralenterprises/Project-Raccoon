package com.feralenterprises.raccoon.playtest.r02.panel.friends;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.panel.settings.SettingsPanels;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Lane E host wiring. Resolve the private inputs off the EDT before installing the routes on the EDT. */
public final class CollectionPanels {
	private CollectionPanels() { }

	public static List<RouteRegistration> registrations(Path laneInputs, Path frozenReview, FixtureDispatcher dispatcher)
		throws IOException {
		return List.of(FriendsPanels.registration(laneInputs, frozenReview), SettingsPanels.registration(dispatcher));
	}
}
