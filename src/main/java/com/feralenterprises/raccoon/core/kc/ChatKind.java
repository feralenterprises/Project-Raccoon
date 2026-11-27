package com.feralenterprises.raccoon.core.kc;

/** Chat message type reduced to what KC observation needs. */
public enum ChatKind
{
	/** RuneLite {@code GAMEMESSAGE}: a normal game message. */
	GAME,
	/** RuneLite {@code SPAM}: a filtered game message. */
	SPAM,
	/** Anything else (player chat, trade, clan, notifications, …): never KC. */
	OTHER
}
