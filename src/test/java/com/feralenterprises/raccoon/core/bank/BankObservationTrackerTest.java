package com.feralenterprises.raccoon.core.bank;

import static com.feralenterprises.raccoon.core.bank.BankHarness.A;
import static com.feralenterprises.raccoon.core.bank.BankHarness.B;
import static com.feralenterprises.raccoon.core.bank.BankHarness.COINS;
import static com.feralenterprises.raccoon.core.bank.BankHarness.LOBSTER;
import static com.feralenterprises.raccoon.core.bank.BankHarness.WORLD;
import static com.feralenterprises.raccoon.core.bank.BankHarness.slots;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.ClientPhase;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

/** Acceptance cases B01–B10 (R03, FR-003) for read-only bank observation. */
public class BankObservationTrackerTest
{
	private static final int ABSENT = 4151;

	private BankHarness h;

	@Before
	public void setUp()
	{
		h = new BankHarness();
	}

	private void assertNoClaims()
	{
		assertEquals(Ownership.Kind.UNKNOWN, h.tracker.bankOwnership(COINS).kind());
		assertEquals(Ownership.Kind.UNKNOWN, h.tracker.bankOwnership(ABSENT).kind());
		assertEquals(Ownership.Kind.UNKNOWN, h.tracker.combinedOwnership(COINS).kind());
		assertFalse(h.tracker.snapshot().current().isPresent());
	}

	private void assertQualified()
	{
		assertEquals(BankStatus.QUALIFIED_CURRENT, h.status());
		assertEquals(Ownership.Kind.OWNED, h.tracker.bankOwnership(COINS).kind());
	}

	private void qualify()
	{
		h.ticks(BankObservationTracker.POPULATED_STREAK);
		assertQualified();
	}

	// ------------------------------------------------------------------ B01

	@Test
	public void b01_twoMatchingPopulatedConsecutiveTicksQualify()
	{
		h.tick();
		assertEquals(BankStatus.WAITING, h.status());
		assertNoClaims();
		h.tick();
		assertQualified();
		assertEquals(1000, h.tracker.bankOwnership(COINS).quantity());
		assertEquals(Ownership.Kind.NOT_OWNED, h.tracker.bankOwnership(ABSENT).kind());
		BankEvidence e = h.tracker.snapshot().current().get();
		assertEquals(h.tick - 1, e.firstTick());
		assertEquals(h.tick, e.lastTick());
	}

	@Test
	public void b01_unknownWitnessPermitsDegradedOnly()
	{
		h.witness = TabWitness.unknown();
		h.ticks(10);
		assertEquals(BankStatus.DEGRADED_POPULATED, h.status());
		assertEquals(BankReason.WITNESS_UNKNOWN, h.reason());
		assertNoClaims();
	}

	@Test
	public void b01_negativeWitnessCountIsUnknown()
	{
		h.witness = TabWitness.of(1, -1, 0, 0, 0, 0, 0, 0, 0);
		h.ticks(5);
		assertEquals(BankStatus.DEGRADED_POPULATED, h.status());
		assertNoClaims();
	}

	@Test
	public void b01_witnessBecomingKnownNeedsAFreshStreak()
	{
		h.witness = TabWitness.unknown();
		h.ticks(4);
		h.witness = TabWitness.of(1, 0, 0, 0, 0, 0, 0, 0, 0);
		h.tick();
		assertEquals(BankStatus.WAITING, h.status());
		h.tick();
		assertQualified();
	}

	// ------------------------------------------------------------------ B02

	@Test
	public void b02_emptyBankNeedsThreeMatchingObservations()
	{
		h.bankSlots = slots(RawSlot.empty(), RawSlot.empty());
		h.witness = TabWitness.of(0, 0, 0, 0, 0, 0, 0, 0, 0);
		h.ticks(2);
		assertEquals(BankStatus.WAITING, h.status());
		assertNoClaims();
		h.tick();
		assertEquals(BankStatus.QUALIFIED_CURRENT, h.status());
		assertEquals(Ownership.Kind.NOT_OWNED, h.tracker.bankOwnership(COINS).kind());
		assertTrue(h.tracker.snapshot().current().get().isEmptyBank());
	}

