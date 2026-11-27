package com.feralenterprises.raccoon.playtest.r02.panel.activity;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.ActivityObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ActivityObservation.Mode;
import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.panel.home.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Color;
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
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Lane A Current Activity regressions on the accepted shared composition (simulated accounts, no RuneLite). Args: the
 * accepted Lane Inputs ZIP, the extracted frozen {@code Project-Raccoon-R02/}, optional evidence directory.
 */
public final class ActivityLaneTests {
	private static int checks, failures;
	private static final Instant T0 = TestFixtures.T0;

	public static void main(String[] args) throws Exception {
		if (args.length < 2) throw new IllegalArgumentException("Lane Inputs ZIP and extracted Project-Raccoon-R02 required");
		Path zip = Paths.get(args[0]), frozen = Paths.get(args[1]);
		Path evidence = args.length > 2 ? Paths.get(args[2]) : null;
		ActivityInputs in = ActivityInputs.load(LaneInputs.open(zip), frozen);
		inputs(in, zip, frozen);
		model(in);
		FrozenPackageAssets real = new FrozenPackageAssets(frozen);
		SwingUtilities.invokeAndWait(() -> new Mounted(in, real, evidence).run());
		System.out.println("Current Activity lane: " + (checks - failures) + "/" + checks + " checks passed");
		if (failures > 0) System.exit(1);
	}

	// ------------------------------------------------------------------ inputs

	private static void inputs(ActivityInputs in, Path zip, Path frozen) throws IOException {
		check("catalog has the 123 frozen entries", in.catalog.size() == 123);
		check("all frozen environment presets parse (" + in.environmentCount() + ")", in.environmentCount() >= 68);
		boolean every = in.catalog.values().stream().allMatch(e -> in.environment(e.environment).name.equals(e.environment));
		check("every catalog environment resolves to its own preset", every);
		ActivityInputs.Environment frozenStone = in.environment("frozen-stone");
		check("frozen-stone uses the final cascade (arena override light #f4fcff, ink #183047)",
			frozenStone.light.equals(new Color(0xf4fcff)) && frozenStone.ink.equals(new Color(0x183047)));
		check("frozen-stone scene, surface and props resolve", frozenStone.scene != null && frozenStone.surface != null
			&& frozenStone.propA != null && frozenStone.propB != null);
		check("unknown environment falls back to neutral-activity", in.environment("nowhere").name.equals("neutral-activity"));
		ActivityInputs.Labels v = in.labels().get("vorkath");
		check("frozen Vorkath sample labels: Session KC, Kills / hr, Ranged XP", in.labels().size() == 5 && v.primary.equals("Session KC")
			&& v.rate.equals("Kills / hr") && v.xp.equals("Ranged XP") && v.kind.equals("Combat"));
		check("frozen portraits: Fishing fisher art, Thieving coins sprite", "fisher".equals(in.labels().get("fishing").art)
			&& "i995".equals(in.labels().get("thieving").itemIcon) && in.activityArt.containsKey("fisher"));
		Path dir = Files.createTempDirectory("activity-tamper");
		Files.write(dir.resolve("Project-Raccoon-R02.html"), "<html></html>".getBytes());
		boolean refused;
		try { ActivityInputs.load(LaneInputs.open(zip), dir); refused = false; } catch (IOException e) { refused = e.getMessage().contains("SHA-256"); }
		check("a different frozen review HTML is refused", refused);
	}

	// ------------------------------------------------------------------ model

	private static ActivityModel model(ActivityInputs in, FixtureSnapshot s) {
		return ActivityModel.of(s, TestFixtures.registry(), in, T0);
	}

	private static FixtureSnapshot.Builder account(String a) { return FixtureSnapshot.builder(AccountScope.of(a, 1)).generation(1).revision(1); }

	private static ArchiveRecord record(String id, String activity, String encounter, int minutes, int units, String unit, Long xp) {
		return new ArchiveRecord(id, activity, encounter, T0.minus(Duration.ofHours(3)), Duration.ofMinutes(minutes), units, unit,
			xp == null ? Evidence.unavailable() : Evidence.verified(xp, T0, "saved"), Collections.emptyMap());
	}

	private static FixtureSnapshot replay(String key, String recordId, ArchiveRecord... records) {
		return account("A").archive(Evidence.verified(true, T0, "archive"), Arrays.asList(records))
			.activity(new ActivityObservation(Mode.REPLAY, key, recordId, T0, T0, 0, Evidence.unavailable())).build();
	}

	private static Long metric(ActivityModel m, int i) { return m.metrics.get(i).value; }

