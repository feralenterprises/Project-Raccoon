package com.feralenterprises.raccoon.playtest.r02.panel.xpstash;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.panel.bank.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.SegmentedSelector;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
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
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Lane C XP Stash page regressions on the accepted shared composition (simulated accounts, no RuneLite). Args: the
 * accepted Lane Inputs ZIP, the extracted frozen {@code Project-Raccoon-R02/}, optional evidence directory.
 */
public final class XpStashLaneTests {
	private static int checks, failures;
	private static final Instant T0 = TestFixtures.T0;

	public static void main(String[] args) throws Exception {
		Path zip = Paths.get(args[0]), frozen = Paths.get(args[1]);
		Path evidence = args.length > 2 ? Paths.get(args[2]) : null;
		LaneInputs lane = LaneInputs.open(zip);
		XpData data = XpData.load(lane);
		Map<Integer, Long> bank = XpEngineTests.frozenBank(lane);
		FrozenPackageAssets assets = new FrozenPackageAssets(frozen);
		SwingUtilities.invokeAndWait(() -> new Mounted(data, bank, assets, evidence).run());
		System.out.println("XP Stash lane: " + (checks - failures) + "/" + checks + " checks passed");
		if (failures > 0) System.exit(1);
	}

	private static final class Mounted {
		private final UiContext ui;
		private final FixtureDispatcher fixtures;
		private final R02Composition comp;
		private final Path evidence;
		private final Map<Integer, Long> bank;
		private int generation;
		private long revision = 1;

