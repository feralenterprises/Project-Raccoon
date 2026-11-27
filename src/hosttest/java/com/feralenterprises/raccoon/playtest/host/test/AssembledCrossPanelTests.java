package com.feralenterprises.raccoon.playtest.host.test;

import com.feralenterprises.raccoon.playtest.r02.assembly.LaneInputsArchive;
import com.feralenterprises.raccoon.playtest.r02.fixture.Appearance;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.host.ClientPhase;
import com.feralenterprises.raccoon.playtest.r02.host.LaneDiscovery;
import com.feralenterprises.raccoon.playtest.r02.host.PlaytestSession;
import com.feralenterprises.raccoon.playtest.r02.host.RunRecord;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.shell.Navigator;
import com.feralenterprises.raccoon.ui.shell.PageHost;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import java.awt.Component;
import java.awt.Container;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The assembled R02 interface with every lane present: all lane entries loaded through the host from the verified
 * Lane Inputs ZIP, the frozen A/B scenarios, then a crawl that presses every visible, enabled button on every panel.
 * Every press must not throw. Every press that changes route must land on a real lane panel (never a placeholder) and
 * Back must return to the sender. Then scope and appearance checks across all panels.
 * Args: Lane Inputs ZIP, extracted frozen review. Needs every lane's entry on the classpath.
 */
public final class AssembledCrossPanelTests {
	private static final Check T = new Check("AssembledCrossPanelTests");
	private static final String PANEL_PACKAGE = Lane.PANEL_PACKAGE;
	private static final int MAX_PRESSES_PER_PAGE = 250;

