package com.feralenterprises.raccoon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import com.feralenterprises.raccoon.bank.BankObservationService;
import com.feralenterprises.raccoon.core.account.AccountLifecycle;
import com.feralenterprises.raccoon.core.kc.BrutusKcView;
import com.feralenterprises.raccoon.kc.BrutusKcService;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;

/**
 * FR-005 integration cases, through the production wiring (Guice and the real RuneLite event
 * bus; deterministic simulation, see {@link PluginHarness}).
 * <ul>
 * <li>I01: exactly one subscription and one publisher after two enable/disable cycles;</li>
 * <li>I02: a typed event traced to sanitized output with no panel, persistence, network or game
 * mutation (runtime half; the static half is the artifact verifier's deny lists and RuneLite
 * member allowlist).</li>
 * </ul>
 */
public class FoundationIntegrationTest
{
	private static final long HASH = 0x5EC12E7AB1E5L;

	private static AccountLifecycle lifecycle(PluginHarness h)
	{
		return (AccountLifecycle) h.accounts.view();
	}

	@Test
	public void i01_exactlyOneSubscriptionAndPublisherAfterTwoEnableDisableCycles() throws Exception
	{
		PluginHarness h = new PluginHarness();
		AccountLifecycleService accounts = h.accounts;
		BankObservationService bank = h.bankService;
		BrutusKcService kc = h.kc;
		assertEquals("one lifecycle subscriber: the bank tracker", 1, lifecycle(h).subscriberCount());

		for (int cycle = 1; cycle <= 2; cycle++)
		{
			h.enable();
			h.logIn(HASH);
			int ticks = h.calls("getTickCount");
			h.gameTick();
			assertEquals("cycle " + cycle + ": one game-tick dispatch", ticks + 1, h.calls("getTickCount"));
			int worlds = h.calls("getWorldType");
			h.eventBus.post(PluginHarness.brutus(100 + cycle));
			assertEquals("cycle " + cycle + ": one chat dispatch", worlds + 1, h.calls("getWorldType"));
			h.disable();
			assertEquals("cycle " + cycle + ": subscribers unchanged", 1, lifecycle(h).subscriberCount());
			int all = h.calls.values().stream().mapToInt(Integer::intValue).sum();
			h.gameTick();
			h.eventBus.post(PluginHarness.brutus(500));
			h.eventBus.post(PluginHarness.gameStateChanged(GameState.LOGIN_SCREEN));
			h.eventBus.post(PluginHarness.clientTick());
			assertEquals("cycle " + cycle + ": no dispatch while disabled", all, h.calls.values().stream().mapToInt(Integer::intValue).sum());
			assertFalse(accounts.view().snapshot().isPublishable());
		}

		// after two complete cycles: exactly one subscription and one publisher
		h.gameState = GameState.LOGGED_IN;
		h.enable();
		h.logIn(HASH);
		assertEquals(1, lifecycle(h).subscriberCount());
		assertSame("one lifecycle publisher", accounts, h.injector.getInstance(AccountLifecycleService.class));
		assertSame("one bank publisher", bank, h.injector.getInstance(BankObservationService.class));
		assertSame("one KC publisher", kc, h.injector.getInstance(BrutusKcService.class));
		assertSame(accounts.view(), h.accounts.view());

		int ticks = h.calls("getTickCount");
		h.gameTick();
		int worlds = h.calls("getWorldType");
		h.eventBus.post(PluginHarness.brutus(428));
		h.eventBus.post(PluginHarness.brutus(431));
		assertEquals("one game-tick dispatch", ticks + 1, h.calls("getTickCount"));
		assertEquals("two chat events, two dispatches", worlds + 2, h.calls("getWorldType"));
		assertEquals("a single tracker counted the gain once", 3, kc.view().currentLoginTotal().getAsLong());
		h.disable();
		assertTrue(h.subscriberErrors.isEmpty());
		assertTrue("reads only: " + h.unexpected, h.unexpected.isEmpty());
	}

	@Test
	public void i02_typedEventTracedToSanitizedOutputWithoutSideEffects() throws Exception
	{
		PluginHarness h = new PluginHarness();
		h.enable();
		h.logIn(HASH);
		h.gameTick();

		List<ChatMessage> events = new ArrayList<>();
		events.add(PluginHarness.brutus(428)); // baseline
		events.add(PluginHarness.chat(ChatMessageType.PUBLICCHAT, "Your Brutus kill count is: 999.")); // player chat: ignored
		events.add(PluginHarness.chat(ChatMessageType.GAMEMESSAGE, "Your Vorkath kill count is: <col=ff0000>50</col>.")); // other boss
		events.add(PluginHarness.brutus(431)); // +3
		List<String> before = new ArrayList<>();
		for (ChatMessage e : events)
		{
			before.add(e.getType() + "|" + e.getMessage());
			h.eventBus.post(e);
		}

		// sanitized output: one per-login total, nothing else from the message
		BrutusKcView view = h.kc.view();
		assertEquals(3, view.currentLoginTotal().getAsLong());
		assertFalse(view.isQuarantined());

		// the typed events were not modified
		for (int i = 0; i < events.size(); i++)
		{
			assertEquals(before.get(i), events.get(i).getType() + "|" + events.get(i).getMessage());
		}

		// no game mutation: every client call was a reviewed read
		assertTrue("reads only: " + h.unexpected, h.unexpected.isEmpty());
		assertTrue(PluginHarness.READS.containsAll(h.calls.keySet()));
		assertTrue(h.subscriberErrors.isEmpty());

		// no personal account data in any published surface
		List<String> surfaces = new ArrayList<>();
		surfaces.add(String.valueOf(h.accounts.view().snapshot()));
		surfaces.add(String.valueOf(h.accounts.view().snapshot().scope().orElse(null)));
		surfaces.add(String.valueOf(view));
		surfaces.add(String.valueOf(h.bankService.view().snapshot()));
		for (String s : surfaces)
		{
			assertFalse("account hash leaked: " + s, s.contains(Long.toString(HASH)));
			assertFalse("account hash leaked: " + s, s.toLowerCase().contains(Long.toHexString(HASH)));
			assertFalse("raw chat retained: " + s, s.contains("Brutus kill count"));
		}

		// disable: authority and output cleared, nothing retained across the session
		h.disable();
		assertFalse(view.currentLoginTotal().isPresent());
		assertFalse(h.accounts.view().snapshot().scope().isPresent());
	}
}
