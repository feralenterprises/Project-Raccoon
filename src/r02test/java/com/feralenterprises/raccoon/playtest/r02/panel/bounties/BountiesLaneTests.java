package com.feralenterprises.raccoon.playtest.r02.panel.bounties;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.QuestState;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.PathfinderData;
import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.PathfinderPanels;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.SearchField;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Lane D Bandit's Bounties regressions on the accepted shared composition (simulated accounts, no RuneLite), mounted with
 * the real Lane D Pathfinder as the unmet-requirement receiver. Args: the accepted Lane Inputs ZIP, the extracted frozen
 * {@code Project-Raccoon-R02/}, optional evidence directory.
 */
public final class BountiesLaneTests {
	private static int checks, failures;
	private static final Instant T0 = TestFixtures.T0;

	public static void main(String[] args) throws Exception {
		Path zip = Paths.get(args[0]), frozen = Paths.get(args[1]);
		Path evidence = args.length > 2 ? Paths.get(args[2]) : null;
		LaneInputs lane = LaneInputs.open(zip);
		PathfinderData pf = PathfinderData.load(lane);
		BountiesData data = BountiesData.load(lane);
		data(data, pf);
		model(data);
		FrozenPackageAssets assets = new FrozenPackageAssets(frozen);
		SwingUtilities.invokeAndWait(() -> new Mounted(pf, data, assets, evidence).run());
		System.out.println("Bandit's Bounties lane: " + (checks - failures) + "/" + checks + " checks passed");
		if (failures > 0) System.exit(1);
	}

	private static void data(BountiesData d, PathfinderData pf) {
		BountiesData.Entry dk = entry(d, "Dagannoth_Rex"), vork = entry(d, "Vorkath"), barrows = entry(d, "Dharok_the_Wretched");
		check("11 archived bounties in board order", d.entries.size() == 11 && d.entries.get(0).key.equals("Giant_Mole") && d.entries.get(10).key.equals("Brutus"));
		check("example weapon from the Weapon slot (not Ammo): Vorkath rune crossbow, Barrows air battlestaff",
			vork.exampleWeapon == 9185 && barrows.exampleWeapon == 1397 && "dagannoth-kings".equals(dk.outfitKey) && d.itemName(9185).equals("Rune crossbow"));
		check("every gate quest resolves to a Pathfinder quest slug", d.entries.stream().filter(e -> e.gate != null)
			.allMatch(e -> pf.questBySlug(PathfinderData.slug(e.gate)) != null && pf.questBySlug(PathfinderData.slug(e.gate)).name.equals(e.gate)));
	}

	private static BountiesData.Entry entry(BountiesData d, String key) { return d.entries.stream().filter(e -> e.key.equals(key)).findFirst().get(); }

	private static Map<String, BountiesModel.Readiness> states(BountiesModel m) {
		Map<String, BountiesModel.Readiness> out = new HashMap<>();
		for (BountiesModel.Result r : m.results) out.put(r.entry.key, r.readiness);
		return out;
	}

	private static FixtureSnapshot.Builder a() { return FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(1).revision(1); }

	private static Map<Integer, Long> items(int id, long n) { Map<Integer, Long> m = new HashMap<>(); m.put(id, n); return m; }