	public static void main(String[] args) throws Exception {
		Path zip = Paths.get(args[0]), review = Paths.get(args[1]);
		PlaytestSession.Package pkg = PlaytestSession.Package.load(review);
		pkg.assets.withLaneSprites(LaneInputsArchive.open(zip));
		ClassLoader cl = AssembledCrossPanelTests.class.getClassLoader();
		T.that("all five lane entries present", LaneDiscovery.inspect(cl).absent.isEmpty());
		FixtureDispatcher fx = PlaytestSession.newDispatcher(pkg);
		List<RouteRegistration> regs = LaneDiscovery.load(cl, zip, pkg.root, fx);
		Set<RouteId> registered = new TreeSet<>();
		for (RouteRegistration r : regs) registered.add(r.route());
		T.that("every route has a lane panel (14)", registered.size() == RouteId.values().length);
		RunRecord rec = RunRecord.memory();
		PlaytestSession s = edt(() -> new PlaytestSession(pkg, fx, new DefaultFontSource(), null, regs, rec));
		edt(() -> { s.start(ClientPhase.OPEN); return null; });
		T.that("no placeholders in the assembly", s.composition().unregisteredRoutes().isEmpty());
		RaccoonShell shell = s.shell();
		Navigator nav = shell.navigator();
		// With a display (Xvfb), mount the shell in a real window so pages are laid out like the sidebar.
		javax.swing.JFrame frame = java.awt.GraphicsEnvironment.isHeadless() ? null : edt(() -> {
			javax.swing.JFrame f = new javax.swing.JFrame("R02 assembled");
			f.getContentPane().add(shell);
			f.setSize(Math.max(260, shell.getPreferredSize().width + 20), 1000);
			f.setVisible(true);
			return f;
		});
		System.out.println("AssembledCrossPanelTests: " + (frame == null ? "headless" : "real window " + frame.getWidth() + "x" + frame.getHeight()));

		// Every route renders a real lane page for Demo A.
		Map<RouteId, JComponent> pagesA = new java.util.EnumMap<>(RouteId.class);
		Set<RouteId> broken = new TreeSet<>();
		for (RouteId r : RouteId.values()) {
			JComponent page;
			try {
				page = edt(() -> { nav.resetHistory(RouteRequest.of(r.id())); nav.navigate(r.id()); return content(shell); });
			} catch (RuntimeException e) {
				broken.add(r);
				System.out.println("  render error " + r.id() + ": " + trace(e));
				T.that("route " + r.id() + " renders with the populated Demo A scenario", false);
				continue;
			}
			T.that("route " + r.id() + " shows its lane panel", isLanePage(page));
			pagesA.put(r, page);
		}

		// Crawl: every press on a fresh page (new scope, so no local state carries over). Presses that open a local
		// view (the clickables change, route unchanged) are explored one level deeper from that fresh state.
		List<String> links = new ArrayList<>();
		Set<String> errors = new LinkedHashSet<>();
		int[] presses = {0};
		List<RouteRequest> entries = new ArrayList<>();
		for (RouteId r : RouteId.values()) if (!broken.contains(r)) entries.add(RouteRequest.of(r.id()));
		Map<String, Integer> perKind = new java.util.HashMap<>();
		// Chronicle detail for every encounter with a Demo A saved record (its trips link to Feral Files).
		for (com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord saved : s.data().archive("A"))
			if (saved.encounterKey() != null) {
				RouteRequest c = RouteRequest.of(RouteId.CHRONICLE.id(), RouteRequest.Param.ENCOUNTER, saved.encounterKey());
				if (!entries.contains(c)) entries.add(c);
			}
		for (int e = 0; e < entries.size(); e++) {
			RouteRequest entry = entries.get(e);
			// Feral Friends reaches its Chronicle links through category, pet and source levels.
			int depth = RouteId.FRIENDS.id().equals(entry.route()) ? 5 : 2;
			int[] budget = {RouteId.FRIENDS.id().equals(entry.route()) ? 6000 : 20000};
			int before = presses[0];
			explore(s, shell, nav, entry, new int[0], depth, budget, presses, links, errors, entries, perKind);
			System.out.println("  entry " + describe(entry) + ": " + (presses[0] - before) + " presses");
		}
		// Feral Friends -> Boss Chronicle sits four levels deep (album, pet, methods, source): followed by name for every
		// album pet whose methods list boss sources.
		int friendLinks = 0;
		List<String> petKeys = edt(() -> {
			s.selectAccount("A");
			nav.resetHistory(RouteRequest.of(RouteId.FRIENDS.id()));
			nav.navigate(RouteId.FRIENDS.id());
			pressNamed(shell, "friends-album");
			List<String> keys = new ArrayList<>();
			for (JComponent c : named(content(shell), "friends-open-")) keys.add(c.getName().substring("friends-open-".length()));
			return keys;
		});
		for (String pet : petKeys) {
			String[] r = edt(() -> {
				s.selectAccount("A");
				nav.resetHistory(RouteRequest.of(RouteId.FRIENDS.id()));
				nav.navigate(RouteId.FRIENDS.id());
				pressNamed(shell, "friends-album");
				pressNamed(shell, "friends-open-" + pet);
				if (!pressNamed(shell, "friends-methods")) return null;
				List<JComponent> sources = named(content(shell), "friends-source-");
				if (sources.isEmpty()) return null;
				com.feralenterprises.raccoon.ui.component.Expandable src = (com.feralenterprises.raccoon.ui.component.Expandable) sources.get(0);
				String key = src.getName().substring("friends-source-".length());
				src.setExpanded(true);
				shell.validate();
				if (!pressNamed(shell, "friends-chronicle-" + key)) return new String[] {key, "no link button", ""};
				RouteRequest at = nav.currentRequest();
				boolean ok = RouteId.CHRONICLE.id().equals(at.route()) && at.param() == RouteRequest.Param.ENCOUNTER && key.equals(at.paramId())
					&& isLanePage(content(shell));
				int backs = 0;
				while (!RouteId.FRIENDS.id().equals(nav.current()) && backs < 6 && shell.goBack()) backs++;
				return new String[] {key, ok ? "ok" : "wrong target " + at.route() + " " + at.paramId(), RouteId.FRIENDS.id().equals(nav.current()) ? "back-ok" : "back-FAILED"};
			});
			if (r == null) continue;
			friendLinks++;
			links.add("friends --[" + pet + " > methods > " + r[0] + "]--> chronicle ENCOUNTER=" + r[0] + " (LINK, " + r[2] + ")");
			T.that("friends pet " + pet + " opens Boss Chronicle for " + r[0], "ok".equals(r[1]));
			T.that("friends pet " + pet + " Back returns to Feral Friends", "back-ok".equals(r[2]));
		}
		System.out.println("  Feral Friends: " + petKeys.size() + " album pets, " + friendLinks + " with a Boss Chronicle source link");
		T.that("Feral Friends reaches Boss Chronicle", friendLinks > 0);
		for (String e : errors) System.out.println("  press error: " + e);
		T.that("no press threw (" + presses[0] + " presses)", errors.isEmpty());
		Set<String> distinct = new TreeSet<>();
		for (String l : links) distinct.add(l.replaceAll(" \\(.*", "").replaceAll("--\\[.*\\]-->", "->"));
		System.out.println("AssembledCrossPanelTests: " + presses[0] + " presses, " + links.size() + " route changes, distinct route joins:");
		for (String d : distinct) System.out.println("  " + d);
		T.that("cross-panel joins exist", distinct.size() >= 5);

		// A -> B: every page rebuilt, history at Home, no A page instance reused.
		int discards = shell.pageDiscards();
		edt(() -> { nav.navigate(RouteId.BANK.id()); s.selectAccount("B"); return null; });
		T.that("A->B discards A's pages and resets to Home", shell.pageDiscards() > discards && RouteId.HOME.id().equals(nav.current()));
		boolean allNew = true;
		for (RouteId r : RouteId.values()) {
			JComponent page;
			try { page = edt(() -> { nav.resetHistory(RouteRequest.of(r.id())); nav.navigate(r.id()); return content(shell); }); }
			catch (RuntimeException e) { System.out.println("  render error after A->B " + r.id() + ": " + trace(e)); allNew = false; continue; }
			if (page == pagesA.get(r) || !isLanePage(page)) { allNew = false; System.out.println("  reused or missing after A->B: " + r.id()); }
		}
		T.that("A->B: every route shows a new lane page", allNew);
		edt(() -> { s.selectAccount(null); return null; });
		T.that("logout keeps every route renderable", renderAll(shell, nav, "signed out", false));

		// Appearance from Settings reaches the shared text scale; then back.
		edt(() -> { s.selectAccount("A"); fx.setAppearance(fx.appearance().withSize(Appearance.Size.LARGE)); return null; });
		T.that("Large appearance reaches the shared text scale", s.ui().textScale() == TextScale.LARGE);
		T.that("every route renders at Large", renderAll(shell, nav, "Large", true));
		edt(() -> { fx.setAppearance(Appearance.DEFAULTS); return null; });

		// Bank variations reach Bank, Capture and XP Stash in place (same scope, newer revision).
		for (com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios.BankState b : com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios.BankState.values()) {
			edt(() -> s.vary("bank=" + b, x -> x.withBank(b)));
			boolean ok = render(shell, nav, "bank " + b, true, RouteId.BANK, RouteId.CAPTURE, RouteId.XPSTASH, RouteId.HOME);
			T.that("bank " + b + " renders in Bank, Capture, XP Stash and Home", ok);
		}

		edt(() -> { s.dispose(); if (frame != null) frame.dispose(); return null; });
		T.that("teardown clean with every lane mounted " + s.teardownReport(), PlaytestSession.clean(s.teardownReport()));
		System.exit(T.finish());
	}

