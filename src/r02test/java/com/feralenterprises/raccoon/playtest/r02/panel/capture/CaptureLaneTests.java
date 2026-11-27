package com.feralenterprises.raccoon.playtest.r02.panel.capture;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.CaptureState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.panel.bank.BankInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.bank.BankPanels;
import com.feralenterprises.raccoon.playtest.r02.panel.bank.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.RButton;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Lane C Account Capture regressions on the accepted shared composition (simulated accounts, no RuneLite). Args: the
 * accepted Lane Inputs ZIP, the extracted frozen {@code Project-Raccoon-R02/}, optional evidence directory.
 */
public final class CaptureLaneTests {
	private static int checks, failures;
	private static final Instant T0 = TestFixtures.T0;
	private static final String[] OTHERS = {"mode", "skills", "quests", "equipment", "inventory", "diaries", "slayer", "collection", "kills"};

	public static void main(String[] args) throws Exception {
		if (args.length < 2) throw new IllegalArgumentException("Lane Inputs ZIP and extracted Project-Raccoon-R02 required");
		Path zip = Paths.get(args[0]), frozen = Paths.get(args[1]);
		Path evidence = args.length > 2 ? Paths.get(args[2]) : null;
		LaneInputs lane = LaneInputs.open(zip);
		CaptureInputs in = CaptureInputs.load(lane, frozen);
		check("ten frozen sources in order", in.sources.stream().map(s -> s.id).collect(Collectors.joining(","))
			.equals("mode,skills,quests,equipment,inventory,bank,diaries,slayer,collection,kills"));
		check("every frozen source icon resolves", in.sources.stream().allMatch(s -> s.icon != null));
		Path dir = Files.createTempDirectory("capture-tamper");
		Files.write(dir.resolve("Project-Raccoon-R02.html"), "<html></html>".getBytes());
		boolean refused;
		try { CaptureInputs.load(lane, dir); refused = false; } catch (IOException e) { refused = e.getMessage().contains("SHA-256"); }
		check("a different frozen review HTML is refused", refused);
		model(in);
		FrozenPackageAssets assets = new FrozenPackageAssets(frozen);
		BankInputs bank = BankInputs.load(lane);
		SwingUtilities.invokeAndWait(() -> new Mounted(in, bank, assets, evidence).run());
		System.out.println("Account Capture lane: " + (checks - failures) + "/" + checks + " checks passed");
		if (failures > 0) System.exit(1);
	}

	private static FixtureSnapshot.Builder account(String a) { return FixtureSnapshot.builder(AccountScope.of(a, 1)).generation(1).revision(1); }

	private static ContainerObservation bank(ContainerState s, boolean empty) {
		return new ContainerObservation(Container.BANK, s, T0, "test", empty ? Collections.emptyMap() : Collections.singletonMap(995, 10L));
	}

	/** Frozen screenshot state: bank verified; quests, diaries, kill log incomplete; the rest pending. */
	static FixtureSnapshot.Builder frozenLike(FixtureSnapshot.Builder b) {
		return b.container(bank(ContainerState.CURRENT, false))
			.capture("quests", Evidence.verified(CaptureState.PARTIAL, T0, "x")).capture("diaries", Evidence.verified(CaptureState.PARTIAL, T0, "x"))
			.capture("kills", Evidence.verified(CaptureState.PARTIAL, T0, "x")).capture("skills", Evidence.verified(CaptureState.PENDING, T0, "x"));
	}

	private static String states(CaptureModel m) { return m.rows.stream().map(r -> r.state.label).collect(Collectors.joining(",")); }

