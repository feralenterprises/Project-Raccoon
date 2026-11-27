package com.feralenterprises.raccoon.ui.test;

import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.component.EquipmentGrid;
import com.feralenterprises.raccoon.ui.shell.Destination;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.ui.shell.ShellGeometry;
import com.feralenterprises.raccoon.ui.shell.ShellHeader;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.Skin;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import com.feralenterprises.raccoon.workbench.FixtureAssetProvider;
import com.feralenterprises.raccoon.workbench.LayoutAudit;
import com.feralenterprises.raccoon.workbench.Offscreen;
import com.feralenterprises.raccoon.workbench.WorkbenchPages;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/**
 * UI-005 adaptive-scaling and compact-header checks (headless, SIMULATED: logical sizes and Java2D transforms on
 * Linux, not Windows DPI). Writes build/evidence/ui-005/scaling-matrix.txt and fractional-scale screenshots.
 *
 * <p>Verified RuneLite facts the matrix is built on (sources pinned in docs/REPORT-UI-005.md):
 * <ul>
 *   <li>Launcher (runelite/launcher 6102d01): default is Windows DPI-unaware scaling; the optional "scale" setting
 *       sets sun.java2d.dpiaware=true and sun.java2d.uiScale. Either way Swing lays out in logical pixels =
 *       physical / scale.</li>
 *   <li>RuneLiteLAF disables FlatLaf UI scaling (flatlaf.uiScale.enabled=false).</li>
 *   <li>Sidebar fonts are FontManager's fixed 16-size RuneScape TTFs; RuneLiteConfig font options (dynamic overlay,
 *       tooltip, interface, infobox: REGULAR/BOLD/SMALL) style in-game overlays, not side panels. The only sidebar
 *       text-size variable is therefore our own TextScale (DEFAULT/LARGE).</li>
 *   <li>The game area cannot go below Constants.GAME_FIXED_HEIGHT (503), so the sidebar is at least ~503 tall; on
 *       screens whose work area is smaller the window extends past the screen.</li>
 * </ul>
 */
public final class AdaptiveTests {
	private static final List<String> failures = new ArrayList<>();
	private static int passed;
	static final int TASKBAR = 48, TITLE_BAR = 31, GAME_MIN_H = 503;  // logical px; taskbar/title bar are estimates
	static final File OUT = new File("build/evidence/ui-005");

	public static void main(String[] args) throws Exception {
		OUT.mkdirs();
		SwingUtilities.invokeAndWait(() -> {
			try {
				auditSelfTest();
				emptyShellBackground();
				header();
				railCues();
				hoverNeverUsesSelectedSprite();
				matrix();
				fractionalPaint();
			} catch (Throwable t) {
				t.printStackTrace();
				failures.add("exception: " + t);
			}
		});
		System.out.println("AdaptiveTests: " + passed + " passed, " + failures.size() + " failed");
		failures.stream().limit(40).forEach(f -> System.out.println("  FAIL " + f));
		System.exit(failures.isEmpty() ? 0 : 1);
	}

	static void check(String name, boolean ok) { if (ok) passed++; else failures.add(name); }

	static FixtureAssetProvider assets() {
		return new FixtureAssetProvider(Paths.get("review-assets/art"), Paths.get("review-assets/item-fixtures"));
	}

	static WorkbenchPages pages(TextScale ts, Skin sk) {
		UiContext c = new UiContext(new DefaultFontSource(), assets());
		c.setSkin(sk);
		c.setTextScale(ts);
		return new WorkbenchPages(c);
	}

	/** The new audits must detect what they claim to (no vacuous passes). */
	static void auditSelfTest() {
		javax.swing.JPanel box = new javax.swing.JPanel(null);
		javax.swing.JLabel a = new javax.swing.JLabel("a"), b = new javax.swing.JLabel("b");
		box.add(a);
		box.add(b);
		box.setSize(100, 40);
		a.setBounds(0, 0, 60, 20);
		b.setBounds(50, 0, 40, 20);
		List<String> issues = new ArrayList<>();
		LayoutAudit.overlaps(box, issues);
		check("audit self-test: overlapping siblings are reported", issues.stream().anyMatch(i -> i.startsWith("OVERLAP")));
		WorkbenchPages p = pages(TextScale.DEFAULT, Skin.THEMED);
		Offscreen.size(p.shell, 268, 600);
		ShellHeader h = p.shell.header();
		h.settings().setBounds(h.getWidth() - 10, 2, 22, 22);
		check("audit self-test: a control pushed outside the header is reported",
			LayoutAudit.headerAccess(h).stream().anyMatch(i -> i.startsWith("INACCESSIBLE")));
	}

