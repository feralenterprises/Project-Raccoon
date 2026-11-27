package com.feralenterprises.raccoon.core.bank;

import com.feralenterprises.raccoon.core.account.AccountScope;

/** Reads the client. Implemented by the RuneLite adapter and by tests or the simulator. */
public interface BankCaptureSource
{
	/** Cheap check, done every tick before any capture. */
	BankVisibility visibility();

	/** One full read for {@code scope} on {@code tickIndex}. May throw: treated as a read failure. */
	BankFrame capture(AccountScope scope, long tickIndex);
}
