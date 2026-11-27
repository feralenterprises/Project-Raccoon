package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.lifecycle.ContextAware;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.paint.ThemedToolTip;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JToolTip;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

/**
 * Themed single-choice dropdown (the R02 selects: encounter, sort, filter, skill, method, state). A standard
 * {@link JComboBox}, so keyboard, mouse and accessibility behave as Swing users expect; only painting is themed.
 * Every dropdown has an accessible name. Selection changes made by the user or by {@link #select} are reported once
 * to the listener given at construction; {@link #setOptions} replaces the options without reporting.
 *
 * @param <T> option type; shown through the label function, compared with {@code equals}
 */
public class RDropdown<T> extends JComboBox<T> implements HeightForWidth, ContextAware, TextFit {
	private static final int ARROW_W = 16;

	private final UiContext ctx;
	private final Function<? super T, String> label;
	private boolean quiet;

	public RDropdown(UiContext ctx, String accessibleName, List<? extends T> options, Function<? super T, String> label,
		Consumer<? super T> onSelect) {
		this.ctx = Objects.requireNonNull(ctx);
		this.label = Objects.requireNonNull(label);
		if (accessibleName == null || accessibleName.trim().isEmpty()) throw new IllegalArgumentException("accessible name required");
		getAccessibleContext().setAccessibleName(accessibleName);
		setToolTipText(accessibleName);
		setOpaque(false);
		setFocusable(true);
		setMaximumRowCount(12);
		setUI(new ThemedUi());
		setRenderer(new Renderer());
		setOptions(options);
		addActionListener(e -> {
			if (!quiet && onSelect != null && getSelectedIndex() >= 0) onSelect.accept(getItemAt(getSelectedIndex()));
		});
		restyle();
	}

	/** Replaces the options, keeping the current selection when it is still offered; never reports a selection. */
	public final void setOptions(List<? extends T> options) {
		T keep = getSelectedIndex() >= 0 ? getItemAt(getSelectedIndex()) : null;
		quiet = true;
		try {
			removeAllItems();
			for (T o : options) addItem(Objects.requireNonNull(o, "option"));
			int i = keep == null ? -1 : indexOf(keep);
			setSelectedIndex(i >= 0 ? i : (getItemCount() > 0 ? 0 : -1));
		} finally {
			quiet = false;
		}
	}

	/** Selects an option programmatically. Returns false (and changes nothing) if it is not offered. */
	public boolean select(T option) {
		int i = indexOf(option);
		if (i < 0) return false;
		setSelectedIndex(i);
		return true;
	}

	/** Selects without notifying the listener (restoring saved route state). */
	public boolean restore(T option) {
		quiet = true;
		try { return select(option); } finally { quiet = false; }
	}

	public T selected() { return getSelectedIndex() < 0 ? null : getItemAt(getSelectedIndex()); }

	public String labelOf(T option) { return label.apply(option); }

	private int indexOf(T option) {
		for (int i = 0; i < getItemCount(); i++) if (getItemAt(i).equals(option)) return i;
		return -1;
	}

	@Override
	public void contextChanged() { restyle(); }

	private void restyle() {
		setFont(ctx.font(FontRole.BODY));
		Palette p = ctx.palette();
		setForeground(p.text);
		setBackground(p.panel);
		revalidate();
		repaint();
	}

	@Override
	public JToolTip createToolTip() { return ThemedToolTip.create(ctx, this); }

	@Override
	public int heightForWidth(int width) { return ctx.metrics(FontRole.BODY).getHeight() + 8; }

	@Override
	public Dimension getPreferredSize() {
		int w = 0;
		java.awt.FontMetrics fm = ctx.metrics(FontRole.BODY);
		for (int i = 0; i < getItemCount(); i++) w = Math.max(w, fm.stringWidth(label.apply(getItemAt(i))));
		return new Dimension(w + 12 + ARROW_W, heightForWidth(0));
	}

	/** The selected label on one line plus the arrow (the closed control does not wrap). */
	@Override
	public int requiredTextWidth() {
		T sel = selected();
		return (sel == null ? 0 : ctx.metrics(FontRole.BODY).stringWidth(label.apply(sel))) + 12 + ARROW_W;
	}

	@Override
	public Dimension getMinimumSize() { return new Dimension(ARROW_W + 24, heightForWidth(0)); }

	private final class Renderer extends DefaultListCellRenderer {
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
			@SuppressWarnings("unchecked")
			String text = value == null ? "" : label.apply((T) value);
			super.getListCellRendererComponent(list, text, index, selected, focus);
			Palette p = ctx.palette();
			setFont(ctx.font(FontRole.BODY));
			setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
			setOpaque(index >= 0);
			setBackground(selected ? p.accentDim : p.panel);
			setForeground(p.text);
			return this;
		}
	}

	private final class ThemedUi extends BasicComboBoxUI {
		@Override
		protected JButton createArrowButton() {
			JButton b = new JButton() {
				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2 = Paints.prepare(g);
					g2.setColor(ctx.palette().muted);
					int cx = getWidth() / 2, cy = getHeight() / 2;
					g2.fillPolygon(new int[] {cx - 4, cx + 4, cx}, new int[] {cy - 2, cy - 2, cy + 3}, 3);
					g2.dispose();
				}
			};
			b.setBorder(BorderFactory.createEmptyBorder());
			b.setContentAreaFilled(false);
			b.setFocusable(false);
			b.setPreferredSize(new Dimension(ARROW_W, 1));
			b.getAccessibleContext().setAccessibleName("Open " + RDropdown.this.getAccessibleContext().getAccessibleName());
			return b;
		}

		@Override
		protected ComboPopup createPopup() {
			BasicComboPopup p = new BasicComboPopup(comboBox);
			p.setBorder(BorderFactory.createLineBorder(ctx.palette().border));
			return p;
		}

		@Override
		protected Insets getInsets() { return new Insets(0, 0, 0, 0); }

		@Override
		public void paint(Graphics g, JComponent c) {
			Graphics2D g2 = Paints.prepare(g);
			Palette p = ctx.palette();
			g2.setColor(ctx.themed() ? p.trough : p.panel);
			g2.fillRect(0, 0, c.getWidth(), c.getHeight());
			g2.setColor(comboBox.isFocusOwner() || isPopupVisible(comboBox) ? p.focus : p.border);
			g2.drawRect(0, 0, c.getWidth() - 1, c.getHeight() - 1);
			T sel = selected();
			if (sel != null) {
				g2.setFont(ctx.font(FontRole.BODY));
				java.awt.FontMetrics fm = g2.getFontMetrics();
				Rectangle r = rectangleForCurrentValue();
				String s = clip(label.apply(sel), fm, r.width - 8);
				g2.setColor(comboBox.isEnabled() ? p.text : p.muted);
				g2.drawString(s, 6, (c.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
			}
			g2.dispose();
		}

		@Override
		public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { }
	}

	/** Fits text to a width with an ellipsis; the full label remains the accessible value and the option text. */
	static String clip(String s, java.awt.FontMetrics fm, int width) {
		if (fm.stringWidth(s) <= width) return s;
		String e = "…";
		int n = s.length();
		while (n > 0 && fm.stringWidth(s.substring(0, n) + e) > width) n--;
		return s.substring(0, n) + e;
	}
}