	private static void model(BountiesData d) {
		BountiesModel a = BountiesModel.of(TestFixtures.demoA(1, 1), d);
		Map<String, BountiesModel.Readiness> s = states(a);
		check("nothing is ever Ready or Past this stage (no equipment or progression evidence)", a.results.stream()
			.noneMatch(r -> r.readiness == BountiesModel.Readiness.READY || r.readiness == BountiesModel.Readiness.OUTGROWN || r.readiness == BountiesModel.Readiness.NOT_READY));
		check("C-03 demo A: Brutus locked, The Ides of Milk is incomplete", s.get("Brutus") == BountiesModel.Readiness.LOCKED
			&& a.result("Brutus").reason.equals("The Ides of Milk is incomplete."));
		check("demo A: DS II complete, rune crossbow verified absent: Vorkath within reach with unknown equipment", s.get("Vorkath") == BountiesModel.Readiness.REACH
			&& a.result("Vorkath").reason.equals("Rune crossbow is absent from the observed bank. Equipped and carried items are unknown."));
		check("demo A: unverified gates are Unverified, never locked or open", s.get("Phantom_Muspah") == BountiesModel.Readiness.UNKNOWN
			&& a.result("Zulrah").reason.equals("Regicide completion is unverified."));
		BountiesModel b = BountiesModel.of(TestFixtures.demoB(1, 1), d);
		check("C-01 demo B: Vorkath locked by its own DS II; Brutus unverified (no Ides evidence)", states(b).get("Vorkath") == BountiesModel.Readiness.LOCKED
			&& states(b).get("Brutus") == BountiesModel.Readiness.UNKNOWN && b.result("Brutus").reason.equals("The Ides of Milk completion is unverified."));
		check("demo B verified-empty bank: ungated example weapons within reach", states(b).get("Scurrius") == BountiesModel.Readiness.REACH);
		BountiesModel held = BountiesModel.of(a().quest("Dragon Slayer II", Evidence.verified(QuestState.COMPLETE, T0, "t"))
			.container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "t", items(9185, 1))).build(), d);
		check("weapon present is still not Ready: requirements incomplete", states(held).get("Vorkath") == BountiesModel.Readiness.UNKNOWN
			&& held.result("Vorkath").reason.equals("Account equipment, access or supply requirements are incomplete."));
		BountiesModel hist = BountiesModel.of(a().container(new ContainerObservation(Container.BANK, ContainerState.HISTORICAL, T0, "t", Collections.emptyMap())).build(), d);
		check("historical bank: a current bank observation is required", hist.result("Scurrius").reason.equals("A current bank observation is required to check gear readiness."));
		BountiesModel inc = BountiesModel.of(a().container(new ContainerObservation(Container.BANK, ContainerState.INCOMPLETE, T0, "t", Collections.emptyMap())).build(), d);
		check("incomplete bank: missing weapon is not absence", states(inc).get("Scurrius") == BountiesModel.Readiness.UNKNOWN);
		BountiesModel none = BountiesModel.of(a().build(), d);
		check("no bank observation: unverified", states(none).get("Giant_Mole") == BountiesModel.Readiness.UNKNOWN);
		BountiesModel out = BountiesModel.of(TestFixtures.signedOut(1, 2), d);
		check("signed out: every bounty unverified, account unavailable", out.accountLine.equals("Account unavailable")
			&& out.results.stream().allMatch(r -> r.readiness == BountiesModel.Readiness.UNKNOWN && r.reason.equals("Account evidence unavailable.")));
		check("filter and search", a.matches(BountiesModel.Readiness.LOCKED, "brut").size() == 1 && a.matches(null, "dagannoth").size() == 3
			&& a.matches(BountiesModel.Readiness.READY, "").isEmpty());
	}

	private static final class Mounted {
		private final UiContext ui;
		private final FixtureDispatcher fixtures;
		private final R02Composition comp;
		private final Path evidence;
		private int generation;

		Mounted(PathfinderData pf, BountiesData data, FrozenPackageAssets assets, Path evidence) {
			this.evidence = evidence;
			ui = new UiContext(new DefaultFontSource(), new AssetProvider() {
				public BufferedImage art(ArtKey key) {
					String f = key.candidateFile();
					return f.matches("(PR|SIG)-\\d+_.*") ? assets.art(f.substring(0, f.indexOf('_'))) : null;
				}
				public BufferedImage nativeSprite(NativeSprite sprite, Runnable onLoaded) { return null; }
				public BufferedImage item(int itemId, int quantity, Runnable onLoaded) { return null; }
				public String provenance() { return "Headless Lane D"; }
			});
			fixtures = new FixtureDispatcher(TestFixtures.registry(), TestFixtures.clock());
			List<RouteRegistration> pages = new ArrayList<>();
			pages.add(BountiesPanels.registration(data));
			pages.add(PathfinderPanels.registration(pf));
			pages.add(new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), c -> new JLabel("Home test receiver")));
			pages.add(new RouteRegistration(RouteId.CHRONICLE, Lane.COMBAT.name(), c -> new JLabel("Chronicle test receiver")));
			pages.add(new RouteRegistration(RouteId.OUTFIT, Lane.COMBAT.name(), c -> new JLabel("Outfitter test receiver")));
			comp = new R02Composition(ui, fixtures, assets, pages);
			generation = fixtures.bind();
			comp.start(TestFixtures.demoA(generation, 1));
		}

		void run() {
			try {
				comp.navigator().navigate("bounties");
				size(225);
				BountiesPanel p = page();
				check("composition mounts Lane D Bandit's Bounties", p != null && comp.navigator().current().equals("bounties")
					&& text("bb-account").equals("Account A · Offline"));
				check("groups in frozen order; Past this stage absent", named("bb-group-REACH") != null && named("bb-group-LOCKED") != null
					&& named("bb-group-UNKNOWN") != null && named("bb-group-READY") == null && named("bb-group-OUTGROWN") == null);
				shot("01-bounties.png");
				@SuppressWarnings("unchecked")
				RDropdown<String> f = (RDropdown<String>) named("bb-filter");
				f.select("LOCKED");
				size(225);
				((SearchField) named("bb-search")).setText("Brutus");
				size(225);
				check("frozen flow: Locked filter and search Brutus", named("bb-row-Brutus") != null && named("bb-group-UNKNOWN") == null);
				((RButton) named("bb-row-Brutus")).doClick();
				size(225);
				check("C-03 detail: Locked, The Ides of Milk is incomplete; local Back level", page().hasLocalBack() && text("bb-state").equals("Locked")
					&& text("bb-reason").equals("The Ides of Milk is incomplete.") && named("bb-access") != null);
				shot("02-bounty-detail.png");
				((RButton) named("bb-access")).doClick();
				size(225);
				check("C-03 assembly: unmet requirement opens Pathfinder's typed quest context, verified Not started",
					comp.navigator().currentRequest().equals(RouteRequest.of("pathfinder", RouteRequest.Param.MILESTONE, "the-ides-of-milk"))
					&& text("pf-quest-name").equals("The Ides of Milk") && text("pf-quest-state").equals("Quest state: Not started"));
				comp.shell().header().back().doClick();
				size(225);
				check("Back returns to the bounty detail", comp.navigator().current().equals("bounties") && text("bb-reason").equals("The Ides of Milk is incomplete."));
				comp.shell().header().back().doClick();
				size(225);
				@SuppressWarnings("unchecked")
				RDropdown<String> f2 = (RDropdown<String>) named("bb-filter");
				check("Back again: board with Locked filter and search kept", f2 != null && "LOCKED".equals(f2.selected())
					&& ((SearchField) named("bb-search")).getText().equals("Brutus"));
				((RButton) named("bb-row-Brutus")).doClick();
				size(225);
				((RButton) named("bb-chronicle")).doClick();
				size(225);
				check("Open Boss Chronicle sends a typed ENCOUNTER", comp.navigator().currentRequest().equals(RouteRequest.of("chronicle", RouteRequest.Param.ENCOUNTER, "brutus")));
				comp.shell().header().back().doClick();
				size(225);
				((RButton) named("bb-outfit")).doClick();
				size(225);
				check("Open Outfitter sends a typed ENCOUNTER", comp.navigator().currentRequest().equals(RouteRequest.of("outfit", RouteRequest.Param.ENCOUNTER, "brutus")));
				comp.shell().header().back().doClick();
				size(225);
				comp.shell().header().back().doClick();
				size(225);
				((SearchField) named("bb-search")).setText("nothing-is-named-this");
				size(225);
				check("empty filter result", text("bb-empty").equals("No bounties match these filters."));
				((SearchField) named("bb-search")).setText("");
				filter().select("all");
				size(225);
				((SearchField) named("bb-search")).setText("Barrows");
				size(225);
				((RButton) named("bb-row-Dharok_the_Wretched")).doClick();
				size(225);
				check("encounter without a shared registry entry offers no Chronicle or Outfitter route", named("bb-chronicle") == null && named("bb-outfit") == null
					&& text("bb-state").equals("Within reach"));
				comp.shell().header().back().doClick();
				((SearchField) named("bb-search")).setText("");
				size(225);
				FixtureSnapshot more = FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(2)
					.quest("The Ides of Milk", Evidence.verified(QuestState.COMPLETE, T0, "x")).build();
				check("same-scope revision re-evaluates in place: Brutus no longer locked", fixtures.publish(more) && page() == p && size(225)
					&& String.valueOf(named("bb-row-Brutus").getAccessibleContext().getAccessibleName()).equals("Brutus, Unverified"));
				layout();
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.demoB(generation, 1));
				comp.navigator().navigate("bounties");
				size(225);
				check("new scope discards filters and detail; B evaluates its own evidence", page() != p && !page().hasLocalBack()
					&& text("bb-account").equals("Account B · Offline")
					&& String.valueOf(named("bb-row-Vorkath").getAccessibleContext().getAccessibleName()).equals("Vorkath, Locked"));
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.signedOut(generation, 2));
				comp.navigator().navigate("bounties");
				size(225);
				check("signed out: account unavailable, all unverified", text("bb-account").equals("Account unavailable") && named("bb-group-REACH") == null);
			} finally { comp.dispose(); }
		}

		@SuppressWarnings("unchecked")
		private RDropdown<String> filter() { return (RDropdown<String>) named("bb-filter"); }

		private void layout() {
			for (TextScale scale : TextScale.values()) {
				ui.setTextScale(scale);
				for (String view : new String[] {"board", "detail"}) {
					if (view.equals("detail")) { ((RButton) named("bb-row-Vorkath")).doClick(); ((Expandable) named("bb-reference")).setExpanded(true); }
					for (int width : new int[] {225, 200}) {
						size(width);
						List<String> issues = new ArrayList<>();
						audit(page(), issues);
						check(scale + " " + view + " at " + width + "px: no clipped or overflowing component " + issues.subList(0, Math.min(3, issues.size())), issues.isEmpty());
					}
					if (view.equals("detail")) {
						if (scale == TextScale.LARGE) shot("03-bounty-detail-large-text.png");
						page().localBack();
					}
				}
			}
			ui.setTextScale(TextScale.DEFAULT);
		}

		private BountiesPanel page() { return findPage(comp.shell()); }

		private static BountiesPanel findPage(Component c) {
			if (c instanceof BountiesPanel) return (BountiesPanel) c;
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
				BountiesPanel p = findPage(k);
				if (p != null) return p;
			}
			return null;
		}

		private boolean size(int width) {
			comp.shell().setSize(width + 43, 900);
			layoutAll(comp.shell());
			layoutAll(comp.shell());
			return true;
		}

		private static void layoutAll(java.awt.Container parent) {
			parent.doLayout();
			for (Component child : parent.getComponents()) if (child instanceof java.awt.Container) layoutAll((java.awt.Container) child);
		}

		private JComponent named(String name) { return named(comp.shell(), name); }

		private static JComponent named(Component c, String name) {
			if (!c.isVisible()) return null;
			if (name.equals(c.getName()) && c instanceof JComponent) return (JComponent) c;
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
				JComponent j = named(k, name);
				if (j != null) return j;
			}
			return null;
		}

		private String text(String name) {
			JComponent c = named(name);
			return c instanceof TextBlock ? ((TextBlock) c).text() : "<missing " + name + ">";
		}

		private String accessible(String name) {
			JComponent c = named(name);
			return c == null ? "<missing " + name + ">" : String.valueOf(c.getAccessibleContext().getAccessibleName());
		}

		private void activate(JComponent c) {
			if (c == null) throw new AssertionError("missing row");
			c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false));
			size(225);
		}

		private static void audit(java.awt.Container c, List<String> issues) {
			for (Component child : c.getComponents()) {
				if (!child.isVisible()) continue;
				if (child.getX() < 0 || child.getX() + child.getWidth() > c.getWidth() + 1) issues.add(child.getName() + " overflows " + c.getName());
				if (child instanceof HeightForWidth && child.getWidth() > 0) {
					HeightForWidth h = (HeightForWidth) child;
					if (child.getHeight() + 1 < h.heightForWidth(child.getWidth())) issues.add(child.getName() + " clipped vertically");
					if (h.minimumContentWidth() > child.getWidth() + 1) issues.add(child.getName() + " narrower than its content");
				}
				if (child instanceof java.awt.Container) audit((java.awt.Container) child, issues);
			}
		}

		private void shot(String name) {
			if (evidence == null) return;
			size(225);
			BufferedImage image = new BufferedImage(comp.shell().getWidth(), comp.shell().getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			try {
				comp.shell().printAll(g);
				Files.createDirectories(evidence);
				ImageIO.write(image, "png", evidence.resolve(name).toFile());
			} catch (IOException e) {
				throw new AssertionError(e);
			} finally { g.dispose(); }
		}
	}

	private static void check(String name, boolean ok) {
		checks++;
		if (!ok) failures++;
		System.out.println((ok ? "  PASS " : "  FAIL ") + name);
	}
}