	/**
	 * Fresh page for {@code from} (same simulated account, new scope), then the presses in {@code path}; the last one is
	 * judged. Returns null when the last index is past the clickables. Result: kind, label path, target, back, changed.
	 */
	private static String[] pressPath(PlaytestSession s, RaccoonShell shell, Navigator nav, RouteRequest entry, int[] path) throws Exception {
		RouteId from = RouteId.of(entry.route());
		return edt(() -> {
			s.selectAccount(s.selection().account == null ? "A" : s.selection().account);
			nav.resetHistory(RouteRequest.of(from.id()));
			if (entry.hasParam()) nav.navigate(entry); else nav.navigate(from.id());
			shell.validate();
			StringBuilder labels = new StringBuilder();
			for (int k = 0; k < path.length; k++) {
				List<JComponent> cs = clickables(content(shell));
				if (path[k] >= cs.size()) return k == path.length - 1 ? null : new String[] {"GONE", labels.toString(), "", "", ""};
				JComponent b = cs.get(path[k]);
				String label = label(b);
				labels.append(labels.length() == 0 ? "" : " > ").append(label);
				List<String> beforeLabels = labelsOf(cs);
				RouteRequest before = nav.currentRequest();
				Object scopeBefore = s.fixtures().current() == null ? null : s.fixtures().current().scope();
				try {
					press(b);
				} catch (RuntimeException e) {
					return new String[] {"ERROR", labels.toString(), trace(e), "", ""};
				}
				shell.validate();
				RouteRequest after = nav.currentRequest();
				Object scopeAfter = s.fixtures().current() == null ? null : s.fixtures().current().scope();
				if (!java.util.Objects.equals(scopeBefore, scopeAfter))
					return new String[] {"SCOPE", labels.toString(), String.valueOf(after == null ? null : after.route()), "", ""};
				if (after != null && !after.route().equals(before.route())) {
					JComponent target = content(shell);
					boolean real = isLanePage(target);
					String param = after.param() == null ? "" : " " + after.param() + "=" + after.paramId();
					int backs = 0;
					while (!from.id().equals(nav.current()) && backs < 6 && shell.goBack()) backs++;
					return new String[] {real ? "LINK" : "PLACEHOLDER", labels.toString(), after.route() + param,
						from.id().equals(nav.current()) ? "back-ok" : "back-FAILED", ""};
				}
				if (k == path.length - 1) {
					boolean changed = !labelsOf(clickables(content(shell))).equals(beforeLabels);
					return new String[] {"LOCAL", labels.toString(), "", "", changed ? "changed" : "same"};
				}
			}
			return null;
		});
	}

