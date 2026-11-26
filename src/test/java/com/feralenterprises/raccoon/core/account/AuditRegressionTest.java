package com.feralenterprises.raccoon.core.account;

import static com.feralenterprises.raccoon.core.account.LifecycleHarness.A;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.B;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.UNKNOWN;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.UNSUPPORTED;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.WORLD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

/**
 * Regressions for the 2026-11-26 independent FR-002 audit (issue #1). Each test reproduced a
 * finding against handback 6db6bc30 before the repair.
 */
public class AuditRegressionTest
{
	private LifecycleHarness h;
	private AccountSlot<Integer> kcTotals;

	@Before
	public void setUp()
	{
		h = new LifecycleHarness();
		kcTotals = h.lifecycle.loginSlot("kc-totals");
	}

	// Finding 1: data stayed publishable through LOADING without a fresh identity.

	@Test
	public void finding1_loadingMasksDataUntilIdentityIsFresh()
	{
		h.loggedInAs(A);
		kcTotals.set(h.scope(), 5);
		h.phase(ClientPhase.LOADING);
		assertFalse("not publishable while identity freshness is unknown", h.snap().isPublishable());
		assertEquals(Optional.empty(), kcTotals.get());
	}

	@Test
	public void finding1_unknownIdentityDuringLoadingRetires()
	{
		h.loggedInAs(A);
		kcTotals.set(h.scope(), 5);
		h.phase(ClientPhase.LOADING).tick(UNKNOWN);
		assertEquals(AccountState.RETIRED, h.snap().state());
		assertEquals(RetireReason.IDENTITY_LOST, h.snap().lastRetireReason());
		h.phase(ClientPhase.LOGGED_IN).tick(A);
		assertEquals("retired totals never come back", Optional.empty(), kcTotals.get());
	}

	@Test
	public void finding1_loggedInAfterLoadingStillNeedsAFreshTick()
	{
		h.loggedInAs(A);
		h.phase(ClientPhase.LOADING).phase(ClientPhase.LOGGED_IN);
		assertFalse(h.snap().isPublishable());
	}

	// Finding 2: an unsupported world during pending authentication kept the suspended login.

	@Test
	public void finding2_unsupportedWorldWithUnknownIdentityRetiresSuspendedLogin()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		kcTotals.set(before, 7);
		h.hop().tick(UNKNOWN, UNSUPPORTED);
		assertEquals(AccountState.RETIRED, h.snap().state());
		assertEquals(RetireReason.UNSUPPORTED_WORLD, h.snap().lastRetireReason());

