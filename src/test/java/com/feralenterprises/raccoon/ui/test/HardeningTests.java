package com.feralenterprises.raccoon.ui.test;

import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.component.EquipmentGrid;
import com.feralenterprises.raccoon.ui.component.StateNotice;
import com.feralenterprises.raccoon.ui.lifecycle.RepaintFence;
import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.model.EquipmentSlot;
import com.feralenterprises.raccoon.ui.model.ScopedState;
import com.feralenterprises.raccoon.ui.shell.PageHost;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.ui.shell.ShellGeometry;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.Skin;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import com.feralenterprises.raccoon.workbench.FixtureAssetProvider;
import com.feralenterprises.raccoon.workbench.Fixtures;
import com.feralenterprises.raccoon.workbench.LayoutAudit;
import com.feralenterprises.raccoon.workbench.Offscreen;
import com.feralenterprises.raccoon.workbench.WorkbenchPages;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;

/**
 * UI-002 hardening checks: adaptive geometry, overlay scrollbar, header destination menu, listener disposal, async
 * image fencing, account-data clearing and scope continuity, and the native sprite id table. Headless.
 */
public final class HardeningTests {
	private static final List<String> failures = new ArrayList<>();
	private static int passed;

	public static void main(String[] args) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				geometryPolicy();
				railSeam();
				adaptiveShell();
				overlayScrollbar();
				headerMenu();
				disposal();
				accountGating();
				scopedState();
				sprites();
			} catch (Throwable t) {
				t.printStackTrace();
				failures.add("exception: " + t);
			}
		});
		asyncFencing();
		System.out.println("HardeningTests: " + passed + " passed, " + failures.size() + " failed");
		failures.forEach(f -> System.out.println("  FAIL " + f));
		System.exit(failures.isEmpty() ? 0 : 1);
	}

	static void check(String name, boolean ok) { if (ok) passed++; else failures.add(name); }

	static UiContext ctx(FixtureAssetProvider assets) { return new UiContext(new DefaultFontSource(), assets); }

	static FixtureAssetProvider assets() {
		return new FixtureAssetProvider(Paths.get("review-assets/art"), Paths.get("review-assets/item-fixtures"));
	}

	// ---- geometry ---------------------------------------------------------------------------------------------

	static void geometryPolicy() {
		ShellGeometry.Spec s = ShellGeometry.Spec.defaults();
		// UI-003 owner correction: rail 34 px, no chrome or track between rail and content.
		ShellGeometry.Layout full = ShellGeometry.compute(s, 268);
		check("geometry 268: rail, reserved scrollbar, outer chrome only, content 225 (" + full + ")",
			full.railVisible && full.scrollbarReserved && full.chrome == 3 && full.chromeLeft == 0 && full.contentWidth == 225);
		check("geometry 275: rail with no separator, content 232", ShellGeometry.compute(s, 275).contentWidth == 232
			&& ShellGeometry.compute(s, 275).chromeLeft == 0);
		ShellGeometry.Layout g263 = ShellGeometry.compute(s, 263);
		check("geometry 263: keeps rail by overlaying the scrollbar, content 226 (" + g263 + ")",
			g263.railVisible && !g263.scrollbarReserved && g263.chromeLeft == 0 && g263.contentWidth == 226);
		ShellGeometry.Layout g242 = ShellGeometry.compute(s, 242);
		check("geometry 242 (RuneLite wrapped PluginPanel): rail moves to header menu, content >= 225 (" + g242 + ")",
			!g242.railVisible && g242.contentWidth >= 225);
		ShellGeometry.Layout g225 = ShellGeometry.compute(s, 225);
		check("geometry 225 (unwrapped PluginPanel): menu, overlay, no chrome, content exactly 225 (" + g225 + ")",
			!g225.railVisible && !g225.scrollbarReserved && g225.chrome == 0 && g225.contentWidth == 225);
		check("geometry 330: extra width goes to content", ShellGeometry.compute(s, 330).contentWidth == 287);
		check("geometry: chrome stays on the outer side when the rail is hidden", ShellGeometry.compute(s, 242).chromeLeft == 3);
		ShellGeometry.Layout forced = ShellGeometry.compute(s.withRail(38, ShellGeometry.RailMode.SHOWN), 225);
		check("geometry: explicit SHOWN rail honoured even when content shrinks", forced.railVisible && forced.contentWidth < 225);
		check("geometry: explicit HIDDEN rail honoured at full width", !ShellGeometry.compute(s.withRail(38, ShellGeometry.RailMode.HIDDEN), 330).railVisible);
		check("geometry: explicit RESERVED scrollbar honoured", ShellGeometry.compute(s.withScrollbar(ShellGeometry.ScrollbarMode.RESERVED), 225).scrollbarReserved);
		check("geometry: rail width is a parameter", ShellGeometry.compute(s.withRail(30, ShellGeometry.RailMode.AUTO), 264).railVisible
			&& !ShellGeometry.compute(s.withRail(30, ShellGeometry.RailMode.AUTO), 254).railVisible);
		boolean rejected = false;
		try { new ShellGeometry.Spec(-1, ShellGeometry.RailMode.AUTO, ShellGeometry.ScrollbarMode.AUTO, 3, 6, 225); } catch (IllegalArgumentException e) { rejected = true; }
		check("geometry: negative widths rejected", rejected);
	}

	/** UI-003 owner correction: no separator and no reserved space between rail and content; rail still scrolls. */
	static void railSeam() {
		for (Skin sk : Skin.values()) {
			UiContext c = ctx(assets());
			c.setSkin(sk);
			WorkbenchPages p = new WorkbenchPages(c);
			p.shell.navigator().navigate(WorkbenchPages.GEAR);
			Offscreen.size(p.shell, 300);
			RaccoonShell s = p.shell;
			String tag = " (" + sk + ")";
			check("seam: rail is the button column only (34 px)" + tag, s.railScrollPane().getWidth() == Tokens.RAIL_WIDTH
				&& s.rail().getWidth() == Tokens.RAIL_WIDTH);
			check("seam: content column starts exactly where the rail ends" + tag, s.columnX() == s.railScrollPane().getWidth() && s.leftChrome() == 0);
			check("seam: rail scrollbar takes no width" + tag, s.railScrollPane().getVerticalScrollBar().getWidth() == 0);
			check("seam: header begins at the column edge" + tag, s.header().getParent().getX() == Tokens.RAIL_WIDTH && s.header().getX() == 0);
			java.awt.image.BufferedImage img = Offscreen.paint(s, 1.0);
			int top = s.header().getHeight() + 2, rows = 0, separatorLike = 0, differsFromPage = 0;
			int oldChrome = 0x1a1916, oldAccentLine = 0x5b5037;  // UI-002 column fill and its bronze line (alpha-blended)
			for (int y = top; y < img.getHeight() - 4; y++) {
				rows++;
				int edge = img.getRGB(Tokens.RAIL_WIDTH, y) & 0xffffff, inner = img.getRGB(Tokens.RAIL_WIDTH + 6, y) & 0xffffff;
				if (edge == oldChrome || near(edge, oldAccentLine)) separatorLike++;
				if (edge != inner) differsFromPage++;
			}
			check("seam: no old chrome or bronze line pixels at the boundary (" + separatorLike + "/" + rows + ")" + tag, separatorLike == 0);
			if (sk == Skin.STRUCTURAL)
				check("seam: structural boundary pixels equal the page surface (" + differsFromPage + "/" + rows + " differ)" + tag, differsFromPage == 0);
			// Rail overflows at 300 px tall: wheel and navigation must still reach every destination.
			javax.swing.JViewport rv = s.railScrollPane().getViewport();
			check("seam: rail overflows in a short host" + tag, s.rail().getHeight() > rv.getHeight());
			s.railScrollPane().dispatchEvent(new java.awt.event.MouseWheelEvent(s.railScrollPane(), java.awt.event.MouseEvent.MOUSE_WHEEL,
				0, 0, 10, 100, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 3));
			check("seam: mouse wheel scrolls the rail with no visible bar" + tag, rv.getViewPosition().y > 0);
			rv.setViewPosition(new java.awt.Point(0, 0));
			s.navigator().navigate("bounties");
			java.awt.Rectangle b = s.rail().button("bounties").getBounds();
			check("seam: navigating scrolls the current rail button into view" + tag,
				rv.getViewRect().contains(b));
		}
	}

	static boolean near(int a, int b) {
		return Math.abs((a >> 16 & 255) - (b >> 16 & 255)) < 6 && Math.abs((a >> 8 & 255) - (b >> 8 & 255)) < 6 && Math.abs((a & 255) - (b & 255)) < 6;
	}

	static void adaptiveShell() {
		int[] hosts = {225, 242, 263, 268, 275, 306, 313, 330};
		for (int host : hosts) for (TextScale ts : TextScale.values()) for (Skin sk : Skin.values()) {
			UiContext c = ctx(assets());
			c.setSkin(sk);
			c.setTextScale(ts);
			WorkbenchPages p = new WorkbenchPages(c);
			for (String id : new String[]{WorkbenchPages.COMPONENTS, WorkbenchPages.GEAR, WorkbenchPages.STATES, "home"}) {
				p.shell.navigator().navigate(id);
				Offscreen.size(p.shell, host, 720);
				ShellGeometry.Layout g = p.shell.geometry();
				String tag = host + "/" + ts + "/" + sk + "/" + id;
				check("adaptive " + tag + ": viewport equals geometry content width", p.shell.scrollPane().getViewport().getWidth() == g.contentWidth);
				check("adaptive " + tag + ": menu shown iff rail hidden", p.shell.header().menu().isVisible() == !g.railVisible
					&& p.shell.getComponent(0).isVisible() == g.railVisible);
				List<String> issues = LayoutAudit.audit(p.shell.currentHost());
				check("adaptive " + tag + ": no overflow or clipping " + issues, issues.isEmpty());
				check("adaptive " + tag + ": header controls fit", LayoutAudit.audit(p.shell.header()).isEmpty());
			}
		}
	}

	static void overlayScrollbar() {
		WorkbenchPages p = new WorkbenchPages(ctx(assets()));
		p.shell.navigator().navigate(WorkbenchPages.COMPONENTS);
		Offscreen.size(p.shell, 263, 600);
		check("overlay: 263 host uses overlay scrollbar", !p.shell.geometry().scrollbarReserved);
		JScrollBar bar = p.shell.scrollPane().getVerticalScrollBar();
		Rectangle vp = p.shell.scrollPane().getViewport().getBounds();
		Rectangle b = bar.getBounds();
		check("overlay: bar visible and inside the viewport's right inset",
			bar.isVisible() && b.x >= vp.x + vp.width - Tokens.INSET && b.x + b.width <= vp.x + vp.width);
		check("overlay: bar painted above the viewport", p.shell.scrollPane().getComponentZOrder(bar) == 0);
		PageHost host = p.shell.currentHost();
		int contentRight = host.content().getX() + host.content().getWidth();
		check("overlay: no content under the bar", contentRight <= host.getWidth() - b.width);
		Offscreen.size(p.shell, 275, 600);
		check("reserved: 275 host reserves the track beside the viewport",
			p.shell.geometry().scrollbarReserved && bar.getX() >= p.shell.scrollPane().getViewport().getWidth());
	}

	static void headerMenu() {
		WorkbenchPages p = new WorkbenchPages(ctx(assets()));
		p.shell.navigator().navigate(WorkbenchPages.COMPONENTS);
		Offscreen.size(p.shell, 225, 600);
		check("menu: visible at 225 with rail hidden", p.shell.header().menu().isVisible() && !p.shell.getComponent(0).isVisible());
		javax.swing.JPopupMenu popup = p.shell.buildDestinationMenu();
		check("menu: lists every rail destination once (15; Tools is off the rail)", popup.getComponentCount() == 15);
		((javax.swing.JMenuItem) popup.getComponent(3)).doClick(0);
		check("menu: item navigates", "home".equals(p.shell.navigator().current()));
		Offscreen.size(p.shell, 330, 600);
		check("menu: hidden again when the rail fits (no duplicate navigation)", !p.shell.header().menu().isVisible());
	}

	// ---- lifecycle --------------------------------------------------------------------------------------------

	static void disposal() {
		UiContext c = ctx(assets());
		int ctxBefore = c.listenerCount();
		WorkbenchPages p = new WorkbenchPages(c);
		RaccoonShell s = p.shell;
		for (String id : new String[]{WorkbenchPages.COMPONENTS, WorkbenchPages.GEAR, WorkbenchPages.STATES, "home"}) {
			s.navigator().navigate(id);
			Offscreen.size(s, 600);
		}
		check("dispose: shell registers exactly one context listener", c.listenerCount() == ctxBefore + 1);
		check("dispose: search fields no longer register their own context listeners", c.listenerCount() == ctxBefore + 1);
		int navBefore = s.navigator().listenerCount();
		check("dispose: shell and rail registered navigator listeners", navBefore == 2);
		EquipmentGrid grid = p.equipment;
		RepaintFence fence = grid.fence();
		s.dispose();
		check("dispose: context listener removed", c.listenerCount() == ctxBefore);
		check("dispose: navigator listeners removed", s.navigator().listenerCount() == 0);
		check("dispose: cached pages disposed and released", s.host(WorkbenchPages.GEAR) == null && s.scrollPane().getViewport().getView() == null);
		check("dispose: grid fence disposed and account values released",
			fence.isDisposed() && grid.model().state() == DataState.UNAVAILABLE && grid.model().items().isEmpty());
		grid.setModel(Fixtures.equipment(DataState.ACTIVE, 0));
		check("dispose: disposed grid refuses new models", grid.model().state() == DataState.UNAVAILABLE);
		s.navigator().navigate(WorkbenchPages.GEAR);
		check("dispose: navigation after dispose builds nothing", s.host(WorkbenchPages.GEAR) == null);
		s.showAccount(WorkbenchPages.FIXTURE_SOURCE, new Object());
		s.hideAccount(WorkbenchPages.FIXTURE_SOURCE, StateNotice.Presence.SIGNED_OUT);
		check("dispose: account calls after dispose are ignored", s.scope() == null && s.presence() != StateNotice.Presence.SIGNED_OUT);
		s.dispose();
		check("dispose: idempotent", s.isDisposed());
		c.setSkin(Skin.STRUCTURAL);
		check("dispose: later context changes reach nothing", c.listenerCount() == ctxBefore);
	}

	static void asyncFencing() throws Exception {
		FixtureAssetProvider a = assets();
		a.setAsyncDelayMs(150);
		UiContext c = ctx(a);
		EquipmentGrid[] g = new EquipmentGrid[1];
		SwingUtilities.invokeAndWait(() -> {
			g[0] = new EquipmentGrid(c, Fixtures.equipment(DataState.ACTIVE, 0));
			g[0].setSize(225, EquipmentGrid.GRID_H);
			Offscreen.paint(g[0], 1.0);
		});
		check("async: first paint has no images yet (provider still loading)", g[0].lastPaintedItems() == 0);
		SwingUtilities.invokeAndWait(() -> g[0].setModel(Fixtures.equipment(DataState.ACTIVE, 1)));
		Thread.sleep(500);
		SwingUtilities.invokeAndWait(() -> { });
		check("async: callbacks issued for the replaced model are ignored", g[0].fence().ignoredCallbacks() >= 5);
		SwingUtilities.invokeAndWait(() -> Offscreen.paint(g[0], 1.0));
		check("async: images appear once loaded", g[0].lastPaintedItems() == 4);
		a.setAsyncDelayMs(150);
		SwingUtilities.invokeAndWait(() -> { Offscreen.paint(g[0], 1.0); g[0].dispose(); });
		int before = g[0].fence().ignoredCallbacks();
		Thread.sleep(500);
		SwingUtilities.invokeAndWait(() -> { });
		check("async: callbacks after dispose are ignored", g[0].fence().ignoredCallbacks() >= before + 4);
	}

	static void accountGating() {
		UiContext c = ctx(assets());
		WorkbenchPages p = new WorkbenchPages(c, true);
		RaccoonShell s = p.shell;
		List<Object> rescoped = new ArrayList<>();
		s.addScopeListener(rescoped::add);
		s.addScopeListener(sc -> p.deliverFixture());
		s.setScopeContinuity((a, b) -> ((String) a).split("/")[0].equals(((String) b).split("/")[0]));
		Object src = new Object();
		s.attachAccountSource(src, null);
		int d0 = s.pageDiscards();
		s.navigator().navigate(WorkbenchPages.GEAR);
		Offscreen.size(s, 720);
		check("gating: gated shell starts neutral; account page is not even built before publication",
			s.presence() == StateNotice.Presence.UNAVAILABLE && s.host(WorkbenchPages.GEAR) == null
				&& s.currentHost().getName().equals("notice:UNAVAILABLE"));
		s.showAccount(src, "epoch1/auth1");
		Offscreen.size(s, 720);
		check("gating: publish delivers data through the scope listener", p.equipment.model().state() == DataState.ACTIVE && rescoped.size() == 1);
		PageHost first = s.host(WorkbenchPages.GEAR);
		p.gearLoadoutSelector.setSelected(1);
		s.hideAccount(src, StateNotice.Presence.RECONNECTING);
		Offscreen.size(s, 720);
		check("gating: hide shows the shared notice instead of the account page",
			s.currentHost().getName().equals("notice:RECONNECTING") && s.currentHost().content().getComponentCount() == 1);
		check("gating: hide drops every account value held by the hidden page",
			p.equipment.model().items().isEmpty() && p.inventory.model().slots().stream().allMatch(Objects::isNull)
				&& p.gearStatus.state() == DataState.UNAVAILABLE);
		p.tick();
		check("gating: a control cannot re-show data while hidden (data source cleared too)",
			p.equipment.model().state() == DataState.UNAVAILABLE);
		int selection = p.gearLoadoutSelector.selected();
		s.navigator().navigate(WorkbenchPages.COMPONENTS);
		Offscreen.size(s, 720);
		check("gating: non-account page still shows while hidden", s.currentHost().getName().equals("page:" + WorkbenchPages.COMPONENTS));
		s.navigator().back();
		s.showAccount(src, "epoch1/auth2");
		Offscreen.size(s, 720);
		check("gating: same-epoch rescope keeps the page and its UI state",
			s.host(WorkbenchPages.GEAR) == first && p.gearLoadoutSelector.selected() == selection && s.pageDiscards() == d0);
		check("gating: same-epoch rescope re-delivers values for the new scope", p.equipment.model().state() == DataState.ACTIVE && rescoped.size() == 2);
		s.hideAccount(src, StateNotice.Presence.RECONNECTING);
		s.showAccount(src, "epoch2/auth3");
		Offscreen.size(s, 720);
		check("gating: new epoch discards the page (fresh instance, reset selection)",
			s.host(WorkbenchPages.GEAR) != first && p.gearLoadoutSelector.selected() == 0 && s.pageDiscards() == d0 + 1);
		check("gating: old page's grids were disposed", first != null && allFencesDisposed(first));
		s.hideAccount(src, StateNotice.Presence.SIGNED_OUT);
		check("gating: signed out uses the SIGNED_OUT notice", s.presence() == StateNotice.Presence.SIGNED_OUT);
		s.dispose();
	}

	static boolean allFencesDisposed(Component k) {
		if (k instanceof EquipmentGrid && !((EquipmentGrid) k).fence().isDisposed()) return false;
		if (k instanceof Container) for (Component x : ((Container) k).getComponents()) if (!allFencesDisposed(x)) return false;
		return true;
	}

	static void scopedState() {
		ScopedState<int[]> st = new ScopedState<>(() -> new int[]{0},
			(a, b) -> ((String) a).split("/")[0].equals(((String) b).split("/")[0]));
		st.get("e1/a1")[0] = 5;
		check("scoped: continuity keeps state across same-epoch rescope", st.get("e1/a2")[0] == 5);
		check("scoped: new epoch resets state", st.get("e2/a3")[0] == 0);
		st.get("e2/a3")[0] = 7;
		check("scoped: null scope (not publishable) resets state", st.get(null)[0] == 0);
		st.get("e3/a4")[0] = 9;
		st.clear();
		check("scoped: clear resets state", st.get("e3/a4")[0] == 0);
	}

	static void sprites() {
		Set<Integer> ids = new HashSet<>();
		boolean ok = true;
		for (NativeSprite n : NativeSprite.values()) if (n.resolved()) ok &= ids.add(n.spriteId()) && n.gamevalConstant() != null && n.legacyConstant() != null;
		check("sprites: resolved ids unique and fully named", ok && ids.size() == 13);
		boolean empties = true;
		for (EquipmentSlot s : EquipmentSlot.values()) empties &= s.emptySprite().resolved();
		check("sprites: every equipment slot has a resolved empty-slot icon", empties);
		check("sprites: unresolved items return -1, never a guessed id",
			NativeSprite.EQUIP_CONNECTOR_H.spriteId() == -1 && NativeSprite.INVENTORY_BACKGROUND.spriteId() == -1);
	}
}
