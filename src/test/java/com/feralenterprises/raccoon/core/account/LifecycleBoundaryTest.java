package com.feralenterprises.raccoon.core.account;

import static com.feralenterprises.raccoon.core.account.LifecycleHarness.A;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.UNKNOWN;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.UNSUPPORTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

/** Adjacent transitions and limit boundaries reviewed after the FR-002 audit. */
public class LifecycleBoundaryTest
{
	private LifecycleHarness h;
	private AccountSlot<Integer> kcTotals;
	private AccountSlot<Integer> epochData;

	@Before
	public void setUp()
	{
		h = new LifecycleHarness();
		kcTotals = h.lifecycle.loginSlot("kc-totals");
		epochData = h.lifecycle.epochSlot("epoch-data");
	}

	@Test
	public void regionChangeKeepsLoginAndEpochAfterFreshVerification()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		kcTotals.set(before, 4);
		epochData.set(before, 9);
		h.phase(ClientPhase.LOADING).tick(A);
		assertFalse("a verified tick during LOADING does not re-establish freshness", h.snap().isPublishable());
		assertTrue(h.snap().isAwaitingFreshIdentity());
		h.phase(ClientPhase.LOGGED_IN).tick(A);
		assertEquals("same epoch: a region change is not a hop", before, h.scope());
		assertEquals(Optional.of(4), kcTotals.get());
		assertEquals(Optional.of(9), epochData.get());
	}

	@Test
	public void writesAndCallbacksAreRejectedWhileAwaitingFreshIdentity()
	{
		h.loggedInAs(A);
		AccountScope s = h.scope();
		AtomicInteger ran = new AtomicInteger();
		h.phase(ClientPhase.LOADING);
		assertFalse(kcTotals.set(s, 1));
		assertFalse(h.lifecycle.isCurrent(s));
		h.lifecycle.fenced(s, ran::incrementAndGet).run();
		assertEquals(0, ran.get());
		assertEquals(Membership.UNKNOWN, h.snap().membership());
	}

	@Test
	public void duplicateLoggedInEventMasksUntilNextTick()
	{
		h.loggedInAs(A).phase(ClientPhase.LOGGED_IN);
		assertFalse(h.snap().isPublishable());
		h.tick(A);
		assertTrue(h.snap().isPublishable());
	}

	@Test
	public void unsupportedWorldDuringLoadingRetires()
	{
		h.loggedInAs(A).phase(ClientPhase.LOADING).tick(A, UNSUPPORTED);
		assertEquals(RetireReason.UNSUPPORTED_WORLD, h.snap().lastRetireReason());
	}

	@Test
	public void restoreJustBeforeTimeLimitSucceeds()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		h.hop().advanceSeconds(119.999).tick(A);
		assertTrue(h.scope().sameLogin(before));
	}

	@Test
	public void clockRunningBackwardsRetires()
	{
		h.loggedInAs(A).phase(ClientPhase.HOPPING);
		h.now -= 1;
		h.poll();
		assertEquals(RetireReason.CONTINUITY_TIMEOUT, h.snap().lastRetireReason());
	}

	@Test
	public void ticksBeforeLoggedInDoNotCountTowardTickLimit()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		h.phase(ClientPhase.HOPPING).ticks(30, UNKNOWN).phase(ClientPhase.LOGGED_IN).tick(A);
		assertTrue(h.scope().sameLogin(before));
	}

	@Test
	public void writesDuringSuspensionNeverReappearAfterRestore()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		kcTotals.set(before, 2);
		h.phase(ClientPhase.HOPPING);
		assertFalse(kcTotals.set(before, 99));
		h.phase(ClientPhase.LOGGED_IN).tick(A);
		assertEquals(Optional.of(2), kcTotals.get());
	}

	@Test
	public void enableDuringLoadingWaitsForLoggedInTick()
	{
		h.start().phase(ClientPhase.LOADING).tick(A);
		assertFalse(h.snap().isPublishable());
		h.phase(ClientPhase.LOGGED_IN).tick(A);
		assertTrue(h.snap().isPublishable());
	}

	@Test
	public void stopWhileAwaitingFreshIdentityRetires()
	{
		h.loggedInAs(A).phase(ClientPhase.LOADING);
		h.lifecycle.stop();
		assertEquals(RetireReason.PLUGIN_STOPPED, h.snap().lastRetireReason());
		assertEquals(Optional.empty(), kcTotals.get());
	}

	@Test
	public void scopedReadRejectsAScopeThatIsNoLongerCurrent()
	{
		h.loggedInAs(A);
		AccountScope s1 = h.scope();
		epochData.set(s1, 1);
		assertEquals(Optional.of(1), epochData.get(s1));
		h.hop().tick(A);
		AccountScope s2 = h.scope();
		epochData.set(s2, 2);
		assertEquals(Optional.empty(), epochData.get(s1));
		assertEquals(Optional.of(2), epochData.get(s2));
	}
}