	private static void model(ActivityInputs in) {
		ActivityModel a = model(in, TestFixtures.demoA(1, 1));
		check("demo A Vorkath replay: Combat, Vorkath, Ungael · Dragon Slayer II, frozen-stone", a.state == ActivityModel.State.ACTIVE
			&& a.kind.equals("Combat") && a.title.equals("Vorkath") && a.subtitle.equals("Ungael · Dragon Slayer II") && a.environment.equals("frozen-stone"));
		check("C-08 replay is labelled a saved session replay, never detected or current", a.replay && a.status.equals("Saved session replay")
			&& !a.status.contains("detected") && a.coverage.startsWith("Replay of a saved activity · "));
		check("frozen Vorkath metrics from the saved record: 12 Session KC, 23 Kills / hr, 38,400 Ranged XP, 74,323 XP / hr",
			a.metrics.get(0).label.equals("Session KC") && metric(a, 0) == 12 && metric(a, 1) == 23 && a.metrics.get(2).label.equals("Ranged XP")
			&& metric(a, 2) == 38_400 && metric(a, 3) == 74_323 && "Estimated".equals(a.metrics.get(1).quality));
		check("active time 31m 00s from the record; elapsed unavailable (not in the shared record)",
			a.lines.get(0).value.equals("31m 00s") && a.lines.get(1).label.equals("Elapsed session") && a.lines.get(1).value == null);
		check("replay coverage uses the record's own start (UTC)", a.coverage.equals("Replay of a saved activity · 26 Nov · 15:15"));

		ActivityModel barrows = model(in, replay("barrows", "a2", record("a2", "barrows", "barrows", 47, 8, "chests", 12_600L)));
		check("C-11 Barrows replay counts chests with its own unit, never kills", metric(barrows, 0) == 8
			&& barrows.metrics.get(0).label.equals("Chests") && !barrows.metrics.get(0).label.toLowerCase().contains("kill")
			&& !barrows.metrics.get(1).label.toLowerCase().contains("kill"));
		ActivityModel mislabeled = model(in, replay("barrows", "a2", record("a2", "barrows", "barrows", 47, 8, "kills", 12_600L)));
		check("C-11 a Barrows record claiming kills establishes no count", metric(mislabeled, 0) == null
			&& mislabeled.metrics.get(0).label.equals("Chests") && metric(mislabeled, 1) == null);
		check("C-11 saved XP still shown for the mislabeled record", metric(mislabeled, 2) == 12_600);

		ActivityModel unknownXp = model(in, replay("vorkath", "a10", record("a10", "vorkath", "vorkath", 17, 5, "kills", null)));
		check("unknown saved XP stays unavailable, rates too", metric(unknownXp, 2) == null && metric(unknownXp, 3) == null && metric(unknownXp, 0) == 5);
		ActivityModel missing = model(in, replay("vorkath", "a99"));
		check("replay of a record not in this scope shows a reference with no values", missing.status.equals("Coverage preview")
			&& missing.metrics.stream().allMatch(m -> m.value == null) && missing.coverage.equals("Activity reference · account observation unavailable"));
		ActivityModel planned = model(in, replay("attack", "a11", record("a11", "attack", null, 18, 1, "recorded progress", null)));
		check("visual-only catalog entry is a preview, never detection", planned.planned && planned.status.equals("Preview only"));

		ActivityModel out = model(in, TestFixtures.signedOut(1, 2));
		check("signed out: Logged out, account unavailable, no footer", out.state == ActivityModel.State.UNAVAILABLE
			&& out.title.equals("Logged out") && out.heading.equals("Account unavailable") && out.footer == null && out.metrics.isEmpty());
		ActivityModel b = model(in, TestFixtures.demoB(1, 1));
		check("account with no activity observation: unrecognized, nothing inferred", b.title.equals("Unrecognized activity")
			&& b.notes.get(0).equals("No supported activity signal is available. No progress is inferred."));
		ActivityModel unsupported = model(in, account("A").activity(new ActivityObservation(Mode.UNSUPPORTED, "vorkath", null, T0, T0, 0,
			Evidence.unavailable())).build());
		check("unsupported signal is unrecognized even with an activity key", unsupported.title.equals("Unrecognized activity") && unsupported.metrics.isEmpty());

		ActivityModel banking = model(in, account("A").activity(new ActivityObservation(Mode.BANKING, "cooking", null,
			T0.minus(Duration.ofMinutes(14)), null, 720_000, Evidence.unavailable())).build());
		check("banking: frozen title, paused, last activity retained", banking.state == ActivityModel.State.IDLE
			&& banking.title.equals("Banking · suspended") && banking.subtitle.equals("Active time paused")
			&& banking.heading.equals("Last activity · Cooking") && banking.status.equals("Paused"));
		check("C-09 paused session shows no invented counts or XP", banking.metrics.stream().allMatch(m -> m.value == null));
		check("paused times come from the observation (12m active, 14m elapsed)", banking.lines.get(0).value.equals("12m 00s")
			&& banking.lines.get(1).value.equals("14m 00s"));
		ActivityModel idle = model(in, account("A").activity(new ActivityObservation(Mode.IDLE, null, null, null, null, 0,
			Evidence.unavailable())).build());
		check("idle with no previous activity waits", idle.title.equals("Idle") && idle.heading.equals("Waiting for activity")
			&& idle.lines.get(0).value == null);
		check("frozen time format", ActivityModel.time(3725).equals("1h 2m 05s") && ActivityModel.time(1860).equals("31m 00s"));
	}

	// ------------------------------------------------------------------ mounted

	private static final class Mounted {
		private final UiContext ui;
		private final FixtureDispatcher fixtures;
		private final R02Composition comp;
		private final Path evidence;
		private int generation;

