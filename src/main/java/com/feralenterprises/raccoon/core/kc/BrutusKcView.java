package com.feralenterprises.raccoon.core.kc;

import com.feralenterprises.raccoon.core.account.AccountScope;
import java.util.OptionalLong;

/** Read side of the Brutus KC service. */
public interface BrutusKcView
{
	/** Kills gained in the current login (sum of verified deltas); empty unless publishable. */
	OptionalLong currentLoginTotal();

	/** As {@link #currentLoginTotal()}, but only if {@code scope} is still current. */
	OptionalLong currentLoginTotal(AccountScope scope);

	/** True while observation is quarantined in the current epoch. */
	boolean isQuarantined();

	/** Increases on every baseline, gain or quarantine (change detection for consumers). */
	long revision();
}
