package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.paint.ThemedToolTip;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JToolTip;

/**
 * Framework button: normal, hover, pressed, focused, selected and disabled states; optional artwork icon above the
 * live label. Labels wrap instead of clipping. No action logic lives here.
 */
public class RButton extends JButton implements HeightForWidth {
	private static final int ICON_H = 32;
	private final UiContext ctx;
	private ArtKey icon;

	public RButton(UiContext ctx, String text) {
		super(text);
		this.ctx = ctx;
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setOpaque(false);
		setRolloverEnabled(true);
	}

	public UiContext context() { return ctx; }

	public RButton withIcon(ArtKey key) { icon = key; revalidate(); return this; }

	@Override
	public JToolTip createToolTip() { return ThemedToolTip.create(ctx, this); }

	private boolean hasIcon() { return icon != null; }

	@Override
	public int heightForWidth(int width) {
		FontMetrics fm = ctx.metrics(FontRole.BODY);
		int lines = Math.max(1, TextWrap.wrap(getText(), fm, width - 2 * Tokens.PAD_X).size());
		return lines * fm.getHeight() + 2 * Tokens.PAD_Y + 2 + (hasIcon() ? ICON_H + 2 : 0);
	}

	@Override
	public Dimension getPreferredSize() {
		if (isPreferredSizeSet()) return super.getPreferredSize();
		FontMetrics fm = ctx.metrics(FontRole.BODY);
		int w = Math.max(48, fm.stringWidth(getText()) + 2 * Tokens.PAD_X + 2);
		return new Dimension(w, heightForWidth(w));
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		Palette p = ctx.palette();
		ButtonModel m = getModel();
		int w = getWidth(), h = getHeight();
		boolean on = m.isSelected();
			if (!isEnabled()) g2.setColor(Paints.alpha(p.panel, ctx.themed() ? 160 : 255));
			else if (m.isPressed()) g2.setColor(p.trough);
			else if (on) g2.setColor(p.accentDim);
			else if (m.isRollover()) g2.setColor(p.panelRaised);
			else g2.setColor(p.panel);
			g2.fillRect(0, 0, w, h);
		g2.setColor(!isEnabled() ? p.panelRaised : (on ? p.accent : p.border));
		g2.drawRect(0, 0, w - 1, h - 1);
		if (ctx.themed() && isEnabled() && !m.isPressed()) {
			g2.setColor(Paints.alpha(java.awt.Color.WHITE, 18));
			g2.drawLine(1, 1, w - 2, 1);
		}
		if (isFocusOwner()) {
			g2.setColor(p.focus);
			g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{2f, 2f}, 0f));
			g2.drawRect(2, 2, w - 5, h - 5);
		}
		int y = Tokens.PAD_Y + 1;
		if (hasIcon()) {
			BufferedImage img = ctx.themed() ? ctx.assets().art(icon) : null;
			if (img != null) {
				int iw = Paints.widthForHeight(img, ICON_H);
				Paints.image(g2, img, (w - iw) / 2, y, iw, ICON_H, isEnabled() ? 1f : 0.4f);
			} else {
				Paints.dashedRect(g2, p.muted, (w - ICON_H) / 2, y, ICON_H, ICON_H);
			}
			y += ICON_H + 2;
		}
		g2.setFont(ctx.font(FontRole.BODY));
		FontMetrics fm = g2.getFontMetrics();
		g2.setColor(isEnabled() ? p.text : p.muted);
		List<String> lines = TextWrap.wrap(getText(), fm, w - 2 * Tokens.PAD_X);
		y += fm.getAscent();
		for (String l : lines) {
			g2.drawString(l, (w - fm.stringWidth(l)) / 2, y);
			y += fm.getHeight();
		}
		g2.dispose();
	}
}
