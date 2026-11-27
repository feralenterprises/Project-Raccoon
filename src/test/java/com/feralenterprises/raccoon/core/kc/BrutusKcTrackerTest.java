package com.feralenterprises.raccoon.core.kc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.feralenterprises.raccoon.core.account.AccountLifecycle;
import com.feralenterprises.raccoon.core.account.AccountDataRegistry;
import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.AccountSlot;
import com.feralenterprises.raccoon.core.account.ClientPhase;
import com.feralenterprises.raccoon.core.account.IdentityReading;
import com.feralenterprises.raccoon.core.account.Membership;
import com.feralenterprises.raccoon.core.account.WorldContext;
import java.util.OptionalLong;
import org.junit.Before;
import org.junit.Test;

/** Acceptance cases K01–K05 (R03, FR-004) for Brutus-only typed chat KC. */
public class BrutusKcTrackerTest
{
	private static final IdentityReading A = IdentityReading.of(424242424242L);
	private static final IdentityReading B = IdentityReading.of(535353535353L);
	private static final WorldContext WORLD = WorldContext.supported(Membership.UNKNOWN);

	private AccountLifecycle lc;
	private BrutusKcTracker kc;
	private long now;

	@Before
	public void setUp()
	{
		lc = new AccountLifecycle();
		kc = new BrutusKcTracker(lc, lc);
		now = 1_000_000_000L;
		lc.start(now);
		lc.onClientPhase(ClientPhase.LOGGED_IN, now);
		lc.onGameTick(A, WORLD, now);
	}

	private static String line(int n)
	{
		return "Your Brutus kill count is: <col=ff0000>" + n + "</col>.";
	}

	private KcResult game(String text)
	{
		return kc.onChatMessage(lc.snapshot().scope().orElse(null), ChatKind.GAME, text);
	}

	private KcResult kill(int n)
	{
		return game(line(n));
	}

	private long total()
	{
		return kc.currentLoginTotal().orElseThrow(AssertionError::new);
	}

	private void tick(IdentityReading id)
	{
		now += 600_000_000L;
		lc.onGameTick(id, WORLD, now);
	}

	private void hopAndRestore()
	{
		lc.onClientPhase(ClientPhase.HOPPING, now);
		lc.onClientPhase(ClientPhase.LOADING, now);
		lc.onClientPhase(ClientPhase.LOGGED_IN, now);
		tick(A);
	}

	private void k01Sequence()
	{
		assertEquals(KcResult.BASELINED, kill(428));
		assertEquals(KcResult.GAIN, kill(429));
		assertEquals(KcResult.UNCHANGED, kill(429));
		assertEquals(KcResult.GAIN, kill(431));
	}

	// ------------------------------------------------------------------ K01

	@Test
	public void k01_genuineSequenceAddsThree()
	{
		assertEquals(KcResult.BASELINED, kill(428));
		assertEquals(0, total());
		assertEquals(KcResult.GAIN, kill(429));
		assertEquals(1, total());
		assertEquals(KcResult.UNCHANGED, kill(429));
		assertEquals(1, total());
		assertEquals(KcResult.GAIN, kill(431));
		assertEquals(3, total());
	}

	@Test
	public void k01_spamTypeAndPlainOrGroupedCountsAreAccepted()
	{
		assertEquals(KcResult.BASELINED, kc.onChatMessage(lc.snapshot().scope().get(), ChatKind.SPAM, line(999)));
		assertEquals(KcResult.GAIN, game("Your Brutus kill count is: 1,000"));
		assertEquals(KcResult.GAIN, game("Your Brutus kill count is:1001."));
		assertEquals(2, total());
	}

	// ------------------------------------------------------------------ K02

