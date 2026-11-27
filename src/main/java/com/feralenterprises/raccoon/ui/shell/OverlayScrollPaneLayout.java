package com.feralenterprises.raccoon.ui.shell;

import java.awt.Container;
import java.awt.Rectangle;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneLayout;

/**
 * Lays the vertical scrollbar either beside the viewport (reserved) or over its right edge (overlay). In overlay
 * mode the viewport keeps the full width; the bar sits inside the page's 8 px inset, so it never covers content.
 * (Concept from Burrow SidebarHost, re-implemented.)
 */
final class OverlayScrollPaneLayout extends ScrollPaneLayout {
	private boolean overlay;

	void setOverlay(boolean overlay) { this.overlay = overlay; }
	boolean overlay() { return overlay; }

	@Override
	public void layoutContainer(Container parent) {
		if (!overlay) { super.layoutContainer(parent); return; }
		JScrollPane sp = (JScrollPane) parent;
		Rectangle r = new Rectangle(sp.getInsets().left, sp.getInsets().top,
			sp.getWidth() - sp.getInsets().left - sp.getInsets().right, sp.getHeight() - sp.getInsets().top - sp.getInsets().bottom);
		viewport.setBounds(r);
		if (vsb != null) {
			int w = vsb.getPreferredSize().width;
			vsb.setVisible(true);
			vsb.setBounds(r.x + r.width - w, r.y, w, r.height);
		}
		if (hsb != null) hsb.setVisible(false);
	}
}
