package com.feralenterprises.raccoon.ui.test;

import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.shell.Destination;
import com.feralenterprises.raccoon.ui.shell.Environment;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.Navigator;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.ui.shell.RouteAware;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.Skin;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import com.feralenterprises.raccoon.workbench.FixtureAssetProvider;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * R02 shared framework: typed route requests, local-detail Back, Back enablement, payload delivery on return,
 * per-route scroll restoration, history reset on scope change and the themed dropdown. Dependency-free main.
 */
public final class RouteTests {
	private static final List<String> failures = new ArrayList<>();
	private static int passed;

	public static void main(String[] args) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try { run(); } catch (Throwable t) { t.printStackTrace(); failures.add("exception: " + t); }
		});
		System.out.println("RouteTests: " + passed + " passed, " + failures.size() + " failed");
		for (String f : failures) System.out.println("FAIL " + f);
		System.exit(failures.isEmpty() ? 0 : 1);
	}

	static void check(String name, boolean ok) { if (ok) passed++; else failures.add(name); }

	static Navigator nav() {
		return new Navigator(Arrays.asList(
			new Destination("home", "Home", "HO", Destination.Group.PRODUCT, false),
			new Destination("outfit", "Outfitter", "OF", Destination.Group.PRODUCT, false),
			new Destination("chronicle", "Boss Chronicle", "BC", Destination.Group.PRODUCT, false),
			new Destination("gim", "GIM Hub", "GH", Destination.Group.PRODUCT, true),
			new Destination("settings", "Settings", "SE", Destination.Group.UTILITY, false)));
	}

	/** A page with one local detail level, recording what the shell delivers. */
	static final class DetailPage extends StackPanel implements RouteAware, LocalBack {
		final List<String> shown = new ArrayList<>();
		String selected;
		boolean detail;
		Runnable listener;
		DetailPage() { super(4); for (int i = 0; i < 60; i++) add(new JLabel("row " + i)); }
		@Override public void routeShown(RouteRequest r, boolean returning) {
			shown.add(r + (returning ? "<" : ""));
			if (!returning && r.hasParam()) { selected = r.paramId(); open(); }
		}
		void open() { detail = true; if (listener != null) listener.run(); }
		@Override public boolean hasLocalBack() { return detail; }
		@Override public void localBack() { detail = false; if (listener != null) listener.run(); }
		@Override public void setLocalBackListener(Runnable r) { listener = r; }
	}

	static void run() {
		// ---- RouteRequest ------------------------------------------------------------------------------------
		RouteRequest v = RouteRequest.of("outfit", RouteRequest.Param.ENCOUNTER, "vorkath");
		check("request: equal by route, kind and id", v.equals(RouteRequest.of("outfit", RouteRequest.Param.ENCOUNTER, "vorkath")));
		check("request: different id differs", !v.equals(RouteRequest.of("outfit", RouteRequest.Param.ENCOUNTER, "zulrah")));
		check("request: bare differs from payload", !v.equals(RouteRequest.of("outfit")));
		check("request: toString is readable", "outfit?encounter=vorkath".equals(v.toString()));
		boolean threw = false;
		try { RouteRequest.of("outfit", RouteRequest.Param.SESSION, ""); } catch (IllegalArgumentException e) { threw = true; }
		check("request: empty id refused", threw);

		// ---- Navigator ----------------------------------------------------------------------------------------
		Navigator n = nav();
		List<String> seen = new ArrayList<>();
		n.addListener(seen::add);
		check("nav: first bare navigation", n.navigate("home") && "home".equals(n.current()) && !n.canGoBack());
		check("nav: rail re-select is a no-op", !n.navigate("home") && n.historyDepth() == 0);
		check("nav: typed navigation pushes", n.navigate(v) && v.equals(n.currentRequest()) && n.historyDepth() == 1);
		check("nav: identical typed request is a no-op", !n.navigate(v) && n.historyDepth() == 1);
		check("nav: bare request for the shown route is a no-op", !n.navigate("outfit") && n.historyDepth() == 1);
		check("nav: same route, other payload pushes", n.navigate(RouteRequest.of("outfit", RouteRequest.Param.ENCOUNTER, "zulrah")) && n.historyDepth() == 2);
		check("nav: not returning after forward", !n.returning());
		check("nav: back restores previous payload", n.back() && v.equals(n.currentRequest()) && n.returning());
		check("nav: back to home", n.back() && "home".equals(n.current()) && !n.canGoBack());
		check("nav: deferred refused", !n.navigate(RouteRequest.of("gim")) && "home".equals(n.current()));
		threw = false;
		try { n.navigate(RouteRequest.of("nope")); } catch (IllegalArgumentException e) { threw = true; }
		check("nav: unknown route refused", threw);
		n.navigate(v);
		n.navigate("chronicle");
		n.resetHistory(RouteRequest.of("home"));
		check("nav: reset clears history and shows start", "home".equals(n.current()) && !n.canGoBack() && !n.returning());
		check("nav: listeners told each change", seen.equals(Arrays.asList("home", "outfit", "outfit", "outfit", "home", "outfit", "chronicle", "home")));
		for (int i = 0; i < Navigator.HISTORY_LIMIT + 10; i++) n.navigate(RouteRequest.of("outfit", RouteRequest.Param.ENCOUNTER, "e" + i));
		check("nav: history stays bounded", n.historyDepth() == Navigator.HISTORY_LIMIT);

		// ---- Shell: local Back, delivery, scroll ---------------------------------------------------------------
		UiContext ctx = new UiContext(new DefaultFontSource(), FixtureAssetProvider.empty());
		ctx.setSkin(Skin.STRUCTURAL);
		Navigator sn = nav();
		RaccoonShell shell = new RaccoonShell(ctx, sn);
		DetailPage outfit = new DetailPage();
		DetailPage home = new DetailPage();
		shell.register("home", Environment.PLAIN, () -> home);
		shell.register("outfit", Environment.PLAIN, () -> outfit);
		shell.setSize(263, 400);
		shell.doLayout();
		Object source = new Object();
		shell.attachAccountSource(source, null);
		shell.showAccount(source, "A:1");
		sn.navigate("home");
		check("shell: home shown, Back disabled", !shell.header().back().isEnabled() && !shell.canGoBack());
		check("shell: bare request delivered", home.shown.equals(Arrays.asList("home")));
		shell.scrollPane().validate();
		shell.scrollPane().getViewport().setViewPosition(new Point(0, 120));
		sn.navigate(v);
		check("shell: payload delivered to the target page", outfit.shown.equals(Arrays.asList("outfit?encounter=vorkath")) && "vorkath".equals(outfit.selected));
		check("shell: page opened its detail", outfit.detail && shell.header().back().isEnabled());
		check("shell: Back closes local detail first", shell.goBack() && !outfit.detail && "outfit".equals(sn.current()));
		check("shell: Back still enabled with history", shell.header().back().isEnabled());
		check("shell: second Back leaves the route", shell.goBack() && "home".equals(sn.current()));
		check("shell: home told it is a return", home.shown.equals(Arrays.asList("home", "home<")));
		check("shell: home scroll restored", shell.scrollPane().getViewport().getViewPosition().y == 120);
		check("shell: Back disabled at start", !shell.header().back().isEnabled() && !shell.goBack());
		sn.navigate(v);
		check("shell: forward delivery re-applies the payload", "vorkath".equals(outfit.selected) && outfit.detail);
		outfit.localBack();
		outfit.selected = "custom";
		sn.navigate("home");
		shell.goBack();
		check("shell: returning page keeps its own selection", "custom".equals(outfit.selected) && !outfit.detail);
		check("shell: return delivery flagged", outfit.shown.get(outfit.shown.size() - 1).endsWith("<"));
		outfit.open();
		check("shell: page-local change refreshes Back", shell.header().back().isEnabled());
		shell.header().back().doClick();
		check("shell: header Back button uses local Back", !outfit.detail && "outfit".equals(sn.current()));
		shell.dispose();
		check("shell: Back after dispose does nothing", !shell.goBack());

		// ---- Dropdown ------------------------------------------------------------------------------------------
		List<String> picked = new ArrayList<>();
		RDropdown<String> d = new RDropdown<>(ctx, "Encounter", Arrays.asList("vorkath", "zulrah", "scurrius"), s -> s.toUpperCase(java.util.Locale.ROOT), picked::add);
		check("dropdown: accessible name", "Encounter".equals(d.getAccessibleContext().getAccessibleName()));
		check("dropdown: first option selected, nothing reported", "vorkath".equals(d.selected()) && picked.isEmpty());
		check("dropdown: user selection reported once", d.select("zulrah") && picked.equals(Arrays.asList("zulrah")));
		check("dropdown: restore is silent", d.restore("scurrius") && picked.size() == 1 && "scurrius".equals(d.selected()));
		check("dropdown: unknown option refused", !d.select("nope") && "scurrius".equals(d.selected()));
		d.setOptions(Arrays.asList("brutus", "scurrius"));
		check("dropdown: options replaced, selection kept, silent", "scurrius".equals(d.selected()) && picked.size() == 1 && d.getItemCount() == 2);
		d.setOptions(Arrays.asList("obor"));
		check("dropdown: missing selection falls back to first, silent", "obor".equals(d.selected()) && picked.size() == 1);
		check("dropdown: intrinsic height", d.getPreferredSize().height == d.heightForWidth(225) && d.getPreferredSize().height < 40);
		check("dropdown: labels via function", "OBOR".equals(d.labelOf("obor")));
		threw = false;
		try { new RDropdown<>(ctx, " ", Arrays.asList("a"), s -> s, null); } catch (IllegalArgumentException e) { threw = true; }
		check("dropdown: accessible name required", threw);
		ctx.setSkin(Skin.THEMED);
		d.contextChanged();
		check("dropdown: restyles on context change", d.getFont().equals(ctx.font(com.feralenterprises.raccoon.ui.theme.FontRole.BODY)));
	}
}
