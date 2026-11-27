package com.feralenterprises.raccoon.playtest.r02.panel.bank;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.ItemSlots;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.SearchField;
import com.feralenterprises.raccoon.ui.component.SegmentedSelector;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.component.Tone;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.layout.StackLayout;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.layout.TextWrap;
import com.feralenterprises.raccoon.ui.lifecycle.Disposable;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * R02 Bank Memory (frozen {@code #bank}): observation status and estimate, category, search, sort and grid/list
 * controls, the observed items with an expandable item detail, and About bank data. Functional build on shared
 * components; visual polish follows owner playtesting. Filters, sort, mode and selection are page state, reset on a
 * scope change (the page is discarded) and kept across same-scope revisions.
 */
final class BankPanel extends StackPanel implements Disposable {
	static final String ABOUT = "Bank Memory reads only a complete observation of the open in-game bank in this account scope. "
		+ "Closed or partial captures never establish ownership. Quantities are exact item IDs as observed; names and "
		+ "illustrative prices describe those same IDs. No live GE prices or other accounts are used.";

	private final RouteContext context;
	private final UiContext uiContext;
	private final BankInputs in;
	private final Runnable textScaleChanged = this::render;
	private final TextBlock status, observed, note, count, countLabel, value, valueLabel, valueNote, resultCount, qtyLabel, accountNote;
	private final CategoryBar categoryBar;
	private final SearchField search;
	private final RButton clear;
	private final RDropdown<BankModel.Sort> sort;
	private final SegmentedSelector mode;
	private final StackPanel results = new StackPanel(0);
	private BankModel model;
	private String category = "All", query = "";
	private BankModel.Sort order = BankModel.Sort.ID;
	private boolean grid = true;
	private Integer selected;

	BankPanel(RouteContext context, BankInputs in) {
		super(7);
		this.context = context;
		this.uiContext = context.ui();
		this.in = in;
		setName("bank");
		setOpaque(false);
		setBorder(new EmptyBorder(0, 0, 6, 0));

		status = text("bank-status", FontRole.TITLE, Tone.ACCENT);
		observed = text("bank-observed", FontRole.SMALL, Tone.MUTED);
		note = text("bank-note", FontRole.SMALL, Tone.TEXT);
		count = text("bank-count", FontRole.VALUE, Tone.ACCENT);
		countLabel = text("bank-count-label", FontRole.SMALL, Tone.MUTED);
		value = text("bank-value", FontRole.VALUE, Tone.ACCENT);
		valueLabel = text("bank-value-label", FontRole.SMALL, Tone.MUTED);
		valueNote = text("bank-value-note", FontRole.SMALL, Tone.MUTED);
		resultCount = text("bank-result-count", FontRole.SMALL, Tone.MUTED);
		qtyLabel = text("bank-qty-label", FontRole.SMALL, Tone.MUTED);
		accountNote = text("bank-account-note", FontRole.SMALL, Tone.MUTED);

		Card head = new Card(uiContext, "Bank observation");
		head.setName("bank-head");
		head.addAll(status, observed, note, new Pair(stack(count, countLabel), stack(value, valueLabel)), valueNote);
		add(head);

		categoryBar = new CategoryBar(c -> { category = c; selected = null; renderRows(); });
		search = new SearchField(uiContext, "Search name or ID…", q -> { query = q; selected = null; renderRows(); });
		search.setName("bank-search");
		clear = new RButton(uiContext, "Clear");
		clear.setName("bank-clear");
		clear.addActionListener(e -> {
			search.setText("");
			category = "All";
			categoryBar.select("All");
			selected = null;
			renderRows();
			search.requestFocusInWindow();
		});
		sort = new RDropdown<>(uiContext, "Sort bank items", Arrays.asList(BankModel.Sort.values()), s -> s.label, s -> { order = s; renderRows(); });
		sort.setName("bank-sort");
		mode = new SegmentedSelector(uiContext, "Grid", "List");
		mode.setName("bank-mode");
		mode.addSelectionListener(i -> { grid = i == 0; renderRows(); });
		Card controls = new Card(uiContext, "Items");
		controls.setName("bank-controls");
		controls.addAll(categoryBar, new Pair(search, clear, 0.72f), new Pair(sort, mode, 0.55f), new Pair(resultCount, qtyLabel, 0.6f), results);
		results.setOpaque(false);
		add(controls);

		add(new Expandable(uiContext, "About bank data", text("bank-about", FontRole.SMALL, Tone.TEXT, ABOUT)));
		add(accountNote);

		model = BankModel.of(context.snapshot(), context.registry(), in);
		render();
		context.onSnapshot(this::onSnapshot);
		uiContext.addChangeListener(textScaleChanged);
	}

	@Override public void dispose() { uiContext.removeChangeListener(textScaleChanged); }

	BankModel model() { return model; }

	private void onSnapshot(FixtureSnapshot s) {
		model = BankModel.of(s, context.registry(), in);
		render();
	}

	private void render() {
		status.setText(model.statusLabel);
		observed.setText(model.observed);
		note.setText(model.statusNote);
		count.setText(model.countText);
		countLabel.setText(model.countLabel);
		value.setText(model.valueText);
		valueLabel.setText(model.valueLabel);
		valueNote.setText(model.valueNote);
		accountNote.setText(model.accountNote);
		boolean on = model.controlsEnabled();
		for (JComponent c : new JComponent[] {search, clear, sort, mode, categoryBar}) c.setEnabled(on);
		renderRows();
	}

	private void renderRows() {
		results.removeAll();
		List<BankModel.Row> rows = model.rows(category, query, order);
		if (selected != null && rows.stream().noneMatch(r -> r.id == selected)) selected = null;
		resultCount.setText(model.resultCount(rows.size()));
		qtyLabel.setText(model.quantityLabel);
		String[] msg = model.message(rows.size());
		if (msg != null) {
			StackPanel m = stack(text("bank-message-title", FontRole.BODY, Tone.ACCENT, msg[0]), text("bank-message", FontRole.SMALL, Tone.TEXT, msg[1]));
			m.setName("bank-message-box");
			results.add(m);
		} else {
			results.add(grid ? new Grid(rows) : list(rows));
			BankModel.Row sel = selected == null ? null : rows.stream().filter(r -> r.id == selected).findFirst().orElse(null);
			if (sel != null) results.add(detail(sel));
		}
		results.revalidate();
		results.repaint();
		revalidate();
		repaint();
	}

	private JComponent list(List<BankModel.Row> rows) {
		StackPanel p = new StackPanel(0);
		p.setOpaque(false);
		p.setName("bank-list");
		for (BankModel.Row r : rows) p.add(new ListRow(r));
		return p;
	}

	private JComponent detail(BankModel.Row r) {
		Card c = new Card(uiContext, r.name);
		c.setName("bank-selection");
		String verified = model.current ? "verified" : "last verified";
		c.addAll(text("bank-selection-qty", FontRole.BODY, Tone.ACCENT, "× " + BankModel.fmt(r.quantity) + " · " + verified),
			text("bank-selection-id", FontRole.SMALL, Tone.TEXT, "Item ID: " + r.id),
			text("bank-selection-category", FontRole.SMALL, Tone.TEXT, "Category: " + r.category),
			text("bank-selection-price", FontRole.SMALL, Tone.TEXT, "Est. per item: " + (r.unitPrice == null ? "Unpriced" : BankModel.fmt(r.unitPrice) + " gp")),
			text("bank-selection-stack", FontRole.SMALL, Tone.TEXT, "Est. stack: " + (r.unitPrice == null ? "Unknown" : BankModel.fmt(r.stackValue()) + " gp")),
			text("bank-selection-note", FontRole.SMALL, Tone.MUTED, (model.current ? "Observed in open bank." : "Historical count; current quantity unknown.")
				+ " Prices are illustrative fixture values."));
		return c;
	}

	private void toggle(int id) {
		selected = selected != null && selected == id ? null : id;
		renderRows();
		SwingUtilities.invokeLater(() -> {
			JComponent again = find(results, "bank-item-" + id);
			if (again != null) again.requestFocusInWindow();
		});
	}

	// ------------------------------------------------------------------ helpers

	private TextBlock text(String name, FontRole role, Tone tone) { return text(name, role, tone, ""); }

	private TextBlock text(String name, FontRole role, Tone tone, String t) {
		TextBlock b = new TextBlock(uiContext, t, role, tone);
		b.setName(name);
		return b;
	}

	private static StackPanel stack(JComponent... c) {
		StackPanel p = new StackPanel(0);
		p.setOpaque(false);
		for (JComponent x : c) p.add(x);
		return p;
	}

	static JComponent find(java.awt.Component c, String name) {
		if (name.equals(c.getName()) && c instanceof JComponent) return (JComponent) c;
		if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
			JComponent j = find(k, name);
			if (j != null) return j;
		}
		return null;
	}

	private static void activator(JComponent c, Runnable action) {
		c.setFocusable(true);
		c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		c.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) { c.requestFocusInWindow(); action.run(); }
		});
		c.addKeyListener(new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) { action.run(); e.consume(); }
			}
		});
		c.addFocusListener(new FocusAdapter() {
			@Override public void focusGained(FocusEvent e) { c.repaint(); }
			@Override public void focusLost(FocusEvent e) { c.repaint(); }
		});
	}

	/** Shared frozen sprite first, then the verified lane sprite for the same exact ID; null shows the ID instead. */
	private BufferedImage sprite(int id) {
		BufferedImage img = context.assets().item(id);
		return img != null ? img : in.sprite(id);
	}

	private static void focusRing(Graphics2D g, JComponent c, Color color) {
		if (!c.isFocusOwner()) return;
		g.setColor(color);
		g.setStroke(new BasicStroke(2f));
		g.drawRect(1, 1, c.getWidth() - 3, c.getHeight() - 3);
	}

	private static void contain(Graphics2D g, BufferedImage img, int x, int y, int w, int h) {
		if (img == null) return;
		double s = Math.min(1.0, Math.min(w / (double) img.getWidth(), h / (double) img.getHeight()));
		int dw = (int) Math.round(img.getWidth() * s), dh = (int) Math.round(img.getHeight() * s);
		g.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
	}

	private String accessibleQty(BankModel.Row r) {
		return r.name + ", " + (model.current ? "verified quantity " : "last verified quantity ") + BankModel.fmt(r.quantity)
			+ (sprite(r.id) == null ? " (sprite unavailable, item " + r.id + ")" : "");
	}

	// ------------------------------------------------------------------ components

	/** Two components side by side ({@code leftShare} of the width to the first). */
	private final class Pair extends JPanel implements HeightForWidth {
		private final JComponent a, b;
		private final float share;

		Pair(JComponent a, JComponent b) { this(a, b, 0.5f); }

		Pair(JComponent a, JComponent b, float share) {
			super(null);
			this.a = a;
			this.b = b;
			this.share = share;
			setOpaque(false);
			add(a);
			add(b);
		}

		private int split(int w) { return Math.round((w - 6) * share); }

		/** Side by side only when the second fits at its preferred width beside a usable first; otherwise stacked. */
		private boolean stacked(int w) {
			int need = b.getPreferredSize().width;
			return w - split(w) - 6 < need && w - need - 6 < Math.min(a.getPreferredSize().width, 90);
		}

		private int right(int w) { return Math.max(w - split(w) - 6, Math.min(b.getPreferredSize().width, w - 96)); }

		@Override public int heightForWidth(int width) {
			if (stacked(width)) return StackLayout.heightFor(a, width) + 4 + StackLayout.heightFor(b, width);
			int rw = right(width);
			return Math.max(StackLayout.heightFor(a, width - rw - 6), StackLayout.heightFor(b, rw));
		}

		@Override public Dimension getPreferredSize() { return new Dimension(200, heightForWidth(200)); }

		@Override public void doLayout() {
			int w = getWidth();
			if (stacked(w)) {
				int ha = StackLayout.heightFor(a, w);
				a.setBounds(0, 0, w, ha);
				b.setBounds(0, ha + 4, w, StackLayout.heightFor(b, w));
				return;
			}
			int rw = right(w), h = getHeight();
			a.setBounds(0, 0, w - rw - 6, h);
			b.setBounds(w - rw, 0, rw, h);
		}
	}

	/** Frozen category toggles (All, Materials, Gear, Supplies, Other), wrapping as needed; pressed state exposed. */
	private final class CategoryBar extends JPanel implements HeightForWidth {
		private final java.util.function.Consumer<String> onSelect;
		private String current = "All";

		CategoryBar(java.util.function.Consumer<String> onSelect) {
			super(null);
			this.onSelect = onSelect;
			setOpaque(false);
			setName("bank-category");
			for (String c : BankModel.CATEGORIES) {
				RButton b = new RButton(uiContext, c);
				b.setName("bank-category-" + c.toLowerCase());
				b.addActionListener(e -> { select(c); this.onSelect.accept(c); });
				add(b);
			}
			select("All");
		}

		void select(String c) {
			current = c;
			for (java.awt.Component k : getComponents()) {
				RButton b = (RButton) k;
				boolean on = b.getText().equals(c);
				b.setSelected(on);
				b.getAccessibleContext().setAccessibleDescription(on ? "pressed" : null);
			}
			repaint();
		}

		String current() { return current; }

		@Override public void setEnabled(boolean e) {
			super.setEnabled(e);
			for (java.awt.Component k : getComponents()) k.setEnabled(e);
		}

		private int[] widths() {
			int[] w = new int[getComponentCount()];
			for (int i = 0; i < w.length; i++) w[i] = getComponent(i).getPreferredSize().width;
			return w;
		}

		@Override public int heightForWidth(int width) {
			int rowH = getComponent(0).getPreferredSize().height, x = 0, rows = 1;
			for (int w : widths()) {
				if (x > 0 && x + w > width) { rows++; x = 0; }
				x += w + 4;
			}
			return rows * rowH + (rows - 1) * 4;
		}

		@Override public int minimumContentWidth() { return Arrays.stream(widths()).max().orElse(0); }

		@Override public Dimension getPreferredSize() { return new Dimension(200, heightForWidth(200)); }

		@Override public void doLayout() {
			int rowH = getComponent(0).getPreferredSize().height, x = 0, y = 0;
			int[] w = widths();
			for (int i = 0; i < w.length; i++) {
				if (x > 0 && x + w[i] > getWidth()) { y += rowH + 4; x = 0; }
				getComponent(i).setBounds(x, y, Math.min(w[i], getWidth()), rowH);
				x += w[i] + 4;
			}
		}
	}

	/** Grid mode: three columns of tiles (sprite, compact OSRS quantity); a tile toggles its item detail. */
	private final class Grid extends JPanel implements HeightForWidth {
		Grid(List<BankModel.Row> rows) {
			super(null);
			setOpaque(false);
			setName("bank-grid");
			for (BankModel.Row r : rows) add(new Tile(r));
		}

		private int cols(int width) { return Math.max(1, Math.min(3, (width + 4) / 52)); }

		@Override public int heightForWidth(int width) {
			int c = cols(width), n = getComponentCount(), rows = (n + c - 1) / c;
			return rows * 52 + Math.max(0, rows - 1) * 4;
		}

		@Override public int minimumContentWidth() { return 48; }

		@Override public Dimension getPreferredSize() { return new Dimension(200, heightForWidth(200)); }

		@Override public void doLayout() {
			int c = cols(getWidth()), w = (getWidth() - (c - 1) * 4) / c;
			for (int i = 0; i < getComponentCount(); i++) getComponent(i).setBounds((i % c) * (w + 4), (i / c) * 56, w, 52);
		}
	}

	private final class Tile extends JComponent {
		private final BankModel.Row r;

		Tile(BankModel.Row r) {
			this.r = r;
			setName("bank-item-" + r.id);
			setToolTipText(r.name + " × " + BankModel.fmt(r.quantity));
			activator(this, () -> toggle(r.id));
		}

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = Paints.prepare(g);
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			int w = getWidth(), h = getHeight();
			boolean sel = selected != null && selected == r.id;
			g2.setColor(sel ? new Color(0x3d3322) : new Color(0x1d1a15));
			g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setColor(sel ? new Color(0xd3a85a) : new Color(0x6f5b3b));
			g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
			BufferedImage img = sprite(r.id);
			if (img != null) contain(g2, img, (w - 36) / 2, 14, 36, 32);
			else {
				g2.setFont(uiContext.font(FontRole.QUANTITY));
				g2.setColor(new Color(0xc1b19a));
				String t = "#" + r.id;
				g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, 36);
			}
			int q = (int) Math.min(Integer.MAX_VALUE, r.quantity);
			g2.setFont(uiContext.font(FontRole.QUANTITY));
			g2.setColor(Color.BLACK);
			g2.drawString(ItemSlots.quantityText(q), 5, 4 + g2.getFontMetrics().getAscent());
			g2.setColor(ItemSlots.quantityColor(q));
			g2.drawString(ItemSlots.quantityText(q), 4, 3 + g2.getFontMetrics().getAscent());
			focusRing(g2, this, new Color(0xd8bc73));
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.TOGGLE_BUTTON; }
				@Override public String getAccessibleName() { return accessibleQty(r); }
				@Override public AccessibleStateSet getAccessibleStateSet() {
					AccessibleStateSet s = super.getAccessibleStateSet();
					s.add(selected != null && selected == r.id ? AccessibleState.EXPANDED : AccessibleState.COLLAPSED);
					return s;
				}
			};
			return accessibleContext;
		}
	}

	/** List mode: sprite, name and full quantity. */
	private final class ListRow extends JComponent implements HeightForWidth {
		private final BankModel.Row r;

		ListRow(BankModel.Row r) {
			this.r = r;
			setName("bank-item-" + r.id);
			activator(this, () -> toggle(r.id));
		}

		private String qty() { return BankModel.fmt(r.quantity); }

		private int qtyW() { return getFontMetrics(uiContext.font(FontRole.VALUE)).stringWidth(qty()); }

		@Override public int heightForWidth(int width) {
			FontMetrics fm = getFontMetrics(uiContext.font(FontRole.BODY));
			return Math.max(34, 8 + TextWrap.wrap(r.name, fm, Math.max(1, width - 40 - qtyW() - 18)).size() * fm.getHeight());
		}

		@Override public int minimumContentWidth() { return 40 + qtyW() + 30; }

		@Override public Dimension getPreferredSize() { return new Dimension(200, heightForWidth(200)); }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = Paints.prepare(g);
			int w = getWidth(), h = getHeight();
			BufferedImage img = sprite(r.id);
			if (img != null) contain(g2, img, 2, (h - 32) / 2, 36, 32);
			g2.setFont(uiContext.font(FontRole.BODY));
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(new Color(0xf0dfbd));
			List<String> lines = TextWrap.wrap(r.name, fm, Math.max(1, w - 40 - qtyW() - 18));
			int y = (h - lines.size() * fm.getHeight()) / 2;
			for (String l : lines) { g2.drawString(l, 42, y + fm.getAscent()); y += fm.getHeight(); }
			g2.setFont(uiContext.font(FontRole.VALUE));
			g2.setColor(new Color(0xf2ce79));
			g2.drawString(qty(), w - 14 - qtyW(), (h + g2.getFontMetrics().getAscent()) / 2 - 2);
			g2.setColor(new Color(0xd3b681));
			g2.drawString(selected != null && selected == r.id ? "⌄" : "›", w - 9, (h + g2.getFontMetrics().getAscent()) / 2 - 2);
			g2.setColor(new Color(0x7c, 0x69, 0x43, 0x40));
			g2.fillRect(0, h - 1, w, 1);
			focusRing(g2, this, new Color(0xd8bc73));
			g2.dispose();
		}

		@Override public AccessibleContext getAccessibleContext() {
			if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
				@Override public AccessibleRole getAccessibleRole() { return AccessibleRole.TOGGLE_BUTTON; }
				@Override public String getAccessibleName() { return accessibleQty(r); }
			};
			return accessibleContext;
		}
	}
}
