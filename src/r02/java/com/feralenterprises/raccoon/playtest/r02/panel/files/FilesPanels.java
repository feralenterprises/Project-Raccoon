package com.feralenterprises.raccoon.playtest.r02.panel.files;

import com.feralenterprises.raccoon.playtest.r02.panel.home.HomeInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.home.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.io.IOException;

/** Lane A Feral Files entry point. Load off the EDT; the shared composition installs it on the EDT. */
public final class FilesPanels {
	private FilesPanels() { }

	/** {@code home}: Home's verified inputs (frozen {@code --stone} and {@code --wood}). */
	public static RouteRegistration registration(LaneInputs lane, HomeInputs home) throws IOException {
		FilesModel.Presentation p = new FilesModel.Presentation(lane);
		return new RouteRegistration(RouteId.FILES, Lane.ADVENTURE.name(), context -> new FilesPanel(context, p, home.stone(), home.wood()));
	}
}