	@Test
	public void b02_emptyBankWithUnknownWitnessNeverQualifies()
	{
		h.bankSlots = slots(RawSlot.empty());
		h.witness = TabWitness.unknown();
		h.ticks(10);
		assertEquals(BankStatus.WAITING, h.status());
		assertNoClaims();
	}

	@Test
	public void b02_placeholderOnlyBankIsEmpty()
	{
		h.bankSlots = slots(RawSlot.of(1000, 0, ItemKind.PLACEHOLDER));
		h.witness = TabWitness.of(1, 0, 0, 0, 0, 0, 0, 0, 0);
		h.ticks(2);
		assertEquals("an empty bank needs three", BankStatus.WAITING, h.status());
		h.tick();
		assertEquals(BankStatus.QUALIFIED_CURRENT, h.status());
	}

	// ------------------------------------------------------------------ B03

	@Test
	public void b03_pinResetsAndRevokes()
	{
		qualify();
		h.visibility = BankVisibility.PIN;
		long attempts = h.tracker.captureAttempts();
		h.tick();
		assertEquals(BankReason.PIN, h.reason());
		assertNoClaims();
		assertEquals("no capture behind a PIN", attempts, h.tracker.captureAttempts());
		h.visibility = BankVisibility.OPEN;
		h.tick();
		assertEquals("fresh streak required", BankStatus.HISTORICAL, h.status());
		h.tick();
		assertQualified();
	}

	@Test
	public void b03_hiddenBankRevokes()
	{
		qualify();
		h.visibility = BankVisibility.CLOSED;
		h.tick();
		assertEquals(BankReason.BANK_CLOSED, h.reason());
		assertNoClaims();
	}

	@Test
	public void b03_missingContainerRevokes()
	{
		qualify();
		h.bankOverride = ContainerRead.missing();
		h.tick();
		assertEquals(BankReason.MISSING_CONTAINER, h.reason());
		assertNoClaims();
	}

	@Test
	public void b03_readExceptionRevokes()
	{
		qualify();
		h.captureThrows = true;
		h.tick();
		assertEquals(BankReason.READ_FAILURE, h.reason());
		assertNoClaims();
		h.captureThrows = false;
		h.tick();
		assertFalse(h.status() == BankStatus.QUALIFIED_CURRENT);
		h.tick();
		assertQualified();
	}

	@Test
	public void b03_unreadableContainerAndVisibilityRevoke()
	{
		qualify();
		h.bankOverride = ContainerRead.unreadable();
		h.tick();
		assertEquals(BankReason.READ_FAILURE, h.reason());
		h.bankOverride = null;
		qualify();
		h.visibilityThrows = true;
		h.tick();
		assertEquals(BankReason.VISIBILITY_UNREADABLE, h.reason());
		assertNoClaims();
	}

	// ------------------------------------------------------------------ B04

	@Test
	public void b04_changedContentsResetsAndRevokes()
	{
		qualify();
		h.bankSlots = slots(RawSlot.item(COINS, 999), RawSlot.item(LOBSTER, 20), RawSlot.empty());
		h.tick();
		assertEquals(BankReason.EVIDENCE_CHANGED, h.reason());
		assertNoClaims();
		h.tick();
		assertQualified();
		assertEquals(999, h.tracker.bankOwnership(COINS).quantity());
	}

	@Test
	public void b04_changedWitnessResetsAndRevokes()
	{
		qualify();
		h.witness = TabWitness.of(2, 0, 0, 0, 0, 0, 0, 0, 0);
		h.tick();
		assertEquals(BankReason.EVIDENCE_CHANGED, h.reason());
		assertNoClaims();
	}

	@Test
	public void b04_nonconsecutiveTickResetsAndRevokes()
	{
		qualify();
		h.skipTick().tick();
		assertEquals(BankReason.NONCONSECUTIVE_TICK, h.reason());
		assertNoClaims();
		h.tick();
		assertQualified();
	}