	private static List<String> labelsOf(List<JComponent> cs) {
		List<String> out = new ArrayList<>();
		for (JComponent c : cs) out.add(label(c));
		return out;
	}

	private static void explore(PlaytestSession s, RaccoonShell shell, Navigator nav, RouteRequest entry, int[] prefix, int depth, int[] budget,
		int[] presses, List<String> links, Set<String> errors, List<RouteRequest> entries, Map<String, Integer> perKind) throws Exception {
		for (int i = 0; i < MAX_PRESSES_PER_PAGE && budget[0] > 0; i++) {
			int[] path = java.util.Arrays.copyOf(prefix, prefix.length + 1);
			path[prefix.length] = i;
			String[] r = pressPath(s, shell, nav, entry, path);
			if (r == null) return;
			budget[0]--;
			presses[0]++;
			record(r, entry, links, errors, entries, perKind);
			if ("LOCAL".equals(r[0]) && "changed".equals(r[4]) && path.length < depth)
				explore(s, shell, nav, entry, path, depth, budget, presses, links, errors, entries, perKind);
		}
	}

	/** Detail entry points found by links are crawled too (up to 3 ids per route and parameter kind). */
	private static void record(String[] r, RouteRequest entry, List<String> links, Set<String> errors, List<RouteRequest> entries,
		Map<String, Integer> perKind) {
		String from = describe(entry);
		if ("ERROR".equals(r[0])) errors.add(from + " [" + r[1] + "] " + r[2]);
		if ("SCOPE".equals(r[0])) System.out.println("  scope reset " + from + " [" + r[1] + "] -> " + r[2] + " (history reset, expected)");
		if ("LINK".equals(r[0]) || "PLACEHOLDER".equals(r[0])) {
			links.add(entry.route() + " --[" + r[1] + "]--> " + r[2] + " (" + r[0] + ", " + r[3] + ")");
			T.that("link " + from + " [" + r[1] + "] lands on a lane panel", "LINK".equals(r[0]));
			T.that("link " + from + " [" + r[1] + "] Back returns to the sender", "back-ok".equals(r[3]));
			String[] t = r[2].split(" ", 2);
			if (t.length == 2) {
				String[] kv = t[1].split("=", 2);
				String kind = t[0] + " " + kv[0];
				RouteRequest next = RouteRequest.of(t[0], RouteRequest.Param.valueOf(kv[0]), kv[1]);
				if (!entries.contains(next) && perKind.merge(kind, 1, Integer::sum) <= 3) entries.add(next);
			}
		}
	}

