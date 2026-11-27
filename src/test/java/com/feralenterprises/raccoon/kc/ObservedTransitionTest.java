package com.feralenterprises.raccoon.kc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.AccountSnapshot;
import com.feralenterprises.raccoon.core.account.AccountState;
import com.feralenterprises.raccoon.core.account.RetireReason;
import com.feralenterprises.raccoon.core.kc.KcResult;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import net.runelite.api.ChatMessageType;

/**
 * FR-004 re-review (3e5cd549), A04/K05 boundary: HOPPING or CONNECTION_LOST seen at an event,
 * with no phase event or game tick between, starts the continuity deadline at that observation.
 * Later or duplicate phase events never restart it. A verified restoration opens a new epoch;
 * past the deadline the login is retired and cleared. Both transition types.
 */
public class ObservedTransitionTest
{
	private static final long A = 4242L;
	private static final long SECOND = 1_000_000_000L;

	private String gameState;
	private long now;
	private AccountLifecycleService accounts;
	private BrutusKcService kc;
	private AccountScope before;

	@Before
	public void setUp()
	{
		gameState = "LOGGED_IN";
		now = 1_000 * SECOND;
		accounts = new AccountLifecycleService(() -> gameState, () -> A, () -> Collections.singleton("MEMBERS"), () -> now);
		kc = new BrutusKcService(accounts);
		accounts.start();
		accounts.onGameTick();
		before = snap().scope().get();
		assertEquals(KcResult.BASELINED, chat(428));
		assertEquals(KcResult.GAIN, chat(431));
	}

	private KcResult chat(int n)
	{
		return kc.onChatMessage(ChatMessageType.GAMEMESSAGE, "Your Brutus kill count is: <col=ff0000>" + n + "</col>.");
	}

	private AccountSnapshot snap()
	{
		return accounts.view().snapshot();
	}

	/** Chat arrives while the live client already shows the transition; no phase event yet. */
	private void observe(String transition)
	{
		gameState = transition;
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
	}

	private void returnAndTick()
	{
		gameState = "LOGGED_IN";
		accounts.onGameTick();
	}

	private void assertRestoredInNewEpoch()
	{
		AccountScope after = snap().scope().get();
		assertEquals("same login", before.loginToken(), after.loginToken());
		assertNotEquals("verified restoration opens a new epoch", before.epoch(), after.epoch());
		assertEquals("K05: first count after the transition is a zero-delta baseline", KcResult.BASELINED, chat(431));
		assertEquals(KcResult.GAIN, chat(432));
		assertEquals(4, kc.view().currentLoginTotal().getAsLong());
	}

	private void assertRetiredAndCleared()
	{
		AccountScope after = snap().scope().get(); // the tick may activate a brand-new login
		assertNotEquals("old login not preserved", before.loginToken(), after.loginToken());
		assertEquals(RetireReason.CONTINUITY_TIMEOUT, snap().lastRetireReason());
		assertEquals(KcResult.BASELINED, chat(432));
		assertEquals("old total cleared", 0, kc.view().currentLoginTotal().getAsLong());
	}

	// ------------------------------------------------------------------ missing phase event

	private void missingEventRestores(String t)
	{
		observe(t);
		assertEquals("the observation starts the suspension", AccountState.SUSPENDED_CONTINUITY, snap().state());
		assertFalse(kc.view().currentLoginTotal().isPresent());
		now += 5 * SECOND;
		returnAndTick();
		assertRestoredInNewEpoch();
	}

	@Test
	public void hopSeenOnlyAtChatRestoresInNewEpoch()
	{
		missingEventRestores("HOPPING");
	}

	@Test
	public void connectionLossSeenOnlyAtChatRestoresInNewEpoch()
	{
		missingEventRestores("CONNECTION_LOST");
	}

	// ------------------------------------------------------------------ expiration

	private void missingEventExpires(String t)
	{
		observe(t);
		now += 121 * SECOND;
		returnAndTick();
		assertRetiredAndCleared();
	}

	@Test
	public void hopSeenOnlyAtChatExpiresAfterTheLimit()
	{
		missingEventExpires("HOPPING");
	}

	@Test
	public void connectionLossSeenOnlyAtChatExpiresAfterTheLimit()
	{
		missingEventExpires("CONNECTION_LOST");
	}

	private void eventPastTheDeadlineRetires(String t)
	{
		observe(t);
		now += 121 * SECOND;
		observe(t); // any later event past the deadline retires at once
		assertEquals(AccountState.RETIRED, snap().state());
		assertEquals(RetireReason.CONTINUITY_TIMEOUT, snap().lastRetireReason());
		returnAndTick();
		assertRetiredAndCleared();
	}

