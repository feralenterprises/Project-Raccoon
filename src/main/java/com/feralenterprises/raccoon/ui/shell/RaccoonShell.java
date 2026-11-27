package com.feralenterprises.raccoon.ui.shell;

import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.StateNotice;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.lifecycle.AccountBound;
import com.feralenterprises.raccoon.ui.lifecycle.ContextAware;
import com.feralenterprises.raccoon.ui.lifecycle.Disposable;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.paint.ThemedToolTip;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Palette;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToolTip;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

/**
 * The common sidebar shell: navigation rail (or header menu), shared header (Back / title / Settings), framed
 * content column with a scrolling viewport. Pages are created once and cached, so each keeps its own selection,
 * expansion and scroll position, until account rules require clearing them.
 *
 * <p>Width: the shell uses whatever width its host gives it, arranged by {@link ShellGeometry}.
 *
 * <p>Account data (FR-002 ui-integration rules 1-2): the shell starts in the neutral UNAVAILABLE state and shows
 * account pages only while exactly one attached account source says so. {@link #hideAccount} drops every
 * account-derived value held by pages of account destinations and shows the one shared {@link StateNotice};
 * {@link #showAccount} re-enables them and, when the new scope does not continue the previous one, discards those pages
 * entirely (selection, scroll, values). Attaching or detaching a source resets everything (values, pages, scope), so
 * no state can carry across sources, and calls from a source that is not attached are ignored.
 *
 * <p>Lifecycle: {@link #dispose()} removes every listener this shell registered, disposes cached pages and ignores
 * later navigation. Must be called on the EDT when the hosting plugin panel is removed.
 */
public final class RaccoonShell extends JPanel implements Disposable {
	private final UiContext ctx;
	private final Navigator nav;
	private final NavRail rail;
	private final JScrollPane railScroll;
	private final RailContainer railBox;
	private final ShellHeader header;
	private final ContentScrollPane scroll;
	private final OverlayScrollPaneLayout scrollLayout = new OverlayScrollPaneLayout();
	private final Column column;
	private final Map<String, Supplier<JComponent>> factories = new HashMap<>();
	private final Map<String, Environment> environments = new HashMap<>();
	private final Map<String, PageHost> hosts = new HashMap<>();
	private final Map<String, Point> scrollPositions = new HashMap<>();
	private final Map<String, Component> focusOwners = new HashMap<>();
	private final List<Consumer<Object>> scopeListeners = new CopyOnWriteArrayList<>();
	private final List<Runnable> clearListeners = new CopyOnWriteArrayList<>();
	private final Consumer<String> onNavigate = this::show;
	private final Runnable onContextChange = this::restyle;
	private ShellGeometry.Spec spec = ShellGeometry.Spec.defaults();
	private ShellGeometry.Layout layout;
	private int preferredContent = Tokens.CONTENT_WIDTH;
	private String shown;
	private boolean disposed;

	// Account presentation state. Null presence = account data may be shown. Neutral until a source publishes.
	private StateNotice.Presence presence = StateNotice.Presence.UNAVAILABLE;
	private Object source;
	private Runnable onShellDisposed;
	private int ignoredSourceCalls;
	private volatile int accountGeneration;
	private Object scope;
	private BiPredicate<Object, Object> continuity = Objects::equals;
	private PageHost noticeHost;
	private int accountClears, pageDiscards;

