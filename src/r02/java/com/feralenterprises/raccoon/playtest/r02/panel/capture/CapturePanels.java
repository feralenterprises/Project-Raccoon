package com.feralenterprises.raccoon.playtest.r02.panel.capture;

import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;

/** Lane C Account Capture entry point. Load inputs off the EDT; the shared composition installs it on the EDT. */
public final class CapturePanels {
	private CapturePanels() { }

	public static RouteRegistration registration(CaptureInputs inputs) {
		return new RouteRegistration(RouteId.CAPTURE, Lane.ACCOUNT.name(), context -> new CapturePanel(context, inputs));
	}
}
