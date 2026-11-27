package com.feralenterprises.raccoon.playtest.r02.panel.xpstash;

import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;

/** Lane C XP Stash entry point. Load inputs off the EDT; the shared composition installs it on the EDT. */
public final class XpStashPanels {
	private XpStashPanels() { }

	public static RouteRegistration registration(XpData data) {
		return new RouteRegistration(RouteId.XPSTASH, Lane.ACCOUNT.name(), context -> new XpStashPanel(context, data));
	}
}
