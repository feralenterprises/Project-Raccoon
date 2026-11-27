package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

/**
 * Single-choice selector. Options share a row while their labels fit, otherwise the selector flows into more rows,
 * so it never overflows horizontally at 225 px or with Large text. Mouse and keyboard (arrows, Home, End).
 */
public class SegmentedSelector extends RComponent implements TextFit {
	private final List<String> options;
	private final List<IntConsumer> listeners = new CopyOnWriteArrayList<>();
	private int selected;
	private int hover = -1;

	public SegmentedSelector(UiContext ctx, String... options) {
		super(ctx);
		if (options.length == 0) throw new IllegalArgumentException("options");
		this.options = new ArrayList<>(Arrays.asList(options));
		setFocusable(true);
		getAccessibleContext().setAccessibleName("Selector");
		MouseAdapter mouse = new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) {
				int i = indexAt(e.getX(), e.getY());
				if (i >= 0) { requestFocusInWindow(); setSelected(i); }
			}
			@Override public void mouseMoved(MouseEvent e) { int i = indexAt(e.getX(), e.getY()); if (i != hover) { hover = i; repaint(); } }
			@Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addKeyListener(new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) {
				int n = SegmentedSelector.this.options.size();
				switch (e.getKeyCode()) {
					case KeyEvent.VK_LEFT: case KeyEvent.VK_UP: setSelected((selected + n - 1) % n); break;
					case KeyEvent.VK_RIGHT: case KeyEvent.VK_DOWN: setSelected((selected + 1) % n); break;
					case KeyEvent.VK_HOME: setSelected(0); break;
					case KeyEvent.VK_END: setSelected(n - 1); break;
					default: return;
				}
				e.consume();
			}
		});
		addFocusListener(new FocusAdapter() {
			@Override public void focusGained(FocusEvent e) { repaint(); }
			@Override public void focusLost(FocusEvent e) { repaint(); }
		});
	}

	public int selected() { return selected; }
	public String selectedOption() { return options.get(selected); }
	public int optionCount() { return options.size(); }
	public void addSelectionListener(IntConsumer l) { listeners.add(l); }

	public void setSelected(int i) {
		if (i < 0 || i >= options.size() || i == selected) return;
		selected = i;
		repaint();
		listeners.forEach(l -> l.accept(i));
	}

	/** Number of columns used at a width; exposed for layout tests. */
	public int columnsFor(int width) {
		FontMetrics fm = ctx.metrics(FontRole.BODY);
		int n = options.size();
		for (int c = n; c > 1; c--) {
			int cw = (width - (c - 1) * Tokens.GAP_SMALL) / c;
			boolean fits = true;
			for (String o : options) if (fm.stringWidth(o) + 2 * Tokens.PAD_X > cw) { fits = false; break; }
			if (fits) return c;
		}
		return 1;
	}

	private int cellHeight() {
		FontMetrics fm = ctx.metrics(FontRole.BODY);
		return fm.getHeight() + 2 * Tokens.PAD_Y + 2;
	}

	public Rectangle cell(int i, int width) {
		int c = columnsFor(width);
		int cw = (width - (c - 1) * Tokens.GAP_SMALL) / c;
		int row = i / c, col = i % c;
		int ch = cellHeight();
		int x = col * (cw + Tokens.GAP_SMALL);
		int w = (col == c - 1) ? width - x : cw;
		return new Rectangle(x, row * (ch + Tokens.GAP_SMALL), w, ch);
	}

	private int indexAt(int x, int y) {
		for (int i = 0; i < options.size(); i++) if (cell(i, getWidth()).contains(x, y)) return i;
		return -1;
	}

	/** One column needs the widest option on one line (a cell shows only its first wrapped line). */
	@Override
	public int requiredTextWidth() { return minimumContentWidth(); }

	@Override
	public int minimumContentWidth() {
		FontMetrics fm = ctx.metrics(FontRole.BODY);
		int m = 0;
		for (String o : options) m = Math.max(m, fm.stringWidth(o) + 2 * Tokens.PAD_X);
		return m;
	}

	@Override
	public int heightForWidth(int width) {
		int c = columnsFor(width);
		int rows = (options.size() + c - 1) / c;
		return rows * cellHeight() + (rows - 1) * Tokens.GAP_SMALL;
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Paints.prepare(g);
		Palette p = ctx.palette();
		g2.setFont(ctx.font(FontRole.BODY));
		FontMetrics fm = g2.getFontMetrics();
		for (int i = 0; i < options.size(); i++) {
			Rectangle r = cell(i, getWidth());
			boolean on = i == selected;
			g2.setColor(on ? p.accentDim : (i == hover ? p.panelRaised : p.panel));
			g2.fillRect(r.x, r.y, r.width, r.height);
			g2.setColor(on ? p.accent : p.border);
			g2.drawRect(r.x, r.y, r.width - 1, r.height - 1);
			if (on && isFocusOwner()) {
				g2.setColor(p.focus);
				g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{2f, 2f}, 0f));
				g2.drawRect(r.x + 2, r.y + 2, r.width - 5, r.height - 5);
				g2.setStroke(new BasicStroke(1f));
			}
			g2.setColor(on ? p.text : p.muted);
			String label = TextWrap.wrap(options.get(i), fm, r.width - 2 * Tokens.PAD_X).get(0);
			g2.drawString(label, r.x + (r.width - fm.stringWidth(label)) / 2, r.y + Tokens.PAD_Y + 1 + fm.getAscent());
		}
		g2.dispose();
	}
}