	private static String describe(RouteRequest e) { return e.route() + (e.hasParam() ? " " + e.param() + "=" + e.paramId() : ""); }

	private static List<JComponent> named(Component root, String prefix) {
		List<JComponent> out = new ArrayList<>();
		collectNamed(root, prefix, out);
		return out;
	}

	private static void collectNamed(Component c, String prefix, List<JComponent> out) {
		if (c == null || !c.isVisible()) return;
		if (c instanceof JComponent && c.getName() != null && c.getName().startsWith(prefix)) out.add((JComponent) c);
		if (c instanceof Container) for (Component k : ((Container) c).getComponents()) collectNamed(k, prefix, out);
	}

	private static boolean pressNamed(RaccoonShell shell, String name) {
		for (JComponent c : named(content(shell), name)) if (name.equals(c.getName())) { press(c); shell.validate(); return true; }
		return false;
	}

	private static boolean renderAll(RaccoonShell shell, Navigator nav, String when, boolean lanePage) throws Exception {
		return render(shell, nav, when, lanePage, RouteId.values());
	}

	private static boolean render(RaccoonShell shell, Navigator nav, String when, boolean lanePage, RouteId... routes) throws Exception {
		boolean ok = true;
		for (RouteId r : routes) {
			try {
				JComponent c = edt(() -> { nav.resetHistory(RouteRequest.of(r.id())); nav.navigate(r.id()); return content(shell); });
				if (c == null || (lanePage && !isLanePage(c))) { ok = false; System.out.println("  not a lane page (" + when + "): " + r.id()); }
			} catch (RuntimeException e) {
				ok = false;
				System.out.println("  render error (" + when + ") " + r.id() + ": " + trace(e));
			}
		}
		return ok;
	}

	private static JComponent content(RaccoonShell shell) {
		PageHost h = shell.currentHost();
		return h == null ? null : h.content();
	}

	/** A component tree containing a class from a lane's own package (a placeholder never does). */
	private static boolean isLanePage(Component c) {
		if (c == null) return false;
		if (c.getClass().getName().startsWith(PANEL_PACKAGE)) return true;
		if (c instanceof Container) for (Component k : ((Container) c).getComponents()) if (isLanePage(k)) return true;
		return false;
	}

	/** Enabled, visible buttons, plus components clicked through a lane's or the framework's own mouse listener. */
	private static List<JComponent> clickables(Component root) {
		List<JComponent> out = new ArrayList<>();
		Map<Component, Boolean> seen = new IdentityHashMap<>();
		collect(root, out, seen);
		return out;
	}

