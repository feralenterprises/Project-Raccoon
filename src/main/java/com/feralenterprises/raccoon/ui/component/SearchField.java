package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.paint.ThemedToolTip;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Themed text field with a live placeholder; notifies on every edit. */
public class SearchField extends JTextField implements HeightForWidth, com.feralenterprises.raccoon.ui.lifecycle.ContextAware {
	private final UiContext ctx;
	private final String placeholder;

	public SearchField(UiContext ctx, String placeholder, Consumer<String> onChange) {
		this.ctx = ctx;
		this.placeholder = placeholder;
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		getAccessibleContext().setAccessibleName(placeholder);
		restyle();
		getDocument().addDocumentListener(new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e) { onChange.accept(getText()); }
			@Override public void removeUpdate(DocumentEvent e) { onChange.accept(getText()); }
			@Override public void changedUpdate(DocumentEvent e) { onChange.accept(getText()); }
		});
	}

	@Override
	public void contextChanged() { restyle(); }

	private void restyle() {
		Palette p = ctx.palette();
		setFont(ctx.font(FontRole.BODY));
		setForeground(p.text);
		setCaretColor(p.text);
		setSelectionColor(p.accentDim);
		setSelectedTextColor(p.text);
	}

	@Override
	public JToolTip createToolTip() { return ThemedToolTip.create(ctx, this); }

	@Override
	public int heightForWidth(int width) { return ctx.metrics(FontRole.BODY).getHeight() + 8; }

	@Override
	public Dimension getPreferredSize() { return new Dimension(getWidth() > 0 ? getWidth() : 225, heightForWidth(0)); }

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		Palette p = ctx.palette();
		g2.setColor(ctx.themed() ? p.trough : p.panel);
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setColor(isFocusOwner() ? p.focus : p.border);
		g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
		if (getText().isEmpty()) {
			g2.setFont(ctx.font(FontRole.BODY));
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(p.muted);
			g2.drawString(placeholder, 6, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
		}
		g2.dispose();
		super.paintComponent(g);
	}
}
