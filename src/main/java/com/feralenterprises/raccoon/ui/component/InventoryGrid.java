package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.model.InventoryView;
import com.feralenterprises.raccoon.ui.model.ItemStack;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/** Authentic 4-column, 7-row, 28-slot inventory arrangement with native 36x32 items and quantity overlays. */
public class InventoryGrid extends RComponent
	implements com.feralenterprises.raccoon.ui.lifecycle.AccountBound, com.feralenterprises.raccoon.ui.lifecycle.Disposable {
	public static final int COLUMNS = 4, ROWS = 7;
	public static final int PAD = 4;
	public static final int GRID_W = Tokens.ITEM_W + 3 * Tokens.INV_PITCH_X;
	public static final int GRID_H = Tokens.ITEM_H + 6 * Tokens.INV_PITCH_Y;

	private InventoryView model;
	private int hover = -1;
	private int lastPaintedItems;
	private final com.feralenterprises.raccoon.ui.lifecycle.RepaintFence fence = new com.feralenterprises.raccoon.ui.lifecycle.RepaintFence(this);
	private Runnable repaintLater = fence.callback();

	public InventoryGrid(UiContext ctx, InventoryView model) {
		super(ctx);
		setModel(model);
		setToolTipText("");
		MouseAdapter m = new MouseAdapter() {
			@Override public void mouseMoved(MouseEvent e) { int s = slotAt(e.getX(), e.getY()); if (s != hover) { hover = s; repaint(); } }
			@Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
		};
		addMouseListener(m);
		addMouseMotionListener(m);
	}

	public InventoryView model() { return model; }
	public int lastPaintedItems() { return lastPaintedItems; }
	public com.feralenterprises.raccoon.ui.lifecycle.RepaintFence fence() { return fence; }

	@Override
	public void clearAccountData() { setModel(InventoryView.of(com.feralenterprises.raccoon.ui.model.DataState.UNAVAILABLE)); }

	@Override
	public void dispose() {
		model = InventoryView.of(com.feralenterprises.raccoon.ui.model.DataState.UNAVAILABLE);
		fence.dispose();
	}

	public void setModel(InventoryView m) {
		if (fence.isDisposed()) return;
		fence.invalidate();
		repaintLater = fence.callback();
		model = m;
		getAccessibleContext().setAccessibleName("Inventory, " + m.state().label());
		repaint();
	}

	private int originX() { return (getWidth() - GRID_W) / 2; }

	public Rectangle slotRect(int i) {
		if (i < 0 || i >= InventoryView.SLOTS) throw new IndexOutOfBoundsException();
		return new Rectangle(originX() + (i % COLUMNS) * Tokens.INV_PITCH_X, PAD + (i / COLUMNS) * Tokens.INV_PITCH_Y,
			Tokens.ITEM_W, Tokens.ITEM_H);
	}

	public int slotAt(int x, int y) {
		for (int i = 0; i < InventoryView.SLOTS; i++) if (slotRect(i).contains(x, y)) return i;
		return -1;
	}

	@Override
	public String getToolTipText(MouseEvent e) {
		int i = slotAt(e.getX(), e.getY());
		if (i < 0) return null;
		if (!model.state().showsData()) return "Slot " + (i + 1) + ": " + model.state().label();
		ItemStack it = model.slot(i);
		return "Slot " + (i + 1) + ": " + (it == null ? "empty" : it.name() + (it.quantity() > 1 ? " x " + it.quantity() : ""));
	}

	@Override
	public int heightForWidth(int width) { return GRID_H + 2 * PAD; }

	@Override
	protected int naturalWidth() { return GRID_W + 2 * PAD; }

	@Override
	public int minimumContentWidth() { return GRID_W + 2 * PAD; }

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		Palette p = ctx.palette();
		Rectangle area = new Rectangle(originX() - PAD, 0, GRID_W + 2 * PAD, GRID_H + 2 * PAD);
		BufferedImage bg = ctx.themed() ? ctx.assets().nativeSprite(NativeSprite.INVENTORY_BACKGROUND, repaintLater) : null;
		if (bg != null) g2.drawImage(bg, area.x, area.y, area.width, area.height, null);
		else {
			g2.setColor(ctx.themed() ? new java.awt.Color(0x3e3529) : p.panel);
			g2.fillRect(area.x, area.y, area.width, area.height);
			g2.setColor(ctx.themed() ? new java.awt.Color(0x1f1a14) : p.border);
			g2.drawRect(area.x, area.y, area.width - 1, area.height - 1);
		}
		if (!ctx.themed()) {
			g2.setColor(p.accentDim);
			for (int i = 0; i < InventoryView.SLOTS; i++) { Rectangle r = slotRect(i); g2.drawRect(r.x, r.y, r.width - 1, r.height - 1); }
		}
		int painted = 0;
		for (int i = 0; i < InventoryView.SLOTS; i++) {
			Rectangle r = slotRect(i);
			if (i == hover) { g2.setColor(Paints.alpha(p.focus, 70)); g2.fillRect(r.x, r.y, r.width, r.height); }
			if (ItemSlots.paintItem(g2, ctx, model.slot(i), r, model.state(), true, repaintLater)) painted++;
		}
		lastPaintedItems = painted;
		if (model.state() == com.feralenterprises.raccoon.ui.model.DataState.STALE) {
			g2.setColor(Paints.alpha(p.stale, 160));
			g2.drawRect(area.x, area.y, area.width - 1, area.height - 1);
		}
		ItemSlots.paintStateOverlay(g2, ctx, model.state(), area);
		g2.dispose();
	}
}