	@Test
	public void b04_duplicateTickResetsRevokesAndDoesNotCapture()
	{
		qualify();
		long attempts = h.tracker.captureAttempts();
		h.sameTick();
		assertEquals(BankReason.DUPLICATE_TICK, h.reason());
		assertEquals("one attempt per tick", attempts, h.tracker.captureAttempts());
		assertNoClaims();
		h.tick();
		assertFalse(h.status() == BankStatus.QUALIFIED_CURRENT);
		h.tick();
		assertQualified();
	}

	@Test
	public void b04_scopeRevalidationAfterLoadingRevokesAndNeedsFreshStreak()
	{
		qualify();
		AccountScope before = h.scope();
		h.phase(ClientPhase.LOADING);
		assertNoClaims();
		h.phase(ClientPhase.LOGGED_IN).tick(); // lifecycle revalidates: same epoch, new authority
		assertTrue(before.sameEpoch(h.scope()));
		assertFalse(before.equals(h.scope()));
		assertFalse("old evidence never regains authority", h.status() == BankStatus.QUALIFIED_CURRENT);
		assertNoClaims();
		h.tick();
		assertQualified();
		assertEquals(h.scope(), h.tracker.snapshot().current().get().scope());
	}

	// ------------------------------------------------------------------ B05

	@Test
	public void b05_filteredViewQualifiesWhenUnderlyingContainerComplete()
	{
		// the view (search, Bank Tags) is never read; only the BANK container counts
		h.bankSlots = slots(RawSlot.item(COINS, 1), RawSlot.item(LOBSTER, 2), RawSlot.item(ABSENT, 1), RawSlot.empty());
		h.witness = TabWitness.of(2, 1, 0, 0, 0, 0, 0, 0, 0);
		h.ticks(2);
		assertQualified();
	}

	@Test
	public void b05_incompleteContainerNeverQualifies()
	{
		h.bankSlots = slots(RawSlot.item(COINS, 1));
		h.witness = TabWitness.of(2, 1, 0, 0, 0, 0, 0, 0, 0);
		h.ticks(4);
		assertEquals(BankReason.WITNESS_EXCEEDS_SLOTS, h.reason());
		assertNoClaims();
	}

	// ------------------------------------------------------------------ B06

	@Test
	public void b06_placeholdersFillerAndNotedAreExcluded()
	{
		h.bankSlots = slots(RawSlot.item(COINS, 10), RawSlot.of(LOBSTER, 0, ItemKind.PLACEHOLDER),
			RawSlot.of(20594, 1, ItemKind.FILLER), RawSlot.of(380, 7, ItemKind.NOTED));
		h.ticks(2);
		assertQualified();
		assertEquals(Ownership.Kind.NOT_OWNED, h.tracker.bankOwnership(LOBSTER).kind());
		assertEquals(Ownership.Kind.NOT_OWNED, h.tracker.bankOwnership(20594).kind());
		assertEquals(Ownership.Kind.NOT_OWNED, h.tracker.bankOwnership(380).kind());
		assertEquals(1, h.tracker.snapshot().current().get().contents().size());
	}

	private void assertInvalid(BankReason expected)
	{
		h.ticks(3);
		assertEquals(expected, h.reason());
		assertNoClaims();
	}

	@Test
	public void b06_malformedQuantitiesAreRejected()
	{
		h.bankSlots = slots(RawSlot.item(COINS, 0));
		assertInvalid(BankReason.MALFORMED_QUANTITY);
		setUp();
		h.bankSlots = slots(RawSlot.item(COINS, -5));
		assertInvalid(BankReason.MALFORMED_QUANTITY);
		setUp();
		h.bankSlots = slots(RawSlot.of(-1, 3, ItemKind.ITEM));
		assertInvalid(BankReason.MALFORMED_QUANTITY);
		setUp();
		h.bankSlots = slots(RawSlot.of(LOBSTER, -1, ItemKind.PLACEHOLDER));
		assertInvalid(BankReason.MALFORMED_QUANTITY);
	}

