package com.feralenterprises.raccoon.playtest;

/**
 * R1: complete deterministic regression replay on the test machine, outside the game client:
 * all 20 permanent suites of the candidate, no skips. Exit code 0 only on a complete pass.
 */
public final class R1Runner
{
	private R1Runner()
	{
	}

	public static void main(String[] args) throws Exception
	{
		int code;
		try (Receipt receipt = Receipt.open("R1"))
		{
			Environment.record(receipt, "R1");
			SuiteRunner.Outcome o = SuiteRunner.run(SuiteRunner.ALL_SUITES, receipt);
			receipt.event("verdict", "step", "R1", "result", o.passed() ? "PASS" : "FAIL",
				"tests", o.run, "failed", o.failed, "skipped", o.ignored, "missingSuites", o.missing.size());
			System.out.println("R1 " + (o.passed() ? "PASS" : "FAIL") + ": " + o.run + " tests, " + o.failed + " failed, "
				+ o.ignored + " skipped, " + o.missing.size() + " missing suites; receipt " + receipt.file());
			for (String f : o.failures)
			{
				System.out.println("  " + f);
			}
			code = o.passed() ? 0 : 1;
		}
		System.exit(code);
	}
}
