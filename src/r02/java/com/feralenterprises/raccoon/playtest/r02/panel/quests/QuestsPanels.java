package com.feralenterprises.raccoon.playtest.r02.panel.quests;

import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.PathfinderData;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.io.IOException;

/** Lane D Quests & Diaries entry point. Load inputs off the EDT; the shared composition installs it on the EDT. */
public final class QuestsPanels {
	private QuestsPanels() { }

	public static RouteRegistration registration(PathfinderData data) throws IOException {
		QuestsModel.Diaries diaries = new QuestsModel.Diaries(data.lane);
		return new RouteRegistration(RouteId.QUESTS, Lane.PROGRESSION.name(), context -> new QuestsPanel(context, data, diaries));
	}
}
