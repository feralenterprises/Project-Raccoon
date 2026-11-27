package com.feralenterprises.raccoon.workbench;

import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.shell.PageHost;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.ui.theme.Skin;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Headless evidence: renders the real shell and pages at every width / text / skin combination, audits overflow and
 * clipping, measures geometry, and writes PNGs plus geometry.csv and audit.txt. Exit code 1 if any issue is found.
 * Usage: java -cp raccoon-ui-workbench.jar com.feralenterprises.raccoon.workbench.Evidence OUT_DIR [ASSETS_DIR]
 */
public final class Evidence {
	private static final String[] PAGES = {WorkbenchPages.COMPONENTS, WorkbenchPages.GEAR, WorkbenchPages.STATES, "home",
		WorkbenchPages.SETTINGS};
	private static final int SHELL_HEIGHT = 720;

	public static void main(String[] args) throws Exception {
		File out = new File(args.length > 0 ? args[0] : "evidence");
		Path assets = args.length > 1 ? Paths.get(args[1]) : Workbench.assetsDir(new String[0]);
		int[] issues = {0};
		SwingUtilities.invokeAndWait(() -> {
			try {
				issues[0] = run(out, assets);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		System.out.println(issues[0] == 0 ? "EVIDENCE PASS: no overflow or clipping" : "EVIDENCE FAIL: " + issues[0] + " issue(s)");
		System.exit(issues[0] == 0 ? 0 : 1);
	}

	private static int run(File out, Path assets) throws Exception {
		List<String> audit = new ArrayList<>();
		int issueCount = 0;
		try (PrintWriter geo = new PrintWriter(new File(mk(out), "geometry.csv"), StandardCharsets.UTF_8.name())) {
			geo.println("width,text,skin,page,shell_w,rail_w,chrome_l,viewport_w,scrollbar_w,chrome_r,viewport_h,page_h,content_bottom,footer_reserve,fills_viewport,issues");
			for (int width : Tokens.CONTENT_WIDTHS) for (TextScale text : TextScale.values()) for (Skin skin : Skin.values()) {
				UiContext ctx = Workbench.context(assets);
				ctx.setSkin(skin);
				ctx.setTextScale(text);
				WorkbenchPages pages = new WorkbenchPages(ctx);
				RaccoonShell shell = pages.shell;
				shell.setContentWidth(width);
				String tag = width + "-" + text.name().toLowerCase() + "-" + skin.name().toLowerCase();
				for (String page : PAGES) {
					shell.navigator().navigate(page);
					Offscreen.size(shell, SHELL_HEIGHT);
					PageHost host = shell.currentHost();
					List<String> found = LayoutAudit.audit(host);
					int railW = shell.getComponent(0).getWidth();
					int vpW = shell.scrollPane().getViewport().getWidth();
					int sbW = shell.scrollPane().getVerticalScrollBar().getWidth();
					int vpH = shell.scrollPane().getViewport().getHeight();
					if (vpW != width) found.add("VIEWPORT width " + vpW + " != " + width);
					if (host.getWidth() != vpW) found.add("HOST width " + host.getWidth() + " != viewport " + vpW);
					boolean fills = host.getHeight() >= vpH;
					if (!fills) found.add("BACKGROUND does not fill viewport");
					if (host.contentBottom() + host.footerReserve() > Math.max(host.getHeight(), host.getPreferredSize().height))
						found.add("FOOTER overlaps content");
					geo.println(String.join(",", "" + width, text.name(), skin.name(), page, "" + shell.getWidth(), "" + railW,
						"" + shell.leftChrome(), "" + vpW, "" + sbW, "" + Tokens.CHROME, "" + vpH, "" + host.getPreferredSize().height,
						"" + host.contentBottom(), "" + host.footerReserve(), "" + fills, "" + found.size()));
					for (String f : found) audit.add(tag + " " + page + ": " + f);
					issueCount += found.size();
					if (!page.equals("home") && !page.equals(WorkbenchPages.SETTINGS) || width == 225)
						Offscreen.write(Offscreen.fullPage(host), new File(out, "pages/" + tag + "/" + page + "-full.png"));
					Offscreen.write(Offscreen.paint(shell, 1.0), new File(out, "shell/" + tag + "/" + page + ".png"));
				}
			}
		}
		// Host widths: how the shell adapts to the width a RuneLite sidebar actually gives it (pending native measurement).
		try (PrintWriter hg = new PrintWriter(new File(out, "host-geometry.csv"), StandardCharsets.UTF_8.name())) {
			hg.println("host_w,text,skin,page,rail,rail_w,chrome_rail_side,chrome_outer,scrollbar,viewport_w,menu_visible,issues");
			for (int host : new int[]{225, 242, 263, 268, 275, 306, 313, 330}) for (TextScale text : TextScale.values()) for (Skin skin : Skin.values()) {
				UiContext ctx = Workbench.context(assets);
				ctx.setSkin(skin);
				ctx.setTextScale(text);
				WorkbenchPages pages = new WorkbenchPages(ctx);
				RaccoonShell shell = pages.shell;
				for (String page : new String[]{WorkbenchPages.COMPONENTS, WorkbenchPages.GEAR}) {
					shell.navigator().navigate(page);
					Offscreen.size(shell, host, SHELL_HEIGHT);
					com.feralenterprises.raccoon.ui.shell.ShellGeometry.Layout g = shell.geometry();
					List<String> found = LayoutAudit.audit(shell.currentHost());
					found.addAll(LayoutAudit.audit(shell.header()));
					int vpW = shell.scrollPane().getViewport().getWidth();
					if (vpW != g.contentWidth) found.add("VIEWPORT " + vpW + " != geometry " + g.contentWidth);
					if (shell.header().menu().isVisible() == g.railVisible) found.add("MENU/RAIL duplicate or missing navigation");
					hg.println(host + "," + text + "," + skin + "," + page + "," + (g.railVisible ? "shown" : "menu") + "," + g.railWidth + ","
						+ g.chromeLeft + "," + g.chrome + "," + (g.scrollbarReserved ? "reserved" : "overlay") + "," + vpW + "," + shell.header().menu().isVisible() + "," + found.size());
					for (String f : found) audit.add("host " + host + " " + text + " " + skin + " " + page + ": " + f);
					issueCount += found.size();
					if (text == TextScale.DEFAULT || page.equals(WorkbenchPages.GEAR))
						Offscreen.write(Offscreen.paint(shell, 1.0), new File(out, "hosts/" + host + "-" + text.name().toLowerCase() + "-"
							+ skin.name().toLowerCase() + "-" + page + ".png"));
				}
			}
		}
		// Simulated 150 % scaling: same component tree painted through a 1.5x transform (Java2D's uiScale mechanism).
		for (TextScale text : TextScale.values()) for (Skin skin : Skin.values()) {
			UiContext ctx = Workbench.context(assets);
			ctx.setSkin(skin);
			ctx.setTextScale(text);
			WorkbenchPages pages = new WorkbenchPages(ctx);
			for (String page : new String[]{WorkbenchPages.COMPONENTS, WorkbenchPages.GEAR}) {
				pages.shell.navigator().navigate(page);
				Offscreen.size(pages.shell, SHELL_HEIGHT);
				Offscreen.write(Offscreen.paint(pages.shell, 1.5), new File(out, "scale150/225-" + text.name().toLowerCase()
					+ "-" + skin.name().toLowerCase() + "-" + page + ".png"));
			}
		}
		// Gear component in each state.
		for (Skin skin : Skin.values()) {
			UiContext ctx = Workbench.context(assets);
			ctx.setSkin(skin);
			WorkbenchPages pages = new WorkbenchPages(ctx);
			pages.shell.navigator().navigate(WorkbenchPages.GEAR);
			Offscreen.size(pages.shell, SHELL_HEIGHT);
			for (DataState s : DataState.values()) {
				pages.setGearState(s);
				Offscreen.size(pages.shell, SHELL_HEIGHT);
				Offscreen.write(Offscreen.fullPage(pages.shell.currentHost()),
					new File(out, "states/gear-" + s.name().toLowerCase() + "-" + skin.name().toLowerCase() + ".png"));
			}
		}
		// Degradation: no artwork and no item images at all.
		UiContext bare = new UiContext(new com.feralenterprises.raccoon.ui.theme.DefaultFontSource(), FixtureAssetProvider.empty());
		WorkbenchPages bp = new WorkbenchPages(bare);
		for (String page : new String[]{WorkbenchPages.COMPONENTS, WorkbenchPages.GEAR}) {
			bp.shell.navigator().navigate(page);
			Offscreen.size(bp.shell, SHELL_HEIGHT);
			List<String> found = LayoutAudit.audit(bp.shell.currentHost());
			for (String f : found) audit.add("no-assets " + page + ": " + f);
			issueCount += found.size();
			Offscreen.write(Offscreen.fullPage(bp.shell.currentHost()), new File(out, "no-assets/" + page + "-full.png"));
		}
		try (PrintWriter w = new PrintWriter(new File(out, "audit.txt"), StandardCharsets.UTF_8.name())) {
			w.println("Layout audit: overflow, vertical/horizontal clipping, viewport width, full-height background, footer.");
			w.println("Combinations: widths 225/263/280 x text DEFAULT/LARGE x skin THEMED/STRUCTURAL x 5 pages; host widths 225/242/263/268/275/306/313/330 x text x skin x 2 pages; no-assets.");
			w.println("Issues: " + issueCount);
			audit.forEach(w::println);
		}
		return issueCount;
	}

	private static File mk(File f) { f.mkdirs(); return f; }
}
