package com.feralenterprises.raccoon.core.account;

import static com.feralenterprises.raccoon.core.account.LifecycleHarness.A;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.B;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.FAILED;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.HASH_A;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.HASH_B;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.UNKNOWN;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.UNSUPPORTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

/** Acceptance cases A01–A06 (R03 §10) for the centralized account lifecycle. */
public class AccountLifecycleTest
{
	private LifecycleHarness h;
	private AccountSlot<Integer> kcTotals;
	private AccountSlot<Boolean> bankWitness;
	private AccountSlot<List<String>> queuedMessages;
	private EpochBaseline kcBaseline;

	@Before
	public void setUp()
	{
		h = new LifecycleHarness();
		kcTotals = h.lifecycle.loginSlot("kc-totals");
		bankWitness = h.lifecycle.epochSlot("bank-witness");
		queuedMessages = h.lifecycle.epochSlot("queued-messages");
		kcBaseline = new EpochBaseline(h.lifecycle, "kc-baseline");
	}

	private void assertNoView()
	{
		assertFalse(h.snap().isPublishable());
		assertFalse(h.snap().scope().isPresent());
		assertEquals(Membership.UNKNOWN, h.snap().membership());
		assertEquals(Optional.empty(), kcTotals.get());
		assertEquals(Optional.empty(), bankWitness.get());
	}

	private void assertRetired(RetireReason reason)
	{
		assertEquals(AccountState.RETIRED, h.snap().state());
		assertEquals(reason, h.snap().lastRetireReason());
		assertNoView();
	}

	// ------------------------------------------------------------------ A01

	@Test
	public void a01_noInitialIdentityProducesNoView()
	{
		assertEquals(AccountState.UNAUTHENTICATED, h.snap().state());
		assertNoView();
		h.start();
		assertEquals(AccountState.UNAUTHENTICATED, h.snap().state());
		assertNoView();
	}

	@Test
	public void a01_signedOutProducesNoView()
	{
		h.start().phase(ClientPhase.LOGIN_SCREEN).tick(A).tick(A);
		assertEquals(AccountState.UNAUTHENTICATED, h.snap().state());
		assertNoView();
	}

	@Test
	public void a01_loggedInWithoutIdentityStaysUnauthenticated()
	{
		h.start().phase(ClientPhase.LOGGED_IN).ticks(50, UNKNOWN).ticks(5, FAILED);
		assertEquals(AccountState.UNAUTHENTICATED, h.snap().state());
		assertNoView();
	}

	@Test
	public void a01_identityLossOutsideTransitionRetires()
	{
		h.loggedInAs(A);
		AccountScope s = h.scope();
		assertTrue(kcTotals.set(s, 7));
		h.tick(UNKNOWN);
		assertRetired(RetireReason.IDENTITY_LOST);
		assertFalse(h.lifecycle.isCurrent(s));
	}

	@Test
	public void a01_readFailureOutsideTransitionRetires()
	{
		h.loggedInAs(A).tick(FAILED);
		assertRetired(RetireReason.IDENTITY_LOST);
	}

	@Test
	public void a01_loadingIsNotAWitnessedTransition()
	{
		h.loggedInAs(A).phase(ClientPhase.LOADING).phase(ClientPhase.LOGGED_IN).tick(A);
		assertEquals(AccountState.ACTIVE, h.snap().state());
		h.phase(ClientPhase.LOADING).phase(ClientPhase.LOGGED_IN).tick(UNKNOWN);
		assertRetired(RetireReason.IDENTITY_LOST);
	}

	// ------------------------------------------------------------------ A02

	@Test
	public void a02_enableWhileLoggedInRequiresFreshIdentity()
	{
		h.start();
		assertNoView();
		h.phase(ClientPhase.LOGGED_IN);
		assertNoView();
		h.tick(A);
		assertEquals(AccountState.ACTIVE, h.snap().state());
		// bank witness and KC baseline must be established in this scope
		assertEquals(Optional.empty(), bankWitness.get());
		assertEquals(Optional.empty(), kcTotals.get());
		assertEquals(OptionalLong.of(0), kcBaseline.observe(h.scope(), 42));
		assertTrue(bankWitness.set(h.scope(), true));
		assertEquals(Optional.of(true), bankWitness.get());
	}

	@Test
	public void a02_reEnableDiscardsEverythingFromThePreviousRun()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		kcTotals.set(before, 10);
		bankWitness.set(before, true);
		kcBaseline.observe(before, 100);
		h.lifecycle.stop();
		assertRetired(RetireReason.PLUGIN_STOPPED);

