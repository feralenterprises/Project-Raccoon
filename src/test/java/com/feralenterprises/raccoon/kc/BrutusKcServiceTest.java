package com.feralenterprises.raccoon.kc;

import static org.junit.Assert.assertEquals;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import com.feralenterprises.raccoon.core.kc.ChatKind;
import com.feralenterprises.raccoon.core.kc.KcResult;
import java.util.Collections;
import net.runelite.api.ChatMessageType;
import org.junit.Test;

/** Adapter: chat type mapping and scope captured at the chat event. */
public class BrutusKcServiceTest
{
	private long now = 1_000_000_000L;

	@Test
	public void onlyGameMessageAndSpamAreKcTypes()
	{
		assertEquals(ChatKind.GAME, BrutusKcService.kindOf(ChatMessageType.GAMEMESSAGE));
		assertEquals(ChatKind.SPAM, BrutusKcService.kindOf(ChatMessageType.SPAM));
		for (ChatMessageType t : ChatMessageType.values())
		{
			if (t != ChatMessageType.GAMEMESSAGE && t != ChatMessageType.SPAM)
			{
				assertEquals(t.name(), ChatKind.OTHER, BrutusKcService.kindOf(t));
			}
		}
		assertEquals(ChatKind.OTHER, BrutusKcService.kindOf(null));
	}

	@Test
	public void serviceUsesTheCurrentLifecycleScope()
	{
		AccountLifecycleService accounts = new AccountLifecycleService(() -> "LOGGED_IN", () -> 77L,
			() -> Collections.singleton("MEMBERS"), () -> now);
		BrutusKcService kc = new BrutusKcService(accounts);
		accounts.start();
		assertEquals("no scope before the first tick", KcResult.REJECTED_SCOPE,
			kc.onChatMessage(ChatMessageType.GAMEMESSAGE, "Your Brutus kill count is: <col=ff0000>5</col>."));
		accounts.onGameTick();
		assertEquals(KcResult.BASELINED, kc.onChatMessage(ChatMessageType.GAMEMESSAGE, "Your Brutus kill count is: <col=ff0000>5</col>."));
		assertEquals(KcResult.NOT_KC, kc.onChatMessage(ChatMessageType.PUBLICCHAT, "Your Brutus kill count is: <col=ff0000>9</col>."));
		assertEquals(KcResult.GAIN, kc.onChatMessage(ChatMessageType.SPAM, "Your Brutus kill count is: <col=ff0000>6</col>."));
		assertEquals(1, kc.view().currentLoginTotal().getAsLong());
		accounts.stop();
		assertEquals(false, kc.view().currentLoginTotal().isPresent());
	}
}
