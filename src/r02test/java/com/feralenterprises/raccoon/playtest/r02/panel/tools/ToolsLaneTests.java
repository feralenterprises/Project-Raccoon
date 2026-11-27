package com.feralenterprises.raccoon.playtest.r02.panel.tools;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.ActivityObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.EncounterDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.ItemDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.panel.home.AdventurePanels;
import com.feralenterprises.raccoon.playtest.r02.panel.home.HomeInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.home.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Lane A Tools regressions, plus the whole-lane assembly: every Lane A route through {@code AdventurePanels} on the
 * accepted shared composition, with real typed joins between Lane A pages. Args: the accepted Lane Inputs ZIP, the
 * extracted frozen {@code Project-Raccoon-R02/}, optional evidence directory.
 */
public final class ToolsLaneTests {
	private static int checks, failures;
	private static final Instant T0 = TestFixtures.T0;

	public static void main(String[] args) throws Exception {
		if (args.length < 2) throw new IllegalArgumentException("Lane Inputs ZIP and extracted Project-Raccoon-R02 required");
		Path zip = Paths.get(args[0]), frozen = Paths.get(args[1]);
		Path evidence = args.length > 2 ? Paths.get(args[2]) : null;
		LaneInputs lane = LaneInputs.open(zip);
		ToolsInputs in = ToolsInputs.load(lane, frozen);
		List<String> labels = in.actions.stream().map(a -> a.label).collect(Collectors.toList());
		check("frozen Tools actions in order", labels.equals(Arrays.asList("Biggest Wins", "Bank Memory", "Quests & Diaries", "Boss Chronicle",
			"Account Capture", "Settings")));
		check("Biggest Wins is local; five typed routes", in.actions.get(0).route == null && in.actions.get(1).route == RouteId.BANK
			&& in.actions.get(2).route == RouteId.QUESTS && in.actions.get(3).route == RouteId.CHRONICLE && in.actions.get(4).route == RouteId.CAPTURE
			&& in.actions.get(5).route == RouteId.SETTINGS);
		check("frozen rail icons resolve for the four icon actions; Settings and Biggest Wins have none", in.actions.get(1).icon != null
			&& in.actions.get(2).icon != null && in.actions.get(3).icon != null && in.actions.get(4).icon != null
			&& in.actions.get(0).icon == null && in.actions.get(5).icon == null);
		BufferedImage capture = in.actions.get(4).icon;
		check("Account Capture uses the frozen runtime capture icon (raccoon and gear, 'rail:capture')", capture != null && capture.getWidth() > 1);
		Path dir = Files.createTempDirectory("tools-tamper");
		Files.write(dir.resolve("Project-Raccoon-R02.html"), "<html></html>".getBytes());
		boolean refused;
		try { ToolsInputs.load(lane, dir); refused = false; } catch (IOException e) { refused = e.getMessage().contains("SHA-256"); }
		check("a different frozen review HTML is refused", refused);

		List<RouteRegistration> all = AdventurePanels.registrations(zip, frozen);
		Set<RouteId> routes = all.stream().map(RouteRegistration::route).collect(Collectors.toCollection(() -> EnumSet.noneOf(RouteId.class)));
		check("AdventurePanels registers exactly the four Lane A routes, all owned by Adventure", routes.equals(Lane.ADVENTURE.routes())
			&& all.stream().allMatch(r -> r.owner().equals(Lane.ADVENTURE.name())));

		FrozenPackageAssets assets = new FrozenPackageAssets(frozen);
		SwingUtilities.invokeAndWait(() -> new Mounted(all, assets, evidence).run());
		System.out.println("Tools lane: " + (checks - failures) + "/" + checks + " checks passed");
		if (failures > 0) System.exit(1);
	}

	private static Registry registry() {
		Registry base = TestFixtures.registry();
		List<ItemDef> items = new ArrayList<>(base.items());
		items.add(new ItemDef(4728, "Guthan's platebody", true, "Gear"));
		List<EncounterDef> enc = new ArrayList<>(base.encounters());
		enc.add(new EncounterDef("barrows", "Barrows", "Regular", "Medium", "barrows"));
		return new Registry(items, enc);
	}

