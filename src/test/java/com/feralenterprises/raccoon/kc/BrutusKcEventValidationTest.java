package com.feralenterprises.raccoon.kc;

import static org.junit.Assert.assertEquals;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import com.feralenterprises.raccoon.core.kc.KcResult;
import java.util.Collections;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import org.junit.Before;
import org.junit.Test;

/**
 * FR-004 review (f556ebb5): a chat event is counted only if the live client still matches the
 * lifecycle's current scope at that moment (identity, phase, world), confirmed through the
 * central lifecycle. Reproduced against the official JAR before the repair.
 */
public class BrutusKcEventValidationTest
{
	private String gameState;
	private long hash;
	private boolean hashThrows;
	private Set<String> world;
	private long now;
	private AccountLifecycleService accounts;
	private BrutusKcService kc;

	@Before
	public void setUp()
	{
		gameState = "LOGGED_IN";
		hash = 4242L;
		hashThrows = false;
		world = Collections.singleton("MEMBERS");
		now = 1_000_000_000L;
		accounts = new AccountLifecycleService(() -> gameState, () ->
		{
			if (hashThrows)
			{
				throw new IllegalStateException("read failed");
			}
			return hash;
		}, () -> world, () -> now);
		kc = new BrutusKcService(accounts);
		accounts.start();
		accounts.onGameTick();
		assertEquals(KcResult.BASELINED, chat(428));
		assertEquals(KcResult.GAIN, chat(431));
		assertEquals(3, total());
	}

	private KcResult chat(int n)
	{
		return kc.onChatMessage(ChatMessageType.GAMEMESSAGE, "Your Brutus kill count is: <col=ff0000>" + n + "</col>.");
	}

	private long total()
	{
		return kc.view().currentLoginTotal().getAsLong();
	}

	@Test
	public void identityChangedBeforeNextTickIsRejected()
	{
		hash = 9999L;
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		assertEquals("old account total unchanged", 3, total());
	}

	@Test
	public void loginScreenBeforeReconciliationIsRejected()
	{
		gameState = "LOGIN_SCREEN";
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		assertEquals(3, total());
	}

	@Test
	public void unsupportedWorldBeforeReconciliationIsRejected()
	{
		world = Collections.singleton("SEASONAL");
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		assertEquals(3, total());
	}

	@Test
	public void unknownIdentityReadIsRejected()
	{
		hash = -1L;
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		assertEquals(3, total());
	}

	@Test
	public void failedIdentityReadIsRejected()
	{
		hashThrows = true;
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		assertEquals(3, total());
	}

	@Test
	public void unreadableGameStateIsRejected()
	{
		gameState = null;
		assertEquals(KcResult.REJECTED_SCOPE, chat(500));
		assertEquals(3, total());
	}

	@Test
	public void ordinaryOperationStillCounts()
	{
		assertEquals(KcResult.GAIN, chat(432));
		assertEquals(4, total());
		assertEquals(KcResult.UNCHANGED, chat(432));
		assertEquals(4, total());
	}

	@Test
	public void rejectedEventDoesNotBreakLaterValidOperation()
	{
		hash = 9999L;
		chat(500);
		hash = 4242L;
		assertEquals("the rejected line never became the baseline", KcResult.GAIN, chat(432));
		assertEquals(4, total());
	}
}
