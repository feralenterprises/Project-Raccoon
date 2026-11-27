package com.feralenterprises.raccoon.ui.test;

import com.feralenterprises.raccoon.ui.component.EquipmentGrid;
import com.feralenterprises.raccoon.ui.component.InventoryGrid;
import com.feralenterprises.raccoon.ui.component.ItemSlots;
import com.feralenterprises.raccoon.ui.component.ProgressBar;
import com.feralenterprises.raccoon.ui.component.SegmentedSelector;
import com.feralenterprises.raccoon.ui.layout.ColumnsPanel;
import com.feralenterprises.raccoon.ui.layout.StackLayout;
import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.model.EquipmentSlot;
import com.feralenterprises.raccoon.ui.model.Evidence;
import com.feralenterprises.raccoon.ui.model.InventoryView;
import com.feralenterprises.raccoon.ui.model.ItemStack;
import com.feralenterprises.raccoon.ui.model.ProgressView;
import com.feralenterprises.raccoon.ui.model.ScopedState;
import com.feralenterprises.raccoon.ui.shell.Destination;
import com.feralenterprises.raccoon.ui.shell.Navigator;
import com.feralenterprises.raccoon.ui.shell.PageHost;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.ui.theme.Skin;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import com.feralenterprises.raccoon.workbench.Fixtures;
import com.feralenterprises.raccoon.workbench.LayoutAudit;
import com.feralenterprises.raccoon.workbench.Offscreen;
import com.feralenterprises.raccoon.workbench.Workbench;
import com.feralenterprises.raccoon.workbench.WorkbenchPages;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Dependency-free component interaction tests (JUnit is not available offline in this workspace; each check is a
 * named assertion and the process exits 1 on any failure). Runs headless against the real framework components.
 */
public final class ComponentTests {
	private static final List<String> failures = new ArrayList<>();
	private static int passed;