	@Test
	public void k02_playerAndUnrelatedChatGainNothing()
	{
		kill(428);
		AccountScope s = lc.snapshot().scope().get();
		assertEquals(KcResult.NOT_KC, kc.onChatMessage(s, ChatKind.OTHER, line(500)));
		assertEquals(KcResult.NOT_KC, game("Brutus has been defeated!"));
		assertEquals(KcResult.NOT_KC, game("Oh dear, you are dead!"));
		assertEquals(0, total());
	}

	@Test
	public void k02_differentBossGainsNothing()
	{
		kill(428);
		assertEquals(KcResult.NOT_KC, game("Your Demonic Brutus kill count is: <col=ff0000>500</col>."));
		assertEquals(KcResult.NOT_KC, game("Your Vorkath kill count is: <col=ff0000>500</col>."));
		assertEquals(KcResult.NOT_KC, game("Your completion count for Brutus is: <col=ff0000>500</col>."));
		assertEquals(KcResult.NOT_KC, game("Your Brutus harvest count is: <col=ff0000>500</col>."));
		assertEquals(0, total());
	}

	@Test
	public void k02_lootOnlyGainsNothing()
	{
		kill(428);
		assertEquals(KcResult.NOT_KC, game("Valuable drop: Cow mask (12,000 coins)"));
		assertEquals(KcResult.NOT_KC, game("<col=ef1020>You have a funny feeling like you're being followed.</col>"));
		assertEquals(KcResult.NOT_KC, game("Untradeable drop: Brutus kill count 500"));
		assertEquals(0, total());
	}

	@Test
	public void k02_malformedAndOverflowCountsGainNothing()
	{
		kill(428);
		for (String bad : new String[]{
			"Your Brutus kill count is: 4x", "Your Brutus kill count is: 1,23", "Your Brutus kill count is: ",
			"Your Brutus kill count is: -500", "your brutus kill count is: 500", "Your Brutus kill count is: 500!",
			"Your Brutus kill count is: 2147483648", "Your Brutus kill count is: 99,999,999,999",
			"Your Brutus kill count is: <b>500</b>", " Prefix Your Brutus kill count is: 500", null})
		{
			assertEquals(String.valueOf(bad), KcResult.NOT_KC, game(bad));
		}
		StringBuilder longLine = new StringBuilder("Your Brutus kill count is: 500");
		while (longLine.length() <= BrutusKcParser.MAX_LENGTH)
		{
			longLine.append(' ');
		}
		assertEquals(KcResult.NOT_KC, game(longLine.toString()));
		assertEquals(0, total());
	}

	@Test
	public void k02_totalOverflowQuarantinesWithoutAddingAnything()
	{
		// route the tracker's total slot through a handle the test can pre-fill
		AccountLifecycle l2 = new AccountLifecycle();
		AccountSlot<Long> totalHandle = l2.loginSlot("test.total");
		AccountDataRegistry registry = new AccountDataRegistry()
		{
			@Override
			@SuppressWarnings("unchecked")
			public <T> AccountSlot<T> loginSlot(String name)
			{
				return "kc.brutus.total".equals(name) ? (AccountSlot<T>) totalHandle : l2.loginSlot(name);
			}

			@Override
			public <T> AccountSlot<T> epochSlot(String name)
			{
				return l2.epochSlot(name);
			}
		};
		BrutusKcTracker k2 = new BrutusKcTracker(l2, registry);
		l2.start(0);
		l2.onClientPhase(ClientPhase.LOGGED_IN, 0);
		l2.onGameTick(A, WORLD, 0);
		AccountScope s = l2.snapshot().scope().get();
		assertEquals(KcResult.BASELINED, k2.onChatMessage(s, ChatKind.GAME, line(1)));
		totalHandle.set(s, Long.MAX_VALUE - 1);
		assertEquals(KcResult.OVERFLOW_QUARANTINED, k2.onChatMessage(s, ChatKind.GAME, line(3)));
		assertEquals(Long.MAX_VALUE - 1, k2.currentLoginTotal().getAsLong());
		assertTrue(k2.isQuarantined());
	}