		Mounted(ActivityInputs in, FrozenPackageAssets assets, Path evidence) {
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
			fixtures = new FixtureDispatcher(TestFixtures.registry(), TestFixtures.clock());
			List<RouteRegistration> pages = new ArrayList<>();
			pages.add(ActivityPanels.registration(in));
			pages.add(new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), c -> new JLabel("Home test receiver")));
			comp = new R02Composition(ui, fixtures, assets, pages);
			generation = fixtures.bind();
			comp.start(TestFixtures.demoA(generation, 1));
		}

		void run() {
			try {
				comp.navigator().navigate("activity");
				size();
				ActivityPanel p = page();
				check("composition mounts Lane A Current Activity", p != null && comp.navigator().current().equals("activity"));
				check("hero names the replayed encounter with its portrait", accessible("activity-hero").equals("Combat, Vorkath, Ungael · Dragon Slayer II"));
				check("metric cells are accessible with values", accessible("metric-session-kc").equals("Session KC: 12")
					&& accessible("metric-kills-hr").equals("Kills / hr: 23 (Estimated)") && accessible("metric-xp-hr").equals("XP / hr: 74,323 (Estimated)"));
				check("elapsed line says Unavailable", accessible("line-elapsed-session").equals("Elapsed session: Unavailable"));
				shot("01-activity-vorkath-replay.png");
				click("activity-details");
				check("Data details opens the frozen provenance copy", shown("activity-details-text"));
				FixtureSnapshot same = FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(2)
					.archive(Evidence.verified(true, T0, "archive"), Collections.singletonList(record("a2", "barrows", "barrows", 47, 8, "chests", 12_600L)))
					.activity(new ActivityObservation(Mode.REPLAY, "barrows", "a2", T0, T0, 0, Evidence.unavailable())).build();
				check("same-scope revision updates the page in place", fixtures.publish(same) && page() == p && size()
					&& accessible("metric-chests").equals("Chests: 8"));
				check("same-scope revision keeps Data details open", shown("activity-details-text"));
				shot("02-activity-barrows-replay.png");
				check("an older revision is refused", !fixtures.publish(TestFixtures.demoA(generation, 1)) && accessible("metric-chests").equals("Chests: 8"));
				layout();
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.demoB(generation, 1));
				comp.navigator().navigate("activity");
				size();
				check("a new account scope discards the page and its local state", page() != p && !shown("activity-details-text")
					&& accessible("activity-hero").startsWith("Unavailable, Unrecognized activity"));
				check("no Account A value survives into B; unavailable hides the footer (frozen)", named("metric-session-kc") == null && named("activity-footer") == null);
				shot("03-activity-unrecognized.png");
				generation = fixtures.bind();
				fixtures.publish(FixtureSnapshot.builder(AccountScope.of("A", 2)).generation(generation).revision(1)
					.activity(new ActivityObservation(Mode.BANKING, "cooking", null, T0.minus(Duration.ofMinutes(14)), null, 720_000,
						Evidence.unavailable())).build());
				comp.navigator().navigate("activity");
				size();
				check("banking shows the retained activity with frozen times", accessible("line-active-time").equals("Active time: 12m 00s"));
				shot("04-activity-banking.png");
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.signedOut(generation, 3));
				comp.navigator().navigate("activity");
				size();
				check("signed out hides the environment footer", named("activity-footer") == null
					&& accessible("activity-hero").startsWith("Unavailable, Logged out"));
				shot("05-activity-logged-out.png");
			} finally { comp.dispose(); }
		}

		private void layout() {
			for (TextScale scale : TextScale.values()) {
				ui.setTextScale(scale);
				for (int width : new int[] {225, 200}) {
					size(width);
					List<String> issues = new ArrayList<>();
					audit(page(), issues);
					check(scale + " text at " + width + "px: no clipped or overflowing Activity component " + issues, issues.isEmpty());
				}
			}
			ui.setTextScale(TextScale.LARGE);
			shot("06-activity-large-text.png");
			ui.setTextScale(TextScale.DEFAULT);
			size();
		}

		private ActivityPanel page() { return find(comp.shell()); }

		private static ActivityPanel find(Component c) {
			if (c instanceof ActivityPanel) return (ActivityPanel) c;
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
				ActivityPanel p = find(k);
				if (p != null) return p;
			}
			return null;
		}

		private boolean size() { return size(225); }

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
			if (name.equals(c.getName()) && c instanceof JComponent && c.isVisible()) return (JComponent) c;
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
				JComponent j = named(k, name);
				if (j != null) return j;
			}
			return null;
		}

		private boolean shown(String name) {
			Component c = named(name);
			if (c == null) return false;
			for (; c != null && c != comp.shell(); c = c.getParent()) if (!c.isVisible()) return false;
			return c == comp.shell();
		}

		private String accessible(String name) {
			JComponent c = named(name);
			return c == null ? "<missing " + name + ">" : c.getAccessibleContext().getAccessibleName();
		}

		private void click(String name) {
			((AbstractButton) named(name)).doClick();
			size();
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
			size();
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
