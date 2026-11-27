package com.feralenterprises.raccoon.ui.paint;

import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JToolTip;

/** Per-component styled tooltip. Avoids changing UIManager defaults, which would restyle all of RuneLite. */
public final class ThemedToolTip {
	private ThemedToolTip() { }

	public static JToolTip create(UiContext ctx, JComponent owner) {
		JToolTip tip = new JToolTip();
		tip.setComponent(owner);
		Palette p = ctx.palette();
		tip.setBackground(p.panelRaised);
		tip.setForeground(p.text);
		tip.setFont(ctx.font(FontRole.SMALL));
		tip.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(p.border),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));
		return tip;
	}
}
