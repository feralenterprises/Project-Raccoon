package com.feralenterprises.raccoon.ui.paint;

import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import javax.swing.border.Border;

/**
 * Resizable stone frame. THEMED: nine-slice stone with fixed 16 px corners; STRUCTURAL (or missing art): 1 px line.
 * Insets are identical in both skins so layout and measurements do not change when the skin changes.
 */
public final class FrameBorder implements Border {
	private final UiContext ctx;
	private final int padding;

	public FrameBorder(UiContext ctx, int padding) {
		this.ctx = ctx;
		this.padding = padding;
	}

	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
		Graphics2D g2 = Paints.prepare(g);
		BufferedImage atlas = ctx.themed() ? ctx.assets().art(ArtKey.FRAME_STONE_NINE_SLICE) : null;
		if (atlas != null) {
			NineSlice.paint(g2, atlas, Tokens.FRAME_SLICE, Tokens.FRAME_CORNER, x, y, w, h);
		} else {
			g2.setColor(ctx.palette().border);
			g2.drawRect(x, y, w - 1, h - 1);
		}
		g2.dispose();
	}

	@Override
	public Insets getBorderInsets(Component c) {
		int i = Tokens.FRAME_INSET + padding;
		return new Insets(i, i, i, i);
	}

	@Override
	public boolean isBorderOpaque() { return false; }
}
