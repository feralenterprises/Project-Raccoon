package com.feralenterprises.raccoon.kc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import com.feralenterprises.raccoon.core.account.AccountSnapshot;
import com.feralenterprises.raccoon.core.account.AccountState;
import com.feralenterprises.raccoon.core.account.RetireReason;
import com.feralenterprises.raccoon.core.kc.KcResult;
import java.util.Collections;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import org.junit.Before;
import org.junit.Test;

/**
 * FR-004 targeted re-review (0f983cff): a contradictory live observation at an event must end or
 * mask authority in the central lifecycle at once, by the lifecycle's existing rules, and not only
 * be rejected for that one event. Observations never activate, restore or re-verify: those still
 * need a LOGGED_IN game tick. Legitimate verified hops and reconnects keep the login.
 */
public class EventAuthorityTest
{
	private static final long A = 4242L;
	private static final long B = 9999L;

	private String gameState;
	private long hash;
	private Set<String> world;
	private long now;
	private AccountLifecycleService accounts;
	private BrutusKcService kc;
	private long loginA;

	@Before
	public void setUp()
	{
		gameState = "LOGGED_IN";
		hash = A;
		world = Collections.singleton("MEMBERS");
		now = 1_000_000_000L;
		accounts = new AccountLifecycleService(() -> gameState, () -> hash, () -> world, () -> now);
		kc = new BrutusKcService(accounts);
		accounts.start();
		accounts.onGameTick();
		loginA = snap().scope().get().loginToken();
		assertEquals(KcResult.BASELINED, chat(428));
		assertEquals(KcResult.GAIN, chat(431));
		assertEquals(3, kc.view().currentLoginTotal().getAsLong());
	}

	private KcResult chat(int n)
	{
		return kc.onChatMessage(ChatMessageType.GAMEMESSAGE, "Your Brutus kill count is: <col=ff0000>" + n + "</col>.");
	}

	private AccountSnapshot snap()
	{
		return accounts.view().snapshot();
	}

	private void assertNoReadableAuthority()
	{
		assertFalse("no scope readable", snap().scope().isPresent());
		assertFalse("no total readable", kc.view().currentLoginTotal().isPresent());
	}

	private void tick()
	{
		now += 600_000_000L;
		accounts.onGameTick();
	}

	private void phase(String name)
	{
		gameState = name;
		accounts.onGameState(name);
	}

	// ------------------------------------------------------------------ immediate retirement

	@Test
	public void contradictoryIdentityRetiresImmediately()
	{
		hash = B;
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		assertEquals(AccountState.RETIRED, snap().state());
		assertEquals(RetireReason.DIFFERENT_ACCOUNT, snap().lastRetireReason());
		assertNoReadableAuthority();
	}

	@Test
	public void abaBetweenTicksDoesNotPreserveThePreviousLogin()
	{
		hash = B;
		chat(500);
		hash = A;
		assertEquals("no activation without a game tick", KcResult.REJECTED_SCOPE, chat(432));
		assertNoReadableAuthority();
		tick();
		assertNotEquals("A returns as a new login", loginA, snap().scope().get().loginToken());
		assertEquals(KcResult.BASELINED, chat(432));
		assertEquals(0, kc.view().currentLoginTotal().getAsLong());
	}

	@Test
	public void observedLogoutRetiresImmediately()
	{
		gameState = "LOGIN_SCREEN";
		chat(500);
		assertEquals(RetireReason.LOGOUT, snap().lastRetireReason());
		assertNoReadableAuthority();
	}

	@Test
	public void observedUnsupportedWorldRetiresImmediately()
	{
		world = Collections.singleton("SEASONAL");
		chat(500);
		assertEquals(RetireReason.UNSUPPORTED_WORLD, snap().lastRetireReason());
		assertNoReadableAuthority();
	}

	@Test
	public void observedIdentityLossRetiresImmediately()
	{
		hash = -1L;
		chat(500);
		assertEquals(RetireReason.IDENTITY_LOST, snap().lastRetireReason());
		assertNoReadableAuthority();
	}

	@Test
	public void observedLoadingMasksWithoutRetiring()
	{
		gameState = "LOADING";
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		assertEquals(AccountState.ACTIVE, snap().state());
		assertNoReadableAuthority();
		gameState = "LOGGED_IN";
		tick();
		assertEquals("same login after re-verification", loginA, snap().scope().get().loginToken());
		assertEquals(3, kc.view().currentLoginTotal().getAsLong());
	}

