package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.paint.FrameBorder;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.JComponent;

/** Framed container: stone frame (or line), its own content surface, optional live heading, stacked children. */
public class Card extends StackPanel {
	private final UiContext ctx;

	public Card(UiContext ctx, String heading) {
		super(Tokens.GAP);
		this.ctx = ctx;
		setBorder(new FrameBorder(ctx, 0));
		if (heading != null) {
			TextBlock h = new TextBlock(ctx, heading, FontRole.TITLE, Tone.TEXT);
			h.setName("card-heading");
			add(h);
		}
	}

	public Card addAll(JComponent... children) {
		for (JComponent c : children) super.add(c);
		return this;
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		Insets in = getInsets();
		int band = Math.max(2, in.left - 4);
		g2.setColor(ctx.themed() ? Paints.alpha(ctx.palette().panel, 236) : ctx.palette().panel);
		g2.fillRect(band, band, getWidth() - 2 * band, getHeight() - 2 * band);
		g2.dispose();
	}
}