	// ------------------------------------------------------------------ K03

	@Test
	public void k03_regressionQuarantinesUntilNewEpoch()
	{
		kill(431);
		assertEquals(KcResult.REGRESSION_QUARANTINED, kill(430));
		assertTrue(kc.isQuarantined());
		assertEquals(KcResult.QUARANTINED, kill(432));
		assertEquals(0, total());
		// a region change keeps the epoch: still quarantined (stricter than the donor)
		lc.onClientPhase(ClientPhase.LOADING, now);
		lc.onClientPhase(ClientPhase.LOGGED_IN, now);
		tick(A);
		assertEquals(KcResult.QUARANTINED, kill(433));
		// a new valid epoch (verified hop) lifts it with a fresh baseline
		hopAndRestore();
		assertFalse(kc.isQuarantined());
		assertEquals(KcResult.BASELINED, kill(433));
		assertEquals(KcResult.GAIN, kill(434));
		assertEquals(1, total());
	}

	// ------------------------------------------------------------------ K04

	@Test
	public void k04_duplicateSequenceGainsNothing()
	{
		k01Sequence();
		for (int n : new int[]{428, 429, 429, 431})
		{
			kill(n);
		}
		assertEquals(3, total());
		assertTrue(kc.isQuarantined());
	}

	@Test
	public void k04_repeatedLineGainsNothing()
	{
		k01Sequence();
		assertEquals(KcResult.UNCHANGED, kill(431));
		assertEquals(KcResult.UNCHANGED, kill(431));
		assertEquals(3, total());
	}

	@Test
	public void k04_staleTokenIsRejected()
	{
		kill(428);
		AccountScope stale = lc.snapshot().scope().get();
		// authority renewed by a region change
		lc.onClientPhase(ClientPhase.LOADING, now);
		lc.onClientPhase(ClientPhase.LOGGED_IN, now);
		tick(A);
		assertEquals(KcResult.REJECTED_SCOPE, kc.onChatMessage(stale, ChatKind.GAME, line(440)));
		assertEquals(0, total());
	}

	@Test
	public void k04_wrongEpochIsRejected()
	{
		kill(428);
		AccountScope epoch1 = lc.snapshot().scope().get();
		hopAndRestore();
		assertEquals(KcResult.REJECTED_SCOPE, kc.onChatMessage(epoch1, ChatKind.GAME, line(440)));
		assertEquals(0, total());
	}

	@Test
	public void k04_wrongAccountGetsNoGainOrForeignTotal()
	{
		k01Sequence();
		AccountScope a = lc.snapshot().scope().get();
		tick(B); // missed logout: A retired, B active
		assertEquals(KcResult.REJECTED_SCOPE, kc.onChatMessage(a, ChatKind.GAME, line(440)));
		assertEquals("no foreign total", 0, kc.currentLoginTotal().orElse(0));
		assertEquals(OptionalLong.empty(), kc.currentLoginTotal(a));
		assertEquals(KcResult.BASELINED, kill(10));
		assertEquals(0, total());
	}

	@Test
	public void k04_foreignLifecycleScopeIsRejected()
	{
		AccountLifecycle other = new AccountLifecycle();
		other.start(0);
		other.onClientPhase(ClientPhase.LOGGED_IN, 0);
		other.onGameTick(A, WORLD, 0);
		kill(428);
		AccountScope foreign = other.snapshot().scope().get();
		assertEquals(KcResult.REJECTED_SCOPE, kc.onChatMessage(foreign, ChatKind.GAME, line(440)));
		assertEquals(OptionalLong.empty(), kc.currentLoginTotal(foreign));
		assertEquals(0, total());
	}

	@Test
	public void k04_noScopeIsRejected()
	{
		lc.onClientPhase(ClientPhase.HOPPING, now);
		assertEquals(KcResult.REJECTED_SCOPE, kill(428));
	}

