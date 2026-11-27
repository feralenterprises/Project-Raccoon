package com.feralenterprises.raccoon.core.bank;

import static com.feralenterprises.raccoon.core.bank.BankHarness.B;
import static com.feralenterprises.raccoon.core.bank.BankHarness.COINS;
import static com.feralenterprises.raccoon.core.bank.BankHarness.LOBSTER;
import static com.feralenterprises.raccoon.core.bank.BankHarness.slots;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.feralenterprises.raccoon.core.account.ClientPhase;
import com.feralenterprises.raccoon.core.account.IdentityReading;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

/**
 * Regressions for the 2026-11-26 independent FR-003 review (issue #1). Each reproduced a finding
 * against handback fa6749c9 before the repair.
 */
public class BankRepairRegressionTest
{
	private BankHarness h;

	@Before
	public void setUp()
	{
		h = new BankHarness();
	}

	private void qualify()
	{
		h.ticks(BankObservationTracker.POPULATED_STREAK);
		assertEquals(BankStatus.QUALIFIED_CURRENT, h.status());
	}

	private void assertRestartedAtOne()
	{
		assertEquals(BankReason.EVIDENCE_CHANGED, h.reason());
		assertEquals(1, h.tracker.snapshot().streak());
		assertEquals(Ownership.Kind.UNKNOWN, h.tracker.bankOwnership(COINS).kind());
	}

	// 1. Full B04 stability contract

	@Test
	public void b04_rawSlotChangeWithSameAggregateRestartsStreak()
	{
		qualify();
		h.bankSlots = slots(RawSlot.item(COINS, 500), RawSlot.item(COINS, 500), RawSlot.item(LOBSTER, 20));
		h.tick();
		assertRestartedAtOne();
	}

	@Test
	public void b04_carriedReportingChangeRestartsStreak()
	{
		qualify();
		h.inventory = ContainerRead.missing();
		h.tick();
		assertRestartedAtOne();
	}

	@Test
	public void b04_carriedMapChangeRestartsStreak()
	{
		qualify();
		h.inventory = ContainerRead.present(Arrays.asList(RawSlot.item(COINS, 6)));
		h.tick();
		assertRestartedAtOne();
	}

	@Test
	public void b04_equipmentMapChangeRestartsStreak()
	{
		qualify();
		h.equipment = ContainerRead.present(Arrays.asList(RawSlot.item(LOBSTER, 1)));
		h.tick();
		assertRestartedAtOne();
	}

	// 2. Single qualification announcement

	@Test
	public void b01_unchangedRevalidationIsAnnouncedOnce()
	{
		qualify();
		long revision = h.tracker.snapshot().revision();
		BankEvidence accepted = h.tracker.snapshot().current().get();
		h.ticks(6);
		assertEquals(BankStatus.QUALIFIED_CURRENT, h.status());
		assertEquals("unchanged qualification is not re-announced", revision, h.tracker.snapshot().revision());
		assertTrue(accepted == h.tracker.snapshot().current().get());
		long announcements = h.published.stream().filter(s -> s.status() == BankStatus.QUALIFIED_CURRENT).count();
		assertEquals(1, announcements);
	}

	// 3. Historical evidence and lifecycle changes while the bank is closed

	private void qualifyAndClose()
	{
		qualify();
		h.visibility = BankVisibility.CLOSED;
		h.tick();
		assertEquals(BankStatus.HISTORICAL, h.status());
	}

	@Test
	public void history_clearedOnStopWhileClosed()
	{
		qualifyAndClose();
		h.lifecycle.stop();
		assertEquals(BankStatus.WAITING, h.status());
		assertFalse(h.tracker.snapshot().historical().isPresent());
	}

	@Test
	public void history_clearedOnLogoutWhileClosed()
	{
		qualifyAndClose();
		h.phase(ClientPhase.LOGIN_SCREEN);
		assertEquals(BankStatus.WAITING, h.status());
		assertFalse(h.tracker.snapshot().historical().isPresent());
	}

	@Test
	public void history_clearedOnAccountChangeWhileClosed()
	{
		qualifyAndClose();
		h.lifecycle.onGameTick(B, BankHarness.WORLD, h.now); // missed logout: A retired, B active
		assertEquals(BankStatus.WAITING, h.status());
		assertFalse(h.tracker.snapshot().historical().isPresent());
	}

	@Test
	public void history_maskedWhileAccountNotPublishable()
	{
		qualifyAndClose();
		h.phase(ClientPhase.LOADING);
		assertFalse(h.tracker.snapshot().historical().isPresent());
		h.phase(ClientPhase.LOGGED_IN).tick();
		assertEquals("same login again: provenance only", BankStatus.HISTORICAL, h.status());
	}

