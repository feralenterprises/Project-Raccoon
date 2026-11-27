package com.feralenterprises.raccoon.core.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Lifecycle cancellation guards matching the bank tests: same round, self, external. */
public class SubscriptionCancelTest
{
	private static final WorldContext WORLD = WorldContext.supported(Membership.UNKNOWN);

	@Test
	public void sameRoundAndSelfCancellation()
	{
		AccountLifecycle lc = new AccountLifecycle();
		AtomicBoolean cancelled = new AtomicBoolean();
		AtomicInteger afterCancel = new AtomicInteger();
		AtomicInteger selfCalls = new AtomicInteger();
		Subscription[] victim = new Subscription[1];
		Subscription[] self = new Subscription[1];
		lc.subscribe(s ->
		{
			if (victim[0] != null && s.revision() > 0 && !cancelled.get())
			{
				victim[0].cancel();
				cancelled.set(true);
			}
		});
		victim[0] = lc.subscribe(s ->
		{
			if (cancelled.get())
			{
				afterCancel.incrementAndGet();
			}
		});
		self[0] = lc.subscribe(s ->
		{
			if (selfCalls.incrementAndGet() == 2)
			{
				self[0].cancel();
			}
		});
		lc.start(0);
		lc.onClientPhase(ClientPhase.LOGGED_IN, 0);
		lc.onGameTick(IdentityReading.of(9L), WORLD, 0);
		lc.onClientPhase(ClientPhase.HOPPING, 1);
		assertEquals(0, afterCancel.get());
		assertEquals(2, selfCalls.get());
	}

	@Test(timeout = 20_000)
	public void externalCancelReturnsOnlyAfterTheVictimsOwnCallbackFinished() throws Exception
	{
		AccountLifecycle lc = new AccountLifecycle();
		lc.start(0);
		lc.onClientPhase(ClientPhase.LOGGED_IN, 0);
		CountDownLatch inCallback = new CountDownLatch(1);
		AtomicBoolean armed = new AtomicBoolean();
		AtomicBoolean cancelReturned = new AtomicBoolean();
		AtomicBoolean runningAfterCancel = new AtomicBoolean();
		Subscription victim = lc.subscribe(s ->
		{
			if (armed.get())
			{
				inCallback.countDown();
				long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
				while (System.nanoTime() < until && !cancelReturned.get())
				{
					Thread.onSpinWait();
				}
				if (cancelReturned.get())
				{
					runningAfterCancel.set(true);
				}
			}
		});
		armed.set(true);
		Thread driver = new Thread(() -> lc.onGameTick(IdentityReading.of(9L), WORLD, 1));
		driver.start();
		assertTrue(inCallback.await(5, TimeUnit.SECONDS));
		Thread canceller = new Thread(() ->
		{
			victim.cancel();
			cancelReturned.set(true);
		});
		canceller.start();
		canceller.join();
		driver.join();
		assertFalse(runningAfterCancel.get());
	}
}