	/** UI-005 defect: an un-navigated shell painted Swing's default grey viewport (seen in the rail-cue screenshot). */
	static void emptyShellBackground() {
		for (Skin sk : Skin.values()) {
			WorkbenchPages p = pages(TextScale.DEFAULT, sk);
			Offscreen.size(p.shell, 268, 300);
			BufferedImage img = Offscreen.paint(p.shell, 1.0);
			java.awt.Color bg = p.ctx.palette().background;
			JViewport vp = p.shell.scrollPane().getViewport();
			Rectangle r = SwingUtilities.convertRectangle(vp.getParent(), vp.getBounds(), p.shell);
			int off = 0, n = 0;
			for (int y = r.y + 2; y < r.y + r.height - 2; y += 3) for (int x = r.x + 2; x < r.x + r.width - 2; x += 3) {
				n++;
				if ((img.getRGB(x, y) & 0xffffff) != (bg.getRGB() & 0xffffff)) off++;
			}
			check("empty shell " + sk + ": viewport shows the page surface before first navigation (" + off + "/" + n + " differ)", n > 0 && off == 0);
		}
	}

	// ---- compact header ---------------------------------------------------------------------------------------

	static void header() {
		String[] titles = {"Home", "Bandit's Bounties", "A deliberately long destination title that must wrap rather than clip"};
		for (int host : new int[]{200, 225, 242, 268, 330}) for (TextScale ts : TextScale.values()) for (Skin sk : Skin.values()) {
			WorkbenchPages p = pages(ts, sk);
			p.shell.navigator().navigate("home");
			for (String t : titles) {
				p.shell.header().title().setText(t);
				Offscreen.size(p.shell, host, 720);
				ShellHeader h = p.shell.header();
				String tag = host + "/" + ts + "/" + sk + "/" + t.length();
				Rectangle back = h.back().getBounds(), set = h.settings().getBounds(), title = h.title().getBounds();
				check("header " + tag + ": Settings is the rightmost control", set.x + set.width == h.getWidth() - 4);
				check("header " + tag + ": Back is adjacent to Settings", set.x - (back.x + back.width) >= 0 && set.x - (back.x + back.width) <= 4
					&& back.y == set.y);
				check("header " + tag + ": title is left of Back", title.x + title.width <= back.x && title.x < back.x);
				if (h.menu().isVisible())
					check("header " + tag + ": menu is left of the title", h.menu().getX() + h.menu().getWidth() <= title.x);
				List<String> issues = LayoutAudit.headerAccess(h);
				check("header " + tag + ": controls accessible, no overlap or clipping " + issues, issues.isEmpty());
				check("header " + tag + ": title fully shown (wraps, never clips)", h.title().getHeight() >= h.title().heightForWidth(h.title().getWidth()));
				if (t.equals("Home") && ts == TextScale.DEFAULT)
					check("header " + tag + ": compact height <= 30 px (" + h.getHeight() + ")", h.getHeight() <= 30);
			}
		}
	}

	// ---- rail overflow cues -----------------------------------------------------------------------------------