	@Test
	public void b06_malformedSlotsAreRejected()
	{
		h.bankSlots = slots(RawSlot.of(-7, 0, ItemKind.ITEM));
		assertInvalid(BankReason.MALFORMED_SLOT);
		setUp();
		h.bankSlots = new ArrayList<>(Arrays.asList(RawSlot.item(COINS, 1), null));
		assertInvalid(BankReason.MALFORMED_SLOT);
	}

	@Test
	public void b06_unreadableDefinitionIsRejected()
	{
		h.bankSlots = slots(RawSlot.item(COINS, 1), RawSlot.of(LOBSTER, 3, ItemKind.UNREADABLE));
		assertInvalid(BankReason.UNREADABLE_DEFINITION);
	}

	@Test
	public void b06_overflowIsRejected()
	{
		h.bankSlots = slots(RawSlot.item(COINS, Integer.MAX_VALUE), RawSlot.item(COINS, 1));
		assertInvalid(BankReason.OVERFLOW);
	}

	@Test
	public void b06_combinedBankAndCarriedOverflowIsRejected()
	{
		h.bankSlots = slots(RawSlot.item(COINS, Integer.MAX_VALUE));
		h.inventory = ContainerRead.present(Arrays.asList(RawSlot.item(COINS, 1)));
		assertInvalid(BankReason.OVERFLOW);
	}

	@Test
	public void b06_witnessOverflowIsRejected()
	{
		h.witness = TabWitness.of(Integer.MAX_VALUE, 1, 0, 0, 0, 0, 0, 0, 0);
		assertInvalid(BankReason.OVERFLOW);
	}

	// ------------------------------------------------------------------ B07

	@Test
	public void b07_missingCarriedContainersAreUnreportedNotEmpty()
	{
		h.inventory = ContainerRead.missing();
		h.ticks(2);
		assertQualified();
		BankEvidence e = h.tracker.snapshot().current().get();
		assertFalse(e.inventory().isReported());
		assertTrue(e.equipment().isReported());
		assertEquals("bank claim still allowed", 1000, h.tracker.bankOwnership(COINS).quantity());
		assertEquals("combined claim needs both carried containers", Ownership.Kind.UNKNOWN,
			h.tracker.combinedOwnership(COINS).kind());
		assertEquals(Ownership.Kind.UNKNOWN, h.tracker.combinedOwnership(ABSENT).kind());
	}

	@Test
	public void b07_reportedCarriedItemsCombine()
	{
		h.equipment = ContainerRead.present(Arrays.asList(RawSlot.item(COINS, 2)));
		h.ticks(2);
		assertEquals(1007, h.tracker.combinedOwnership(COINS).quantity());
		assertEquals(Ownership.Kind.NOT_OWNED, h.tracker.combinedOwnership(ABSENT).kind());
	}

	@Test
	public void b07_presentButUnreadableCarriedContainerInvalidates()
	{
		qualify();
		h.equipment = ContainerRead.unreadable();
		h.tick();
		assertEquals(BankReason.CARRIED_UNREADABLE, h.reason());
		assertNoClaims();
	}

	@Test
	public void b07_malformedCarriedSlotInvalidates()
	{
		h.inventory = ContainerRead.present(Arrays.asList(RawSlot.item(COINS, 0)));
		assertInvalid(BankReason.MALFORMED_QUANTITY);
	}

	@Test
	public void b07_notedCarriedItemsBlockCombinedClaims()
	{
		h.inventory = ContainerRead.present(Arrays.asList(RawSlot.of(380, 4, ItemKind.NOTED)));
		h.ticks(2);
		assertQualified();
		assertEquals(Ownership.Kind.UNKNOWN, h.tracker.combinedOwnership(LOBSTER).kind());
	}

	@Test
	public void b07_carriedChangesDoNotResetTheBankStreak()
	{
		h.tick();
		h.inventory = ContainerRead.present(Arrays.asList(RawSlot.item(COINS, 6)));
		h.tick();
		assertQualified();
		assertEquals(1006, h.tracker.combinedOwnership(COINS).quantity());
	}

	// ------------------------------------------------------------------ B08

