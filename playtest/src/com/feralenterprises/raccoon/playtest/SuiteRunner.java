package com.feralenterprises.raccoon.playtest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/**
 * Runs permanent regression suites with JUnit and records every test in a receipt. Same rules as
 * the CI verifier: every required suite must run, no test may be skipped, none may fail.
 */
final class SuiteRunner
{
	private static final String OWN = "com.feralenterprises.raccoon.";

	/** Identical to the verifier's REQUIRED_SUITES at the candidate (docs/foundation-audit.md §1). */
	static final List<String> ALL_SUITES = Collections.unmodifiableList(Arrays.asList(
		"RaccoonPluginDescriptorTest", "FoundationIntegrationTest", "FoundationPerformanceTest",
		"core.account.AccountLifecycleTest", "core.account.AuditRegressionTest", "core.account.ConsumerScopeGateTest",
		"core.account.LifecycleBoundaryTest", "core.account.LifecycleConcurrencyTest", "core.account.ScopeNamespaceTest",
		"core.account.SubscriptionCancelTest", "account.AccountLifecycleServiceTest",
		"core.bank.BankObservationTrackerTest", "core.bank.BankRepairRegressionTest", "core.bank.BankSubscriptionTest",
		"bank.BankObservationServiceTest",
		"core.kc.BrutusKcTrackerTest", "kc.BrutusKcServiceTest", "kc.BrutusKcEventValidationTest",
		"kc.EventAuthorityTest", "kc.ObservedTransitionTest"));

	/** RuneLite adapter suites (synthetic fixtures through the production adapters and wiring). */
	static final List<String> ADAPTER_SUITES = Collections.unmodifiableList(Arrays.asList(
		"FoundationIntegrationTest", "FoundationPerformanceTest", "account.AccountLifecycleServiceTest",
		"bank.BankObservationServiceTest", "kc.BrutusKcServiceTest", "kc.BrutusKcEventValidationTest",
		"kc.EventAuthorityTest", "kc.ObservedTransitionTest"));

	static final class Outcome
	{
		int run;
		int failed;
		int ignored;
		final Map<String, Integer> perSuite = new LinkedHashMap<>();
		final List<String> missing = new ArrayList<>();
		final List<String> failures = new ArrayList<>();

		boolean passed()
		{
			return run > 0 && failed == 0 && ignored == 0 && missing.isEmpty();
		}
	}

	private SuiteRunner()
	{
	}

	static Outcome run(List<String> suites, Receipt receipt) throws ClassNotFoundException
	{
		Outcome o = new Outcome();
		List<Class<?>> classes = new ArrayList<>();
		for (String s : suites)
		{
			classes.add(Class.forName(OWN + s));
			o.perSuite.put(OWN + s, 0);
		}
		JUnitCore core = new JUnitCore();
		core.addListener(new RunListener()
		{
			private final Map<Description, Boolean> failedTests = new LinkedHashMap<>();

			@Override
			public void testFailure(Failure failure)
			{
				failedTests.put(failure.getDescription(), true);
				String msg = String.valueOf(failure.getMessage());
				o.failures.add(failure.getDescription().getDisplayName() + ": " + (msg.length() > 300 ? msg.substring(0, 300) : msg));
			}

			@Override
			public void testIgnored(Description d)
			{
				receipt.event("test", "suite", d.getClassName(), "name", d.getMethodName(), "result", "SKIPPED");
			}

			@Override
			public void testFinished(Description d)
			{
				boolean failed = failedTests.containsKey(d);
				o.perSuite.merge(d.getClassName(), 1, Integer::sum);
				receipt.event("test", "suite", d.getClassName(), "name", d.getMethodName(), "result", failed ? "FAIL" : "PASS",
					"thread", Thread.currentThread().getName());
			}
		});
		Result r = core.run(classes.toArray(new Class<?>[0]));
		o.run = r.getRunCount();
		o.failed = r.getFailureCount();
		o.ignored = r.getIgnoreCount();
		for (Map.Entry<String, Integer> e : o.perSuite.entrySet())
		{
			if (e.getValue() == 0)
			{
				o.missing.add(e.getKey());
			}
		}
		for (String f : o.failures)
		{
			receipt.event("failure", "detail", f);
		}
		receipt.event("suites", "requested", suites.size(), "run", o.run, "failed", o.failed, "skipped", o.ignored,
			"missing", String.join(",", o.missing), "perSuite", o.perSuite.toString(), "passed", o.passed());
		return o;
	}
}
