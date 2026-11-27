package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.paint.ThemedToolTip;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Dimension;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.JToolTip;

/** Base for painted framework components: transparent, context-aware, themed tooltip, width-driven height. */
public abstract class RComponent extends JComponent implements HeightForWidth {
	protected final UiContext ctx;

	protected RComponent(UiContext ctx) {
		this.ctx = ctx;
		setOpaque(false);
	}

	public UiContext context() { return ctx; }

	@Override
	public AccessibleContext getAccessibleContext() {
		if (accessibleContext == null) {
			accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.PANEL; }
			};
		}
		return accessibleContext;
	}

	@Override
	public JToolTip createToolTip() { return ThemedToolTip.create(ctx, this); }

	@Override
	public Dimension getPreferredSize() {
		if (isPreferredSizeSet()) return super.getPreferredSize();
		int w = getWidth() > 0 ? getWidth() : naturalWidth();
		return new Dimension(w, heightForWidth(w));
	}

	/** Width used when no width has been assigned yet. */
	protected int naturalWidth() { return 225; }
}
