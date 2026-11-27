package com.feralenterprises.raccoon.playtest.r02.panel.quests;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.QuestState;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.PathfinderData;
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
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Lane D Quests & Diaries regressions on the accepted shared composition (simulated accounts, no RuneLite). Args: the
 * accepted Lane Inputs ZIP, the extracted frozen {@code Project-Raccoon-R02/}, optional evidence directory.
 */
public final class QuestsLaneTests {
	private static int checks, failures;
	private static final Instant T0 = TestFixtures.T0;

	public static void main(String[] args) throws Exception {
		Path zip = Paths.get(args[0]), frozen = Paths.get(args[1]);
		Path evidence = args.length > 2 ? Paths.get(args[2]) : null;
		LaneInputs lane = LaneInputs.open(zip);
		PathfinderData data = PathfinderData.load(lane);
		QuestsModel.Diaries diaries = new QuestsModel.Diaries(lane);
		check("48 archived diary tiers; Ardougne Easy has 10 tasks", diaries.tasks.size() == 48 && diaries.tasks.get("Ardougne Easy").size() == 10
			&& diaries.tasks.get("Ardougne Easy").get(0).other.equals("Rune Mysteries") && diaries.tasks.get("Ardougne Easy").get(1).skills.equals("Thieving 5"));
		model(data);
		FrozenPackageAssets assets = new FrozenPackageAssets(frozen);
		SwingUtilities.invokeAndWait(() -> new Mounted(data, assets, evidence).run());
		System.out.println("Quests & Diaries lane: " + (checks - failures) + "/" + checks + " checks passed");
		if (failures > 0) System.exit(1);
	}

	private static FixtureSnapshot.Builder a() { return FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(1).revision(1); }

	private static Evidence<QuestState> q(QuestState s) { return Evidence.verified(s, T0, "test"); }

