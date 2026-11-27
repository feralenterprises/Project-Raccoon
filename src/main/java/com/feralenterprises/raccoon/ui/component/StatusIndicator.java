package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.model.Evidence;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;

/** State chip (dot + label) with an optional evidence line underneath (source and age). */
public class StatusIndicator extends RComponent implements com.feralenterprises.raccoon.ui.lifecycle.AccountBound {
	private DataState state;
	private Evidence evidence;

	public StatusIndicator(UiContext ctx, DataState state, Evidence evidence) {
		super(ctx);
		set(state, evidence);
	}

	public DataState state() { return state; }
	public Evidence evidence() { return evidence; }

	@Override
	public void clearAccountData() { set(DataState.UNAVAILABLE, Evidence.NONE); }

	public void set(DataState s, Evidence e) {
		state = s;
		evidence = e == null ? Evidence.NONE : e;
		getAccessibleContext().setAccessibleName(s.label() + (evidence.isEmpty() ? "" : ", " + evidence.line()));
		revalidate();
		repaint();
	}

	public static Color colorFor(DataState s, Palette p) {
		switch (s) {
			case ACTIVE: return p.active;
			case LOADING: return p.loading;
			case UNAVAILABLE: return p.unavailable;
			case STALE: return p.stale;
			default: return p.empty;
		}
	}

	private List<String> evidenceLines(int width) {
		return TextWrap.wrap(evidence.line(), ctx.metrics(FontRole.SMALL), width);
	}

	@Override
	public int minimumContentWidth() { return ctx.metrics(FontRole.VALUE).stringWidth(state.label()) + 18; }

	@Override
	public int heightForWidth(int width) {
		FontMetrics vf = ctx.metrics(FontRole.VALUE), sf = ctx.metrics(FontRole.SMALL);
		int h = vf.getHeight() + 2;
		if (!evidence.isEmpty()) h += evidenceLines(width).size() * sf.getHeight();
		return h;
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		Palette p = ctx.palette();
		FontMetrics vf = ctx.metrics(FontRole.VALUE), sf = ctx.metrics(FontRole.SMALL);
		Color c = colorFor(state, p);
		int chipW = vf.stringWidth(state.label()) + 18, chipH = vf.getHeight() + 2;
		g2.setColor(Paints.alpha(c, ctx.themed() ? 46 : 30));
		g2.fillRoundRect(0, 0, chipW, chipH, 6, 6);
		g2.setColor(c);
		g2.drawRoundRect(0, 0, chipW - 1, chipH - 1, 6, 6);
		int d = 6;
		if (state == DataState.LOADING || state == DataState.EMPTY) g2.drawOval(5, (chipH - d) / 2, d, d);
		else g2.fillOval(5, (chipH - d) / 2, d, d);
		g2.setFont(ctx.font(FontRole.VALUE));
		g2.setColor(p.text);
		g2.drawString(state.label(), 14, 1 + vf.getAscent());
		if (!evidence.isEmpty()) {
			g2.setFont(ctx.font(FontRole.SMALL));
			g2.setColor(state == DataState.STALE ? p.stale : p.muted);
			int y = chipH + sf.getAscent();
			for (String l : evidenceLines(getWidth())) { g2.drawString(l, 0, y); y += sf.getHeight(); }
		}
		g2.dispose();
	}
}
