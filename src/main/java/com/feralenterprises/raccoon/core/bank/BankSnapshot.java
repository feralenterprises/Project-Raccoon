package com.feralenterprises.raccoon.core.bank;

import java.util.Optional;

/** Immutable published state of bank observation. Safe on any thread. */
public final class BankSnapshot
{
	private final long revision;
	private final BankStatus status;
	private final BankReason reason;
	private final BankEvidence current;
	private final BankEvidence historical;
	private final int streak;

	BankSnapshot(long revision, BankStatus status, BankReason reason, BankEvidence current, BankEvidence historical, int streak)
	{
		this.revision = revision;
		this.status = status;
		this.reason = reason;
		this.current = status == BankStatus.QUALIFIED_CURRENT ? current : null;
		this.historical = status == BankStatus.HISTORICAL ? historical : null;
		this.streak = streak;
	}

	public long revision()
	{
		return revision;
	}

	public BankStatus status()
	{
		return status;
	}

	public BankReason reason()
	{
		return reason;
	}

	/**
	 * Present only when {@link BankStatus#QUALIFIED_CURRENT}. Its authority still has to be
	 * checked at use: prefer {@link BankObservationView#bankOwnership}, or gate on its scope.
	 */
	public Optional<BankEvidence> current()
	{
		return Optional.ofNullable(current);
	}

	/** Present only when {@link BankStatus#HISTORICAL}: provenance of this login, never current. */
	public Optional<BankEvidence> historical()
	{
		return Optional.ofNullable(historical);
	}

	/** Length of the current matching streak (diagnostics). */
	public int streak()
	{
		return streak;
	}

	boolean sameContentAs(BankSnapshot o)
	{
		return o != null && o.status == status && o.reason == reason && o.current == current && o.historical == historical
			&& o.streak == streak;
	}

	@Override
	public String toString()
	{
		return "BankSnapshot[rev " + revision + ", " + status + ", " + reason + ", streak " + streak + "]";
	}
}
