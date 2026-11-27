package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.model.ProgressView;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Real progress bar driven by a {@link ProgressView}: live label, live value, fill width computed from the model.
 * Unavailable stays empty and labelled; loading shows a hatched trough; stale keeps the fill but marks its age.
 */
public class ProgressBar extends RComponent implements com.feralenterprises.raccoon.ui.lifecycle.AccountBound {
	public enum Variant { PROGRESS, GOAL }

	private final Variant variant;
	private ProgressView model;

	public ProgressBar(UiContext ctx, ProgressView model, Variant variant) {
		super(ctx);
		this.variant = variant;
		setModel(model);
	}

	public ProgressView model() { return model; }

	public void setModel(ProgressView m) {
		model = m;
		setToolTipText(m.evidence().isEmpty() ? null : m.evidence().line());
		getAccessibleContext().setAccessibleName(m.label() + ": " + valueText());
		revalidate();
		repaint();
	}

	@Override
	public void clearAccountData() {
		setModel(new ProgressView(model.label(), 0, 0, DataState.UNAVAILABLE, null));
	}

	public String valueText() {
		switch (model.state()) {
			case UNAVAILABLE: return "—";
			case LOADING: return "…";
			case EMPTY: return "0%";
			default:
				if (model.target() <= 0) return "—";
				String pct = Math.round(model.fraction() * 100) + "%";
				return model.state() == DataState.STALE ? pct + " (stale)" : pct;
		}
	}

	public String detailText() {
		if (!model.state().showsData() || model.target() <= 0) return null;
		NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
		return nf.format(model.current()) + " / " + nf.format(model.target());
	}

	private int labelWidth(int width) {
		FontMetrics vf = ctx.metrics(FontRole.VALUE);
		return Math.max(1, width - vf.stringWidth(valueText()) - Tokens.GAP);
	}

	private List<String> labelLines(int width) {
		return TextWrap.wrap(model.label(), ctx.metrics(FontRole.SMALL), labelWidth(width));
	}

	@Override
	public int minimumContentWidth() { return ctx.metrics(FontRole.VALUE).stringWidth(valueText()) + Tokens.GAP + 1; }

	@Override
	public int heightForWidth(int width) {
		FontMetrics sf = ctx.metrics(FontRole.SMALL), vf = ctx.metrics(FontRole.VALUE);
		int top = Math.max(labelLines(width).size() * sf.getHeight(), vf.getHeight());
		int h = top + 2 + Tokens.PROGRESS_HEIGHT;
		if (detailText() != null) h += 1 + sf.getHeight();
		return h;
	}

	/** Trough rectangle at the current size; exposed for tests. */
	public Rectangle trough() {
		FontMetrics sf = ctx.metrics(FontRole.SMALL), vf = ctx.metrics(FontRole.VALUE);
		int top = Math.max(labelLines(getWidth()).size() * sf.getHeight(), vf.getHeight());
		return new Rectangle(0, top + 2, getWidth(), Tokens.PROGRESS_HEIGHT);
	}

	/** Fill width in pixels at the current size; exposed for tests. */
	public int fillWidth() {
		Rectangle t = trough();
		return (int) Math.round((t.width - 2) * model.fraction());
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		Palette p = ctx.palette();
		FontMetrics sf = ctx.metrics(FontRole.SMALL), vf = ctx.metrics(FontRole.VALUE);
		g2.setFont(ctx.font(FontRole.SMALL));
		g2.setColor(p.text);
		int y = sf.getAscent();
		for (String l : labelLines(getWidth())) { g2.drawString(l, 0, y); y += sf.getHeight(); }
		g2.setFont(ctx.font(FontRole.VALUE));
		String v = valueText();
		g2.setColor(model.state() == DataState.STALE ? p.stale : (model.state().showsData() ? p.text : p.muted));
		g2.drawString(v, getWidth() - vf.stringWidth(v), vf.getAscent());

		Rectangle t = trough();
		g2.setColor(p.trough);
		g2.fillRect(t.x, t.y, t.width, t.height);
		int fw = fillWidth();
		if (fw > 0) {
			Color fill = variant == Variant.GOAL ? p.goal : p.progress;
			if (model.state() == DataState.STALE) fill = Paints.alpha(fill, 140);
			g2.setColor(fill);
			g2.fillRect(t.x + 1, t.y + 1, fw, t.height - 2);
			if (ctx.themed()) {
				g2.setColor(Paints.alpha(Color.WHITE, 40));
				g2.drawLine(t.x + 1, t.y + 1, t.x + fw, t.y + 1);
			}
		}
		if (model.state() == DataState.LOADING) {
			g2.setColor(Paints.alpha(p.loading, 120));
			for (int x = t.x - t.height; x < t.x + t.width; x += 6) g2.drawLine(x, t.y + t.height - 1, x + t.height - 1, t.y);
		}
		g2.setColor(ctx.themed() ? p.border : p.border);
		g2.drawRect(t.x, t.y, t.width - 1, t.height - 1);
		String d = detailText();
		if (d != null) {
			g2.setFont(ctx.font(FontRole.SMALL));
			g2.setColor(p.muted);
			g2.drawString(d, 0, t.y + t.height + 1 + sf.getAscent());
		}
		g2.dispose();
	}
}
