package com.feralenterprises.raccoon.ui.shell;

import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.component.Tone;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import javax.swing.JComponent;

/** Structural stand-in for destinations whose panels are not built (UI-001 builds no functional panels). */
public final class Placeholder {
	private Placeholder() { }

	public static JComponent forDestination(UiContext ctx, Destination d) {
		StackPanel s = new StackPanel(Tokens.GAP);
		Card c = new Card(ctx, d.label());
		c.addAll(new TextBlock(ctx, "Panel not built. This destination exists so the shell, rail and Back history can "
			+ "be checked with the full product scope. Panel order and design await owner approval.", FontRole.SMALL, Tone.MUTED));
		s.add(c);
		return s;
	}
}
