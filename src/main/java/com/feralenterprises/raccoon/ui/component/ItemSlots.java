package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.model.ItemStack;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** Shared item drawing for equipment and inventory so both grids look and behave identically. */
public final class ItemSlots {
	private ItemSlots() { }

	/** Native item-quantity colours: yellow below 100K, white "K" below 10M, green "M" above. */
	public static final Color QTY_YELLOW = new Color(255, 255, 0);
	public static final Color QTY_WHITE = new Color(255, 255, 255);
	public static final Color QTY_GREEN = new Color(0, 255, 128);

	public static String quantityText(int q) {
		if (q < 100_000) return Integer.toString(q);
		if (q < 10_000_000) return (q / 1_000) + "K";
		return (q / 1_000_000) + "M";
	}

	public static Color quantityColor(int q) {
		if (q < 100_000) return QTY_YELLOW;
		if (q < 10_000_000) return QTY_WHITE;
		return QTY_GREEN;
	}

	/**
	 * Draws one item (36x32, never scaled) centred in r. Returns true if a real item image was painted. Missing images
	 * draw a dashed placeholder; the item name remains available through the tooltip.
	 */
	public static boolean paintItem(Graphics2D g, UiContext ctx, ItemStack item, Rectangle r, DataState state,
		boolean showQuantity, Runnable repaint) {
		if (item == null) return false;
		Palette p = ctx.palette();
		int x = r.x + (r.width - Tokens.ITEM_W) / 2, y = r.y + (r.height - Tokens.ITEM_H) / 2;
		BufferedImage img = ctx.assets().item(item.itemId(), item.quantity(), repaint);
		float alpha = state == DataState.STALE ? 0.6f : 1f;
		boolean painted = false;
		if (img != null) {
			Paints.sprite(g, img, x, y, Tokens.ITEM_W, Tokens.ITEM_H, alpha);
			painted = true;
		} else {
			Paints.dashedRect(g, p.muted, x + 2, y + 2, Tokens.ITEM_W - 4, Tokens.ITEM_H - 4);
			g.setFont(ctx.font(FontRole.QUANTITY));
			g.setColor(p.muted);
			g.drawString("?", x + Tokens.ITEM_W / 2 - 3, y + Tokens.ITEM_H / 2 + 4);
		}
		if (showQuantity && item.quantity() != 1) {
			g.setFont(ctx.font(FontRole.QUANTITY));
			FontMetrics fm = g.getFontMetrics();
			String t = quantityText(item.quantity());
			g.setColor(Color.BLACK);
			g.drawString(t, x + 1, y + fm.getAscent() + 1);
			g.setColor(quantityColor(item.quantity()));
			g.drawString(t, x, y + fm.getAscent());
		}
		return painted;
	}

	/** Overlay shown across a grid for states that carry no item data. */
	public static void paintStateOverlay(Graphics2D g, UiContext ctx, DataState state, Rectangle area) {
		if (state.showsData() || state == DataState.EMPTY) return;
		Palette p = ctx.palette();
		g.setColor(Paints.alpha(p.background, ctx.themed() ? 150 : 170));
		g.fillRect(area.x, area.y, area.width, area.height);
		g.setFont(ctx.font(FontRole.VALUE));
		FontMetrics fm = g.getFontMetrics();
		String t = state == DataState.LOADING ? "Loading…" : "Unavailable";
		int tw = fm.stringWidth(t) + 12, th = fm.getHeight() + 4;
		int x = area.x + (area.width - tw) / 2, y = area.y + (area.height - th) / 2;
		g.setColor(p.panel);
		g.fillRect(x, y, tw, th);
		g.setColor(StatusIndicator.colorFor(state, p));
		g.drawRect(x, y, tw - 1, th - 1);
		g.setColor(p.text);
		g.drawString(t, x + 6, y + 2 + fm.getAscent());
	}
}
