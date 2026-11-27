package com.feralenterprises.raccoon.ui.layout;

import java.awt.Dimension;
import javax.swing.JPanel;

/** Transparent vertical container using {@link StackLayout}. */
public class StackPanel extends JPanel implements HeightForWidth {
	private final StackLayout stack;

	public StackPanel(int gap) {
		stack = new StackLayout(gap);
		setLayout(stack);
		setOpaque(false);
	}

	@Override
	public int heightForWidth(int width) { return stack.heightForWidth(this, width); }

	@Override
	public Dimension getPreferredSize() {
		if (isPreferredSizeSet()) return super.getPreferredSize();
		int w = getWidth() > 0 ? getWidth() : 225;
		return new Dimension(w, heightForWidth(w));
	}
}