	static void railCues() {
		for (Skin sk : Skin.values()) {
			WorkbenchPages p = pages(TextScale.DEFAULT, sk);
			RaccoonShell s = p.shell;
			Offscreen.size(s, 268, 1200);
			check("cues " + sk + ": none when every destination fits", !s.railUpCue().isVisible() && !s.railDownCue().isVisible());
			Offscreen.size(s, 268, 300);
			JViewport vp = s.railScrollPane().getViewport();
			try { Offscreen.write(Offscreen.paint(s, 2.0), new File(OUT, "rail-cues-" + sk.name().toLowerCase() + "-268x300-x2.png")); }
			catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
			check("cues " + sk + ": short rail shows only the down cue", s.railDownCue().isVisible() && !s.railUpCue().isVisible());
			check("cues " + sk + ": cues are labelled", "Scroll destinations down".equals(s.railDownCue().getAccessibleContext().getAccessibleName()));
			int guard = 0;
			while (s.railDownCue().isVisible() && guard++ < 50) s.railDownCue().doClick(0);
			check("cues " + sk + ": down cue reaches the end", vp.getViewPosition().y == s.rail().getHeight() - vp.getHeight());
			check("cues " + sk + ": at the end only the up cue shows", s.railUpCue().isVisible() && !s.railDownCue().isVisible());
			Component last = s.rail().getComponent(s.rail().getComponentCount() - 1);
			check("cues " + sk + ": last destination fully in view at the end", vp.getViewRect().contains(last.getBounds()));
			while (s.railUpCue().isVisible() && guard++ < 100) s.railUpCue().doClick(0);
			check("cues " + sk + ": up cue returns to the top", vp.getViewPosition().y == 0 && !s.railUpCue().isVisible());
			check("cues " + sk + ": rail still takes no scrollbar width", s.railScrollPane().getVerticalScrollBar().getWidth() == 0);
		}
	}

	// ---- UI-004 defect regression -----------------------------------------------------------------------------

