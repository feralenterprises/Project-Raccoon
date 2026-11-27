package com.feralenterprises.raccoon.bank;

import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.bank.BankFrame;
import com.feralenterprises.raccoon.core.bank.BankVisibility;
import com.feralenterprises.raccoon.core.bank.NativeEvaluationProbe;
import net.runelite.api.Client;

/**
 * FR-006 playtest probe (never in the production artifact). Times the production read path,
 * {@link RuneLiteBankCaptureSource}, against the genuine client while the bank is genuinely open,
 * plus the pure evaluation of that frame. Read-only, the same reviewed client reads as
 * production; it holds no state and never feeds the production tracker.
 */
public final class NativeBankProbe
{
	private final RuneLiteBankCaptureSource source;

	public NativeBankProbe(Client client)
	{
		this.source = new RuneLiteBankCaptureSource(client);
	}

	/** One sample, or null when the bank is not open (visibility cost is not reported then). */
	public Sample sample(AccountScope scope, long tick)
	{
		long t0 = System.nanoTime();
		BankVisibility v = source.visibility();
		if (v != BankVisibility.OPEN)
		{
			return null;
		}
		BankFrame frame = source.capture(scope, tick);
		NativeEvaluationProbe.Result e = NativeEvaluationProbe.evaluate(frame);
		long t1 = System.nanoTime();
		return new Sample(t1 - t0, e.valid, e.failure, frame.bank().slots().size(), frame.witness().isKnown(),
			frame.identityBefore().isVerified() && frame.identityAfter().isVerified());
	}

	public static final class Sample
	{
		public final long nanos;
		public final boolean valid;
		public final String failure;
		public final int bankSlots;
		/** Whether this read's tab witness was known (per read, as the tracker sees it). */
		public final boolean witnessKnown;
		/** Whether an identity was readable before and after the read (boolean only; no hash). */
		public final boolean identityKnown;

		Sample(long nanos, boolean valid, String failure, int bankSlots, boolean witnessKnown, boolean identityKnown)
		{
			this.nanos = nanos;
			this.valid = valid;
			this.failure = failure;
			this.bankSlots = bankSlots;
			this.witnessKnown = witnessKnown;
			this.identityKnown = identityKnown;
		}
	}
}
