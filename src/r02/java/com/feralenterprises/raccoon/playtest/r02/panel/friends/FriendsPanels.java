package com.feralenterprises.raccoon.playtest.r02.panel.friends;

import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.io.IOException;
import java.nio.file.Path;

/** Loads private frozen catalog and artwork before the host creates pages on the Swing event thread. */
public final class FriendsPanels {
	private FriendsPanels() { }

	public static RouteRegistration registration(Path laneInputs, Path frozenReview) throws IOException {
		return registration(FriendsCatalog.load(laneInputs, frozenReview));
	}

	static RouteRegistration registration(FriendsCatalog catalog) {
		return new RouteRegistration(RouteId.FRIENDS, Lane.COLLECTION.name(), context -> new FriendsPanel(context, catalog));
	}
}
