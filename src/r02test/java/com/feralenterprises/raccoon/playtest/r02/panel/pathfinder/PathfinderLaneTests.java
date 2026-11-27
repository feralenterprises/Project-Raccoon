package com.feralenterprises.raccoon.playtest.r02.panel.pathfinder;

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
import com.feralenterprises.raccoon.playtest.r02.fixture.SkillObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.SegmentedSelector;
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
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Lane D Pathfinder regressions on the accepted shared composition (simulated accounts, no RuneLite). Args: the accepted
 * Lane Inputs ZIP, the extracted frozen {@code Project-Raccoon-R02/}, optional evidence directory.
 */
public final class PathfinderLaneTests {
	private static int checks, failures;
	private static final Instant T0 = TestFixtures.T0;

	public static void main(String[] args) throws Exception {
		Path zip = Paths.get(args[0]), frozen = Paths.get(args[1]);
		Path evidence = args.length > 2 ? Paths.get(args[2]) : null;
		LaneInputs lane = LaneInputs.open(zip);
		check("lane-input archive verifies (" + lane.verifiedCount() + " files)", lane.verifiedCount() == 939);
		PathfinderData data = PathfinderData.load(lane);
		check("13 grouped milestones and 215 catalogue quests", data.nodes.size() == 13 && data.quests.size() == 215);
		check("quest milestone slugs match Boss Chronicle's typed ids", data.questBySlug("the-ides-of-milk").name.equals("The Ides of Milk")
			&& data.questBySlug("dragon-slayer-ii").name.equals("Dragon Slayer II") && data.questBySlug("song-of-the-elves") != null);
		model(data);
		FrozenPackageAssets assets = new FrozenPackageAssets(frozen);
		SwingUtilities.invokeAndWait(() -> new Mounted(data, assets, evidence).run());
		System.out.println("Pathfinder lane: " + (checks - failures) + "/" + checks + " checks passed");
		if (failures > 0) System.exit(1);
	}

	private static FixtureSnapshot.Builder a() { return FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(1).revision(1); }

	private static Evidence<QuestState> q(QuestState s) { return Evidence.verified(s, T0, "test"); }

	/** Frozen "seed" scenario: Song of the Elves complete. */
	static FixtureSnapshot.Builder seed(FixtureSnapshot.Builder b) { return b.quest("Song of the Elves", q(QuestState.COMPLETE)); }

	private static String states(PathfinderModel m) {
		StringBuilder s = new StringBuilder();
		for (PathfinderData.Node n : m.data.nodes) s.append(m.state(n.key) == PathfinderModel.State.CURRENT ? '*' : m.state(n.key).name().charAt(0));
		return s.toString();
	}