	private static ArchiveRecord record(String id, String activity, String encounter, int minutes, int units, String unit, long xp, int loot) {
		return new ArchiveRecord(id, activity, encounter, T0.minus(Duration.ofHours(3)), Duration.ofMinutes(minutes), units, unit,
			Evidence.verified(xp, T0, "saved"), loot == 0 ? Collections.emptyMap() : Collections.singletonMap(loot, 1L));
	}

	private static final class Mounted {
		private final UiContext ui;
		private final FixtureDispatcher fixtures;
		private final R02Composition comp;
		private final Path evidence;
		private int generation;

		Mounted(List<RouteRegistration> lanePages, FrozenPackageAssets assets, Path evidence) {
			this.evidence = evidence;
			ui = new UiContext(new DefaultFontSource(), new AssetProvider() {
				public BufferedImage art(ArtKey key) {
					String f = key.candidateFile();
					return f.matches("(PR|SIG)-\\d+_.*") ? assets.art(f.substring(0, f.indexOf('_'))) : null;
				}
				public BufferedImage nativeSprite(NativeSprite sprite, Runnable onLoaded) { return null; }
				public BufferedImage item(int itemId, int quantity, Runnable onLoaded) { return null; }
				public String provenance() { return "Headless Lane A"; }
			});
			fixtures = new FixtureDispatcher(registry(), TestFixtures.clock());
			List<RouteRegistration> pages = new ArrayList<>(lanePages);
			for (RouteId r : new RouteId[] {RouteId.BANK, RouteId.QUESTS, RouteId.CHRONICLE, RouteId.CAPTURE, RouteId.SETTINGS, RouteId.OUTFIT})
				pages.add(new RouteRegistration(r, Lane.owning(r).name(), c -> new JLabel(r.label() + " test receiver")));
			comp = new R02Composition(ui, fixtures, assets, pages);
			generation = fixtures.bind();
			comp.start(withWins(1));
		}

		private FixtureSnapshot withWins(long revision) {
			return FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(revision)
				.officialKc("scurrius", Evidence.count(12, T0, "kill log"))
				.archive(Evidence.verified(true, T0, "archive"), Arrays.asList(record("a1", "vorkath", "vorkath", 31, 12, "kills", 38_400, 0),
					record("a2", "barrows", "barrows", 47, 8, "chests", 12_600, 4728)))
				.activity(new ActivityObservation(ActivityObservation.Mode.REPLAY, "vorkath", "a1", T0, T0, 0, Evidence.unavailable())).build();
		}