	@Test
	public void b08_identityChangeDuringCaptureRejectsResult()
	{
		qualify();
		h.duringCapture = () -> h.lifecycle.onGameTick(B, WORLD, h.now); // account B appears mid-capture
		h.tick();
		assertEquals(BankReason.SCOPE_CHANGED, h.reason());
		assertNoClaims();
		h.identity = B;
		h.tick();
		assertFalse("no cross-account history", h.tracker.snapshot().historical().isPresent());
		assertEquals(BankStatus.WAITING, h.status());
	}

	@Test
	public void b08_hopDuringCaptureRejectsResult()
	{
		h.tick();
		h.duringCapture = () -> h.lifecycle.onClientPhase(ClientPhase.HOPPING, h.now);
		h.tick();
		assertEquals(BankReason.SCOPE_CHANGED, h.reason());
		assertNoClaims();
	}

	@Test
	public void b08_scopeChangeRevokesImmediatelyWithoutATick()
	{
		qualify();
		h.lifecycle.onClientPhase(ClientPhase.HOPPING, h.now);
		assertNoClaims();
		assertFalse(h.status() == BankStatus.QUALIFIED_CURRENT);
	}

	@Test
	public void b08_accountSwitchNeverShowsPriorHistory()
	{
		qualify();
		h.visibility = BankVisibility.CLOSED;
		h.tick();
		assertEquals(BankStatus.HISTORICAL, h.status());
		h.phase(ClientPhase.LOGIN_SCREEN);
		h.identity = B;
		h.phase(ClientPhase.LOGGED_IN).tick();
		assertEquals(BankStatus.WAITING, h.status());
		assertFalse(h.tracker.snapshot().historical().isPresent());
	}

	// ------------------------------------------------------------------ B09

	@Test
	public void b09_closingRevokesAndKeepsSameLoginHistoryOnly()
	{
		qualify();
		BankEvidence accepted = h.tracker.snapshot().current().get();
		h.visibility = BankVisibility.CLOSED;
		h.tick();
		assertEquals(BankStatus.HISTORICAL, h.status());
		assertEquals(accepted.lastTick(), h.tracker.snapshot().historical().get().lastTick());
		assertNoClaims();
	}

	@Test
	public void b09_noCaptureAttemptsWhileClosed()
	{
		h.visibility = BankVisibility.CLOSED;
		h.ticks(20);
		assertEquals(0, h.tracker.captureAttempts());
	}

	@Test
	public void b09_reopeningRequiresAFreshStreak()
	{
		qualify();
		h.visibility = BankVisibility.CLOSED;
		h.ticks(3);
		h.visibility = BankVisibility.OPEN;
		h.tick();
		assertEquals(BankStatus.HISTORICAL, h.status());
		assertNoClaims();
		h.tick();
		assertQualified();
	}

	@Test
	public void b09_historyIsClearedWhenTheLoginRetires()
	{
		qualify();
		h.visibility = BankVisibility.CLOSED;
		h.tick();
		h.phase(ClientPhase.LOGIN_SCREEN).phase(ClientPhase.LOGGED_IN).tick();
		assertEquals("same account, new login: no history", BankStatus.WAITING, h.status());
	}

	@Test
	public void b09_historySurvivesSameLoginHopButNeverBecomesCurrent()
	{
		qualify();
		h.visibility = BankVisibility.CLOSED;
		h.tick();
		h.phase(ClientPhase.HOPPING).phase(ClientPhase.LOADING).phase(ClientPhase.LOGGED_IN).tick();
		assertEquals(BankStatus.HISTORICAL, h.status());
		assertNoClaims();
	}

	// ------------------------------------------------------------------ B10

	@Test
	public void b10_failedEvaluationAfterPopulatedAcceptanceRevokes()
	{
		qualify();
		h.bankSlots = slots(RawSlot.item(COINS, -1));
		h.tick();
		assertEquals(BankReason.MALFORMED_QUANTITY, h.reason());
		assertNoClaims();
		h.bankSlots = slots(RawSlot.item(COINS, 1000), RawSlot.item(LOBSTER, 20), RawSlot.empty());
		h.tick();
		assertNoClaims();
		h.tick();
		assertQualified();
	}

