package com.feralenterprises.raccoon.playtest.r02.panel.bank;

import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;

/** Lane C Bank Memory entry point. Load inputs off the EDT; the shared composition installs it on the EDT. */
public final class BankPanels {
	private BankPanels() { }

	public static RouteRegistration registration(BankInputs inputs) {
		return new RouteRegistration(RouteId.BANK, Lane.ACCOUNT.name(), context -> new BankPanel(context, inputs));
	}
}