	private static void model(PathfinderData data) {
		QuestsModel a = QuestsModel.of(TestFixtures.demoA(1, 1), data);
		check("demo A states from shared evidence only", a.quest("Dragon Slayer II") == QuestsModel.State.COMPLETE
			&& a.quest("Song of the Elves") == QuestsModel.State.PROGRESS && a.quest("The Ides of Milk") == QuestsModel.State.NOT_STARTED
			&& a.quest("Cook's Assistant") == QuestsModel.State.UNKNOWN);
		check("C-01 Dragon Slayer II: A complete, B not started", QuestsModel.of(TestFixtures.demoB(1, 1), data).quest("Dragon Slayer II") == QuestsModel.State.NOT_STARTED);
		check("C-06 demo A coverage partial, one verified complete (never a complete list)", a.coverage.equals("Partial quest observations")
			&& a.countText.equals("1 verified complete"));
		QuestsModel none = QuestsModel.of(a().build(), data);
		check("C-06 no quest observation: unknown coverage, no verified total", none.coverage.equals("Quest observations unknown") && none.countText.equals("No verified total")
			&& none.quest("Dragon Slayer II") == QuestsModel.State.UNKNOWN);
		QuestsModel out = QuestsModel.of(TestFixtures.signedOut(1, 2), data);
		check("C-06 signed out: unavailable, every state unknown", out.coverage.equals("Account evidence unavailable") && out.quest("Dragon Slayer II") == QuestsModel.State.UNKNOWN);
		check("stale evidence is unknown, never complete", QuestsModel.of(a().quest("Dragon Slayer II", Evidence.stale(QuestState.COMPLETE, T0, "x")).build(), data)
			.quest("Dragon Slayer II") == QuestsModel.State.UNKNOWN);
		check("status filter: complete is Dragon Slayer II only; not started is The Ides of Milk", a.quests("", "COMPLETE", "all").size() == 1
			&& a.quests("", "NOT_STARTED", "all").get(0).name.equals("The Ides of Milk") && a.quests("", "UNKNOWN", "all").size() == 212);
		check("type filter and search", a.quests("", "all", "miniquest").size() == 20 && a.quests("", "all", "subquest").size() == 10
			&& a.quests("elves", "all", "all").stream().anyMatch(x -> x.name.equals("Song of the Elves")));
		check("diaries without evidence: Unknown, tier count Unknown", a.diary("Ardougne", "Easy") == QuestsModel.State.UNKNOWN
			&& a.tierCount("Easy", Arrays.asList(QuestsModel.REGIONS)).equals("Unknown"));
		QuestsModel partial = QuestsModel.of(a().diary("Ardougne Easy", q(QuestState.COMPLETE)).build(), data);
		check("one verified diary tier: at least 1/12, never a full count", partial.diary("Ardougne", "Easy") == QuestsModel.State.COMPLETE
			&& partial.tierCount("Easy", Arrays.asList(QuestsModel.REGIONS)).equals("At least 1/12 complete"));
		FixtureSnapshot.Builder all = a();
		for (String r : QuestsModel.REGIONS) all.diary(r + " Easy", q(r.equals("Wilderness") ? QuestState.NOT_STARTED : QuestState.COMPLETE));
		check("all twelve verified: exact 11/12", QuestsModel.of(all.build(), data).tierCount("Easy", Arrays.asList(QuestsModel.REGIONS)).equals("11/12 complete"));
		check("region filter and search", a.regions("", "Desert").size() == 1 && a.regions("kour", "all").get(0).equals("Kourend & Kebos"));
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
			try { pages.add(QuestsPanels.registration(data)); } catch (IOException e) { throw new AssertionError(e); }
			pages.add(new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), c -> new JLabel("Home test receiver")));
			pages.add(new RouteRegistration(RouteId.PATHFINDER, Lane.PROGRESSION.name(), c -> new JLabel("Pathfinder test receiver")));
			comp = new R02Composition(ui, fixtures, assets, pages);
			generation = fixtures.bind();
			comp.start(TestFixtures.demoA(generation, 1));
		}

		void run() {
			try {
				comp.navigator().navigate("quests");
				size(225);
				QuestsPanel p = page();
				check("composition mounts Lane D Quests & Diaries", p != null && comp.navigator().current().equals("quests"));
				check("journal: 215 entries, partial coverage, one verified complete", text("qd-count").equals("215 of 215 archived entries · 1 verified complete")
					&& text("qd-coverage").equals("Partial quest observations") && accessible("qd-quest-1").equals("Learning the Ropes, Unknown"));
				shot("01-quests.png");
				((SearchField) named("qd-search")).setText("dragon slayer");
				size(225);
				check("search narrows the journal", text("qd-count").startsWith("2 of 215"));
				JComponent ds2 = named("qd-list").getComponentCount() > 0 ? (JComponent) find(comp.shell(), "Dragon Slayer II") : null;
				activate(ds2);
				check("quest detail is a local Back level with curated requirements", page().hasLocalBack() && text("qd-detail-state").equals("Complete")
					&& text("qd-req").equals("Skill requirements"));
				shot("02-quests-detail.png");
				comp.shell().header().back().doClick();
				size(225);
				check("header Back returns to the journal with the search kept", !page().hasLocalBack() && text("qd-count").startsWith("2 of 215")
					&& comp.navigator().current().equals("quests"));
				((SearchField) named("qd-search")).setText("song of the elves");
				size(225);
				activate((JComponent) find(comp.shell(), "Song of the Elves"));
				check("Song of the Elves: in progress, archived targets labelled, Pathfinder link", text("qd-detail-state").equals("In progress"));
				((RButton) named("qd-to-pathfinder")).doClick();
				size(225);
				check("Song of the Elves opens the typed Pathfinder milestone", comp.navigator().currentRequest()
					.equals(RouteRequest.of("pathfinder", RouteRequest.Param.MILESTONE, "song-of-the-elves")));
				comp.shell().header().back().doClick();
				size(225);
				comp.shell().header().back().doClick();
				size(225);
				((SearchField) named("qd-search")).setText("");
				size(225);
				@SuppressWarnings("unchecked")
				RDropdown<String[]> status = (RDropdown<String[]>) named("qd-status");
				status.select(status.getItemAt(3));
				size(225);
				check("status filter Not started shows only The Ides of Milk and survives re-render", text("qd-count").startsWith("1 of 215")
					&& accessible("qd-quest-" + firstId()).equals("The Ides of Milk, Not started")
					&& ((RDropdown<?>) named("qd-status")).getSelectedIndex() == 3);
				@SuppressWarnings("unchecked")
				RDropdown<String[]> st2 = (RDropdown<String[]>) named("qd-status");
				st2.select(st2.getItemAt(0));
				size(225);
				((SegmentedSelector) named("qd-tabs")).setSelected(1);
				size(225);
				check("Diaries: twelve regions per tier, states Unknown without evidence", ((Expandable) named("qd-tier-Easy")).expanded()
					&& accessible("qd-diary-Ardougne-Easy").equals("Ardougne, Unknown"));
				activate(named("qd-diary-Ardougne-Easy"));
				check("diary detail lists archived tasks; completion unobserved", text("qd-tasks-note").equals("Task completion observations unavailable.")
					&& text("qd-task-0").startsWith("1. Have Wizard Cromperty teleport you") && text("qd-task-9").startsWith("10. "));
				shot("03-diary-detail.png");
				comp.shell().header().back().doClick();
				size(225);
				check("Back returns to the diaries", !page().hasLocalBack() && named("qd-tier-Easy") != null);
				FixtureSnapshot more = FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(2)
					.diary("Ardougne Easy", Evidence.verified(QuestState.COMPLETE, T0, "x")).build();
				check("same-scope revision updates states and keeps the tab", fixtures.publish(more) && page() == p && size(225)
					&& accessible("qd-diary-Ardougne-Easy").equals("Ardougne, Complete"));
				layout();
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.demoB(generation, 1));
				comp.navigator().navigate("quests");
				size(225);
				check("new scope discards tab and filters; B shows only its own evidence", page() != p && named("qd-list") != null
					&& text("qd-count").equals("215 of 215 archived entries · 0 verified complete"));
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.signedOut(generation, 2));
				comp.navigator().navigate("quests");
				size(225);
				check("signed out: account evidence unavailable", text("qd-coverage").equals("Account evidence unavailable"));
			} finally { comp.dispose(); }
		}

		private void layout() {
			for (TextScale scale : TextScale.values()) {
				ui.setTextScale(scale);
				for (int t = 0; t < 2; t++) {
					((SegmentedSelector) named("qd-tabs")).setSelected(t);
					for (int width : new int[] {225, 200}) {
						size(width);
						List<String> issues = new ArrayList<>();
						audit(page(), issues);
						check(scale + " tab " + t + " at " + width + "px: no clipped or overflowing component " + issues.subList(0, Math.min(3, issues.size())), issues.isEmpty());
					}
				}
			}
			ui.setTextScale(TextScale.LARGE);
			((SegmentedSelector) named("qd-tabs")).setSelected(0);
			size(225);
			shot("04-quests-large-text.png");
			ui.setTextScale(TextScale.DEFAULT);
		}

		private String firstId() {
			Component c = named("qd-list").getComponent(0);
			return c.getName().substring("qd-quest-".length());
		}

		private QuestsPanel page() { return findPage(comp.shell()); }

		private static QuestsPanel findPage(Component c) {
			if (c instanceof QuestsPanel) return (QuestsPanel) c;
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
				QuestsPanel p = findPage(k);
				if (p != null) return p;
			}
			return null;
		}

		/** The visible row whose accessible name starts with {@code title + ", "}. */
		private static Component find(Component c, String title) {
			if (c.isVisible() && c instanceof JComponent && String.valueOf(((JComponent) c).getName()).startsWith("qd-quest-")
				&& String.valueOf(((JComponent) c).getAccessibleContext().getAccessibleName()).startsWith(title + ", ")) return c;
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
				Component f = find(k, title);
				if (f != null) return f;
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
