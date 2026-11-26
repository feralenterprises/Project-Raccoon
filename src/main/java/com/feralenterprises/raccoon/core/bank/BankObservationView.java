package com.feralenterprises.raccoon.core.bank;

import com.feralenterprises.raccoon.core.account.Subscription;

/** Read side of bank observation for panels, the simulator and later features. */
public interface BankObservationView
{
	/** Latest snapshot; never null. */
	BankSnapshot snapshot();

	/** Listener receives the current snapshot immediately, then every change. */
	Subscription subscribe(BankObservationListener listener);

	/**
	 * Authoritative bank-only answer: OWNED/NOT_OWNED only while QUALIFIED_CURRENT and its scope is
	 * still current in the lifecycle; UNKNOWN otherwise.
	 */
	Ownership bankOwnership(int itemId);

	/**
	 * Authoritative bank + carried answer: as {@link #bankOwnership}, and additionally only when
	 * inventory and equipment are both reported with no excluded noted variants.
	 */
	Ownership combinedOwnership(int itemId);

	/**
	 * Authoritative bank fence: true only if {@code evidence} is the tracker's current
	 * qualification (same generation) and its account scope is still current. False after the
	 * bank closes, any failed revalidation, a changed sample or any scope change, even if the
	 * account scope itself is still current.
	 */
	boolean isCurrent(BankEvidence evidence);

	/** Wraps work derived from {@code evidence}; it runs only if the evidence is still current. */
	Runnable fenced(BankEvidence evidence, Runnable work);
}