	/** UI-004 found sprite 179 is a red "selected" tile; hover must never request it (permanent regression). */
	static void hoverNeverUsesSelectedSprite() {
		Set<NativeSprite> requested = EnumSet.noneOf(NativeSprite.class);
		AssetProvider spy = new AssetProvider() {
			final FixtureAssetProvider base = assets();
			@Override public BufferedImage art(ArtKey key) { return base.art(key); }
			@Override public BufferedImage nativeSprite(NativeSprite sprite, Runnable onLoaded) {
				requested.add(sprite);
				return new BufferedImage(36, 36, BufferedImage.TYPE_INT_ARGB);
			}
			@Override public BufferedImage item(int itemId, int quantity, Runnable onLoaded) { return base.item(itemId, quantity, onLoaded); }
			@Override public String provenance() { return "spy"; }
		};
		UiContext c = new UiContext(new DefaultFontSource(), spy);
		WorkbenchPages p = new WorkbenchPages(c);
		p.shell.navigator().navigate(WorkbenchPages.GEAR);
		Offscreen.size(p.shell, 268, 900);
		EquipmentGrid g = p.equipment;
		Offscreen.paint(p.shell, 1.0);
		check("hover: grid painted native sprites", !requested.isEmpty());
		requested.clear();
		for (int y = 2; y < g.getHeight(); y += 6) for (int x = 2; x < g.getWidth(); x += 6) {
			g.dispatchEvent(new MouseEvent(g, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
			BufferedImage img = new BufferedImage(Math.max(1, g.getWidth()), Math.max(1, g.getHeight()), BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g2 = img.createGraphics();
			g.printAll(g2);
			g2.dispose();
		}
		check("hover: EQUIP_SLOT_SELECTED (179) never requested while hovering " + requested,
			!requested.contains(NativeSprite.EQUIP_SLOT_SELECTED));
	}

	// ---- resolution x Windows scale x host width x text -------------------------------------------------------

	static void matrix() throws Exception {
		int[][] screens = {{1920, 1080}, {2560, 1440}, {3840, 2160}};
		int[] scales = {100, 125, 150, 175, 200};
		int[] hosts = {242, 268};
		try (PrintWriter w = new PrintWriter(new File(OUT, "scaling-matrix.txt"), "UTF-8")) {
			w.println("UI-005 adaptive scaling matrix (SIMULATED: logical layout on Linux/Java2D; not Windows/RuneLite).");
			w.println("logical = physical / scale. visibleH = logicalH - taskbar(" + TASKBAR + ") - title bar(" + TITLE_BAR
				+ ") [estimates]. panelH = max(visibleH, " + GAME_MIN_H + ") (game min height). If panelH > visibleH the window"
				+ " extends past the screen: Windows check required.");
			w.println("Each cell runs every navigable destination at host widths 242 (rail -> header menu) and 268 (rail) x text"
				+ " DEFAULT/LARGE x skin THEMED/STRUCTURAL, at both panelH and visibleH.");
			w.println();
			w.printf("%-10s %-6s %-11s %-8s %-7s %-7s %-8s %s%n", "screen", "scale", "logical", "visibleH", "panelH", "issues", "result", "note");
			for (int[] sc : screens) for (int pct : scales) {
				int lw = (int) Math.floor(sc[0] * 100.0 / pct), lh = (int) Math.floor(sc[1] * 100.0 / pct);
				int visible = lh - TASKBAR - TITLE_BAR, panel = Math.max(visible, GAME_MIN_H);
				int before = failures.size();
				for (int host : hosts) for (TextScale ts : TextScale.values()) for (Skin sk : Skin.values())
					for (int h : panel == visible ? new int[]{panel} : new int[]{panel, visible})
						cell(sc[0] + "x" + sc[1] + "@" + pct + "/" + host + "/" + ts + "/" + sk + "/h" + h, host, h, ts, sk);
				int issues = failures.size() - before;
				String note = panel > visible ? "window taller than work area by " + (panel - visible) + " px: BLOCKED (Windows)" : "";
				w.printf("%-10s %-6s %-11s %-8d %-7d %-7d %-8s %s%n", sc[0] + "x" + sc[1], pct + "%", lw + "x" + lh, visible, panel, issues,
					issues == 0 ? "PASS*" : "FAIL", note);
			}
			w.println();
			w.println("PASS* = simulated pass. RuneLite sidebar font size: N/A (no RuneLite setting; fixed 16-size RuneScape TTFs).");
			w.println("Fractional scales (125/150/175 %) under the launcher 'scale' option: render transform checked in fractionalPaint;");
			w.println("Windows DPI-unaware bitmap stretching and real monitor DPI: BLOCKED until owner-authorized Windows testing.");
		}
	}

	static void cell(String tag, int host, int height, TextScale ts, Skin sk) {
		WorkbenchPages p = pages(ts, sk);
		RaccoonShell s = p.shell;
		for (Destination d : s.navigator().destinations()) {
			if (d.deferred()) continue;
			s.navigator().navigate(d.id());
			Offscreen.size(s, host, height);
			String t = tag + "/" + d.id();
			ShellGeometry.Layout g = s.geometry();
			List<String> issues = new ArrayList<>(LayoutAudit.audit(s.currentHost()));
			issues.addAll(LayoutAudit.headerAccess(s.header()));
			check(t + ": no clipping, overlap, overflow or inaccessible control " + issues, issues.isEmpty());
			JViewport vp = s.scrollPane().getViewport();
			check(t + ": no horizontal overflow", vp.getView().getWidth() <= vp.getWidth());
			if (vp.getView().getHeight() > vp.getHeight())
				check(t + ": tall content is reachable by the vertical scrollbar", s.scrollPane().getVerticalScrollBar().isVisible());
			if (g.railVisible) {
				JViewport rv = s.railScrollPane().getViewport();
				boolean overflow = s.rail().getHeight() > rv.getHeight();
				check(t + ": rail overflow has a visible cue", !overflow || s.railDownCue().isVisible() || s.railUpCue().isVisible());
			} else check(t + ": rail hidden -> destinations menu visible", s.header().menu().isVisible());
		}
		s.dispose();
	}

	// ---- fractional render scales ------------------------------------------------------------------------------

	static void fractionalPaint() throws Exception {
		for (double scale : new double[]{1.0, 1.25, 1.5, 1.75, 2.0}) for (int host : new int[]{242, 268}) {
			WorkbenchPages p = pages(TextScale.DEFAULT, Skin.THEMED);
			p.shell.navigator().navigate(WorkbenchPages.GEAR);
			Offscreen.size(p.shell, host, GAME_MIN_H);
			BufferedImage img = Offscreen.paint(p.shell, scale);
			// Offscreen.paint rounds the image up; a final partially covered row/column at a non-integer device size is a
			// harness edge, not an unpainted region, so the check covers the fully covered device pixels.
			int gaps = 0, fw = (int) Math.floor(host * scale), fh = (int) Math.floor(p.shell.getHeight() * scale);
			for (int y = 0; y < fh; y++) for (int x = 0; x < fw; x++)
				if ((img.getRGB(x, y) & 0xffffff) == 0xff00ff) gaps++;
			String tag = "paint x" + scale + " host " + host;
			check(tag + ": image is the scaled logical size", img.getWidth() == (int) Math.ceil(host * scale));
			check(tag + ": continuous background, no unpainted pixels (" + gaps + ")", gaps == 0);
			Offscreen.write(img, new File(OUT, "gear-" + host + "-x" + scale + ".png"));
			p.shell.dispose();
		}
	}

}
