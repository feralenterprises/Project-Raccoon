package com.feralenterprises.raccoon.core.bank;

/** Receives every published bank snapshot, in order, on the client thread. Must not block. */
public interface BankObservationListener
{
	void onBankSnapshot(BankSnapshot snapshot);
}
