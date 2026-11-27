package com.feralenterprises.raccoon.playtest.r02.panel.settings;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.util.Objects;

/** Registers Settings with the same dispatcher supplied to the composition. */
public final class SettingsPanels {
	private SettingsPanels() { }

	public static RouteRegistration registration(FixtureDispatcher fixtures) {
		Objects.requireNonNull(fixtures, "shared fixture dispatcher");
		return new RouteRegistration(RouteId.SETTINGS, Lane.COLLECTION.name(),
			context -> new SettingsPanel(context, fixtures::setAppearance));
	}
}
