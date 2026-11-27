package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** Live title with optional character art and a divider beneath. Ornament never displaces the text. */
public class SectionHeader extends RComponent {
	private static final int ART_H = 28;
	private final String title;
	private final ArtKey character;
	private final Divider.Kind divider;

	public SectionHeader(UiContext ctx, String title, ArtKey character, Divider.Kind divider) {
		super(ctx);
		this.title = title;
		this.character = character;
		this.divider = divider;
		getAccessibleContext().setAccessibleName(title);
	}

	public String title() { return title; }

	private int artWidth() {
		if (character == null) return 0;
		BufferedImage img = ctx.assets().art(character);
		return img == null ? ART_H : Paints.widthForHeight(img, ART_H);
	}

	private int textX() { return character == null ? 0 : artWidth() + Tokens.GAP; }

	@Override
	public int heightForWidth(int width) {
		FontMetrics fm = ctx.metrics(FontRole.TITLE);
		int lines = TextWrap.wrap(title, fm, width - textX()).size();
		int top = Math.max(character == null ? 0 : ART_H, lines * fm.getHeight());
		return top + (divider == null ? 0 : 3 + 10);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		Palette p = ctx.palette();
		g2.setFont(ctx.font(FontRole.TITLE));
		FontMetrics fm = g2.getFontMetrics();
		List<String> lines = TextWrap.wrap(title, fm, getWidth() - textX());
		int textH = lines.size() * fm.getHeight();
		int top = Math.max(character == null ? 0 : ART_H, textH);
		if (character != null) {
			BufferedImage img = ctx.themed() ? ctx.assets().art(character) : null;
			if (img != null) Paints.image(g2, img, 0, (top - ART_H) / 2, artWidth(), ART_H, 1f);
			else Paints.dashedRect(g2, p.muted, 0, (top - ART_H) / 2, artWidth(), ART_H);
		}
		g2.setColor(ctx.themed() ? p.accent : p.text);
		int y = (top - textH) / 2 + fm.getAscent();
		for (String l : lines) { g2.drawString(l, textX(), y); y += fm.getHeight(); }
		g2.dispose();
		if (divider != null) {
			Divider d = new Divider(ctx, divider);
			d.setSize(getWidth(), 10);
			Graphics dg = g.create(0, top + 3, getWidth(), 10);
			d.paint(dg);
			dg.dispose();
		}
	}
}
