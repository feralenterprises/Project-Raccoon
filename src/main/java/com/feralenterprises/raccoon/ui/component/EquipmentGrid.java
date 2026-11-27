package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.model.EquipmentSlot;
import com.feralenterprises.raccoon.ui.model.EquipmentView;
import com.feralenterprises.raccoon.ui.model.ItemStack;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * The familiar OSRS worn-equipment arrangement: 11 slots on a 3x5 grid with connecting strips. Items use native
 * 36x32 images from the asset provider; empty slots show the native slot icon when the provider supplies it.
 */
public class EquipmentGrid extends RComponent
	implements com.feralenterprises.raccoon.ui.lifecycle.AccountBound, com.feralenterprises.raccoon.ui.lifecycle.Disposable {
	public static final int GRID_W = Tokens.EQUIP_SLOT + 2 * Tokens.EQUIP_PITCH_X;
	public static final int GRID_H = Tokens.EQUIP_SLOT + 4 * Tokens.EQUIP_PITCH_Y;

	private EquipmentView model;
	private EquipmentSlot hover;
	private int lastPaintedItems;
	private final com.feralenterprises.raccoon.ui.lifecycle.RepaintFence fence = new com.feralenterprises.raccoon.ui.lifecycle.RepaintFence(this);
	private Runnable repaintLater = fence.callback();

	public EquipmentGrid(UiContext ctx, EquipmentView model) {
		super(ctx);
		setModel(model);
		setToolTipText("");
		MouseAdapter m = new MouseAdapter() {
			@Override public void mouseMoved(MouseEvent e) { EquipmentSlot s = slotAt(e.getX(), e.getY()); if (s != hover) { hover = s; repaint(); } }
			@Override public void mouseExited(MouseEvent e) { hover = null; repaint(); }
		};
		addMouseListener(m);
		addMouseMotionListener(m);
	}

	public EquipmentView model() { return model; }
	public int lastPaintedItems() { return lastPaintedItems; }
	public com.feralenterprises.raccoon.ui.lifecycle.RepaintFence fence() { return fence; }

	@Override
	public void clearAccountData() { setModel(EquipmentView.of(com.feralenterprises.raccoon.ui.model.DataState.UNAVAILABLE)); }

	@Override
	public void dispose() {
		model = EquipmentView.of(com.feralenterprises.raccoon.ui.model.DataState.UNAVAILABLE);
		fence.dispose();
	}

	public void setModel(EquipmentView m) {
		if (fence.isDisposed()) return;
		fence.invalidate();
		repaintLater = fence.callback();
		model = m;
		getAccessibleContext().setAccessibleName("Equipment, " + m.state().label());
		repaint();
	}

	private int originX() { return (getWidth() - GRID_W) / 2; }

	public Rectangle slotRect(EquipmentSlot s) {
		return new Rectangle(originX() + s.col() * Tokens.EQUIP_PITCH_X, s.row() * Tokens.EQUIP_PITCH_Y,
			Tokens.EQUIP_SLOT, Tokens.EQUIP_SLOT);
	}

	public EquipmentSlot slotAt(int x, int y) {
		for (EquipmentSlot s : EquipmentSlot.values()) if (slotRect(s).contains(x, y)) return s;
		return null;
	}

	@Override
	public String getToolTipText(MouseEvent e) {
		EquipmentSlot s = slotAt(e.getX(), e.getY());
		if (s == null) return null;
		ItemStack it = model.item(s);
		if (!model.state().showsData()) return s.label() + ": " + model.state().label();
		return s.label() + ": " + (it == null ? "empty" : it.name());
	}

	@Override
	public int heightForWidth(int width) { return GRID_H; }

	@Override
	protected int naturalWidth() { return GRID_W; }

	@Override
	public int minimumContentWidth() { return GRID_W; }

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		Palette p = ctx.palette();
		paintConnectors(g2, p);
		int painted = 0;
		for (EquipmentSlot s : EquipmentSlot.values()) {
			Rectangle r = slotRect(s);
			paintSlotBackground(g2, p, r, s == hover);
			ItemStack it = model.item(s);
			if (it != null) {
				if (ItemSlots.paintItem(g2, ctx, it, r, model.state(), true, repaintLater)) painted++;
			} else if (model.state() != DataState.LOADING && model.state() != DataState.UNAVAILABLE) {
				paintEmptyIcon(g2, p, s, r);
			}
		}
		lastPaintedItems = painted;
		if (model.state() == DataState.STALE) {
			g2.setColor(Paints.alpha(p.stale, 160));
			g2.drawRect(originX() - 2, 0, GRID_W + 3, GRID_H - 1);
		}
		ItemSlots.paintStateOverlay(g2, ctx, model.state(), new Rectangle(originX() - 2, 0, GRID_W + 4, GRID_H));
		g2.dispose();
	}

	private void paintConnectors(Graphics2D g2, Palette p) {
		BufferedImage h = ctx.themed() ? ctx.assets().nativeSprite(NativeSprite.EQUIP_CONNECTOR_H, repaintLater) : null;
		BufferedImage v = ctx.themed() ? ctx.assets().nativeSprite(NativeSprite.EQUIP_CONNECTOR_V, repaintLater) : null;
		int half = Tokens.EQUIP_SLOT / 2;
		Rectangle head = slotRect(EquipmentSlot.HEAD), feet = slotRect(EquipmentSlot.FEET);
		if (v != null) g2.drawImage(v, head.x + half - v.getWidth() / 2, head.y + half, v.getWidth(), feet.y - head.y, null);
		else { g2.setColor(Paints.alpha(p.border, 150)); g2.fillRect(head.x + half - 1, head.y + half, 3, feet.y - head.y); }
		for (EquipmentSlot rowStart : new EquipmentSlot[]{EquipmentSlot.CAPE, EquipmentSlot.WEAPON, EquipmentSlot.HANDS}) {
			Rectangle a = slotRect(rowStart);
			int y = a.y + half;
			int x1 = a.x + half, x2 = a.x + 2 * Tokens.EQUIP_PITCH_X + half;
			if (h != null) g2.drawImage(h, x1, y - h.getHeight() / 2, x2 - x1, h.getHeight(), null);
			else { g2.setColor(Paints.alpha(p.border, 150)); g2.fillRect(x1, y - 1, x2 - x1, 3); }
		}
	}

	private void paintSlotBackground(Graphics2D g2, Palette p, Rectangle r, boolean hot) {
		BufferedImage bg = ctx.themed() ? ctx.assets().nativeSprite(NativeSprite.EQUIP_SLOT_TILE, repaintLater) : null;
		if (bg != null) {
			g2.drawImage(bg, r.x, r.y, r.width, r.height, null);
		} else {
			g2.setColor(ctx.themed() ? new java.awt.Color(0x3e3529) : p.panel);
			g2.fillRect(r.x, r.y, r.width, r.height);
			g2.setColor(ctx.themed() ? new java.awt.Color(0x1f1a14) : p.border);
			g2.drawRect(r.x, r.y, r.width - 1, r.height - 1);
			if (ctx.themed()) {
				g2.setColor(new java.awt.Color(0x5a4c39));
				g2.drawLine(r.x + 1, r.y + r.height - 2, r.x + r.width - 2, r.y + r.height - 2);
			}
		}
		// UI-004: EQUIP_SLOT_SELECTED (179) decoded from the OSRS cache is a bright red tile, not a hover state, so hover
		// keeps the subtle overlay. The key stays available for a deliberate "selected slot" use.
		if (hot) {
			g2.setColor(Paints.alpha(p.focus, 90));
			g2.drawRect(r.x, r.y, r.width - 1, r.height - 1);
		}
	}

	private void paintEmptyIcon(Graphics2D g2, Palette p, EquipmentSlot s, Rectangle r) {
		BufferedImage icon = ctx.themed() ? ctx.assets().nativeSprite(s.emptySprite(), repaintLater) : null;
		if (icon != null) {
			g2.drawImage(icon, r.x + (r.width - icon.getWidth()) / 2, r.y + (r.height - icon.getHeight()) / 2, null);
			return;
		}
		// Labelled text fallback: no recreated game artwork.
		g2.setFont(ctx.font(FontRole.QUANTITY));
		FontMetrics fm = g2.getFontMetrics();
		String t = s.shortLabel();
		g2.setColor(Paints.alpha(p.muted, 170));
		g2.drawString(t, r.x + (r.width - fm.stringWidth(t)) / 2, r.y + (r.height + fm.getAscent()) / 2 - 2);
	}
}