	// 4. Authoritative bank-evidence fencing

	@Test
	public void fencing_evidenceStaleAfterBankCloseEvenThoughScopeIsCurrent()
	{
		qualify();
		BankEvidence e = h.tracker.snapshot().current().get();
		AtomicInteger ran = new AtomicInteger();
		Runnable late = h.tracker.fenced(e, ran::incrementAndGet);
		h.visibility = BankVisibility.CLOSED;
		h.tick();
		assertTrue("account scope is still current", h.lifecycle.isCurrent(e.scope()));
		assertFalse("but the bank evidence is not", h.tracker.isCurrent(e));
		late.run();
		assertEquals(0, ran.get());
	}

	@Test
	public void fencing_oldEvidenceStaysStaleAfterRequalificationInSameScope()
	{
		qualify();
		BankEvidence first = h.tracker.snapshot().current().get();
		h.bankSlots = slots(RawSlot.item(COINS, -1));
		h.tick(); // failed revalidation
		assertFalse(h.tracker.isCurrent(first));
		h.bankSlots = slots(RawSlot.item(COINS, 1000), RawSlot.item(LOBSTER, 20), RawSlot.empty());
		h.ticks(BankObservationTracker.RETRY_TICKS + BankObservationTracker.POPULATED_STREAK);
		BankEvidence second = h.tracker.snapshot().current().get();
		assertEquals(first.scope(), second.scope());
		assertFalse("same contents, same scope, but an older generation", h.tracker.isCurrent(first));
		assertTrue(h.tracker.isCurrent(second));
	}

	// 5. Before/after identity and phase, via the central lifecycle

	@Test
	public void capture_identityChangedBetweenBeforeAndAfterIsRejected()
	{
		h.identityAfter = B;
		h.ticks(4);
		assertEquals(BankReason.IDENTITY_CHANGED, h.reason());
		assertFalse(h.status() == BankStatus.QUALIFIED_CURRENT);
	}

	@Test
	public void capture_identityNotConfirmedByLifecycleIsRejected()
	{
		h.identityBefore = B;
		h.identityAfter = B;
		h.ticks(4);
		assertEquals(BankReason.IDENTITY_CHANGED, h.reason());
		assertFalse(h.status() == BankStatus.QUALIFIED_CURRENT);
	}

	@Test
	public void capture_unknownIdentityIsRejected()
	{
		h.identityBefore = IdentityReading.unknown();
		h.ticks(4);
		assertEquals(BankReason.IDENTITY_CHANGED, h.reason());
		assertFalse(h.status() == BankStatus.QUALIFIED_CURRENT);
	}

	@Test
	public void capture_phaseLeavingLoggedInIsRejected()
	{
		h.phaseAfter = ClientPhase.LOADING;
		h.ticks(4);
		assertEquals(BankReason.IDENTITY_CHANGED, h.reason());
		assertFalse(h.status() == BankStatus.QUALIFIED_CURRENT);
	}

	// Donor bounds and cadence

	@Test
	public void bounds_oversizedBankIsRejected()
	{
		List<RawSlot> big = new ArrayList<>();
		for (int i = 0; i < 4097; i++)
		{
			big.add(RawSlot.empty());
		}
		big.set(0, RawSlot.item(COINS, 1));
		h.bankSlots = big;
		h.ticks(4);
		assertEquals(BankReason.CONTAINER_TOO_LARGE, h.reason());
	}

	@Test
	public void bounds_oversizedCarriedIsRejected()
	{
		List<RawSlot> big = new ArrayList<>();
		for (int i = 0; i < 41; i++)
		{
			big.add(RawSlot.empty());
		}
		h.inventory = ContainerRead.present(big);
		h.ticks(4);
		assertEquals(BankReason.CONTAINER_TOO_LARGE, h.reason());
	}

	@Test
	public void cadence_failedCaptureWaitsBeforeRetrying()
	{
		h.captureThrows = true;
		h.ticks(6);
		assertEquals("attempts on ticks 1 and 4 only", 2, h.tracker.captureAttempts());
	}

	@Test
	public void cadence_changingSampleIsResampledNextTick()
	{
		qualify();
		long attempts = h.tracker.captureAttempts();
		h.bankSlots = slots(RawSlot.item(COINS, 7));
		h.tick().tick();
		assertEquals(attempts + 2, h.tracker.captureAttempts());
		assertEquals(BankStatus.QUALIFIED_CURRENT, h.status());
	}
}
