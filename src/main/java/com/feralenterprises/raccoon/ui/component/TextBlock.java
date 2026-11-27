package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;

/** Wrapping live text. Never clips: its height grows with the number of wrapped lines. */
public class TextBlock extends RComponent {
	private String text;
	private final FontRole role;
	private final Tone tone;
	private boolean centered;

	public TextBlock(UiContext ctx, String text, FontRole role, Tone tone) {
		super(ctx);
		this.text = text == null ? "" : text;
		this.role = role;
		this.tone = tone;
		getAccessibleContext().setAccessibleName(this.text);
	}

	public TextBlock centered() { centered = true; return this; }
	public String text() { return text; }

	public void setText(String t) {
		text = t == null ? "" : t;
		getAccessibleContext().setAccessibleName(text);
		revalidate();
		repaint();
	}

	@Override
	public int heightForWidth(int width) {
		FontMetrics fm = ctx.metrics(role);
		return Math.max(1, TextWrap.wrap(text, fm, width).size()) * fm.getHeight();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		g2.setFont(ctx.font(role));
		FontMetrics fm = g2.getFontMetrics();
		g2.setColor(tone.color(ctx.palette()));
		List<String> lines = TextWrap.wrap(text, fm, getWidth());
		int y = fm.getAscent();
		for (String l : lines) {
			int x = centered ? (getWidth() - fm.stringWidth(l)) / 2 : 0;
			g2.drawString(l, x, y);
			y += fm.getHeight();
		}
		g2.dispose();
	}
}
