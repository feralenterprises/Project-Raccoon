package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.layout.StackLayout;
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
import java.awt.Polygon;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Progressive disclosure: a focusable summary row that shows or hides a details body. */
public class Expandable extends JPanel implements com.feralenterprises.raccoon.ui.layout.HeightForWidth {
	private final UiContext ctx;
	private final Summary summary;
	private final JComponent body;
	private final List<Consumer<Boolean>> listeners = new CopyOnWriteArrayList<>();
	private boolean expanded;

	public Expandable(UiContext ctx, String title, JComponent body) {
		this.ctx = ctx;
		this.body = body;
		setOpaque(false);
		setLayout(new StackLayout(Tokens.GAP_SMALL));
		summary = new Summary(ctx, title);
		add(summary);
		add(body);
		body.setVisible(false);
	}

	public boolean expanded() { return expanded; }
	public JComponent summary() { return summary; }
	public void addExpansionListener(Consumer<Boolean> l) { listeners.add(l); }

	public void setExpanded(boolean e) {
		if (e == expanded) return;
		expanded = e;
		body.setVisible(e);
		summary.getAccessibleContext().setAccessibleDescription(e ? "expanded" : "collapsed");
		revalidate();
		repaint();
		listeners.forEach(l -> l.accept(e));
	}

	@Override
	public int heightForWidth(int width) { return ((StackLayout) getLayout()).heightForWidth(this, width); }

	@Override
	public java.awt.Dimension getPreferredSize() {
		int w = getWidth() > 0 ? getWidth() : 225;
		return new java.awt.Dimension(w, heightForWidth(w));
	}

	private final class Summary extends RComponent {
		private final String title;
		private boolean hover;

		Summary(UiContext ctx, String title) {
			super(ctx);
			this.title = title;
			setFocusable(true);
			getAccessibleContext().setAccessibleName(title);
			addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) { requestFocusInWindow(); setExpanded(!expanded); }
				@Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
				@Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
			});
			addKeyListener(new KeyAdapter() {
				@Override public void keyPressed(KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) { setExpanded(!expanded); e.consume(); }
				}
			});
			addFocusListener(new FocusAdapter() {
				@Override public void focusGained(FocusEvent e) { repaint(); }
				@Override public void focusLost(FocusEvent e) { repaint(); }
			});
		}

		private List<String> lines(int width) { return TextWrap.wrap(title, ctx.metrics(FontRole.BODY), width - 18); }

		@Override
		public int heightForWidth(int width) { return lines(width).size() * ctx.metrics(FontRole.BODY).getHeight() + 6; }

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Paints.prepare(g);
			Palette p = ctx.palette();
			if (hover) { g2.setColor(p.panelRaised); g2.fillRect(0, 0, getWidth(), getHeight()); }
			g2.setColor(ctx.themed() ? p.accent : p.text);
			int cy = 3 + ctx.metrics(FontRole.BODY).getHeight() / 2;
			Polygon tri = expanded
				? new Polygon(new int[]{3, 11, 7}, new int[]{cy - 2, cy - 2, cy + 3}, 3)
				: new Polygon(new int[]{5, 10, 5}, new int[]{cy - 4, cy, cy + 4}, 3);
			g2.fillPolygon(tri);
			g2.setFont(ctx.font(FontRole.BODY));
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(p.text);
			int y = 3 + fm.getAscent();
			for (String l : lines(getWidth())) { g2.drawString(l, 18, y); y += fm.getHeight(); }
			if (isFocusOwner()) {
				g2.setColor(p.focus);
				g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{2f, 2f}, 0f));
				g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
			}
			g2.dispose();
		}
	}
}