		Mounted(XpData data, Map<Integer, Long> bank, FrozenPackageAssets assets, Path evidence) {
			this.bank = bank;
			this.evidence = evidence;
			ui = new UiContext(new DefaultFontSource(), new AssetProvider() {
				public BufferedImage art(ArtKey key) {
					String f = key.candidateFile();
					return f.matches("(PR|SIG)-\\d+_.*") ? assets.art(f.substring(0, f.indexOf('_'))) : null;
				}
				public BufferedImage nativeSprite(NativeSprite sprite, Runnable onLoaded) { return null; }
				public BufferedImage item(int itemId, int quantity, Runnable onLoaded) { return null; }
				public String provenance() { return "Headless Lane C"; }
			});
			fixtures = new FixtureDispatcher(TestFixtures.registry(), TestFixtures.clock());
			List<RouteRegistration> pages = new ArrayList<>();
			pages.add(XpStashPanels.registration(data));
			pages.add(new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), c -> new JLabel("Home test receiver")));
			comp = new R02Composition(ui, fixtures, assets, pages);
			generation = fixtures.bind();
			comp.start(frozenLike());
		}

		private FixtureSnapshot frozenLike() {
			return FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(revision++)
				.container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "A/current", bank)).build();
		}

		void run() {
			try {
				comp.navigator().navigate("xpstash");
				size(225);
				XpStashPanel p = page();
				check("composition mounts Lane C XP Stash on Fletching", p != null && comp.navigator().current().equals("xpstash") && p.options().skill.equals("Fletching"));
				check("frozen projection: account XP and level unavailable, banked 25,800", accessible("xp-line-account-xp").equals("Account XP: Unavailable")
					&& accessible("xp-line-current-level").equals("Current level: Unavailable") && accessible("xp-line-banked-xp").equals("Banked XP: 25,800")
					&& accessible("xp-line-projected-level").equals("Projected level: Unavailable"));
				check("frozen notes: partial sources and no level filter", text("xp-warning").equals("Estimate covers observed sources only.")
					&& text("xp-level-note").startsWith("Skill-level evidence unavailable."));
				check("frozen Arrow shaft: 0 + 19,350 linked, 19,350 XP, ingredients missing", text("xp-entry-qty-Fletching|F_ARROW_SHAFT").equals("0 + 19,350 linked")
					&& accessible("xp-estimate-Fletching|F_ARROW_SHAFT").equals("Estimated XP: 19,350") && named("xp-missing-Fletching|F_ARROW_SHAFT") != null);
				check("frozen Logs: 1,290 for 6,450 XP via arrow shafts", text("xp-entry-qty-Fletching|F_LOGS").equals("1,290")
					&& accessible("xp-estimate-Fletching|F_LOGS").equals("Estimated XP: 6,450"));
				shot("01-xpstash-calculator.png");

				@SuppressWarnings("unchecked")
				RDropdown<XpData.Recipe> method = (RDropdown<XpData.Recipe>) named("xp-method-Fletching|F_LOGS");
				method.select(p.result().entries.stream().filter(e -> e.key.equals("Fletching|F_LOGS")).findFirst().get().all.stream()
					.filter(r -> r.key.equals("F_LONGBOW_U")).findFirst().get());
				size(225);
				check("choosing Longbow (u) links 1,290 bows to stringing", p.options().choices.get("Fletching|F_LOGS").equals("F_LONGBOW_U")
					&& p.result().entries.stream().anyMatch(e -> e.piped == 1290));
				((SegmentedSelector) named("xp-tabs")).setSelected(2);
				size(225);
				check("options: source evidence states", accessible("xp-check-source:bank").equals("Bank · observed")
					&& accessible("xp-check-source:vault").equals("Seed vault · unavailable"));
				click("xp-check-source:vault");
				click("xp-check-limit");
				check("limit with complete evidence: no bowstring, zero stringing actions", p.result().complete
					&& p.result().entries.stream().filter(e -> e.piped == 1290).allMatch(e -> e.runs == 0));
				shot("02-xpstash-options.png");
				((SegmentedSelector) named("xp-tabs")).setSelected(0);
				size(225);
				check("same-scope revision keeps options", fixtures.publish(frozenLike()) && page() == p && p.options().limit
					&& !p.options().sources.get("vault") && "F_LONGBOW_U".equals(p.options().choices.get("Fletching|F_LOGS")));

				@SuppressWarnings("unchecked")
				RDropdown<String> skill = (RDropdown<String>) named("xp-skill");
				skill.select("Firemaking");
				size(225);
				check("switching skill: Firemaking banked 51,600 (vector)", accessible("xp-line-banked-xp").equals("Banked XP: 51,600"));
				check("an older revision is refused", !fixtures.publish(FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(1).build()));
				layout();

				generation = fixtures.bind();
				revision = 1;
				check("account B is a new scope", fixtures.publish(TestFixtures.demoB(generation, 1)));
				comp.navigator().navigate("xpstash");
				size(225);
				check("B's page is a fresh page with default options", page() != p && page().options().skill.equals("Fletching") && !page().options().limit);
				generation = fixtures.bind();
				check("demo A again is a new scope", fixtures.publish(TestFixtures.demoA(generation, 1)));
				comp.navigator().navigate("xpstash");
				size(225);
				XpStashPanel a = page();
				check("a new scope discards options (back to Fletching defaults)", a != p && a.options().skill.equals("Fletching") && !a.options().limit
					&& a.options().choices.isEmpty());
				check("verified level 1 without exact XP: level shown, XP and projection unavailable", accessible("xp-line-current-level").equals("Current level: 1")
					&& accessible("xp-line-account-xp").equals("Account XP: Unavailable") && accessible("xp-line-projected-level").equals("Projected level: Unavailable")
					&& named("xp-level-note") == null);
				shot("03-xpstash-level-only.png");
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.signedOut(generation, 2));
				comp.navigator().navigate("xpstash");
				size(225);
				check("signed out: no resource quantities", text("xp-warning").equals("Resource observations unavailable.")
					&& text("xp-empty").equals("Resource quantities unavailable."));
			} finally { comp.dispose(); }
		}

		private void layout() {
			for (TextScale scale : TextScale.values()) {
				ui.setTextScale(scale);
				for (int t = 0; t < 3; t++) {
					((SegmentedSelector) named("xp-tabs")).setSelected(t);
					for (int width : new int[] {225, 200}) {
						size(width);
						List<String> issues = new ArrayList<>();
						audit(page(), issues);
						check(scale + " tab " + t + " at " + width + "px: no clipped or overflowing component " + issues, issues.isEmpty());
					}
				}
			}
			ui.setTextScale(TextScale.LARGE);
			((SegmentedSelector) named("xp-tabs")).setSelected(0);
			size(225);
			shot("04-xpstash-large-text.png");
			ui.setTextScale(TextScale.DEFAULT);
		}

		private XpStashPanel page() { return find(comp.shell()); }

		private static XpStashPanel find(Component c) {
			if (c instanceof XpStashPanel) return (XpStashPanel) c;
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
				XpStashPanel p = find(k);
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
			JComponent c = named(name);
			if (c == null) throw new AssertionError("missing " + name);
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
