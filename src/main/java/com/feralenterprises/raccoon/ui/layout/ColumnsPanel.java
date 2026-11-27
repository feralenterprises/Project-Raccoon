package com.feralenterprises.raccoon.ui.layout;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * Equal-width columns that flow into rows. Uses up to {@code maxColumns}, but drops columns when any child's
 * preferred width would not fit a cell, so labels are never squeezed or broken mid-word. Each row is as tall as its
 * tallest cell at the cell width.
 */
public class ColumnsPanel extends JPanel implements HeightForWidth {
	private final int maxColumns;
	private final int gap;

	public ColumnsPanel(int maxColumns, int gap) {
		this.maxColumns = Math.max(1, maxColumns);
		this.gap = gap;
		setOpaque(false);
		setLayout(new Layout());
	}

	private List<Component> visible() {
		List<Component> v = new ArrayList<>();
		for (Component c : getComponents()) if (c.isVisible()) v.add(c);
		return v;
	}

	private static int cellWidth(int inner, int cols, int gap) { return Math.max(1, (inner - (cols - 1) * gap) / cols); }

	/** Columns used at a given outer width. */
	public int columnsFor(int width) {
		Insets in = getInsets();
		int inner = width - in.left - in.right;
		List<Component> v = visible();
		for (int c = Math.min(maxColumns, Math.max(1, v.size())); c > 1; c--) {
			int cw = cellWidth(inner, c, gap);
			boolean fits = true;
			for (Component k : v) if (k.getPreferredSize().width > cw) { fits = false; break; }
			if (fits) return c;
		}
		return 1;
	}

	@Override
	public int heightForWidth(int width) {
		Insets in = getInsets();
		int cols = columnsFor(width);
		int cw = cellWidth(width - in.left - in.right, cols, gap);
		List<Component> v = visible();
		int h = 0, rows = 0;
		for (int i = 0; i < v.size(); i += cols) {
			int rowH = 0;
			for (int k = i; k < Math.min(v.size(), i + cols); k++) rowH = Math.max(rowH, StackLayout.heightFor(v.get(k), cw));
			h += rowH;
			rows++;
		}
		return in.top + in.bottom + h + Math.max(0, rows - 1) * gap;
	}

	@Override
	public Dimension getPreferredSize() {
		int w = getWidth() > 0 ? getWidth() : 225;
		return new Dimension(w, heightForWidth(w));
	}

	private final class Layout implements LayoutManager {
		@Override
		public void layoutContainer(Container p) {
			Insets in = getInsets();
			int cols = columnsFor(getWidth());
			int cw = cellWidth(getWidth() - in.left - in.right, cols, gap);
			List<Component> v = visible();
			int y = in.top;
			for (int i = 0; i < v.size(); i += cols) {
				int end = Math.min(v.size(), i + cols), rowH = 0;
				for (int k = i; k < end; k++) rowH = Math.max(rowH, StackLayout.heightFor(v.get(k), cw));
				for (int k = i; k < end; k++) v.get(k).setBounds(in.left + (k - i) * (cw + gap), y, cw, rowH);
				y += rowH + gap;
			}
		}

		@Override public Dimension preferredLayoutSize(Container p) { return getPreferredSize(); }
		@Override public Dimension minimumLayoutSize(Container p) { return getPreferredSize(); }
		@Override public void addLayoutComponent(String n, Component c) { }
		@Override public void removeLayoutComponent(Component c) { }
	}
}
