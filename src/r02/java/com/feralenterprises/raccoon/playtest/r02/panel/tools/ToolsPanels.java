package com.feralenterprises.raccoon.playtest.r02.panel.tools;

import com.feralenterprises.raccoon.playtest.r02.panel.files.ArchiveWins;
import com.feralenterprises.raccoon.playtest.r02.panel.home.HomeInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.home.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.io.IOException;
import java.nio.file.Path;

/** Lane A Tools entry point. Load off the EDT; the shared composition installs it on the EDT. */
public final class ToolsPanels {
	private ToolsPanels() { }

	public static RouteRegistration registration(LaneInputs lane, HomeInputs home, Path frozenReview) throws IOException {
		ToolsInputs in = ToolsInputs.load(lane, frozenReview);
		ArchiveWins wins = new ArchiveWins(lane);
		return new RouteRegistration(RouteId.TOOLS, Lane.ADVENTURE.name(), context -> new ToolsPanel(context, in, wins, home.stone(), home.wood()));
	}
}
