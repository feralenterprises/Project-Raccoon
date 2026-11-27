package com.feralenterprises.raccoon.kc;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.kc.BrutusKcTracker;
import com.feralenterprises.raccoon.core.kc.BrutusKcView;
import com.feralenterprises.raccoon.core.kc.ChatKind;
import com.feralenterprises.raccoon.core.kc.KcResult;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;

/**
 * RuneLite adapter for Brutus KC: forwards typed chat with the account scope current at the
 * moment the message arrives. No inference from loot, NPC death or any other event.
 */
@Singleton
public class BrutusKcService
{
	private final AccountLifecycleService accounts;
	private final BrutusKcTracker tracker;

	@Inject
	public BrutusKcService(AccountLifecycleService accounts)
	{
		this.accounts = accounts;
		this.tracker = new BrutusKcTracker(accounts.view(), accounts.data());
	}

	public BrutusKcView view()
	{
		return tracker;
	}

	public KcResult onChatMessage(ChatMessageType type, String message)
	{
		return onChatMessage(kindOf(type), message);
	}

	/** Seam for tests and the simulator. */
	public KcResult onChatMessage(ChatKind kind, String message)
	{
		AccountScope scope = accounts.view().snapshot().scope().orElse(null);
		return tracker.onChatMessage(scope, kind, message);
	}

	static ChatKind kindOf(ChatMessageType type)
	{
		if (type == ChatMessageType.GAMEMESSAGE)
		{
			return ChatKind.GAME;
		}
		if (type == ChatMessageType.SPAM)
		{
			return ChatKind.SPAM;
		}
		return ChatKind.OTHER;
	}
}
