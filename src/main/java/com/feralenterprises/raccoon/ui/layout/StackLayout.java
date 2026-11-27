package com.feralenterprises.raccoon.ui.layout;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Vertical stack: every visible child gets the full inner width and the height it asks for at that width. No absolute
 * positions, so the same tree lays out correctly at 225, 263 or 280 px and at any text scale.
 */
public final class StackLayout implements LayoutManager {
	private final int gap;

	public StackLayout(int gap) { this.gap = gap; }

	public int gap() { return gap; }

	public static int heightFor(Component c, int width) {
		if (c instanceof HeightForWidth) return ((HeightForWidth) c).heightForWidth(width);
		return c.getPreferredSize().height;
	}

	public int heightForWidth(Container parent, int width) {
		Insets in = parent.getInsets();
		int inner = Math.max(0, width - in.left - in.right);
		int h = in.top + in.bottom;
		int n = 0;
		for (Component c : parent.getComponents()) {
			if (!c.isVisible()) continue;
			h += heightFor(c, inner);
			n++;
		}
		return h + Math.max(0, n - 1) * gap;
	}

	@Override
	public void layoutContainer(Container parent) {
		Insets in = parent.getInsets();
		int inner = Math.max(0, parent.getWidth() - in.left - in.right);
		int y = in.top;
		for (Component c : parent.getComponents()) {
			if (!c.isVisible()) continue;
			int h = heightFor(c, inner);
			c.setBounds(in.left, y, inner, h);
			y += h + gap;
		}
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		int w = parent.getWidth() > 0 ? parent.getWidth() : 225;
		return new Dimension(w, heightForWidth(parent, w));
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }

	@Override public void addLayoutComponent(String name, Component comp) { }
	@Override public void removeLayoutComponent(Component comp) { }
}
