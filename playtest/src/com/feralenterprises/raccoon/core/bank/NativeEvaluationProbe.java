package com.feralenterprises.raccoon.core.bank;

/**
 * FR-006 playtest probe (never in the production artifact): the production evaluation of one
 * captured frame, pure and stateless, for native timing of genuine bank captures.
 */
public final class NativeEvaluationProbe
{
	private NativeEvaluationProbe()
	{
	}

	public static Result evaluate(BankFrame frame)
	{
		BankEvaluator.Evaluation e = BankEvaluator.evaluate(frame);
		return new Result(e.valid(), e.valid() ? null : String.valueOf(e.failure));
	}

	public static final class Result
	{
		public final boolean valid;
		public final String failure;

		Result(boolean valid, String failure)
		{
			this.valid = valid;
			this.failure = failure;
		}
	}
}