		h.start();
		assertNoView();
		h.phase(ClientPhase.LOGGED_IN).tick(A);
		AccountScope after = h.scope();
		assertNotEquals(before.loginToken(), after.loginToken());
		assertNotEquals(before.epoch(), after.epoch());
		assertEquals(Optional.empty(), kcTotals.get());
		assertEquals(Optional.empty(), bankWitness.get());
		assertEquals(OptionalLong.of(0), kcBaseline.observe(after, 105));
	}

	@Test
	public void a02_ticksBeforeStartAreIgnored()
	{
		h.lifecycle.onClientPhase(ClientPhase.LOGGED_IN, h.now);
		h.tick(A);
		assertNoView();
		h.start().tick(A);
		// start forgets the phase; a fresh LOGGED_IN must be observed first
		assertNoView();
		h.phase(ClientPhase.LOGGED_IN).tick(A);
		assertTrue(h.snap().isPublishable());
	}

	// ------------------------------------------------------------------ A03

	@Test
	public void a03_switchAtoBtoANeverExposesPriorViews()
	{
		h.loggedInAs(A);
		AccountScope a1 = h.scope();
		kcTotals.set(a1, 11);
		h.phase(ClientPhase.LOGIN_SCREEN);
		assertRetired(RetireReason.LOGOUT);

		h.phase(ClientPhase.LOGGED_IN).tick(B);
		AccountScope b = h.scope();
		assertNotEquals(a1.accountId(), b.accountId());
		assertEquals(Optional.empty(), kcTotals.get());
		kcTotals.set(b, 22);
		assertFalse(kcTotals.set(a1, 99));
		h.phase(ClientPhase.LOGIN_SCREEN).phase(ClientPhase.LOGGED_IN).tick(A);

		AccountScope a2 = h.scope();
		assertEquals("same account, same opaque id", a1.accountId(), a2.accountId());
		assertNotEquals("but a new login", a1.loginToken(), a2.loginToken());
		assertEquals(Optional.empty(), kcTotals.get());
		assertFalse(h.lifecycle.isCurrent(a1));
		assertFalse(h.lifecycle.isCurrent(b));
	}

	@Test
	public void a03_missedLogoutDifferentHashRetiresBeforeActivating()
	{
		h.loggedInAs(A);
		AccountScope a = h.scope();
		kcTotals.set(a, 5);
		int before = h.published.size();
		h.tick(B);

		List<AccountSnapshot> seen = h.published.subList(before, h.published.size());
		assertEquals(2, seen.size());
		assertEquals(AccountState.RETIRED, seen.get(0).state());
		assertEquals(RetireReason.DIFFERENT_ACCOUNT, seen.get(0).lastRetireReason());
		assertEquals(AccountState.ACTIVE, seen.get(1).state());
		assertNotEquals(a.accountId(), h.scope().accountId());
		assertEquals(Optional.empty(), kcTotals.get());
		assertFalse(h.lifecycle.isCurrent(a));
	}

	@Test
	public void a03_identicalDisplayNamesAreDistinguishedByHashOnly()
	{
		// Display names are not an input: two accounts that share one are told apart by hash.
		h.loggedInAs(IdentityReading.of(HASH_A));
		AccountScope first = h.scope();
		kcTotals.set(first, 3);
		h.tick(IdentityReading.of(HASH_A + 1));
		assertNotEquals(first.accountId(), h.scope().accountId());
		assertEquals(Optional.empty(), kcTotals.get());
	}

	@Test
	public void a03_differentAccountAfterHopIsANewLogin()
	{
		h.loggedInAs(A);
		AccountScope a = h.scope();
		kcTotals.set(a, 8);
		h.hop().tick(B);
		assertEquals(AccountState.ACTIVE, h.snap().state());
		assertNotEquals(a.accountId(), h.scope().accountId());
		assertEquals(Optional.empty(), kcTotals.get());
		boolean sawRetire = false;
		for (AccountSnapshot s : h.published)
		{
			sawRetire |= s.lastRetireReason() == RetireReason.DIFFERENT_ACCOUNT;
		}
		assertTrue(sawRetire);
	}

	// ------------------------------------------------------------------ A04

	@Test
	public void a04_hopWithImmediateIdentityRestoresSameLoginInNewEpoch()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		kcTotals.set(before, 5);
		bankWitness.set(before, true);

		h.phase(ClientPhase.HOPPING);
		assertEquals(AccountState.SUSPENDED_CONTINUITY, h.snap().state());
		assertNoView();
		assertFalse(h.lifecycle.isCurrent(before));

		h.phase(ClientPhase.LOADING).phase(ClientPhase.LOGGED_IN).tick(A);
		AccountScope after = h.scope();
		assertTrue(after.sameLogin(before));
		assertNotEquals(before.epoch(), after.epoch());
		assertEquals("same-login totals restored", Optional.of(5), kcTotals.get());
		assertEquals("epoch data reset", Optional.empty(), bankWitness.get());
	}

	@Test
	public void a04_connectionLostAndReconnectRestores()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		kcTotals.set(before, 9);
		h.phase(ClientPhase.CONNECTION_LOST).advanceSeconds(10).phase(ClientPhase.LOGGING_IN)
			.phase(ClientPhase.LOADING).phase(ClientPhase.LOGGED_IN).tick(A);
		assertTrue(h.scope().sameLogin(before));
		assertEquals(Optional.of(9), kcTotals.get());
	}

	@Test
	public void a04_delayedIdentityWithinTickLimitRestores()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		kcTotals.set(before, 4);
		h.hop().ticks(ContinuityLimits.AUTHENTICATION_TICK_LIMIT - 1, UNKNOWN);
		assertEquals(AccountState.SUSPENDED_CONTINUITY, h.snap().state());
		assertNoView();
		h.tick(A);
		assertTrue(h.scope().sameLogin(before));
		assertEquals(Optional.of(4), kcTotals.get());
	}

	@Test
	public void a04_readFailureDuringTransitionIsPending()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		h.hop().tick(FAILED).tick(UNKNOWN).tick(FAILED);
		assertEquals(AccountState.SUSPENDED_CONTINUITY, h.snap().state());
		h.tick(A);
		assertTrue(h.scope().sameLogin(before));
	}

	@Test
	public void a04_repeatedUnknownIdentityRetiresAtTickLimit()
	{
		h.loggedInAs(A);
		kcTotals.set(h.scope(), 4);
		h.hop().ticks(ContinuityLimits.AUTHENTICATION_TICK_LIMIT, UNKNOWN);
		assertRetired(RetireReason.AUTHENTICATION_TIMEOUT);
		h.tick(A);
		assertEquals("a later identity is a new login with cleared totals", Optional.empty(), kcTotals.get());
	}

	@Test
	public void a04_suspensionExpiresAfter120MonotonicSeconds()
	{
		h.loggedInAs(A);
		kcTotals.set(h.scope(), 1);
		h.phase(ClientPhase.CONNECTION_LOST).advanceSeconds(119.9).poll();
		assertEquals(AccountState.SUSPENDED_CONTINUITY, h.snap().state());
		h.advanceSeconds(0.1).poll();
		assertRetired(RetireReason.CONTINUITY_TIMEOUT);
		h.phase(ClientPhase.LOGGED_IN).tick(A);
		assertEquals(Optional.empty(), kcTotals.get());
	}

	@Test
	public void a04_expiryIsCheckedBeforeRestoring()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		h.hop().advanceSeconds(120).tick(A);
		assertFalse(h.scope().sameLogin(before));
		boolean timedOut = false;
		for (AccountSnapshot s : h.published)
		{
			timedOut |= s.lastRetireReason() == RetireReason.CONTINUITY_TIMEOUT;
		}
		assertTrue(timedOut);
	}

	@Test
	public void a04_repeatedHopsNeverExtendTheTimeLimit()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		h.phase(ClientPhase.HOPPING).advanceSeconds(100).phase(ClientPhase.HOPPING).advanceSeconds(20)
			.phase(ClientPhase.LOGGED_IN).tick(A);
		assertFalse(h.scope().sameLogin(before));
	}

	@Test
	public void a04_repeatedHopsNeverResetTheTickLimit()
	{
		h.loggedInAs(A);
		h.hop().ticks(15, UNKNOWN).hop().ticks(5, UNKNOWN);
		assertRetired(RetireReason.AUTHENTICATION_TIMEOUT);
	}

	@Test
	public void a04_whicheverLimitExpiresFirst()
	{
		h.loggedInAs(A);
		h.hop();
		for (int i = 0; i < 5; i++)
		{
			h.advanceSeconds(30).tick(UNKNOWN);
		}
		assertRetired(RetireReason.CONTINUITY_TIMEOUT);
	}

	@Test
	public void a04_logoutLoginScreenAuthenticatorOrDisableDuringSuspensionRetire()
	{
		h.loggedInAs(A).phase(ClientPhase.HOPPING).phase(ClientPhase.LOGIN_SCREEN);
		assertRetired(RetireReason.LOGOUT);

		setUp();
		h.loggedInAs(A).phase(ClientPhase.CONNECTION_LOST).phase(ClientPhase.LOGIN_SCREEN_AUTHENTICATOR);
		assertRetired(RetireReason.AUTHENTICATOR);

		setUp();
		h.loggedInAs(A).phase(ClientPhase.HOPPING);
		h.lifecycle.stop();
		assertRetired(RetireReason.PLUGIN_STOPPED);
	}

	@Test
	public void a04_unsupportedWorldAfterHopRetires()
	{
		h.loggedInAs(A);
		kcTotals.set(h.scope(), 2);
		h.hop().tick(A, UNSUPPORTED);
		assertRetired(RetireReason.UNSUPPORTED_WORLD);
		h.tick(A);
		assertEquals(Optional.empty(), kcTotals.get());
	}

	@Test
	public void a04_missingOrUnrecognizedContinuityRetires()
	{
		// LOGGING_IN without a witnessed hop or connection loss
		h.loggedInAs(A).phase(ClientPhase.LOGGING_IN);
		assertRetired(RetireReason.UNRECOGNIZED_CONTINUITY);

		for (ClientPhase p : Arrays.asList(ClientPhase.STARTING, ClientPhase.UNKNOWN))
		{
			setUp();
			h.loggedInAs(A).phase(ClientPhase.HOPPING).phase(p);
			assertRetired(RetireReason.UNRECOGNIZED_CONTINUITY);
		}
	}

	@Test
	public void a04_noPublicationOrReplayWhileSuspended()
	{
		h.loggedInAs(A);
		AccountScope before = h.scope();
		queuedMessages.set(before, new ArrayList<>(Arrays.asList("kc +1")));
		int publishedBefore = h.published.size();
		h.phase(ClientPhase.HOPPING);

		assertFalse(kcTotals.set(before, 50));
		assertFalse(queuedMessages.set(before, new ArrayList<>()));
		for (AccountSnapshot s : h.published.subList(publishedBefore, h.published.size()))
		{
			assertFalse(s.isPublishable());
		}
		AtomicInteger ran = new AtomicInteger();
		h.lifecycle.fenced(before, ran::incrementAndGet).run();
		assertEquals(0, ran.get());

		h.phase(ClientPhase.LOADING).phase(ClientPhase.LOGGED_IN).tick(A);
		assertEquals("queued messages are not replayed", Optional.empty(), queuedMessages.get());
		h.lifecycle.fenced(before, ran::incrementAndGet).run();
		assertEquals("callbacks from before the hop stay fenced", 0, ran.get());
	}

	@Test
	public void a04_newEpochBaselineProducesZeroDelta()
	{
		h.loggedInAs(A);
		AccountScope s1 = h.scope();
		assertEquals(OptionalLong.of(0), kcBaseline.observe(s1, 10));
		assertEquals(OptionalLong.of(2), kcBaseline.observe(s1, 12));
		h.hop().tick(A);
		AccountScope s2 = h.scope();
		assertEquals("gap gain is not counted", OptionalLong.of(0), kcBaseline.observe(s2, 20));
		assertEquals(OptionalLong.of(1), kcBaseline.observe(s2, 21));
		assertEquals("stale scope rejected", OptionalLong.empty(), kcBaseline.observe(s1, 30));
	}

	// ------------------------------------------------------------------ A05

	@Test
	public void a05_callbacksAfterStopAreDiscarded()
	{
		h.loggedInAs(A);
		AtomicInteger ran = new AtomicInteger();
		Runnable cb = h.lifecycle.fenced(h.scope(), ran::incrementAndGet);
		h.lifecycle.stop();
		cb.run();
		assertEquals(0, ran.get());
	}

	@Test
	public void a05_callbacksAfterScopeSwitchAreDiscarded()
	{
		h.loggedInAs(A);
		AtomicInteger ran = new AtomicInteger();
		Runnable cb = h.lifecycle.fenced(h.scope(), ran::incrementAndGet);
		h.tick(B);
		cb.run();
		assertEquals(0, ran.get());
	}

	@Test
	public void a05_callbacksAfterEpochChangeAreDiscarded()
	{
		h.loggedInAs(A);
		AccountScope old = h.scope();
		AtomicInteger ran = new AtomicInteger();
		Runnable cb = h.lifecycle.fenced(old, ran::incrementAndGet);
		h.hop().tick(A);
		cb.run();
		assertEquals(0, ran.get());
		assertFalse(kcTotals.set(old, 1));
		h.lifecycle.fenced(h.scope(), ran::incrementAndGet).run();
		assertEquals("current scope still runs", 1, ran.get());
	}

	@Test
	public void a05_callbacksAcrossStopAndRestartAreDiscarded()
	{
		h.loggedInAs(A);
		AccountScope old = h.scope();
		AtomicInteger ran = new AtomicInteger();
		Runnable cb = h.lifecycle.fenced(old, ran::incrementAndGet);
		h.lifecycle.stop();
		h.loggedInAs(A);
		cb.run();
		assertEquals(0, ran.get());
		assertFalse(h.lifecycle.isCurrent(old));
	}

	// ------------------------------------------------------------------ A06

	@Test
	public void a06_unsupportedWorldYieldsNoObservations()
	{
		h.start().phase(ClientPhase.LOGGED_IN).tick(A, UNSUPPORTED).tick(A, UNSUPPORTED);
		assertNoView();
		assertFalse(kcTotals.set(null, 1));
	}

	@Test
	public void a06_movingToUnsupportedWorldRetires()
	{
		h.loggedInAs(A);
		kcTotals.set(h.scope(), 3);
		h.tick(A, UNSUPPORTED);
		assertRetired(RetireReason.UNSUPPORTED_WORLD);
	}

	@Test
	public void a06_unknownMembershipNeverBecomesPositive()
	{
		h.loggedInAs(A);
		assertEquals(Membership.UNKNOWN, h.snap().membership());
		h.tick(A, WorldContext.supported(Membership.MEMBER));
		assertEquals(Membership.MEMBER, h.snap().membership());
		h.tick(A, WorldContext.supported(Membership.UNKNOWN));
		assertEquals("unknown reading does not keep MEMBER", Membership.UNKNOWN, h.snap().membership());
		h.tick(A, WorldContext.supported(Membership.MEMBER)).phase(ClientPhase.HOPPING);
		assertEquals(Membership.UNKNOWN, h.snap().membership());
		h.phase(ClientPhase.LOGGED_IN).tick(A, WorldContext.supported(Membership.UNKNOWN));
		assertEquals("restored epoch does not inherit MEMBER", Membership.UNKNOWN, h.snap().membership());
	}

	// ------------------------------------------------------------------ cross-cutting

	@Test
	public void accountHashNeverAppearsInDiagnostics()
	{
		h.loggedInAs(A).tick(A, WorldContext.supported(Membership.MEMBER));
		AccountScope a = h.scope();
		h.hop().tick(A).tick(B).phase(ClientPhase.LOGIN_SCREEN);
		List<String> diagnostics = new ArrayList<>();
		for (AccountSnapshot s : h.published)
		{
			diagnostics.add(s.toString());
		}
		diagnostics.add(a.toString());
		diagnostics.add(A.toString());
		diagnostics.add(B.toString());
		diagnostics.add(kcTotals.toString());
		diagnostics.add(LifecycleHarness.WORLD.toString());
		for (String d : diagnostics)
		{
			for (long hash : new long[]{HASH_A, HASH_B})
			{
				for (String form : new String[]{Long.toString(hash), Long.toHexString(hash), Long.toHexString(hash).toUpperCase()})
				{
					if (d.contains(form))
					{
						fail("diagnostic exposes an account hash: " + d);
					}
				}
			}
		}
	}

	@Test
	public void snapshotsArePublishedInOrder()
	{
		h.loggedInAs(A).hop().tick(A).tick(B).phase(ClientPhase.LOGIN_SCREEN);
		for (int i = 1; i < h.published.size(); i++)
		{
			assertTrue(h.published.get(i).revision() > h.published.get(i - 1).revision());
		}
		assertEquals(h.snap().revision(), h.published.get(h.published.size() - 1).revision());
	}

	@Test(expected = IllegalArgumentException.class)
	public void slotNamesAreUnique()
	{
		h.lifecycle.epochSlot("kc-totals");
	}

	@Test
	public void suspendedSnapshotCarriesNoScope()
	{
		h.loggedInAs(A).phase(ClientPhase.HOPPING);
		assertEquals(AccountState.SUSPENDED_CONTINUITY, h.snap().state());
		assertFalse(h.snap().scope().isPresent());
		assertFalse(h.snap().isPublishable());
	}
}