	@Test
	public void observedHopWhileActiveMasksButFabricatesNoSuspension()
	{
		gameState = "HOPPING";
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		assertEquals("no hop fabricated", AccountState.ACTIVE, snap().state());
		assertNoReadableAuthority();
		accounts.onGameState("HOPPING");
		assertEquals(AccountState.SUSPENDED_CONTINUITY, snap().state());
		phase("LOADING");
		phase("LOGGED_IN");
		tick();
		assertEquals(loginA, snap().scope().get().loginToken());
		assertEquals(3, kc.view().currentLoginTotal().getAsLong());
	}

	// ------------------------------------------------------------------ interrupted continuity

	@Test
	public void observedLogoutDuringSuspensionEndsContinuity()
	{
		phase("HOPPING");
		gameState = "LOGIN_SCREEN";
		chat(500);
		assertEquals(RetireReason.LOGOUT, snap().lastRetireReason());
		phase("LOGGED_IN");
		tick();
		assertNotEquals(loginA, snap().scope().get().loginToken());
		assertEquals(KcResult.BASELINED, chat(432));
		assertEquals(0, kc.view().currentLoginTotal().getAsLong());
	}

	@Test
	public void observedLoggingInDuringSuspensionEndsContinuity()
	{
		phase("CONNECTION_LOST");
		gameState = "LOGGING_IN";
		chat(500);
		assertEquals(RetireReason.CONTINUITY_INTERRUPTED, snap().lastRetireReason());
		phase("LOGGED_IN");
		tick();
		assertNotEquals(loginA, snap().scope().get().loginToken());
	}

	@Test
	public void observedUnsupportedWorldDuringSuspensionEndsContinuity()
	{
		phase("HOPPING");
		world = Collections.singleton("SEASONAL");
		chat(500);
		assertEquals(RetireReason.UNSUPPORTED_WORLD, snap().lastRetireReason());
		world = Collections.singleton("MEMBERS");
		phase("LOADING");
		phase("LOGGED_IN");
		tick();
		assertNotEquals(loginA, snap().scope().get().loginToken());
		assertFalse(kc.view().currentLoginTotal().isPresent());
	}

	@Test
	public void observedOtherIdentityDuringSuspensionEndsContinuity()
	{
		phase("HOPPING");
		hash = B;
		chat(500);
		assertEquals(RetireReason.DIFFERENT_ACCOUNT, snap().lastRetireReason());
		hash = A;
		phase("LOADING");
		phase("LOGGED_IN");
		tick();
		assertNotEquals(loginA, snap().scope().get().loginToken());
	}

	// ------------------------------------------------------------------ legitimate behaviour kept

	@Test
	public void verifiedHopWithChatDuringTransitionKeepsTheLogin()
	{
		phase("HOPPING");
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		phase("LOADING");
		hash = -1L; // unknown identity inside a witnessed transition is tolerated
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		assertEquals(AccountState.SUSPENDED_CONTINUITY, snap().state());
		hash = A;
		phase("LOGGED_IN");
		tick();
		assertEquals(loginA, snap().scope().get().loginToken());
		assertEquals("K05: first count after the hop is a zero-delta baseline", KcResult.BASELINED, chat(431));
		assertEquals(KcResult.GAIN, chat(432));
		assertEquals(4, kc.view().currentLoginTotal().getAsLong());
	}

	@Test
	public void verifiedReconnectKeepsTheLogin()
	{
		phase("CONNECTION_LOST");
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		phase("LOADING");
		phase("LOGGED_IN");
		tick();
		assertEquals(loginA, snap().scope().get().loginToken());
		assertEquals(3, kc.view().currentLoginTotal().getAsLong());
	}

	@Test
	public void observationNeverRestoresContinuity()
	{
		phase("HOPPING");
		gameState = "LOGGED_IN"; // live state ahead of the event and of the tick
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		assertEquals("restoration needs a LOGGED_IN tick", AccountState.SUSPENDED_CONTINUITY, snap().state());
		tick();
		assertEquals(loginA, snap().scope().get().loginToken());
	}

	@Test
	public void observationNeverActivates()
	{
		AccountLifecycleService fresh = new AccountLifecycleService(() -> "LOGGED_IN", () -> A,
			() -> Collections.singleton("MEMBERS"), () -> now);
		BrutusKcService freshKc = new BrutusKcService(fresh);
		fresh.start();
		assertEquals(KcResult.REJECTED_SCOPE, freshKc.onChatMessage(ChatMessageType.GAMEMESSAGE, "Your Brutus kill count is: 5."));
		assertEquals("activation needs a LOGGED_IN tick", AccountState.UNAUTHENTICATED, fresh.view().snapshot().state());
		assertTrue(fresh.scopeForEventNow().equals(java.util.Optional.empty()));
	}
}
