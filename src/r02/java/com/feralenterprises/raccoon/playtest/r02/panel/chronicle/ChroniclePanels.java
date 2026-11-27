package com.feralenterprises.raccoon.playtest.r02.panel.chronicle;

import com.feralenterprises.raccoon.playtest.r02.panel.outfit.CombatCatalog;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;

public final class ChroniclePanels {
	private ChroniclePanels() { }

	public static RouteRegistration registration(CombatCatalog catalog) {
		return new RouteRegistration(RouteId.CHRONICLE, Lane.COMBAT.name(), context -> new ChroniclePanel(context, catalog));
	}
}
