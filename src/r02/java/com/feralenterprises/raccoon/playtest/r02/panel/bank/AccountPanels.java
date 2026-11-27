package com.feralenterprises.raccoon.playtest.r02.panel.bank;

import com.feralenterprises.raccoon.playtest.r02.panel.capture.CaptureInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.capture.CapturePanels;
import com.feralenterprises.raccoon.playtest.r02.panel.xpstash.XpData;
import com.feralenterprises.raccoon.playtest.r02.panel.xpstash.XpStashPanels;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Lane C (Account) single host entry point: Bank Memory, Account Capture and XP Stash, in the same form as
 * {@code AdventurePanels} and {@code OutfitPanels}. The shared owner calls this off the EDT with the accepted Lane Inputs
 * ZIP and the extracted frozen review, then installs the registrations into its {@code R02Composition} on the EDT. Inputs
 * are read fail-closed: a different archive, or any file not matching its {@code SHA256SUMS}, throws before any page exists.
 */
public final class AccountPanels {
	private AccountPanels() { }

	public static List<RouteRegistration> registrations(Path laneInputsZip, Path frozenReview) throws IOException {
		LaneInputs lane = LaneInputs.open(laneInputsZip);
		return List.of(BankPanels.registration(BankInputs.load(lane)), CapturePanels.registration(CaptureInputs.load(lane, frozenReview)),
			XpStashPanels.registration(XpData.load(lane)));
	}
}