	// ------------------------------------------------------------------ K05

	@Test
	public void k05_verifiedSameLoginHopKeepsTotalAndRebaselines()
	{
		k01Sequence();
		lc.onClientPhase(ClientPhase.HOPPING, now);
		assertEquals("masked while suspended", OptionalLong.empty(), kc.currentLoginTotal());
		assertEquals(KcResult.REJECTED_SCOPE, kill(431));
		lc.onClientPhase(ClientPhase.LOADING, now);
		lc.onClientPhase(ClientPhase.LOGGED_IN, now);
		tick(A);
		assertEquals(3, total());
		assertEquals(KcResult.BASELINED, kill(431));
		assertEquals("baseline is a zero delta", 3, total());
		assertEquals(KcResult.GAIN, kill(432));
		assertEquals(4, total());
	}

	private void assertClearedThenBaselineZero()
	{
		assertEquals(KcResult.BASELINED, kill(431));
		assertEquals(0, total());
		assertEquals(KcResult.GAIN, kill(432));
		assertEquals(1, total());
	}

	@Test
	public void k05_unverifiedHopClears()
	{
		k01Sequence();
		lc.onClientPhase(ClientPhase.HOPPING, now);
		lc.onClientPhase(ClientPhase.LOGGED_IN, now);
		for (int i = 0; i < 20; i++)
		{
			tick(IdentityReading.unknown());
		}
		tick(A);
		assertClearedThenBaselineZero();
	}

	@Test
	public void k05_expiredHopClears()
	{
		k01Sequence();
		lc.onClientPhase(ClientPhase.CONNECTION_LOST, now);
		now += 121_000_000_000L;
		lc.poll(now);
		lc.onClientPhase(ClientPhase.LOGGED_IN, now);
		tick(A);
		assertClearedThenBaselineZero();
	}

	@Test
	public void k05_retiredHopClears()
	{
		k01Sequence();
		lc.onClientPhase(ClientPhase.HOPPING, now);
		lc.onClientPhase(ClientPhase.LOGGING_IN, now);
		lc.onClientPhase(ClientPhase.LOGGED_IN, now);
		tick(A);
		assertClearedThenBaselineZero();
	}

	@Test
	public void k05_logoutClears()
	{
		k01Sequence();
		lc.onClientPhase(ClientPhase.LOGIN_SCREEN, now);
		assertEquals(OptionalLong.empty(), kc.currentLoginTotal());
		lc.onClientPhase(ClientPhase.LOGGED_IN, now);
		tick(A);
		assertClearedThenBaselineZero();
	}

	@Test
	public void k05_disableClears()
	{
		k01Sequence();
		lc.stop();
		assertEquals(OptionalLong.empty(), kc.currentLoginTotal());
		lc.start(now);
		lc.onClientPhase(ClientPhase.LOGGED_IN, now);
		tick(A);
		assertClearedThenBaselineZero();
	}

	// ------------------------------------------------------------------ region change

	@Test
	public void regionChangeRebaselinesWithoutBridgingTheGap()
	{
		kill(428);
		lc.onClientPhase(ClientPhase.LOADING, now);
		assertEquals("not publishable during LOADING", KcResult.REJECTED_SCOPE, kill(429));
		lc.onClientPhase(ClientPhase.LOGGED_IN, now);
		tick(A);
		assertEquals(KcResult.BASELINED, kill(430));
		assertEquals(0, total());
		assertEquals(KcResult.GAIN, kill(431));
		assertEquals(1, total());
	}

	@Test
	public void revisionChangesOnObservableEventsOnly()
	{
		long r0 = kc.revision();
		game("Valuable drop: Cow mask");
		assertEquals(r0, kc.revision());
		kill(428);
		long r1 = kc.revision();
		assertTrue(r1 > r0);
		kill(428);
		assertEquals("unchanged count", r1, kc.revision());
	}
}