		void run() {
			try {
				check("no Lane A route is left unregistered", Collections.disjoint(comp.unregisteredRoutes(), Lane.ADVENTURE.routes()));
				comp.navigator().navigate("tools");
				size(225);
				check("Tools mounts with illustration and Adventure tools card", named("tools-illustration") != null && named("tools-card") != null
					&& comp.navigator().current().equals("tools"));
				shot("01-tools.png");
				for (String[] a : new String[][] {{"tools-bank-memory", "bank"}, {"tools-quests-diaries", "quests"}, {"tools-boss-chronicle", "chronicle"},
					{"tools-account-capture", "capture"}, {"tools-settings", "settings"}}) {
					click(a[0]);
					check("Tools " + a[1] + " is a typed route link", comp.navigator().current().equals(a[1]));
					comp.shell().header().back().doClick();
					size(225);
				}
				click("tools-biggest-wins");
				LocalBack tools = page(ToolsPanel.class);
				check("Biggest Wins is a local Back view with the loot-backed Barrows notable", tools.hasLocalBack()
					&& text("tools-win").equals("Guthan's platebody · Barrows") && comp.navigator().current().equals("tools"));
				shot("02-tools-biggest-wins.png");
				click("tools-open-session");
				check("Open saved session joins the real Feral Files record by typed SESSION payload", comp.navigator().currentRequest()
					.equals(RouteRequest.of("files", RouteRequest.Param.SESSION, "a2")) && filesDetailOpen("Barrows"));
				comp.shell().header().back().doClick();
				size(225);
				check("first Back closes the Files record (local level first)", comp.navigator().current().equals("files") && !filesDetailOpen("Barrows"));
				comp.shell().header().back().doClick();
				size(225);
				check("Back from Files returns to the open Biggest Wins view", comp.navigator().current().equals("tools") && page(ToolsPanel.class).hasLocalBack());
				comp.shell().header().back().doClick();
				size(225);
				check("header Back closes Biggest Wins first", !page(ToolsPanel.class).hasLocalBack() && comp.navigator().current().equals("tools"));
				comp.navigator().navigate("home");
				size(225);
				JComponent saved = named("home-recent-a2");
				check("Home shows the Barrows saved session", saved != null);
				saved.dispatchEvent(new java.awt.event.MouseEvent(saved, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false));
				size(225);
				check("Home → Feral Files typed SESSION join opens the same record", comp.navigator().currentRequest()
					.equals(RouteRequest.of("files", RouteRequest.Param.SESSION, "a2")) && filesDetailOpen("Barrows"));
				comp.navigator().navigate("home");
				size(225);
				JComponent replay = named("home-recent-activity");
				replay.dispatchEvent(new java.awt.event.MouseEvent(replay, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false));
				size(225);
				check("Home → Current Activity join shows the labelled Vorkath replay", comp.navigator().current().equals("activity")
					&& accessible("activity-status").equals("Saved session replay, simulated"));
				layout();
				comp.navigator().navigate("tools");
				size(225);
				click("tools-biggest-wins");
				check("a stale same-scope revision is refused and the open wins view keeps current evidence",
					!fixtures.publish(TestFixtures.demoA(generation, 1)) && text("tools-win").equals("Guthan's platebody · Barrows"));
				check("a newer same-scope revision without the notable refreshes the open view: wins unavailable",
					fixtures.publish(TestFixtures.demoA(generation, 5)) && size() && page(ToolsPanel.class).hasLocalBack()
					&& text("tools-wins-none").equals("Verified wins unavailable."));
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.demoB(generation, 1));
				comp.navigator().navigate("tools");
				size(225);
				check("a new scope discards the open wins view", !page(ToolsPanel.class).hasLocalBack());
				click("tools-biggest-wins");
				check("account B: wins unavailable", text("tools-wins-none").equals("Verified wins unavailable."));
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.signedOut(generation, 2));
				comp.navigator().navigate("tools");
				size(225);
				click("tools-biggest-wins");
				check("signed out: wins unavailable", text("tools-wins-none").equals("Verified wins unavailable."));
			} finally { comp.dispose(); }
		}

		private boolean filesDetailOpen(String title) {
			JComponent hero = named("files-detail-hero");
			return hero != null && hero.getAccessibleContext().getAccessibleName().startsWith(title + ", ");
		}

		private void layout() {
			for (TextScale scale : TextScale.values()) {
				ui.setTextScale(scale);
				for (boolean wins : new boolean[] {false, true}) {
					comp.navigator().navigate("tools");
					LocalBack t = page(ToolsPanel.class);
					if (t.hasLocalBack()) t.localBack();
					if (wins) click("tools-biggest-wins");
					for (int width : new int[] {225, 200}) {
						size(width);
						List<String> issues = new ArrayList<>();
						audit((java.awt.Container) page(ToolsPanel.class), issues);
						check(scale + (wins ? " wins" : " tools") + " at " + width + "px: no clipped or overflowing component " + issues, issues.isEmpty());
					}
				}
			}
			ui.setTextScale(TextScale.LARGE);
			LocalBack t = page(ToolsPanel.class);
			if (t.hasLocalBack()) t.localBack();
			size(225);
			shot("03-tools-large-text.png");
			ui.setTextScale(TextScale.DEFAULT);
		}

		private <T> T page(Class<T> type) { return find(comp.shell(), type); }

		@SuppressWarnings("unchecked")
		private static <T> T find(Component c, Class<T> type) {
			if (type.isInstance(c)) return (T) c;
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
				T t = find(k, type);
				if (t != null) return t;
			}
			return null;
		}

		private boolean size() { size(225); return true; }

		private void size(int width) {
			comp.shell().setSize(width + 43, 900);
			layoutAll(comp.shell());
			layoutAll(comp.shell());
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
			return c instanceof com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Label
				? ((com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Label) c).text() : "<missing " + name + ">";
		}

		private String accessible(String name) {
			JComponent c = named(name);
			return c == null ? "<missing " + name + ">" : String.valueOf(c.getAccessibleContext().getAccessibleName());
		}

		private void click(String name) {
			JComponent c = named(name);
			if (c == null) throw new AssertionError("missing " + name);
			((AbstractButton) c).doClick();
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
