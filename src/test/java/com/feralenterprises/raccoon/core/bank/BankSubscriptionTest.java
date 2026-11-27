package com.feralenterprises.raccoon.core.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.feralenterprises.raccoon.core.account.Subscription;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

/** Bank listener cancellation: same delivery round, self, and from another thread. */
public class BankSubscriptionTest
{
	private BankHarness h;

	@Before
	public void setUp()
	{
		h = new BankHarness();
	}

	@Test
	public void sameRoundCancellationOfALaterSubscriberDeliversNothingFurther()
	{
		AtomicBoolean cancelled = new AtomicBoolean();
		AtomicInteger afterCancel = new AtomicInteger();
		Subscription[] victim = new Subscription[1];
		h.tracker.subscribe(s ->
		{
			if (victim[0] != null && !cancelled.get())
			{
				victim[0].cancel();
				cancelled.set(true);
			}
		});
		victim[0] = h.tracker.subscribe(s ->
		{
			if (cancelled.get())
			{
				afterCancel.incrementAndGet();
			}
		});
		h.ticks(4);
		assertTrue(cancelled.get());
		assertEquals("callbacks after cancel()", 0, afterCancel.get());
	}

	@Test
	public void selfCancellationDeliversNothingFurther()
	{
		AtomicInteger calls = new AtomicInteger();
		Subscription[] self = new Subscription[1];
		self[0] = h.tracker.subscribe(s ->
		{
			if (calls.incrementAndGet() == 2)
			{
				self[0].cancel();
			}
		});
		h.ticks(6);
		assertEquals(2, calls.get());
	}

	@Test(timeout = 20_000)
	public void externalCancellationWaitsForTheRoundInProgress() throws Exception
	{
		CountDownLatch inDelivery = new CountDownLatch(1);
		AtomicBoolean armed = new AtomicBoolean();
		AtomicBoolean cancelReturned = new AtomicBoolean();
		AtomicBoolean afterCancel = new AtomicBoolean();
		h.tracker.subscribe(s ->
		{
			if (armed.get())
			{
				inDelivery.countDown();
				long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
				while (System.nanoTime() < until && !cancelReturned.get())
				{
					Thread.onSpinWait();
				}
			}
		});
		Subscription victim = h.tracker.subscribe(s ->
		{
			if (cancelReturned.get())
			{
				afterCancel.set(true);
			}
		});
		armed.set(true);
		Thread driver = new Thread(h::tick);
		driver.start();
		assertTrue(inDelivery.await(5, TimeUnit.SECONDS));
		Thread canceller = new Thread(() ->
		{
			victim.cancel();
			cancelReturned.set(true);
		});
		canceller.start();
		canceller.join();
		driver.join();
		armed.set(false);
		h.ticks(3);
		assertFalse("callback after cancel() returned", afterCancel.get());
	}

	@Test(timeout = 20_000)
	public void externalCancelReturnsOnlyAfterTheVictimsOwnCallbackFinished() throws Exception
	{
		CountDownLatch inCallback = new CountDownLatch(1);
		AtomicBoolean armed = new AtomicBoolean();
		AtomicBoolean cancelReturned = new AtomicBoolean();
		AtomicBoolean runningAfterCancel = new AtomicBoolean();
		Subscription victim = h.tracker.subscribe(s ->
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
		Thread driver = new Thread(h::tick);
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
		assertFalse("cancel() returned while the listener was still running", runningAfterCancel.get());
	}
}
