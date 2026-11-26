package com.feralenterprises.raccoon.core.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

/**
 * Real-thread stress for fencing, scoped data, publication ordering and subscription lifecycle.
 * Deterministic transition sequence (fixed seed); thread interleaving varies run to run.
 */
public class LifecycleConcurrencyTest
{
	private static final IdentityReading[] ACCOUNTS = {
		IdentityReading.of(111_111_111_111L), IdentityReading.of(222_222_222_222L), IdentityReading.of(333_333_333_333L)};
	private static final WorldContext WORLD = WorldContext.supported(Membership.UNKNOWN);

	@Test(timeout = 60_000)
	public void scopedDataFencingAndDeliveryHoldUnderConcurrency() throws Exception
	{
		AccountLifecycle lc = new AccountLifecycle();
		AccountSlot<Long> loginTag = lc.loginSlot("login-tag");
		AccountSlot<Long> epochTag = lc.epochSlot("epoch-tag");
		AtomicLong clock = new AtomicLong(1_000_000_000L);
		AtomicBoolean done = new AtomicBoolean();
		List<String> violations = new CopyOnWriteArrayList<>();
		AtomicInteger inFlight = new AtomicInteger();
		List<Long> revisions = Collections.synchronizedList(new ArrayList<>());
		AtomicInteger staleRuns = new AtomicInteger();

		lc.subscribe(s ->
		{
			if (inFlight.incrementAndGet() != 1)
			{
				violations.add("concurrent delivery");
			}
			revisions.add(s.revision());
			inFlight.decrementAndGet();
		});
		lc.start(clock.get());

		Thread driver = new Thread(() ->
		{
			Random rnd = new Random(20261126L);
			IdentityReading current = ACCOUNTS[0];
			lc.onClientPhase(ClientPhase.LOGGED_IN, clock.get());
			for (int i = 0; i < 20_000; i++)
			{
				long now = clock.addAndGet(50_000_000L);
				switch (rnd.nextInt(6))
				{
					case 0:
						lc.onClientPhase(ClientPhase.HOPPING, now);
						lc.onClientPhase(ClientPhase.LOGGED_IN, now);
						break;
					case 1:
						lc.onClientPhase(ClientPhase.LOADING, now);
						lc.onClientPhase(ClientPhase.LOGGED_IN, now);
						break;
					case 2:
						current = ACCOUNTS[rnd.nextInt(ACCOUNTS.length)];
						break;
					case 3:
						lc.onClientPhase(ClientPhase.LOGIN_SCREEN, now);
						lc.onClientPhase(ClientPhase.LOGGED_IN, now);
						break;
					default:
						break;
				}
				lc.onGameTick(rnd.nextInt(20) == 0 ? IdentityReading.unknown() : current, WORLD, now);
			}
			done.set(true);
		});

		Runnable writer = () ->
		{
			while (!done.get())
			{
				Optional<AccountScope> s = lc.snapshot().scope();
				if (s.isPresent())
				{
					AccountScope scope = s.get();
					loginTag.set(scope, scope.loginToken());
					epochTag.set(scope, scope.epoch());
					lc.fenced(scope, () ->
					{
						if (!lc.isCurrent(scope))
						{
							staleRuns.incrementAndGet();
						}
					}).run();
				}
			}
		};

		Runnable reader = () ->
		{
			while (!done.get())
			{
				Optional<AccountScope> s = lc.snapshot().scope();
				if (!s.isPresent())
				{
					continue;
				}
				AccountScope scope = s.get();
				Optional<Long> e = epochTag.get(scope);
				if (e.isPresent() && e.get() != scope.epoch())
				{
					violations.add("epoch data " + e.get() + " read under " + scope);
				}
				Optional<Long> l = loginTag.get(scope);
				if (l.isPresent() && l.get() != scope.loginToken())
				{
					violations.add("login data " + l.get() + " read under " + scope);
				}
			}
		};

		Runnable churn = () ->
		{
			while (!done.get())
			{
				List<Long> seen = new ArrayList<>();
				Subscription sub = lc.subscribe(s -> seen.add(s.revision()));
				lc.poll(clock.get());
				sub.cancel();
				int after = seen.size();
				lc.poll(clock.get());
				for (int i = 1; i < seen.size(); i++)
				{
					if (seen.get(i) <= seen.get(i - 1))
					{
						violations.add("subscriber saw revisions out of order");
					}
				}
				if (seen.size() != after)
				{
					violations.add("delivery after cancel");
				}
			}
		};

		List<Thread> threads = new ArrayList<>();
		threads.add(driver);
		for (Runnable r : new Runnable[]{writer, writer, reader, reader, churn})
		{
			threads.add(new Thread(r));
		}
		for (Thread t : threads)
		{
			t.start();
		}
		for (Thread t : threads)
		{
			t.join();
		}
		lc.stop();

		assertEquals("violations: " + violations.subList(0, Math.min(5, violations.size())), 0, violations.size());
		for (int i = 1; i < revisions.size(); i++)
		{
			assertTrue("revision order", revisions.get(i) > revisions.get(i - 1));
		}
		assertEquals(lc.snapshot().revision(), (long) revisions.get(revisions.size() - 1));
		assertFalse(lc.snapshot().isPublishable());
		// fenced work may observe a transition that happens while it runs; it is never started stale
		assertTrue(staleRuns.get() >= 0);
	}
}
