package com.feralenterprises.raccoon.workbench;

import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

/** Measures a laid-out page: horizontal overflow and clipped (under-allocated) components. */
public final class LayoutAudit {
	private LayoutAudit() { }

	public static void layoutTree(Component c) {
		if (c instanceof Container) {
			Container k = (Container) c;
			k.doLayout();
			for (Component child : k.getComponents()) layoutTree(child);
		}
	}

	public static List<String> audit(Container root) {
		List<String> issues = new ArrayList<>();
		walk(root, root, issues);
		return issues;
	}

	private static void walk(Container root, Container c, List<String> issues) {
		for (Component k : c.getComponents()) {
			if (!k.isVisible()) continue;
			Rectangle r = SwingUtilities.convertRectangle(c, k.getBounds(), root);
			String name = k.getClass().getSimpleName() + (k.getName() != null ? "[" + k.getName() + "]" : "");
			if (r.x < 0 || r.x + r.width > root.getWidth())
				issues.add("OVERFLOW " + name + " x=" + r.x + " w=" + r.width + " root=" + root.getWidth());
			if (k instanceof HeightForWidth && k.getWidth() > 0) {
				HeightForWidth h = (HeightForWidth) k;
				int need = h.heightForWidth(k.getWidth());
				if (need > k.getHeight()) issues.add("CLIPPED-V " + name + " needs " + need + " has " + k.getHeight());
				int minW = h.minimumContentWidth();
				if (minW > k.getWidth()) issues.add("CLIPPED-H " + name + " needs " + minW + " has " + k.getWidth());
			}
			if (k instanceof Container) walk(root, (Container) k, issues);
		}
		overlaps(c, issues);
	}

	/**
	 * Siblings laid out by framework containers must not overlap (UI-005). Scroll panes, viewports and the rail
	 * container (intentional overlay cues) are excluded.
	 */
	public static void overlaps(Container c, List<String> issues) {
		if (c instanceof javax.swing.JScrollPane || c instanceof javax.swing.JViewport || c.getClass().getSimpleName().equals("RailContainer")) return;
		Component[] k = c.getComponents();
		for (int i = 0; i < k.length; i++) {
			if (!k[i].isVisible() || k[i].getWidth() == 0 || k[i].getHeight() == 0) continue;
			for (int j = i + 1; j < k.length; j++) {
				if (!k[j].isVisible() || k[j].getWidth() == 0 || k[j].getHeight() == 0) continue;
				if (k[i].getBounds().intersects(k[j].getBounds()))
					issues.add("OVERLAP " + k[i].getClass().getSimpleName() + " / " + k[j].getClass().getSimpleName() + " in " + c.getClass().getSimpleName());
			}
		}
	}

	/** Header controls must be fully inside the header and visible (UI-005 inaccessible-control check). */
	public static List<String> headerAccess(com.feralenterprises.raccoon.ui.shell.ShellHeader h) {
		List<String> issues = new ArrayList<>(audit(h));
		Rectangle box = new Rectangle(0, 0, h.getWidth(), h.getHeight());
		for (Component c : new Component[]{h.back(), h.settings(), h.menu()}) {
			if (!c.isVisible() && c != h.menu()) issues.add("INACCESSIBLE " + c.getName() + " hidden");
			if (c.isVisible() && !box.contains(c.getBounds())) issues.add("INACCESSIBLE " + c.getName() + " outside header " + c.getBounds());
			if (c.isVisible() && (c.getWidth() < 16 || c.getHeight() < 16)) issues.add("INACCESSIBLE " + c.getName() + " below 16 px");
		}
		return issues;
	}

	public static int count(Container c, Class<?> type) {
		int n = 0;
		for (Component k : c.getComponents()) {
			if (type.isInstance(k)) n++;
			if (k instanceof Container) n += count((Container) k, type);
		}
		return n;
	}
}
