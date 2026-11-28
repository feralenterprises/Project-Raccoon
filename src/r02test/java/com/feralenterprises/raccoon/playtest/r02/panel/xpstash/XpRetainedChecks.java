package com.feralenterprises.raccoon.playtest.r02.panel.xpstash;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.panel.bank.RetainedBankRegression;
import java.util.Collections;
import java.util.function.BiConsumer;

/** XP Stash half of {@link RetainedBankRegression}: retained bank supplies are a saved estimate, never current. */
public final class XpRetainedChecks {
	private XpRetainedChecks() { }

	public static void run(BiConsumer<String, Boolean> check) {
		FixtureSnapshot cur = RetainedBankRegression.current();
		check.accept("xp baseline: complete current bank is an observed source", RetainedBankRegression.COMPLETE_ITEMS.equals(XpEngine.source(cur, "bank"))
			&& XpEngine.bankCurrent(cur) && XpEngine.sourceStatus(cur, "bank").equals("observed"));

		FixtureSnapshot s = RetainedBankRegression.retained();
		check.accept("xp retained: supplies come from the last complete capture only", RetainedBankRegression.COMPLETE_ITEMS.equals(XpEngine.source(s, "bank")));
		check.accept("xp retained: bank source is saved, not current", !XpEngine.bankCurrent(s) && XpEngine.sourceStatus(s, "bank").equals("saved"));
		XpEngine.Options o = new XpEngine.Options();
		o.sources.put("vault", false);
		XpEngine.Result r = new XpEngine.Result(Collections.<XpEngine.Entry>emptyList(), XpEngine.source(s, "bank") != null,
			XpEngine.source(s, "bank") != null, false, XpEngine.bankCurrent(s), null, null, 0d, null);
		check.accept("xp retained: projection warns quantities are from the last complete capture",
			"Bank quantities are from the last complete capture.".equals(XpEngine.warning(r, o)));

		FixtureSnapshot u = RetainedBankRegression.incompleteOnly();
		check.accept("xp incomplete without earlier capture: bank supplies unavailable", XpEngine.source(u, "bank") == null
			&& XpEngine.sourceStatus(u, "bank").equals("unavailable") && !XpEngine.bankCurrent(u));
	}
}