	private static void model(PathfinderData data) {
		PathfinderModel seed = PathfinderModel.of(seed(a()).build(), data);
		check("C-02 Song of the Elves complete: 9/13 · 69%, seed current (frozen screenshot)", seed.step == 9 && seed.overallText().equals("9/13 · 69%")
			&& seed.progressText().equals("69% (9/13)") && seed.nextTitle().equals("Enhanced crystal weapon seed") && seed.nextSubtitle().equals("View milestone"));
		check("seed states: nine complete, seed current, shards and method available, bow blocked", states(seed).equals("CCCCCCCCC*AAB"));
		PathfinderModel quest = PathfinderModel.of(TestFixtures.demoA(1, 1), data);
		check("C-02 Song of the Elves in progress (demo A): 8/13, the quest is current", quest.step == 8 && quest.state("sote") == PathfinderModel.State.CURRENT
			&& quest.nextSubtitle().equals("Complete the quest") && quest.state("seed") == PathfinderModel.State.BLOCKED && quest.percent().equals("62%"));
		PathfinderModel bow = PathfinderModel.of(seed(a()).container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "x",
			Collections.singletonMap(25865, 1L))).build(), data);
		check("bow verified in a current bank: goal complete 13/13", bow.step == 13 && bow.nextTitle().equals("Goal complete") && bow.current() == null);
		PathfinderModel histBow = PathfinderModel.of(seed(a()).container(new ContainerObservation(Container.BANK, ContainerState.HISTORICAL, T0, "x",
			Collections.singletonMap(25865, 1L))).build(), data);
		check("bow only in a historical bank is not verified now", histBow.step == 9);
		PathfinderModel unknown = PathfinderModel.of(a().build(), data);
		check("C-02 no quest evidence: every milestone Unknown, never unmet", unknown.step == null && states(unknown).equals("UUUUUUUUUUUUU")
			&& unknown.nextTitle().equals("Evidence unavailable") && unknown.overallText().equals("Unknown"));
		PathfinderModel b = PathfinderModel.of(TestFixtures.demoB(1, 1), data);
		check("C-02 account B: Song of the Elves unknown, route Unknown", b.step == null && b.questState("Song of the Elves") == PathfinderModel.QuestLabel.UNKNOWN);
		PathfinderModel stale = PathfinderModel.of(a().quest("Song of the Elves", Evidence.stale(QuestState.COMPLETE, T0, "x")).build(), data);
		check("stale quest evidence is unverified", stale.step == null);
		PathfinderModel chain = PathfinderModel.of(a().quest("Plague City", q(QuestState.COMPLETE)).quest("Biohazard", q(QuestState.NOT_STARTED)).build(), data);
		check("chain: Plague City complete and Biohazard not started gives 1/13", chain.step == 1 && chain.state("bio") == PathfinderModel.State.CURRENT);
		PathfinderModel gap = PathfinderModel.of(a().quest("Plague City", q(QuestState.COMPLETE)).build(), data);
		check("chain: unknown frontier quest stays Unknown (not unmet)", gap.step == null);
		PathfinderModel implied = PathfinderModel.of(a().quest("Regicide", q(QuestState.COMPLETE)).quest("Roving Elves", q(QuestState.IN_PROGRESS)).build(), data);
		check("chain: a later completion implies the earlier chain", implied.step == 4 && implied.state("plague") == PathfinderModel.State.COMPLETE);
		PathfinderModel grouped = PathfinderModel.of(a().quest("Mourning’s End Part II", q(QuestState.COMPLETE)).quest("Song of the Elves", q(QuestState.NOT_STARTED)).build(), data);
		check("grouped requirements cannot be verified from quest evidence: Unknown", grouped.step == null);
		PathfinderModel skills = PathfinderModel.of(a().skill("agility", new SkillObservation(Evidence.verified(75, T0, "x"), Evidence.unavailable()))
			.skill("mining", new SkillObservation(Evidence.verified(60, T0, "x"), Evidence.unavailable())).build(), data);
		List<String[]> rows = skills.skillRows();
		check("Reqs: verified levels against 70, others Unknown (never demo-met)", rows.get(0)[1].equals("70 · Met (75)") && rows.get(5)[1].equals("70 · Level 60")
			&& rows.get(1)[1].equals("70 · Unknown"));
		check("C-03 The Ides of Milk: not started (demo A) vs unknown (unverified)",
			PathfinderModel.of(TestFixtures.demoA(1, 1), data).questState("The Ides of Milk") == PathfinderModel.QuestLabel.NOT_STARTED
			&& PathfinderModel.of(a().build(), data).questState("The Ides of Milk") == PathfinderModel.QuestLabel.UNKNOWN);
		check("signed out: Unknown", PathfinderModel.of(TestFixtures.signedOut(1, 2), data).step == null);
		check("compact route window (frozen): from Mourning’s End Part II, seven milestones", seed.compactRoute().get(0).key.equals("mourn2") && seed.compactRoute().size() == 7);
	}

	private static final class Mounted {
		private final UiContext ui;
		private final FixtureDispatcher fixtures;
		private final R02Composition comp;
		private final Path evidence;
		private int generation;

		Mounted(PathfinderData data, FrozenPackageAssets assets, Path evidence) {
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
			pages.add(PathfinderPanels.registration(data));
			pages.add(new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), c -> new JLabel("Home test receiver")));
			pages.add(new RouteRegistration(RouteId.CHRONICLE, Lane.COMBAT.name(), c -> new JLabel("Chronicle test sender")));
			comp = new R02Composition(ui, fixtures, assets, pages);
			generation = fixtures.bind();
			comp.start(seed(FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(1)).build());
		}

		void run() {
			try {
				comp.navigator().navigate("pathfinder");
				size(225);
				PathfinderPanel p = page();
				check("composition mounts Lane D Pathfinder", p != null && comp.navigator().current().equals("pathfinder"));
				check("frozen goal: 69% (9/13), next step the enhanced seed", text("pf-goal-progress").equals("69% (9/13)")
					&& ((RButton) named("pf-next")).getText().equals("Enhanced crystal weapon seed ›") && text("pf-overall-text").equals("9/13 · 69%"));
				check("compact route: show 6 earlier milestones, seed current", ((RButton) named("pf-expand")).getText().equals("Show 6 earlier milestones")
					&& accessible("pf-node-seed").equals("Enhanced crystal weapon seed, Current") && accessible("pf-node-bow").equals("Create Bow of Faerdhinen, Blocked"));
				shot("01-pathfinder.png");
				click("pf-expand");
				check("expanded route lists all thirteen milestones", named("pf-node-plague") != null && ((RButton) named("pf-expand")).getText().equals("Compact route"));
				activate("pf-node-bow");
				check("milestone detail is a local Back level with dependency states", page().hasLocalBack() && text("pf-detail-state").equals("Blocked · account evidence")
					&& text("pf-dep-seed").equals("Enhanced crystal weapon seed: Current"));
				shot("02-pathfinder-detail.png");
				comp.shell().header().back().doClick();
				size(225);
				check("header Back returns to the route", named("pf-detail") == null && named("pf-route") != null && comp.navigator().current().equals("pathfinder"));
				((SegmentedSelector) named("pf-tabs")).setSelected(1);
				size(225);
				check("Reqs tab: skill evidence unknown here, never demo-met", text("pf-req-agility").equals("Agility: 70 · Unknown")
					&& text("pf-req-seed").equals("Enhanced seed: Current"));
				((SegmentedSelector) named("pf-tabs")).setSelected(2);
				size(225);
				((SegmentedSelector) named("pf-method")).setSelected(1);
				size(225);
				check("Paths: NPC assistance is a planning choice only", text("pf-path-detail").startsWith("150 shards total") && page().model().step == 9);
				comp.shell().header().back().doClick();
				size(225);
				check("Back from another tab returns to Map first", named("pf-route") != null && comp.navigator().current().equals("pathfinder"));

				comp.navigator().navigate("chronicle");
				comp.navigator().navigate(RouteRequest.of("pathfinder", RouteRequest.Param.MILESTONE, "the-ides-of-milk"));
				size(225);
				check("C-03 typed MILESTONE from Chronicle opens the quest context: unknown here, not unmet", text("pf-quest-name").equals("The Ides of Milk")
					&& text("pf-quest-state").equals("Quest state: Unknown") && text("pf-quest-access").startsWith("Requirement unverified."));
				comp.shell().header().back().doClick();
				size(225);
				check("Back closes the quest context, staying on Pathfinder", named("pf-quest-context") == null && named("pf-goal") != null);
				comp.navigator().navigate(RouteRequest.of("pathfinder", RouteRequest.Param.MILESTONE, "seed"));
				size(225);
				check("typed MILESTONE for a route node opens its detail", text("pf-detail-state").equals("Current · account evidence"));
				comp.shell().header().back().doClick();
				size(225);
				comp.navigator().navigate(RouteRequest.of("pathfinder", RouteRequest.Param.MILESTONE, "no-such-quest"));
				size(225);
				check("unknown MILESTONE id opens nothing", named("pf-detail") == null && named("pf-quest-context") == null);
				check("same-scope revision keeps view state", fixtures.publish(seed(FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(2))
					.container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "x", Collections.singletonMap(25865, 1L))).build())
					&& page() == p && size(225) && text("pf-overall-text").equals("13/13 · 100%"));
				check("an older revision is refused", !fixtures.publish(seed(FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(1)).build()));
				layout();

				generation = fixtures.bind();
				fixtures.publish(TestFixtures.demoB(generation, 1));
				comp.navigator().navigate("pathfinder");
				size(225);
				check("new scope discards the page; account B: everything Unknown", page() != p && text("pf-overall-text").equals("Unknown")
					&& accessible("pf-node-sote").endsWith("Unknown") && text("pf-legend").endsWith("Unknown"));
				shot("04-pathfinder-unknown.png");
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.demoA(generation, 1));
				comp.navigator().navigate(RouteRequest.of("pathfinder", RouteRequest.Param.MILESTONE, "the-ides-of-milk"));
				size(225);
				check("C-03 demo A: The Ides of Milk not started, requirement not met", text("pf-quest-state").equals("Quest state: Not started")
					&& text("pf-quest-access").equals("Requirement not met: the quest is not complete."));
				shot("03-pathfinder-quest-context.png");
				comp.shell().header().back().doClick();
				size(225);
				check("demo A: Song of the Elves in progress, 8/13", text("pf-overall-text").equals("8/13 · 62%"));
			} finally { comp.dispose(); }
		}

		private void layout() {
			for (TextScale scale : TextScale.values()) {
				ui.setTextScale(scale);
				for (int t = 0; t < 4; t++) {
					((SegmentedSelector) named("pf-tabs")).setSelected(t);
					for (int width : new int[] {225, 200}) {
						size(width);
						List<String> issues = new ArrayList<>();
						audit(page(), issues);
						check(scale + " tab " + t + " at " + width + "px: no clipped or overflowing component " + issues, issues.isEmpty());
					}
				}
			}
			ui.setTextScale(TextScale.LARGE);
			((SegmentedSelector) named("pf-tabs")).setSelected(0);
			size(225);
			shot("05-pathfinder-large-text.png");
			ui.setTextScale(TextScale.DEFAULT);
		}

		private PathfinderPanel page() { return find(comp.shell()); }

		private static PathfinderPanel find(Component c) {
			if (c instanceof PathfinderPanel) return (PathfinderPanel) c;
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
				PathfinderPanel p = find(k);
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

		private void click(String name) {
			((RButton) named(name)).doClick();
			size(225);
		}

		private void activate(String name) {
			JComponent c = named(name);
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