	private static void model(CaptureInputs in) {
		CaptureModel f = CaptureModel.of(frozenLike(account("A")).build(), in);
		check("frozen-equivalent rows: Pending, Pending, Incomplete, Pending, Pending, Verified, Incomplete, Pending, Pending, Incomplete",
			states(f).equals("Pending,Pending,Incomplete,Pending,Pending,Verified,Incomplete,Pending,Pending,Incomplete"));
		check("frozen coverage 1 / 10 and summary", f.count.equals("1 / 10") && f.summary.equals("1 verified now · 0 saved · 9 pending")
			&& f.title.equals("Capture your account"));
		check("next step: observe account type with its guidance", f.nextTitle.equals("Observe account type")
			&& f.nextCopy.equals("Detected from supported account identity and mode signals after login.") && f.nextButton.equals("Show guidance ›"));

		CaptureModel contradiction = CaptureModel.of(account("A").container(bank(ContainerState.HISTORICAL, false))
			.capture("bank", Evidence.verified(CaptureState.VERIFIED, T0, "x")).build(), in);
		check("Bank row follows the bank observation, never a contradicting capture flag", contradiction.rows.get(5).state == CaptureModel.State.HISTORICAL
			&& contradiction.nextTitle.equals("Observe account type"));
		CaptureModel incomplete = CaptureModel.of(account("A").container(bank(ContainerState.INCOMPLETE, false)).build(), in);
		check("incomplete bank: next step completes the bank capture first", incomplete.rows.get(5).state == CaptureModel.State.INCOMPLETE
			&& incomplete.nextTitle.equals("Complete bank capture") && incomplete.nextButton.equals("Show bank guidance ›") && incomplete.covered == 0);
		CaptureModel none = CaptureModel.of(account("A").build(), in);
		check("no bank observation: open your bank", none.rows.get(5).state == CaptureModel.State.WAITING && none.nextTitle.equals("Open your bank"));
		CaptureModel empty = CaptureModel.of(account("A").container(bank(ContainerState.CURRENT, true)).build(), in);
		check("verified empty bank is a verified source", empty.rows.get(5).state == CaptureModel.State.VERIFIED && empty.rows.get(5).emptyBank);

		FixtureSnapshot.Builder all = account("A").container(bank(ContainerState.CURRENT, false));
		for (String id : OTHERS) all.capture(id, Evidence.verified(CaptureState.VERIFIED, T0, "x"));
		CaptureModel done = CaptureModel.of(all.build(), in);
		check("all ten verified: capture complete, you're ready", done.count.equals("10 / 10") && done.title.equals("Capture complete")
			&& done.nextTitle.equals("You’re ready") && done.nextButton.equals("View Bank Memory ›") && done.nextId == null);
		FixtureSnapshot.Builder saved = account("A").container(bank(ContainerState.HISTORICAL, false));
		for (String id : OTHERS) saved.capture(id, Evidence.stale(CaptureState.VERIFIED, T0, "x"));
		CaptureModel sv = CaptureModel.of(saved.build(), in);
		check("all ten saved: saved observations, never verified now", sv.verified == 0 && sv.saved == 10 && sv.title.equals("Saved observations")
			&& sv.intro.equals("Saved evidence may be out of date.") && sv.rows.stream().allMatch(r -> r.state == CaptureModel.State.HISTORICAL));
		check("saved rows keep their last-complete time", sv.rows.get(0).lastComplete().startsWith("Last complete: 26 Nov 2026"));
		CaptureModel stalePartial = CaptureModel.of(account("A").capture("collection", Evidence.stale(CaptureState.PARTIAL, T0, "x")).build(), in);
		check("partial evidence is incomplete; counts as saved only with a recorded time", stalePartial.rows.get(8).state == CaptureModel.State.INCOMPLETE
			&& stalePartial.saved == 1 && f.saved == 0);
		CaptureModel unavailable = CaptureModel.of(account("A").capture("slayer", Evidence.unavailable()).build(), in);
		check("unavailable evidence is shown as unavailable", unavailable.rows.get(7).state == CaptureModel.State.UNAVAILABLE);
		CaptureModel out = CaptureModel.of(TestFixtures.signedOut(1, 2), in);
		check("signed out: every source unavailable, nothing exposed", out.rows.stream().allMatch(r -> r.state == CaptureModel.State.UNAVAILABLE)
			&& out.count.equals("—") && out.title.equals("Account unavailable") && out.nextTitle.equals("Log in to your account") && out.nextButton == null);
		CaptureModel b = CaptureModel.of(TestFixtures.demoB(1, 1), in);
		check("account B uses only its own evidence", b.identity.equals("Demo B"));
	}

