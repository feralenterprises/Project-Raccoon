package com.feralenterprises.raccoon.playtest.r02.panel.pathfinder;

import com.feralenterprises.raccoon.playtest.r02.panel.bounties.BountiesData;
import com.feralenterprises.raccoon.playtest.r02.panel.bounties.BountiesPanels;
import com.feralenterprises.raccoon.playtest.r02.panel.quests.QuestsPanels;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Lane D (Progression) single host entry point: Pathfinder, Quests &amp; Diaries and Bandit's Bounties, in the same form as
 * {@code AdventurePanels}, {@code OutfitPanels} and {@code AccountPanels}. The shared owner calls this off the EDT with the
 * accepted Lane Inputs ZIP and the extracted frozen review, then installs the registrations into its {@code R02Composition}
 * on the EDT. Inputs are read fail-closed. Lane D reads no frozen-review file; the parameter keeps the common signature.
 */
public final class ProgressionPanels {
	private ProgressionPanels() { }

	public static List<RouteRegistration> registrations(Path laneInputsZip, Path frozenReview) throws IOException {
		LaneInputs lane = LaneInputs.open(laneInputsZip);
		PathfinderData data = PathfinderData.load(lane);
		return List.of(PathfinderPanels.registration(data), QuestsPanels.registration(data), BountiesPanels.registration(BountiesData.load(lane)));
	}
}
