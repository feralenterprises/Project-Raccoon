package com.feralenterprises.raccoon.playtest.r02.panel.outfit;

import com.feralenterprises.raccoon.playtest.r02.panel.chronicle.ChroniclePanels;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Lane B host entry point. The shared owner supplies the existing authority and the private input path. */
public final class OutfitPanels {
	private OutfitPanels() { }

	public static RouteRegistration registration(CombatCatalog catalog) {
		return new RouteRegistration(RouteId.OUTFIT, Lane.COMBAT.name(), context -> new OutfitPanel(context, catalog));
	}

	/** Load off the EDT, then pass these registrations into the existing R02 composition on the EDT. */
	public static List<RouteRegistration> registrations(Path laneInputs, Path frozenReview) throws IOException {
		CombatCatalog catalog = CombatCatalog.load(laneInputs, frozenReview);
		return List.of(registration(catalog), ChroniclePanels.registration(catalog));
	}
}