	@Test
	public void b10_preReadFailureAfterEmptyAcceptanceRevokes()
	{
		h.bankSlots = slots(RawSlot.empty());
		h.witness = TabWitness.of(0, 0, 0, 0, 0, 0, 0, 0, 0);
		h.ticks(3);
		assertEquals(BankStatus.QUALIFIED_CURRENT, h.status());
		h.visibility = BankVisibility.PIN;
		h.tick();
		assertNoClaims();
		h.visibility = BankVisibility.OPEN;
		h.ticks(2);
		assertFalse("empty recovery needs three again", h.status() == BankStatus.QUALIFIED_CURRENT);
		h.tick();
		assertEquals(BankStatus.QUALIFIED_CURRENT, h.status());
	}

	@Test
	public void b10_failedEvaluationAfterEmptyAcceptanceRevokes()
	{
		h.bankSlots = slots(RawSlot.empty());
		h.witness = TabWitness.of(0, 0, 0, 0, 0, 0, 0, 0, 0);
		h.ticks(3);
		h.bankSlots = slots(RawSlot.of(LOBSTER, 2, ItemKind.UNREADABLE));
		h.tick();
		assertEquals(BankReason.UNREADABLE_DEFINITION, h.reason());
		assertNoClaims();
	}

	@Test
	public void b10_staleResultAfterAcceptanceRevokes()
	{
		qualify();
		h.duringCapture = () -> h.lifecycle.onClientPhase(ClientPhase.LOADING, h.now);
		h.tick();
		assertEquals(BankReason.SCOPE_CHANGED, h.reason());
		assertNoClaims();
	}

	// ------------------------------------------------------------------ capture limits

	@Test
	public void captureIsBoundedAfterRepeatedFailures()
	{
		h.captureThrows = true;
		h.ticks(20);
		assertEquals(BankObservationTracker.MAX_FAILED_ATTEMPTS, h.tracker.captureAttempts());
		assertEquals(BankReason.RETRY_LIMIT, h.reason());
		h.captureThrows = false;
		h.ticks(3);
		assertEquals("still bounded until the bank is reopened", BankObservationTracker.MAX_FAILED_ATTEMPTS,
			h.tracker.captureAttempts());
		h.visibility = BankVisibility.CLOSED;
		h.tick();
		h.visibility = BankVisibility.OPEN;
		h.ticks(2);
		assertQualified();
	}

	@Test
	public void noCaptureWhileAccountNotPublishable()
	{
		h.phase(ClientPhase.LOADING);
		h.tracker.onTick(h.tick + 1, h);
		h.phase(ClientPhase.HOPPING);
		h.tracker.onTick(h.tick + 2, h);
		assertEquals(0, h.tracker.captureAttempts());
		assertEquals(BankReason.NOT_PUBLISHABLE, h.reason());
	}

	@Test
	public void successfulCaptureResetsTheFailureBudget()
	{
		for (int i = 0; i < 3; i++)
		{
			h.captureThrows = true;
			h.ticks(BankObservationTracker.MAX_FAILED_ATTEMPTS - 1);
			h.captureThrows = false;
			h.tick();
		}
		h.tick();
		assertQualified();
	}

	@Test
	public void snapshotsArePublishedInOrder()
	{
		qualify();
		h.visibility = BankVisibility.CLOSED;
		h.tick();
		for (int i = 1; i < h.published.size(); i++)
		{
			assertTrue(h.published.get(i).revision() > h.published.get(i - 1).revision());
		}
		assertEquals(h.tracker.snapshot().revision(), h.published.get(h.published.size() - 1).revision());
	}

	@Test
	public void diagnosticsNeverContainTheAccountHash()
	{
		qualify();
		String text = h.tracker.snapshot().toString() + h.tracker.snapshot().current().get();
		assertFalse(text.contains("123456789012345"));
		assertFalse(text.contains(Long.toHexString(123456789012345L)));
	}
}