	public RaccoonShell(UiContext ctx, Navigator nav) {
		this.ctx = ctx;
		this.nav = nav;
		setLayout(new ShellLayout());
		rail = new NavRail(ctx, nav);
		railScroll = new JScrollPane(rail, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		railScroll.setBorder(BorderFactory.createEmptyBorder());
		railScroll.getVerticalScrollBar().setUI(new ThinScrollBarUI(ctx, Tokens.RAIL_SCROLLBAR));
		railScroll.getVerticalScrollBar().setUnitIncrement(Tokens.RAIL_BUTTON_HEIGHT);
		railBox = new RailContainer(ctx, railScroll);
		header = new ShellHeader(ctx);
		scroll = new ContentScrollPane();
		scroll.setLayout(scrollLayout);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		// UI-005 defect: before the first navigation (or with no view) the viewport showed Swing's default grey and
		// broke the continuous background. It now paints the page surface colour.
		scroll.getViewport().setBackground(ctx.palette().background);
		scroll.getVerticalScrollBar().setUI(new ThinScrollBarUI(ctx, Tokens.SCROLLBAR));
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setComponentZOrder(scroll.getVerticalScrollBar(), 0);
		column = new Column();
		column.add(header, BorderLayout.NORTH);
		column.add(scroll, BorderLayout.CENTER);
		add(railBox);
		add(column);

		header.back().addActionListener(e -> goBack());
		header.settings().addActionListener(e -> {
			for (Destination d : nav.destinations()) if (d.group() == Destination.Group.UTILITY) { nav.navigate(d.id()); return; }
		});
		header.menu().addActionListener(e -> showDestinationMenu());
		nav.addListener(onNavigate);
		ctx.addChangeListener(onContextChange);
	}

	/** Registers the page body for a destination; created lazily on first visit and then kept. */
	public void register(String id, Environment env, Supplier<JComponent> factory) {
		if (nav.destination(id) == null) throw new IllegalArgumentException(id);
		factories.put(id, factory);
		environments.put(id, env);
	}

	public Navigator navigator() { return nav; }

	/**
	 * The Back action: closes the current page's local detail first ({@link LocalBack}), then steps back through
	 * history. Returns false when there was nothing to go back to.
	 */
	public boolean goBack() {
		if (disposed) return false;
		LocalBack local = localBack();
		if (local != null && local.hasLocalBack()) {
			local.localBack();
			refreshBack();
			return true;
		}
		return nav.back();
	}

	/** True when Back would do something (a local detail is open, or there is history). */
	public boolean canGoBack() {
		LocalBack local = localBack();
		return nav.canGoBack() || (local != null && local.hasLocalBack());
	}

	private LocalBack localBack() {
		if (shown == null || showingNotice()) return null;
		PageHost h = hosts.get(shown);
		return h != null && h.content() instanceof LocalBack ? (LocalBack) h.content() : null;
	}

	private void refreshBack() {
		if (!disposed) header.back().setEnabled(canGoBack());
	}
	public NavRail rail() { return rail; }
	public JScrollPane railScrollPane() { return railScroll; }
	/** Overflow cue buttons of the rail (visible only while destinations lie beyond that edge). */
	public JButton railUpCue() { return railBox.upCue(); }
	public JButton railDownCue() { return railBox.downCue(); }
	public ShellHeader header() { return header; }
	public JScrollPane scrollPane() { return scroll; }
	public PageHost currentHost() { return shown == null ? null : (showingNotice() ? noticeHost : hosts.get(shown)); }
	public PageHost host(String id) { return hosts.get(id); }
	public boolean isDisposed() { return disposed; }
	/** Chrome on the rail side of the content column (0 while the rail is shown). */
	public int leftChrome() { return column.leftChrome(); }
	/** x where the content column starts (equals the rail width: no separator space). */
	public int columnX() { return column.getX(); }

	// ---- geometry -------------------------------------------------------------------------------------------

	public void setGeometrySpec(ShellGeometry.Spec s) { spec = Objects.requireNonNull(s); revalidate(); repaint(); }
	public ShellGeometry.Spec geometrySpec() { return spec; }
	/** The arrangement used at the current width (null before first layout). */
	public ShellGeometry.Layout geometry() { return layout; }

	/** Preferred content width when the host lets the shell choose (225 / 263 / 280 proposals). */
	public void setContentWidth(int w) { preferredContent = w; revalidate(); repaint(); }
	public int contentWidth() { return preferredContent; }
	public int shellWidth() { return spec.fullWidth(preferredContent); }

	@Override
	public Dimension getPreferredSize() {
		if (isPreferredSizeSet()) return super.getPreferredSize();
		return new Dimension(shellWidth(), 480);
	}

	// ---- account presentation -------------------------------------------------------------------------------

	/** Continuity test for scope changes (integration passes AccountScope::sameEpoch). */
	public void setScopeContinuity(BiPredicate<Object, Object> c) { continuity = Objects.requireNonNull(c); }

	/** Called on scope (re)publication so data sources re-deliver values tagged with the new scope. */
	public void addScopeListener(Consumer<Object> l) { scopeListeners.add(l); }
	public void removeScopeListener(Consumer<Object> l) { scopeListeners.remove(l); }

	/**
	 * Data sources that cache account values for pages register here; they are told to drop them whenever the shell
	 * clears account values, so a later rebuild or control cannot re-show data from an unpublished scope.
	 */
	public void addClearListener(Runnable r) { clearListeners.add(r); }
	public void removeClearListener(Runnable r) { clearListeners.remove(r); }

	public StateNotice.Presence presence() { return presence; }
	public Object scope() { return scope; }
	public int accountClears() { return accountClears; }
	public int pageDiscards() { return pageDiscards; }

	public Object accountSource() { return source; }
	/**
	 * Binding generation (foundation 41eb2ac ui-integration "binding/disposal guard"): incremented on every attach,
	 * detach and dispose. Sources capture it when they queue work and apply only if it is unchanged. Written on the
	 * EDT; volatile so a source may capture it from the thread that queues work.
	 */
	public int accountGeneration() { return accountGeneration; }
	public int ignoredSourceCalls() { return ignoredSourceCalls; }

	/**
	 * Attaches the single account source (e.g. the FR-002 LifecycleBinding). Resets to the neutral state first, so
	 * nothing from an earlier source or from before the attach can show. {@code onShellDisposed} is run if the shell is
	 * disposed while the source is attached (it should cancel its subscription). EDT only.
	 */
	public void attachAccountSource(Object owner, Runnable onShellDisposed) {
		requireEdt();
		Objects.requireNonNull(owner);
		if (disposed) throw new IllegalStateException("shell disposed");
		if (source != null) throw new IllegalStateException("an account source is already attached; detach it first");
		accountGeneration++;
		resetAccount();
		source = owner;
		this.onShellDisposed = onShellDisposed;
	}

	/** Detaches the source and immediately clears every account value and page, showing the neutral notice. EDT only. */
	public void detachAccountSource(Object owner) {
		requireEdt();
		if (disposed || owner == null || owner != source) { ignoredSourceCalls++; return; }
		accountGeneration++;
		source = null;
		onShellDisposed = null;
		resetAccount();
	}

	/** Account data may not be shown: clear every held value and show the shared notice on account pages. */
	public void hideAccount(Object owner, StateNotice.Presence p) {
		requireEdt();
		Objects.requireNonNull(p);
		if (disposed || owner == null || owner != source) { ignoredSourceCalls++; return; }
		clearAccountValues();
		presence = p;
		noticeHost = null;
		if (shown != null) show(shown);
	}

	/** Account data may be shown for {@code newScope}. Pages are discarded when the scope does not continue. */
	public void showAccount(Object owner, Object newScope) {
		requireEdt();
		Objects.requireNonNull(newScope);
		if (disposed || owner == null || owner != source) { ignoredSourceCalls++; return; }
		if (scope != null && !continuity.test(scope, newScope)) discardAccountPages();
		scope = newScope;
		presence = null;
		noticeHost = null;
		if (shown != null) show(shown);
		scopeListeners.forEach(l -> l.accept(newScope));
	}

	/** Neutral state: no values, no account pages, no scope. */
	private void resetAccount() {
		clearAccountValues();
		discardAccountPages();
		scope = null;
		presence = StateNotice.Presence.UNAVAILABLE;
		noticeHost = null;
		if (shown != null) show(shown);
	}

	private static void requireEdt() {
		if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("account calls must run on the EDT");
	}

	private void clearAccountValues() {
		accountClears++;
		clearListeners.forEach(Runnable::run);
		for (Map.Entry<String, PageHost> e : hosts.entrySet()) if (isAccount(e.getKey())) walk(e.getValue(), c -> {
			if (c instanceof AccountBound) ((AccountBound) c).clearAccountData();
		});
	}

	private void discardAccountPages() {
		pageDiscards++;
		clearListeners.forEach(Runnable::run);
		for (String id : new ArrayList<>(hosts.keySet())) {
			if (!isAccount(id)) continue;
			disposeTree(hosts.remove(id));
			scrollPositions.remove(id);
			focusOwners.remove(id);
		}
	}

	private boolean isAccount(String id) {
		Destination d = nav.destination(id);
		return d != null && d.accountData();
	}

	private boolean showingNotice() { return presence != null && shown != null && isAccount(shown); }

	// ---- navigation -------------------------------------------------------------------------------------------

	private void show(String id) {
		if (disposed) return;
		if (shown != null && hosts.containsKey(shown) && scroll.getViewport().getView() == hosts.get(shown)) {
			scrollPositions.put(shown, scroll.getViewport().getViewPosition());
			Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
			if (f != null && SwingUtilities.isDescendingFrom(f, hosts.get(shown))) focusOwners.put(shown, f);
			else focusOwners.remove(shown);
		}
		shown = id;
		PageHost host;
		if (presence != null && isAccount(id)) {
			if (noticeHost == null) noticeHost = notice(presence);
			host = noticeHost;
		} else {
			host = hosts.get(id);
			if (host == null) {
				Supplier<JComponent> f = factories.get(id);
				JComponent body = f != null ? f.get() : Placeholder.forDestination(ctx, nav.destination(id));
				host = new PageHost(ctx, body, environments.getOrDefault(id, Environment.PLAIN));
				host.setName("page:" + id);
				hosts.put(id, host);
				if (body instanceof LocalBack) ((LocalBack) body).setLocalBackListener(this::refreshBack);
			}
		}
		scroll.setViewportView(host);
		header.title().setText(nav.destination(id).label());
		boolean returning = nav.returning();
		RouteRequest request = nav.currentRequest();
		if (host != noticeHost && host.content() instanceof RouteAware && request != null && request.route().equals(id))
			((RouteAware) host.content()).routeShown(request, returning);
		refreshBack();
		scroll.validate();
		scroll.getViewport().setViewPosition(host == noticeHost ? new Point(0, 0) : scrollPositions.getOrDefault(id, new Point(0, 0)));
		Component opener = returning && host != noticeHost ? focusOwners.get(id) : null;
		if (opener != null && SwingUtilities.isDescendingFrom(opener, host)) opener.requestFocusInWindow();
		header.revalidate();
		repaint();
	}

	private PageHost notice(StateNotice.Presence p) {
		StackPanel s = new StackPanel(Tokens.GAP);
		Card c = new Card(ctx, null);
		c.addAll(new StateNotice(ctx, p));
		s.add(c);
		PageHost h = new PageHost(ctx, s, Environment.PLAIN);
		h.setName("notice:" + p);
		return h;
	}

	private void showDestinationMenu() {
		JPopupMenu m = buildDestinationMenu();
		if (header.menu().isShowing()) m.show(header.menu(), 0, header.menu().getHeight());
	}

	/** The rail's destinations as a menu, used while the rail is hidden. Exposed for tests. */
	public JPopupMenu buildDestinationMenu() {
		JPopupMenu m = new JPopupMenu() {
			@Override public JToolTip createToolTip() { return ThemedToolTip.create(ctx, this); }
		};
		Palette p = ctx.palette();
		m.setBorder(BorderFactory.createLineBorder(p.border));
		for (Destination d : nav.destinations()) {
			if (!d.inRail()) continue;
			JMenuItem item = new JMenuItem(d.label());
			item.setFont(ctx.font(d.id().equals(nav.current()) ? FontRole.VALUE : FontRole.BODY));
			item.setBackground(p.panel);
			item.setForeground(p.text);
			item.addActionListener(e -> nav.navigate(d.id()));
			m.add(item);
		}
		return m;
	}

	// ---- lifecycle --------------------------------------------------------------------------------------------

	@Override
	public void dispose() {
		if (disposed) return;
		Runnable detach = onShellDisposed;
		if (detach != null) detach.run();      // the attached source cancels its subscription and detaches
		if (source != null) { source = null; onShellDisposed = null; resetAccount(); }
		accountGeneration++;
		disposed = true;
		nav.removeListener(onNavigate);
		ctx.removeChangeListener(onContextChange);
		rail.dispose();
		scopeListeners.clear();
		clearListeners.clear();
		for (PageHost h : hosts.values()) disposeTree(h);
		hosts.clear();
		scrollPositions.clear();
		focusOwners.clear();
		noticeHost = null;
		scroll.setViewportView(null);
		scope = null;
	}

	private static void disposeTree(Component root) {
		walk(root, c -> {
			if (c instanceof AccountBound) ((AccountBound) c).clearAccountData();
			if (c instanceof Disposable) ((Disposable) c).dispose();
		});
	}

	private static void walk(Component c, Consumer<Component> f) {
		if (c == null) return;
		f.accept(c);
		if (c instanceof Container) for (Component k : ((Container) c).getComponents()) walk(k, f);
	}

	private void restyle() {
		if (disposed) return;
		List<Component> roots = new ArrayList<>(hosts.values());
		roots.add(this);
		if (noticeHost != null) roots.add(noticeHost);
		scroll.getViewport().setBackground(ctx.palette().background);
		for (Component r : roots) walk(r, c -> {
			if (c instanceof ContextAware) ((ContextAware) c).contextChanged();
			if (c instanceof JComponent) ((JComponent) c).revalidate();
		});
		repaint();
	}

	// ---- layout -------------------------------------------------------------------------------------------------

	private final class ShellLayout implements LayoutManager {
		@Override
		public void layoutContainer(Container parent) {
			layout = ShellGeometry.compute(spec, getWidth());
			railBox.setVisible(layout.railVisible);
			header.menu().setVisible(!layout.railVisible);
			if (layout.railVisible) { railBox.setBounds(0, 0, layout.railWidth, getHeight()); railBox.doLayout(); }
			column.setChrome(layout.chromeLeft, layout.chrome);
			scrollLayout.setOverlay(!layout.scrollbarReserved);
			column.setBounds(layout.railWidth, 0, getWidth() - layout.railWidth, getHeight());
			column.doLayout();
			scroll.doLayout();
		}

		@Override public Dimension preferredLayoutSize(Container p) { return getPreferredSize(); }
		@Override public Dimension minimumLayoutSize(Container p) { return new Dimension(1, 1); }
		@Override public void addLayoutComponent(String n, Component c) { }
		@Override public void removeLayoutComponent(Component c) { }
	}

	/** Overlapping children in overlay mode: optimized drawing must be off so the bar stays on top. */
	private final class ContentScrollPane extends JScrollPane {
		@Override public boolean isOptimizedDrawingEnabled() { return !scrollLayout.overlay(); }
	}

	/** Content column with outer frame chrome on each side (width chosen by the geometry). */
	private final class Column extends JPanel {
		private int left = -1, chrome = -1;

		Column() {
			super(new BorderLayout());
			setOpaque(true);
			setChrome(0, Tokens.CHROME);
		}

		/** {@code l} is 0 while the rail is shown: no separator between rail and content. */
		void setChrome(int l, int c) {
			if (l == left && c == chrome) return;
			left = l;
			chrome = c;
			setBorder(BorderFactory.createEmptyBorder(0, l, c, c));
		}

		int leftChrome() { return left; }

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Paints.prepare(g);
			Palette p = ctx.palette();
			g2.setColor(ctx.themed() ? new java.awt.Color(0x1a1916) : p.border);
			g2.fillRect(0, 0, getWidth(), getHeight());
			if (ctx.themed() && chrome > 0) {
				g2.setColor(Paints.alpha(p.accent, 110));
				if (left > 0) g2.drawLine(1, 0, 1, getHeight() - 2);
				g2.drawLine(getWidth() - 2, 0, getWidth() - 2, getHeight() - 2);
				g2.drawLine(1, getHeight() - 2, getWidth() - 2, getHeight() - 2);
			}
			g2.dispose();
		}
	}
}