		h.tick(A, WORLD);
		assertTrue(h.snap().isPublishable());
		assertFalse("retired session is not resurrected", h.scope().sameLogin(before));
		assertEquals(Optional.empty(), kcTotals.get());
	}

	@Test
	public void finding2_unsupportedWorldTickBeforeLoggedInRetiresSuspendedLogin()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		h.phase(ClientPhase.HOPPING).tick(UNKNOWN, UNSUPPORTED);
		assertEquals(RetireReason.UNSUPPORTED_WORLD, h.snap().lastRetireReason());
		h.phase(ClientPhase.LOGGED_IN).tick(A);
		assertFalse(h.scope().sameLogin(before));
	}

	// Finding 3: LOGGING_IN during suspension was accepted as continuity.

	@Test
	public void finding3_loggingInDuringSuspensionRetires()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		kcTotals.set(before, 3);
		h.phase(ClientPhase.CONNECTION_LOST).phase(ClientPhase.LOGGING_IN);
		assertEquals(AccountState.RETIRED, h.snap().state());
		assertEquals(RetireReason.CONTINUITY_INTERRUPTED, h.snap().lastRetireReason());
		h.phase(ClientPhase.LOADING).phase(ClientPhase.LOGGED_IN).tick(A);
		assertFalse(h.scope().sameLogin(before));
		assertEquals(Optional.empty(), kcTotals.get());
	}

	@Test
	public void finding3_loggingInAfterHopRetires()
	{
		h.loggedInAs(A).phase(ClientPhase.HOPPING).phase(ClientPhase.LOGGING_IN);
		assertEquals(RetireReason.CONTINUITY_INTERRUPTED, h.snap().lastRetireReason());
	}

	// Finding 4: publication ordering and subscription lifecycle.

	@Test
	public void finding4_reentrantInputKeepsPublicationOrderForEveryListener()
	{
		List<Long> second = new ArrayList<>();
		AccountLifecycle lc = h.lifecycle;
		lc.subscribe(s ->
		{
			if (s.state() == AccountState.ACTIVE && s.isPublishable())
			{
				lc.onClientPhase(ClientPhase.HOPPING, h.now);
			}
		});
		lc.subscribe(s -> second.add(s.revision()));
		h.loggedInAs(A);
		for (int i = 1; i < second.size(); i++)
		{
			assertTrue("listener saw revision " + second.get(i) + " after " + second.get(i - 1),
				second.get(i) > second.get(i - 1));
		}
		assertEquals(AccountState.SUSPENDED_CONTINUITY, h.snap().state());
	}

	@Test
	public void finding4_cancelledSubscriptionReceivesNothingFurther()
	{
		List<AccountSnapshot> got = new ArrayList<>();
		Subscription[] victim = new Subscription[1];
		h.lifecycle.subscribe(s ->
		{
			if (victim[0] != null && s.isPublishable())
			{
				victim[0].cancel();
			}
		});
		victim[0] = h.lifecycle.subscribe(got::add);
		int before = got.size();
		h.loggedInAs(A).phase(ClientPhase.LOGIN_SCREEN);
		for (AccountSnapshot s : got.subList(before, got.size()))
		{
			assertFalse("delivered after cancel: " + s, s.isPublishable());
		}
		assertNotEquals(AccountState.RETIRED, got.get(got.size() - 1).state());
	}

	@Test
	public void finding4_differentAccountDuringLoadingDoesNotActivateUntilLoggedIn()
	{
		h.loggedInAs(A).phase(ClientPhase.LOADING).tick(B);
		assertEquals(RetireReason.DIFFERENT_ACCOUNT, h.snap().lastRetireReason());
		assertFalse(h.snap().isPublishable());
		h.phase(ClientPhase.LOGGED_IN).tick(B);
		assertTrue(h.snap().isPublishable());
	}

	// Re-review 2026-11-26 (419a1708): contradictory identity during suspension in a non-LOGGED_IN phase.

	@Test
	public void rereview_contradictoryIdentityDuringHopLoadingRetiresHeldLogin()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		kcTotals.set(before, 7);
		h.phase(ClientPhase.HOPPING).phase(ClientPhase.LOADING).tick(B, WORLD);
		assertEquals(AccountState.RETIRED, h.snap().state());
		assertEquals(RetireReason.DIFFERENT_ACCOUNT, h.snap().lastRetireReason());
		assertFalse("activation still requires LOGGED_IN", h.snap().isPublishable());

		h.phase(ClientPhase.LOGGED_IN).tick(A);
		assertTrue(h.snap().isPublishable());
		assertFalse("held login must not survive a contradictory identity", h.scope().sameLogin(before));
		assertEquals(Optional.empty(), kcTotals.get());
	}

	@Test
	public void rereview_contradictoryIdentityWhileHoppingRetiresHeldLogin()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		h.phase(ClientPhase.HOPPING).tick(B, WORLD);
		assertEquals(RetireReason.DIFFERENT_ACCOUNT, h.snap().lastRetireReason());
		h.phase(ClientPhase.LOGGED_IN).tick(A);
		assertFalse(h.scope().sameLogin(before));
	}

	@Test
	public void rereview_sameIdentityBeforeLoggedInDoesNotRestore()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		h.phase(ClientPhase.HOPPING).phase(ClientPhase.LOADING).tick(A);
		assertEquals(AccountState.SUSPENDED_CONTINUITY, h.snap().state());
		h.phase(ClientPhase.LOGGED_IN).tick(A);
		assertTrue(h.scope().sameLogin(before));
	}

	// Re-review: authority from before LOADING must not be regained after revalidation.

	@Test
	public void rereview_scopeFromBeforeLoadingHasNoAuthorityAfterRevalidation()
	{
		AccountSlot<Integer> epochData = h.lifecycle.epochSlot("epoch-data");
		h.loggedInAs(A);
		AccountScope before = h.scope();
		epochData.set(before, 3);
		java.util.concurrent.atomic.AtomicInteger ran = new java.util.concurrent.atomic.AtomicInteger();
		Runnable late = h.lifecycle.fenced(before, ran::incrementAndGet);

		h.phase(ClientPhase.LOADING).phase(ClientPhase.LOGGED_IN).tick(A);
		AccountScope after = h.scope();
		assertEquals("region change keeps the epoch", before.epoch(), after.epoch());
		assertTrue(before.sameLogin(after));
		late.run();
		assertEquals("callback from before LOADING is discarded", 0, ran.get());
		assertFalse("write with pre-LOADING scope is rejected", epochData.set(before, 4));
		assertFalse(h.lifecycle.isCurrent(before));
		assertEquals("epoch data survives the region change", Optional.of(3), epochData.get(after));
	}

	// Re-review: the consumer gate must check authority when a value is applied.

	@Test
	public void rereview_gateRejectsValueAppliedBeforeQueuedRetirementArrives()
	{
		ConsumerScopeGate gate = new ConsumerScopeGate(h.lifecycle);
		h.loggedInAs(A);
		AccountScope a = h.scope();
		gate.onSnapshot(h.snap());
		// the lifecycle retires A; the retirement snapshot is still queued for the consumer
		h.tick(B);
		assertFalse("stale value must be rejected at application time", gate.accepts(a));
	}
}
