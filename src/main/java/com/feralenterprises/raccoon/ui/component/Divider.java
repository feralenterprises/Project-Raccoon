package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Section divider. Ornament stays secondary: drawn small, never wider than the content. */
public class Divider extends RComponent {
	public enum Kind { PAW, DIAMOND, PLAIN }

	private static final int HEIGHT = 10;
	private final Kind kind;

	public Divider(UiContext ctx, Kind kind) {
		super(ctx);
		this.kind = kind;
	}

	@Override
	public int heightForWidth(int width) { return HEIGHT; }

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		int w = getWidth(), mid = getHeight() / 2;
		BufferedImage art = null;
		if (ctx.themed() && kind == Kind.PAW) art = ctx.assets().art(ArtKey.DIVIDER_PAW);
		if (ctx.themed() && kind == Kind.DIAMOND) art = ctx.assets().art(ArtKey.DIVIDER_DIAMOND);
		if (art != null) {
			int h = kind == Kind.PAW ? HEIGHT : 7;
			int dw = Math.min(w, Paints.widthForHeight(art, h));
			int dh = Math.round(art.getHeight() * (dw / (float) art.getWidth()));
			if (kind == Kind.PAW) {
				g2.setColor(Paints.alpha(ctx.palette().accent, 90));
				g2.drawLine(0, mid, w - 1, mid);
			}
			Paints.image(g2, art, (w - dw) / 2, (getHeight() - dh) / 2, dw, dh, 1f);
		} else {
			g2.setColor(ctx.themed() ? Paints.alpha(ctx.palette().accent, 110) : ctx.palette().border);
			g2.drawLine(0, mid, w - 1, mid);
		}
		g2.dispose();
	}
}
