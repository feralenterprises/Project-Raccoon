package com.feralenterprises.raccoon.playtest.r02.panel.files;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.panel.home.LaneInputs;
import java.io.IOException;

/**
 * The latest verified notable loot in a scope's saved archive: the same rule as Feral Files' Latest spoils (a notable
 * item named by the saved record, present in the shared record's loot, with agreeing count and unit). Used by Tools'
 * Biggest Wins so both panels always agree.
 */
public final class ArchiveWins {
	/** A win: the saved record id, its title and the notable item. */
	public static final class Win {
		public final String recordId, recordTitle, itemName;
		public final int itemId;

		Win(String recordId, String recordTitle, String itemName, int itemId) {
			this.recordId = recordId;
			this.recordTitle = recordTitle;
			this.itemName = itemName;
			this.itemId = itemId;
		}
	}

	private final FilesModel.Presentation presentation;

	public ArchiveWins(LaneInputs lane) throws IOException { presentation = new FilesModel.Presentation(lane); }

	/** The latest win in this snapshot's scope, or null when no verified notable loot exists. */
	public Win latest(FixtureSnapshot s, Registry registry) {
		FilesModel.Rec r = FilesModel.of(s, registry, presentation).latestSpoil();
		return r == null ? null : new Win(r.id, r.title, r.notable.name, r.notable.id);
	}
}
