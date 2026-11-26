package com.feralenterprises.raccoon.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.feralenterprises.raccoon.core.account.AccountState;
import com.feralenterprises.raccoon.core.account.Membership;
import com.feralenterprises.raccoon.core.account.RetireReason;
import com.feralenterprises.raccoon.core.account.WorldContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

/** Adapter behaviour with plain suppliers in place of a RuneLite client. */
public class AccountLifecycleServiceTest
{
	private String gameState;
	private long hash;
	private boolean hashThrows;
	private Set<String> worldTypes;
	private long now;
	private AccountLifecycleService service;

	@Before
	public void setUp()
	{
		gameState = "LOGGED_IN";
		hash = 4242424242L;
		hashThrows = false;
		worldTypes = new HashSet<>(Collections.singletonList("MEMBERS"));
		now = 5_000_000_000L;
		service = new AccountLifecycleService(() -> gameState,
			() ->
			{
				if (hashThrows)
				{
					throw new IllegalStateException("read failed");
				}
				return hash;
			},
			() -> worldTypes, () -> now);
	}

	@Test
	public void enabledMidSessionLearnsPhaseThenReadsIdentityFresh()
	{
		service.start();
		assertEquals(AccountState.UNAUTHENTICATED, service.view().snapshot().state());
		service.onGameTick();
		assertEquals(AccountState.ACTIVE, service.view().snapshot().state());
	}

	@Test
	public void hashReadFailureIsNotAnIdentity()
	{
		hashThrows = true;
		service.start();
		service.onGameTick();
		assertFalse(service.view().snapshot().isPublishable());
		assertFalse(service.readIdentity().isVerified());
	}

	@Test
	public void noIdentityValueIsUnknown()
	{
		hash = -1;
		service.start();
		service.onGameTick();
		assertEquals(AccountState.UNAUTHENTICATED, service.view().snapshot().state());
	}

	@Test
	public void hopThroughServiceRestoresSameLogin()
	{
		service.start();
		service.onGameTick();
		long token = service.view().snapshot().scope().get().loginToken();
		service.onGameState("HOPPING");
		assertEquals(AccountState.SUSPENDED_CONTINUITY, service.view().snapshot().state());
		service.onGameState("LOADING");
		service.onGameState("LOGGED_IN");
		service.onGameTick();
		assertEquals(token, service.view().snapshot().scope().get().loginToken());
	}

	@Test
	public void clientTickExpiresSuspension()
	{
		service.start();
		service.onGameTick();
		service.onGameState("CONNECTION_LOST");
		now += 121_000_000_000L;
		service.onClientTick();
		assertEquals(AccountState.RETIRED, service.view().snapshot().state());
		assertEquals(RetireReason.CONTINUITY_TIMEOUT, service.view().snapshot().lastRetireReason());
	}

	@Test
	public void unrecognisedGameStateNameRetiresActiveLogin()
	{
		service.start();
		service.onGameTick();
		service.onGameState("SOMETHING_NEW");
		assertEquals(RetireReason.UNRECOGNIZED_CONTINUITY, service.view().snapshot().lastRetireReason());
	}

	@Test
	public void stopRetires()
	{
		service.start();
		service.onGameTick();
		service.stop();
		assertEquals(RetireReason.PLUGIN_STOPPED, service.view().snapshot().lastRetireReason());
	}

	@Test
	public void finding4_tickReconcilesPhaseWithClient()
	{
		// last event said LOGGED_IN, but the client is loading when the tick is processed
		service.start();
		service.onGameState("LOGGED_IN");
		service.onGameTick();
		assertTrue(service.view().snapshot().isPublishable());
		gameState = "LOADING";
		service.onGameTick();
		assertFalse("stale adapter phase must not keep data publishable", service.view().snapshot().isPublishable());
	}

	@Test
	public void finding4_unreadableGameStateAtTickIsNotLoggedIn()
	{
		service.start();
		service.onGameState("LOGGED_IN");
		service.onGameTick();
		gameState = null;
		service.onGameTick();
		assertFalse(service.view().snapshot().isPublishable());
	}

	@Test
	public void worldPolicyAllowsOnlyStandardWorlds()
	{
		assertTrue(WorldPolicy.evaluate(Collections.emptySet()).isSupported());
		assertTrue(WorldPolicy.evaluate(new HashSet<>(Arrays.asList("MEMBERS", "SKILL_TOTAL"))).isSupported());
		for (String t : Arrays.asList("SEASONAL", "BETA_WORLD", "NOSAVE_MODE", "TOURNAMENT_WORLD", "FRESH_START_WORLD",
			"DEADMAN", "QUEST_SPEEDRUNNING", "LAST_MAN_STANDING", "PVP_ARENA", "SOME_FUTURE_TYPE"))
		{
			assertFalse(t, WorldPolicy.evaluate(new HashSet<>(Arrays.asList("MEMBERS", t))).isSupported());
		}
		assertFalse(WorldPolicy.evaluate(null).isSupported());
	}

	@Test
	public void worldPolicyNeverClaimsMembership()
	{
		WorldContext w = WorldPolicy.evaluate(new HashSet<>(Collections.singletonList("MEMBERS")));
		assertEquals(Membership.UNKNOWN, w.membership());
	}

	@Test
	public void unreadableWorldIsUnsupported()
	{
		worldTypes = null;
		service.start();
		service.onGameTick();
		assertFalse(service.view().snapshot().isPublishable());
	}
}