	public static void main(String[] args) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				run();
			} catch (Throwable t) {
				t.printStackTrace();
				failures.add("exception: " + t);
			}
		});
		System.out.println("ComponentTests: " + passed + " passed, " + failures.size() + " failed");
		failures.forEach(f -> System.out.println("  FAIL " + f));
		System.exit(failures.isEmpty() ? 0 : 1);
	}

	static void check(String name, boolean ok) {
		if (ok) passed++; else failures.add(name);
	}

	static WorkbenchPages pages(Skin skin, TextScale text, int width) {
		UiContext ctx = Workbench.context(Paths.get("review-assets"));
		ctx.setSkin(skin);
		ctx.setTextScale(text);
		WorkbenchPages p = new WorkbenchPages(ctx);
		p.shell.setContentWidth(width);
		return p;
	}

	static void visit(WorkbenchPages p, String id) {
		p.shell.navigator().navigate(id);
		Offscreen.size(p.shell, 720);
	}

	static void press(JComponent c, int x, int y) {
		c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
	}

	/** Offscreen there is no focus owner, so AWT would drop dispatched key events; call the handlers directly.
	 *  Real focused keyboard input is covered by DisplayTests under a display. */
	static void key(JComponent c, int code) {
		KeyEvent e = new KeyEvent(c, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
		for (java.awt.event.KeyListener l : c.getKeyListeners()) l.keyPressed(e);
	}

	static void run() {
		navigation();
		perPageState();
		selector();
		expandableAndFilter();
		progress();
		equipment();
		inventory();
		statesAndSkins();
		layout();
		scoped();
	}

	static void navigation() {
		WorkbenchPages p = pages(Skin.THEMED, TextScale.DEFAULT, 225);
		Navigator nav = p.shell.navigator();
		RaccoonShell s = p.shell;
		visit(p, WorkbenchPages.COMPONENTS);
		check("nav: Back disabled at start", !s.header().back().isEnabled());
		// UI-005 changed assertion (owner decision, roadmap #1): Tools is no longer on the rail.
		check("nav: rail has 3 workbench + 12 product buttons (Tools off the rail)", s.rail().buttonCount() == 15);
		check("nav: Tools stays registered and navigable but is not on the rail", s.rail().button("tools") == null && nav.navigate("tools") && nav.back());
		check("nav: Settings not duplicated in rail", s.rail().button(WorkbenchPages.SETTINGS) == null);
		check("nav: deferred GIM Hub not in rail", s.rail().button("gim") == null);
		check("nav: deferred GIM Hub cannot be opened", !nav.navigate("gim"));
		s.rail().button("home").doClick(0);
		check("nav: rail click opens Home", "home".equals(nav.current()));
		check("nav: header title follows destination", "Home".equals(s.header().title().text()));
		check("nav: Back enabled after navigating", s.header().back().isEnabled());
		check("nav: rail marks current destination", s.rail().button("home").isCurrent() && !s.rail().button(WorkbenchPages.COMPONENTS).isCurrent());
		check("nav: re-selecting current adds no history", !nav.navigate("home") && nav.historyDepth() == 1);
		s.header().settings().doClick(0);
		check("nav: header Settings opens Settings", WorkbenchPages.SETTINGS.equals(nav.current()));
		s.header().back().doClick(0);
		check("nav: Back returns to Home", "home".equals(nav.current()));
		s.header().back().doClick(0);
		check("nav: Back returns to Components", WorkbenchPages.COMPONENTS.equals(nav.current()));
		check("nav: Back disabled when history empty", !s.header().back().isEnabled() && !nav.back());
		Navigator big = new Navigator(WorkbenchPages.destinations());
		String[] ids = {"home", "outfitter"};
		for (int i = 0; i < 120; i++) big.navigate(ids[i % 2]);
		check("nav: history is bounded", big.historyDepth() == Navigator.HISTORY_LIMIT);
		boolean dup = false;
		try { new Navigator(Arrays.asList(new Destination("a", "A", "A", Destination.Group.PRODUCT, false), new Destination("a", "B", "B", Destination.Group.PRODUCT, false))); }
		catch (IllegalArgumentException e) { dup = true; }
		check("nav: duplicate destination ids rejected", dup);
	}

	static void perPageState() {
		WorkbenchPages p = pages(Skin.THEMED, TextScale.DEFAULT, 225);
		visit(p, WorkbenchPages.GEAR);
		PageHost gearHost = p.shell.currentHost();
		p.gearLoadoutSelector.setSelected(1);
		visit(p, WorkbenchPages.COMPONENTS);
		p.styleSelector.setSelected(2);
		p.shell.scrollPane().getViewport().setViewPosition(new Point(0, 300));
		visit(p, WorkbenchPages.GEAR);
		check("state: page instance kept across navigation", p.shell.currentHost() == gearHost);
		check("state: gear selection kept independently", p.gearLoadoutSelector.selected() == 1);
		check("state: other page selection unaffected", p.styleSelector.selected() == 2);
		check("state: gear page opens at its own scroll position", p.shell.scrollPane().getViewport().getViewPosition().y == 0);
		p.shell.header().back().doClick(0);
		Offscreen.size(p.shell, 720);
		check("state: Back restores scroll position", p.shell.scrollPane().getViewport().getViewPosition().y == 300);
	}

	static void selector() {
		WorkbenchPages p = pages(Skin.THEMED, TextScale.DEFAULT, 225);
		visit(p, WorkbenchPages.COMPONENTS);
		SegmentedSelector sel = p.styleSelector;
		Rectangle r = sel.cell(1, sel.getWidth());
		press(sel, r.x + r.width / 2, r.y + r.height / 2);
		check("selector: mouse press selects option", sel.selected() == 1);
		check("selector: listener updates live text", p.styleReadout.text().equals("Selected: Ranged"));
		key(sel, KeyEvent.VK_RIGHT);
		check("selector: Right arrow advances", sel.selected() == 2);
		key(sel, KeyEvent.VK_RIGHT);
		check("selector: Right arrow wraps", sel.selected() == 0);
		key(sel, KeyEvent.VK_END);
		check("selector: End selects last", sel.selected() == 2);
		key(sel, KeyEvent.VK_HOME);
		check("selector: Home selects first", sel.selected() == 0);
		check("selector: three options share a row at 225 Default", sel.columnsFor(sel.getWidth()) == 3);
		WorkbenchPages large = pages(Skin.THEMED, TextScale.LARGE, 225);
		visit(large, WorkbenchPages.COMPONENTS);
		check("selector: flows to more rows at 225 Large instead of clipping", large.styleSelector.columnsFor(large.styleSelector.getWidth()) < 3);
		visit(p, WorkbenchPages.STATES);
		check("selector: five states flow into rows at 225", p.statesSelector.columnsFor(p.statesSelector.getWidth()) < 5);
		ColumnsPanel cp = new ColumnsPanel(3, 4);
		cp.add(new com.feralenterprises.raccoon.ui.component.RButton(p.ctx, "A very long button label"));
		cp.add(new com.feralenterprises.raccoon.ui.component.RButton(p.ctx, "B"));
		check("columns: drop to one column when a label cannot fit", cp.columnsFor(225) == 1);
	}

	static void expandableAndFilter() {
		WorkbenchPages p = pages(Skin.THEMED, TextScale.DEFAULT, 225);
		visit(p, WorkbenchPages.COMPONENTS);
		int closed = StackLayout.heightFor(p.details, 200);
		key(p.details.summary(), KeyEvent.VK_ENTER);
		check("expandable: Enter expands", p.details.expanded());
		check("expandable: expanded is taller", StackLayout.heightFor(p.details, 200) > closed);
		key(p.details.summary(), KeyEvent.VK_SPACE);
		check("expandable: Space collapses", !p.details.expanded() && StackLayout.heightFor(p.details, 200) == closed);
		p.filter.field().setText("dragon");
		check("filter: substring matches two rows", p.filter.visibleCount() == 2 && !p.filter.showsNoMatches());
		p.filter.field().setText("DRAGON BOOTS");
		check("filter: case-insensitive", p.filter.visibleCount() == 1);
		p.filter.field().setText("zzz");
		check("filter: no matches shows empty message", p.filter.visibleCount() == 0 && p.filter.showsNoMatches());
		p.filter.field().setText("");
		check("filter: clearing restores all rows", p.filter.visibleCount() == 5 && !p.filter.showsNoMatches());
	}

	static void progress() {
		WorkbenchPages p = pages(Skin.THEMED, TextScale.DEFAULT, 225);
		visit(p, WorkbenchPages.COMPONENTS);
		ProgressBar b = p.progress;
		int inner = b.trough().width - 2;
		check("progress: 64% fills 64% of trough", b.fillWidth() == Math.round(inner * 0.64));
		p.advance.doClick(0);
		check("progress: dynamic update via read model", p.progressValue() == 730 && b.fillWidth() == Math.round(inner * 0.73));
		b.setModel(new ProgressView("x", 2000, 1000, DataState.ACTIVE, Evidence.NONE));
		check("progress: clamps above 100%", b.fillWidth() == inner);
		b.setModel(new ProgressView("x", -5, 1000, DataState.ACTIVE, Evidence.NONE));
		check("progress: clamps below 0%", b.fillWidth() == 0);
		b.setModel(new ProgressView("x", 500, 1000, DataState.UNAVAILABLE, Evidence.NONE));
		check("progress: unavailable stays empty and labelled", b.fillWidth() == 0 && b.valueText().equals("—"));
		b.setModel(new ProgressView("x", 500, 1000, DataState.LOADING, Evidence.NONE));
		check("progress: loading has no fill", b.fillWidth() == 0);
		b.setModel(new ProgressView("x", 500, 1000, DataState.STALE, Evidence.NONE));
		check("progress: stale keeps fill and says stale", b.fillWidth() == Math.round(inner * 0.5) && b.valueText().contains("stale"));
		b.setModel(new ProgressView("x", 5, 0, DataState.ACTIVE, Evidence.NONE));
		check("progress: zero target is not a division error", b.fillWidth() == 0);
	}

	static void equipment() {
		WorkbenchPages p = pages(Skin.THEMED, TextScale.DEFAULT, 225);
		visit(p, WorkbenchPages.GEAR);
		EquipmentGrid g = p.equipment;
		check("equipment: exactly 11 slots", EquipmentSlot.values().length == 11);
		Set<Rectangle> rects = new HashSet<>();
		for (EquipmentSlot s : EquipmentSlot.values()) rects.add(g.slotRect(s));
		check("equipment: 11 distinct slot positions", rects.size() == 11);
		int cx = g.slotRect(EquipmentSlot.HEAD).x;
		boolean column = true;
		for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.NECK, EquipmentSlot.BODY, EquipmentSlot.LEGS, EquipmentSlot.FEET})
			column &= g.slotRect(s).x == cx;
		check("equipment: head/neck/body/legs/feet share the centre column", column);
		check("equipment: cape/weapon/hands left, ammo/shield/ring right",
			g.slotRect(EquipmentSlot.CAPE).x < cx && g.slotRect(EquipmentSlot.WEAPON).x < cx && g.slotRect(EquipmentSlot.HANDS).x < cx
				&& g.slotRect(EquipmentSlot.AMMO).x > cx && g.slotRect(EquipmentSlot.SHIELD).x > cx && g.slotRect(EquipmentSlot.RING).x > cx);
		check("equipment: rows top-to-bottom head, neck, body, legs, feet",
			g.slotRect(EquipmentSlot.HEAD).y < g.slotRect(EquipmentSlot.NECK).y && g.slotRect(EquipmentSlot.NECK).y < g.slotRect(EquipmentSlot.BODY).y
				&& g.slotRect(EquipmentSlot.BODY).y < g.slotRect(EquipmentSlot.LEGS).y && g.slotRect(EquipmentSlot.LEGS).y < g.slotRect(EquipmentSlot.FEET).y);
		check("equipment: grid centred in the content width", Math.abs(g.slotRect(EquipmentSlot.CAPE).x - (g.getWidth() - g.slotRect(EquipmentSlot.RING).x - Tokens.EQUIP_SLOT)) <= 1);
		Offscreen.paint(g, 1.0);
		check("equipment: active fixture paints 5 real item images (glory has no fixture image)", g.lastPaintedItems() == 5);
		check("equipment: tooltip names the item", "Head: Helm of neitiznot".equals(g.getToolTipText(at(g, g.slotRect(EquipmentSlot.HEAD)))));
		check("equipment: tooltip for empty slot", "Ammo: empty".equals(g.getToolTipText(at(g, g.slotRect(EquipmentSlot.AMMO)))));
		p.gearLoadoutSelector.setSelected(1);
		Offscreen.paint(g, 1.0);
		check("equipment: dynamic update removes weapon", g.model().item(EquipmentSlot.WEAPON) == null && g.lastPaintedItems() == 4);
		for (DataState s : new DataState[]{DataState.EMPTY, DataState.LOADING, DataState.UNAVAILABLE}) {
			p.setGearState(s);
			Offscreen.paint(g, 1.0);
			check("equipment: " + s + " paints no items", g.lastPaintedItems() == 0);
		}
		p.setGearState(DataState.UNAVAILABLE);
		check("equipment: unavailable tooltip never says empty", g.getToolTipText(at(g, g.slotRect(EquipmentSlot.HEAD))).endsWith("Unavailable"));
		p.setGearState(DataState.STALE);
		p.gearLoadoutSelector.setSelected(0);
		Offscreen.paint(g, 1.0);
		check("equipment: stale still shows last known items", g.lastPaintedItems() == 5);
	}

	static MouseEvent at(JComponent c, Rectangle r) {
		return new MouseEvent(c, MouseEvent.MOUSE_MOVED, 0, 0, r.x + r.width / 2, r.y + r.height / 2, 0, false);
	}

	static void inventory() {
		WorkbenchPages p = pages(Skin.THEMED, TextScale.DEFAULT, 225);
		visit(p, WorkbenchPages.GEAR);
		InventoryGrid g = p.inventory;
		Set<Rectangle> rects = new HashSet<>();
		Set<Integer> xs = new HashSet<>(), ys = new HashSet<>();
		for (int i = 0; i < InventoryView.SLOTS; i++) { Rectangle r = g.slotRect(i); rects.add(r); xs.add(r.x); ys.add(r.y); }
		check("inventory: 28 distinct slots", rects.size() == 28);
		check("inventory: 4 columns x 7 rows", xs.size() == 4 && ys.size() == 7);
		check("inventory: slot 1 top-left, slot 4 top-right, slot 28 bottom-right",
			g.slotRect(0).x < g.slotRect(3).x && g.slotRect(0).y == g.slotRect(3).y && g.slotRect(27).x == g.slotRect(3).x && g.slotRect(27).y > g.slotRect(24).y - 1);
		check("inventory: native pitch 42 x 36", g.slotRect(1).x - g.slotRect(0).x == 42 && g.slotRect(4).y - g.slotRect(0).y == 36);
		Offscreen.paint(g, 1.0);
		check("inventory: paints 4 fixture images (coins has no fixture image)", g.lastPaintedItems() == 4);
		check("inventory: tooltip shows quantity", "Slot 5: Coins x 125000".equals(g.getToolTipText(at(g, g.slotRect(4)))));
		check("inventory: quantity text and colours follow native rules",
			ItemSlots.quantityText(99_999).equals("99999") && ItemSlots.quantityColor(99_999).equals(ItemSlots.QTY_YELLOW)
				&& ItemSlots.quantityText(125_000).equals("125K") && ItemSlots.quantityColor(125_000).equals(ItemSlots.QTY_WHITE)
				&& ItemSlots.quantityText(12_000_000).equals("12M") && ItemSlots.quantityColor(12_000_000).equals(ItemSlots.QTY_GREEN));
		boolean rejected = false;
		try { new InventoryView(DataState.ACTIVE, Arrays.asList(new ItemStack[29]), null); } catch (IllegalArgumentException e) { rejected = true; }
		check("inventory: read model rejects more than 28 slots", rejected);
	}

	static void statesAndSkins() {
		WorkbenchPages t = pages(Skin.THEMED, TextScale.DEFAULT, 225);
		WorkbenchPages s = pages(Skin.STRUCTURAL, TextScale.DEFAULT, 225);
		for (String id : new String[]{WorkbenchPages.COMPONENTS, WorkbenchPages.GEAR, WorkbenchPages.STATES}) {
			visit(t, id);
			visit(s, id);
			check("skins: identical layout height in themed and structural (" + id + ")",
				t.shell.currentHost().getPreferredSize().height == s.shell.currentHost().getPreferredSize().height);
		}
		visit(t, WorkbenchPages.COMPONENTS);
		int before = t.shell.currentHost().getPreferredSize().height;
		t.ctx.setTextScale(TextScale.LARGE);
		Offscreen.size(t.shell, 720);
		check("text: Large reflows the page taller", t.shell.currentHost().getPreferredSize().height > before);
		t.ctx.setSkin(Skin.STRUCTURAL);
		Offscreen.size(t.shell, 720);
		check("skins: live switch keeps viewport at content width", t.shell.scrollPane().getViewport().getWidth() == 225);
		visit(t, WorkbenchPages.STATES);
		for (int i = 0; i < DataState.values().length; i++) {
			t.statesSelector.setSelected(i);
			check("states: selector drives status " + DataState.values()[i], t.statesStatus.state() == DataState.values()[i]
				&& t.statesProgress.model().state() == DataState.values()[i] && t.statesEquipment.model().state() == DataState.values()[i]);
		}
		check("states: EMPTY and UNAVAILABLE are distinct labels", !DataState.EMPTY.label().equals(DataState.UNAVAILABLE.label()));
		check("states: stale evidence carries age", Fixtures.evidence(DataState.STALE).line().contains("ago"));
	}

	static void layout() {
		for (int w : Tokens.CONTENT_WIDTHS) for (TextScale ts : TextScale.values()) {
			WorkbenchPages p = pages(Skin.THEMED, ts, w);
			for (String id : new String[]{WorkbenchPages.COMPONENTS, WorkbenchPages.GEAR, WorkbenchPages.STATES, "home"}) {
				visit(p, id);
				PageHost h = p.shell.currentHost();
				String tag = w + "/" + ts + "/" + id;
				check("layout: viewport is exactly content width " + tag, p.shell.scrollPane().getViewport().getWidth() == w);
				// UI-003 owner correction (changed assertion): no separator chrome on the rail side.
				check("layout: shell width = rail + content + scrollbar + outer chrome, no separator " + tag,
					p.shell.getWidth() == Tokens.RAIL_WIDTH + w + Tokens.SCROLLBAR + Tokens.CHROME
						&& p.shell.columnX() == Tokens.RAIL_WIDTH && p.shell.leftChrome() == 0);
				check("layout: no overflow or clipping " + tag, LayoutAudit.audit(h).isEmpty());
				check("layout: background fills viewport height " + tag, h.getHeight() >= p.shell.scrollPane().getViewport().getHeight());
			}
		}
		WorkbenchPages p = pages(Skin.THEMED, TextScale.DEFAULT, 225);
		visit(p, WorkbenchPages.COMPONENTS);
		PageHost h = p.shell.currentHost();
		check("layout: footer art space reserved below final content", h.getPreferredSize().height == h.contentBottom() + h.footerReserve());
	}

	static void scoped() {
		ScopedState<int[]> st = new ScopedState<>(() -> new int[]{0});
		Object a = new Object(), b = new Object();
		st.get(a)[0] = 3;
		check("scope: state kept for same scope", st.get(a)[0] == 3);
		check("scope: state cleared on scope change", st.get(b)[0] == 0);
		check("scope: returning to old scope does not resurrect state", st.get(a)[0] == 0);
	}
}