	@Test
	public void eventPastTheHopDeadlineRetiresAtOnce()
	{
		eventPastTheDeadlineRetires("HOPPING");
	}

	@Test
	public void eventPastTheConnectionLossDeadlineRetiresAtOnce()
	{
		eventPastTheDeadlineRetires("CONNECTION_LOST");
	}

	// ------------------------------------------------------------------ delayed phase event

	private void delayedEventDoesNotRestart(String t)
	{
		observe(t);
		now += 100 * SECOND;
		accounts.onGameState(t); // the phase event arrives late
		accounts.onGameState("LOADING");
		now += 21 * SECOND; // 121 s after the first observation, 21 s after the event
		accounts.onGameState("LOGGED_IN");
		returnAndTick();
		assertRetiredAndCleared();
	}

	@Test
	public void delayedHopEventDoesNotRestartTheDeadline()
	{
		delayedEventDoesNotRestart("HOPPING");
	}

	@Test
	public void delayedConnectionLossEventDoesNotRestartTheDeadline()
	{
		delayedEventDoesNotRestart("CONNECTION_LOST");
	}

	private void delayedEventWithinLimitRestores(String t)
	{
		observe(t);
		now += 60 * SECOND;
		accounts.onGameState(t);
		accounts.onGameState("LOADING");
		now += 30 * SECOND;
		accounts.onGameState("LOGGED_IN");
		returnAndTick();
		assertRestoredInNewEpoch();
	}

	@Test
	public void delayedHopEventWithinTheLimitRestoresInNewEpoch()
	{
		delayedEventWithinLimitRestores("HOPPING");
	}

	@Test
	public void delayedConnectionLossEventWithinTheLimitRestoresInNewEpoch()
	{
		delayedEventWithinLimitRestores("CONNECTION_LOST");
	}

	// ------------------------------------------------------------------ duplicates

	private void duplicatesDoNotRestart(String t)
	{
		observe(t);
		now += 50 * SECOND;
		observe(t); // duplicate observation
		accounts.onGameState(t); // phase event
		now += 50 * SECOND;
		accounts.onGameState(t); // duplicate phase event
		observe(t);
		now += 21 * SECOND; // 121 s after the first observation
		returnAndTick();
		assertRetiredAndCleared();
	}

	@Test
	public void duplicateHopObservationsAndEventsDoNotRestartTheDeadline()
	{
		duplicatesDoNotRestart("HOPPING");
	}

	@Test
	public void duplicateConnectionLossObservationsAndEventsDoNotRestartTheDeadline()
	{
		duplicatesDoNotRestart("CONNECTION_LOST");
	}

	private void reobservedAfterLoadingDoesNotRestart(String t)
	{
		observe(t);
		accounts.onGameState("LOADING");
		now += 60 * SECOND;
		observe(t); // the transition is seen again at a later event
		now += 61 * SECOND; // 121 s after the first observation
		returnAndTick();
		assertRetiredAndCleared();
	}

	@Test
	public void hopSeenAgainAfterLoadingDoesNotRestartTheDeadline()
	{
		reobservedAfterLoadingDoesNotRestart("HOPPING");
	}

	@Test
	public void connectionLossSeenAgainAfterLoadingDoesNotRestartTheDeadline()
	{
		reobservedAfterLoadingDoesNotRestart("CONNECTION_LOST");
	}

	private void duplicatePhaseEventsDoNotRestart(String t)
	{
		gameState = t;
		accounts.onGameState(t);
		now += 70 * SECOND;
		accounts.onGameState(t);
		now += 51 * SECOND;
		returnAndTick();
		assertRetiredAndCleared();
	}

	@Test
	public void duplicateHopEventsDoNotRestartTheDeadline()
	{
		duplicatePhaseEventsDoNotRestart("HOPPING");
	}

	@Test
	public void duplicateConnectionLossEventsDoNotRestartTheDeadline()
	{
		duplicatePhaseEventsDoNotRestart("CONNECTION_LOST");
	}

	// ------------------------------------------------------------------ legitimate behaviour

	private void ordinaryTransitionRestores(String t)
	{
		gameState = t;
		accounts.onGameState(t);
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		accounts.onGameState("LOADING");
		now += 10 * SECOND;
		accounts.onGameState("LOGGED_IN");
		returnAndTick();
		assertRestoredInNewEpoch();
	}

	@Test
	public void ordinaryHopRestoresInNewEpoch()
	{
		ordinaryTransitionRestores("HOPPING");
	}

	@Test
	public void ordinaryReconnectRestoresInNewEpoch()
	{
		ordinaryTransitionRestores("CONNECTION_LOST");
	}
}
