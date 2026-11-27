package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;

/**
 * The one shared "no account data" presentation (requested by FR-002 docs/ui-integration.md) so panels never invent
 * their own. PROVISIONAL mapping: SIGNED_OUT = UNAUTHENTICATED, RECONNECTING = SUSPENDED_CONTINUITY,
 * WAITING = any non-publishable snapshot before data arrives. RETIRED maps to SIGNED_OUT wording. UNAVAILABLE = no
 * account source attached (initial mount, detached binding).
 */
public class StateNotice extends StackPanel {
	public enum Presence {
		SIGNED_OUT("Not signed in", "Log in to Old School RuneScape to see your account here."),
		WAITING("Waiting for your account", "Your account details will appear once the game has finished loading."),
		RECONNECTING("Reconnecting", "Account details are hidden until the connection is restored."),
		/** No account source is attached (before binding, after detaching). The default of every shell. */
		UNAVAILABLE("Account details unavailable", "Account details are not connected to this panel right now.");

		final String title, body;

		Presence(String title, String body) { this.title = title; this.body = body; }
	}

	public StateNotice(UiContext ctx, Presence presence) {
		super(Tokens.GAP_SMALL);
		add(new TextBlock(ctx, presence.title, FontRole.TITLE, Tone.TEXT));
		add(new TextBlock(ctx, presence.body, FontRole.SMALL, Tone.MUTED));
		getAccessibleContext().setAccessibleName(presence.title);
	}
}