	private static final class Mounted {
		private final UiContext ui;
		private final FixtureDispatcher fixtures;
		private final R02Composition comp;
		private final Path evidence;
		private int generation;

		Mounted(CaptureInputs in, BankInputs bank, FrozenPackageAssets assets, Path evidence) {
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
			pages.add(CapturePanels.registration(in));
			pages.add(BankPanels.registration(bank));
			pages.add(new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), c -> new JLabel("Home test receiver")));
			comp = new R02Composition(ui, fixtures, assets, pages);
			generation = fixtures.bind();
			comp.start(frozenLike(FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(1)).build());
		}

		void run() {
			try {
				comp.navigator().navigate("capture");
				size(225);
				CapturePanel p = page();
				check("composition mounts Lane C Account Capture", p != null && comp.navigator().current().equals("capture"));
				check("coverage and ten rows render", text("capture-count").equals("Capture coverage 1 / 10")
					&& accessible("capture-row-bank").equals("Bank, Verified") && accessible("capture-row-quests").equals("Quests, Incomplete"));
				shot("01-capture.png");
				((RButton) named("capture-guide")).doClick();
				size(225);
				check("guidance opens for the next step", text("capture-guidance").startsWith("Detected from supported account identity"));
				Expandable bankRow = (Expandable) named("capture-row-bank");
				bankRow.setExpanded(true);
				size(225);
				check("bank row detail: last complete and link", text("capture-at-bank").startsWith("Last complete: 26 Nov 2026"));
				shot("02-capture-open.png");
				((RButton) named("capture-row-bank-link")).doClick();
				size(225);
				check("bank row links to the real Bank Memory", comp.navigator().current().equals("bank"));
				comp.shell().header().back().doClick();
				size(225);
				FixtureSnapshot next = frozenLike(FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(2))
					.capture("kills", Evidence.verified(CaptureState.VERIFIED, T0, "x")).build();
				check("same-scope revision updates rows and keeps open rows and guidance", fixtures.publish(next) && page() == p && size(225)
					&& accessible("capture-row-kills").equals("Kill log, Verified") && ((Expandable) named("capture-row-bank")).expanded()
					&& named("capture-guidance") != null && text("capture-count").equals("Capture coverage 2 / 10"));
				check("an older revision is refused", !fixtures.publish(frozenLike(FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(1)).build()));
				layout();
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.demoB(generation, 1));
				comp.navigator().navigate("capture");
				size(225);
				check("a new account scope discards open rows and guidance", page() != p && named("capture-guidance") == null
					&& !((Expandable) named("capture-row-bank")).expanded() && text("capture-rows-title").length() >= 0);
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.signedOut(generation, 2));
				comp.navigator().navigate("capture");
				size(225);
				check("signed out: no account observations exposed", text("capture-summary").equals("No account observations are exposed.")
					&& accessible("capture-row-bank").equals("Bank, Unavailable"));
				shot("03-capture-signed-out.png");
			} finally { comp.dispose(); }
		}

		private void layout() {
			for (TextScale scale : TextScale.values()) {
				ui.setTextScale(scale);
				for (int width : new int[] {225, 200}) {
					size(width);
					List<String> issues = new ArrayList<>();
					audit(page(), issues);
					check(scale + " at " + width + "px: no clipped or overflowing component " + issues, issues.isEmpty());
				}
			}
			ui.setTextScale(TextScale.LARGE);
			size(225);
			shot("04-capture-large-text.png");
			ui.setTextScale(TextScale.DEFAULT);
		}

		private CapturePanel page() { return find(comp.shell()); }

		private static CapturePanel find(Component c) {
			if (c instanceof CapturePanel) return (CapturePanel) c;
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
				CapturePanel p = find(k);
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
			return c instanceof TextBlock ? ((TextBlock) c).text() : "";
		}

		private String accessible(String name) {
			JComponent c = named(name);
			return c == null ? "<missing " + name + ">" : String.valueOf(c.getAccessibleContext().getAccessibleName());
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