	private static void collect(Component c, List<JComponent> out, Map<Component, Boolean> seen) {
		if (c == null || !c.isVisible() || seen.put(c, Boolean.TRUE) != null) return;
		if (c instanceof AbstractButton && c.isEnabled()) out.add((AbstractButton) c);
		else if (c instanceof com.feralenterprises.raccoon.ui.component.SegmentedSelector && c.isEnabled()) {
			com.feralenterprises.raccoon.ui.component.SegmentedSelector sel = (com.feralenterprises.raccoon.ui.component.SegmentedSelector) c;
			for (int i = 0; i < sel.optionCount(); i++) out.add(new Segment(sel, i));
		}
		else if (c instanceof JComponent && c.isEnabled() && ownClick((JComponent) c)) out.add((JComponent) c);
		if (c instanceof Container) for (Component k : ((Container) c).getComponents()) collect(k, out, seen);
	}

	private static boolean ownClick(JComponent c) {
		for (java.awt.event.MouseListener l : c.getMouseListeners()) {
			String n = l.getClass().getName();
			if (n.startsWith(PANEL_PACKAGE) || n.startsWith("com.feralenterprises.raccoon.ui.")) return true;
		}
		return false;
	}

	/** One option of a segmented selector, pressed at its own cell. Never added to the component tree. */
	private static final class Segment extends JComponent {
		final com.feralenterprises.raccoon.ui.component.SegmentedSelector selector;
		final int option;
		Segment(com.feralenterprises.raccoon.ui.component.SegmentedSelector selector, int option) {
			this.selector = selector;
			this.option = option;
			setName((selector.getName() == null ? "segments" : selector.getName()) + "#" + option);
		}
	}

	private static void press(JComponent c) {
		if (c instanceof Segment) {
			Segment seg = (Segment) c;
			java.awt.Rectangle r = seg.selector.cell(seg.option, Math.max(seg.selector.getWidth(), seg.selector.minimumContentWidth()));
			click(seg.selector, r.x + r.width / 2, r.y + r.height / 2);
			return;
		}
		if (c instanceof AbstractButton) { ((AbstractButton) c).doClick(0); return; }
		if (c.getWidth() <= 0) c.setSize(Math.max(1, c.getPreferredSize().width), Math.max(1, c.getPreferredSize().height));
		click(c, Math.max(1, c.getWidth() / 2), Math.max(1, Math.min(c.getHeight() - 1, 8)));
	}

	private static void click(JComponent c, int x, int y) {
		long t = System.currentTimeMillis();
		for (int id : new int[] {java.awt.event.MouseEvent.MOUSE_PRESSED, java.awt.event.MouseEvent.MOUSE_RELEASED, java.awt.event.MouseEvent.MOUSE_CLICKED}) {
			java.awt.event.MouseEvent e = new java.awt.event.MouseEvent(c, id, t, 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1);
			for (java.awt.event.MouseListener l : c.getMouseListeners()) {
				if (id == java.awt.event.MouseEvent.MOUSE_PRESSED) l.mousePressed(e);
				else if (id == java.awt.event.MouseEvent.MOUSE_RELEASED) l.mouseReleased(e);
				else l.mouseClicked(e);
			}
		}
	}

	private static String label(JComponent b) {
		if (b instanceof Segment) return b.getName();
		String n = b.getAccessibleContext() == null ? null : b.getAccessibleContext().getAccessibleName();
		if ((n == null || n.isEmpty()) && b instanceof AbstractButton) n = ((AbstractButton) b).getText();
		if (n == null || n.isEmpty()) n = b.getName();
		return n == null ? b.getClass().getSimpleName() : n.replaceAll("\\s+", " ");
	}

	private static String trace(Throwable e) {
		StringWriter w = new StringWriter();
		e.printStackTrace(new PrintWriter(w));
		String[] lines = w.toString().split("\n");
		return String.join(" | ", java.util.Arrays.asList(lines).subList(0, Math.min(4, lines.length)));
	}

	private static <V> V edt(Callable<V> c) throws Exception {
		AtomicReference<V> out = new AtomicReference<>();
		AtomicReference<Exception> err = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> { try { out.set(c.call()); } catch (Exception e) { err.set(e); } });
		if (err.get() != null) throw err.get();
		return out.get();
	}
}
