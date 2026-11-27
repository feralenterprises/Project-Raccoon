package com.feralenterprises.raccoon.playtest.r02.panel.files;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.ActivityObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.ItemDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.panel.home.HomeInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.home.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.home.MiniJson;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.SearchField;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Lane A Feral Files regressions on the accepted shared composition (simulated accounts, no RuneLite). Args: the
 * accepted Lane Inputs ZIP, the extracted frozen {@code Project-Raccoon-R02/}, optional evidence directory.
 */
public final class FilesLaneTests {
	private static int checks, failures;
	private static final Instant T0 = TestFixtures.T0;

	public static void main(String[] args) throws Exception {
		if (args.length < 2) throw new IllegalArgumentException("Lane Inputs ZIP and extracted Project-Raccoon-R02 required");
		Path zip = Paths.get(args[0]), frozen = Paths.get(args[1]);
		Path evidence = args.length > 2 ? Paths.get(args[2]) : null;
		LaneInputs lane = LaneInputs.open(zip);
		FilesModel.Presentation p = new FilesModel.Presentation(lane);
		List<ArchiveRecord> frozenA = frozenArchive(lane, "A");
		Registry registry = registry();
		model(p, registry, frozenA);
		FrozenPackageAssets assets = new FrozenPackageAssets(frozen);
		HomeInputs home = HomeInputs.load(lane);
		SwingUtilities.invokeAndWait(() -> new Mounted(lane, home, assets, registry, frozenA, evidence).run());
		System.out.println("Feral Files lane: " + (checks - failures) + "/" + checks + " checks passed");
		if (failures > 0) System.exit(1);
	}

	/** Shared registry plus the frozen archive's item names (test registry only). */
	private static Registry registry() {
		Registry base = TestFixtures.registry();
		List<ItemDef> items = new ArrayList<>(base.items());
		items.add(new ItemDef(4728, "Guthan's platebody", true, "Gear"));
		items.add(new ItemDef(560, "Death rune", true, "Supplies"));
		List<com.feralenterprises.raccoon.playtest.r02.fixture.EncounterDef> enc = new ArrayList<>(base.encounters());
		enc.add(new com.feralenterprises.raccoon.playtest.r02.fixture.EncounterDef("barrows", "Barrows", "Regular", "Medium", "barrows"));
		enc.add(new com.feralenterprises.raccoon.playtest.r02.fixture.EncounterDef("giant-mole", "Giant Mole", "Regular", "Easy", "giant-mole"));
		return new Registry(items, enc);
	}

	/**
	 * Test-only: the frozen saved archive as shared ArchiveRecords (count/unit, active, XP and loot from the record).
	 * Records with unknown active time or count (a7, a8, a11) are skipped: the shared ArchiveRecord requires both, and a
	 * zero would be a false claim (reported to the shared owner).
	 */
	private static List<ArchiveRecord> frozenArchive(LaneInputs lane, String account) throws IOException {
		@SuppressWarnings("unchecked")
		Map<String, Object> saved = (Map<String, Object>) lane.json("shared/saved-records.json");
		List<ArchiveRecord> out = new ArrayList<>();
		Map<String, String[]> keys = new LinkedHashMap<>();
		keys.put("Vorkath", new String[] {"vorkath", "vorkath"});
		keys.put("Barrows", new String[] {"barrows", "barrows"});
		keys.put("Giant Mole", new String[] {"giant-mole", "giant-mole"});
		keys.put("Tormented Demons", new String[] {"tormented-demons", "tormented-demons"});
		int i = 0;
		for (Object o : (List<?>) saved.get(account)) {
			@SuppressWarnings("unchecked")
			Map<String, Object> r = (Map<String, Object>) o;
			String title = MiniJson.str(r, "title");
			String[] k = keys.getOrDefault(title, new String[] {title.toLowerCase().replace(' ', '-'), null});
			long units; String unit;
			if (r.get("metric") instanceof Map) { units = MiniJson.num(r.get("metric"), "value"); unit = MiniJson.str(r.get("metric"), "unit"); }
			else if (r.get("kills") instanceof Number) { units = ((Number) r.get("kills")).longValue(); unit = "kills"; }
			else if (r.get("metricCount") instanceof Number) { units = ((Number) r.get("metricCount")).longValue(); unit = MiniJson.str(r, "metricLabel"); }
			else continue;
			Map<Integer, Long> loot = new LinkedHashMap<>();
			if (r.get("loot") instanceof List) for (Object it : (List<?>) r.get("loot")) loot.put((int) MiniJson.num(it, "id"), MiniJson.num(it, "qty"));
			Object xp = r.get("xp"), active = r.get("active");
			if (!(active instanceof Number)) continue;
			out.add(new ArchiveRecord(MiniJson.str(r, "id"), k[0], k[1], T0.minus(Duration.ofHours(3 + 6 * i++)),
				Duration.ofMinutes(((Number) active).longValue()), (int) units, unit,
				xp instanceof Number ? Evidence.verified(((Number) xp).longValue(), T0, "saved") : Evidence.unavailable(), loot));
		}
		return out;
	}

	private static FixtureSnapshot.Builder account(String a, int epoch) { return FixtureSnapshot.builder(AccountScope.of(a, epoch)).generation(1).revision(1); }

	// ------------------------------------------------------------------ model

	private static void model(FilesModel.Presentation p, Registry registry, List<ArchiveRecord> frozenA) {
		FilesModel a = FilesModel.of(TestFixtures.demoA(1, 1), registry, p);
		FilesModel.Rec a1 = a.record("a1");
		check("demo A archive: Saved observations, Vorkath 12 kills · 31m active", a.archive == FilesModel.Archive.POPULATED
			&& a.status.equals("Saved observations") && a1.title.equals("Vorkath") && a1.rowMetric.equals("12 kills") && a1.rowTime.equals("31m active"));
		check("C-10 account-wide KC is the official 48, distinct from 12 trip kills", "48".equals(a.accountKc(a1)) && a1.count == 12);
		check("matching saved note is shown only because count and unit agree", "12 observed trip kills. Account-wide KC comes from the shared kill log.".equals(a1.note));
		ArchiveRecord drifted = new ArchiveRecord("a1", "vorkath", "vorkath", T0, Duration.ofMinutes(31), 10, "kills",
			Evidence.verified(38_400L, T0, "x"), Collections.emptyMap());
		FilesModel d = FilesModel.of(account("A", 1).archive(Evidence.verified(true, T0, "x"), Collections.singletonList(drifted)).build(), registry, p);
		check("a saved note that disagrees with the shared record is withheld", d.record("a1").note == null && d.record("a1").count == 10);

		FilesModel full = FilesModel.of(account("A", 1).archive(Evidence.verified(true, T0, "x"), frozenA).build(), registry, p);
		FilesModel.Rec a2 = full.record("a2");
		check("C-11 Barrows saved record counts 8 chests, never kills", a2.rowMetric.equals("8 chests") && a2.countLabel.equals("Chests")
			&& !a2.rowMetric.contains("kill"));
		check("latest spoils: Guthan's platebody from the Barrows record (loot-backed notable)", full.latestSpoil() == a2
			&& a2.notable.name.equals("Guthan's platebody") && a2.notable.quantity == 1);
		ArchiveRecord mislabeled = new ArchiveRecord("a2", "barrows", "barrows", T0, Duration.ofMinutes(47), 8, "kills",
			Evidence.verified(12_600L, T0, "x"), Collections.singletonMap(4728, 1L));
		FilesModel m = FilesModel.of(account("A", 1).archive(Evidence.verified(true, T0, "x"), Collections.singletonList(mislabeled)).build(), registry, p);
		check("C-11 a Barrows record claiming kills has no count, no note and no notable", m.record("a2").count == null
			&& m.record("a2").rowMetric.equals("Count unknown") && m.record("a2").note == null && m.latestSpoil() == null);
		check("Barrows has no account-wide KC without a kill-log observation", full.accountKc(a2) == null);
		check("fishing record: 96 fish caught, item-sprite icon i379", full.record("a3").rowMetric.equals("96 fish caught")
			&& full.record("a3").icon.equals("i379.png") && full.record("a3").kind.equals("Skilling"));
		check("unknown XP stays unknown (Giant Mole)", full.record("a4").xp == null);
		check("eight frozen records are representable in the shared contract", frozenA.size() == 8);
		check("search and category filter", full.matching("vork", "All").size() == 2 && full.matching("", "Skilling").size() == 3
			&& full.matching("", "Combat").size() == 5 && full.matching("", "Progress").isEmpty() && full.matching("zzz", "All").isEmpty());

		check("C-08 current login is a labelled saved replay", a.current != null && a.current.activity.equals("Vorkath (saved replay)")
			&& a.current.xp == 38_400 && a.current.count == 12);
		FilesModel b = FilesModel.of(TestFixtures.demoB(1, 1), registry, p);
		check("account B: verified empty archive, no current login", b.archive == FilesModel.Archive.EMPTY && b.records.isEmpty()
			&& b.status.equals("Verified empty archive") && b.current == null);
		FilesModel unknown = FilesModel.of(account("A", 1).archive(Evidence.<Boolean>unknown(), Collections.emptyList()).build(), registry, p);
		check("unverified archive hides history", unknown.archive == FilesModel.Archive.UNKNOWN && unknown.records == null
			&& unknown.status.equals("Awaiting verification"));
		FilesModel out = FilesModel.of(TestFixtures.signedOut(1, 2), registry, p);
		check("signed out: account unavailable, nothing exposed", out.archive == FilesModel.Archive.LOGOUT && out.records == null && out.current == null);
		FilesModel scopedB = FilesModel.of(account("B", 1).archive(Evidence.verified(true, T0, "x"), Collections.singletonList(
			new ArchiveRecord("a2", "barrows", "barrows", T0, Duration.ofMinutes(47), 8, "chests", Evidence.unavailable(), Collections.singletonMap(4728, 1L)))).build(), registry, p);
		check("Account A presentation never applies to an account B record with the same id", scopedB.record("a2").note == null
			&& scopedB.latestSpoil() == null);
	}

	// ------------------------------------------------------------------ mounted

	private static final class Mounted {
		private final UiContext ui;
		private final FixtureDispatcher fixtures;
		private final R02Composition comp;
		private final Path evidence;
		private final List<ArchiveRecord> frozenA;
		private int generation;

		Mounted(LaneInputs lane, HomeInputs home, FrozenPackageAssets assets, Registry registry, List<ArchiveRecord> frozenA, Path evidence) {
			this.evidence = evidence;
			this.frozenA = frozenA;
			ui = new UiContext(new DefaultFontSource(), new AssetProvider() {
				public BufferedImage art(ArtKey key) {
					String f = key.candidateFile();
					return f.matches("(PR|SIG)-\\d+_.*") ? assets.art(f.substring(0, f.indexOf('_'))) : null;
				}
				public BufferedImage nativeSprite(NativeSprite sprite, Runnable onLoaded) { return null; }
				public BufferedImage item(int itemId, int quantity, Runnable onLoaded) { return null; }
				public String provenance() { return "Headless Lane A"; }
			});
			fixtures = new FixtureDispatcher(registry, TestFixtures.clock());
			List<RouteRegistration> pages = new ArrayList<>();
			try { pages.add(FilesPanels.registration(lane, home)); } catch (IOException e) { throw new AssertionError(e); }
			for (RouteId r : new RouteId[] {RouteId.HOME, RouteId.ACTIVITY, RouteId.CHRONICLE})
				pages.add(new RouteRegistration(r, Lane.owning(r).name(), c -> new JLabel(r.label() + " test receiver")));
			comp = new R02Composition(ui, fixtures, assets, pages);
			generation = fixtures.bind();
			comp.start(frozenLike(1));
		}

		private FixtureSnapshot frozenLike(long revision) {
			return FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(revision)
				.officialKc("vorkath", Evidence.count(48, T0, "kill log"))
				.archive(Evidence.verified(true, T0, "archive"), frozenA)
				.activity(new ActivityObservation(ActivityObservation.Mode.REPLAY, "vorkath", "a1", T0, T0, 0, Evidence.unavailable())).build();
		}

		void run() {
			try {
				comp.navigator().navigate("files");
				size(225);
				FilesPanel page = page();
				check("composition mounts Lane A Feral Files", page != null && comp.navigator().current().equals("files"));
				check("journal: hero, status with account, latest spoils, four recent sessions", accessible("files-hero").startsWith("Adventurer’s journal")
					&& accessible("files-status").equals("Saved observations · Demo A") && accessible("files-spoil").startsWith("Guthan's platebody, ×1 · Barrows")
					&& named("files-row-a1") != null && named("files-row-a4") != null && named("files-row-a5") == null);
				check("frozen row text: Barrows 8 chests · 47m active", accessible("files-row-a2").startsWith("Barrows, 8 chests · 47m active"));
				shot("01-files-journal.png");
				click("files-earlier-toggle");
				check("earlier observations fold: nothing beyond ten records, says so", texts(comp.shell()).contains("No earlier records in this fixture."));
				click("files-earlier-toggle");
				click("files-view-all");
				check("View all opens Sessions with the current-login card and every record", named("files-current") != null
					&& accessible("files-line-activity").equals("Activity: Vorkath (saved replay)") && named("files-row-a10") != null
					&& texts(comp.shell()).contains("8 shown · 8 archived"));
				shot("02-files-sessions.png");
				SearchField search = (SearchField) named("files-search");
				search.setText("barrows");
				size(225);
				check("search keeps the field and narrows the list", named("files-search") == search && named("files-row-a2") != null
					&& named("files-row-a1") == null && accessible("files-session-list").length() > 0);
				search.setText("");
				@SuppressWarnings("unchecked")
				RDropdown<String> filter = (RDropdown<String>) named("files-filter");
				filter.select("Skilling");
				size(225);
				check("category filter shows only skilling records", named("files-row-a3") != null && named("files-row-a1") == null);
				filter.select("All");
				size(225);
				JComponent row = named("files-row-a2");
				activate(row);
				FilesPanel p = page();
				check("opening a record is a local Back level", p.hasLocalBack() && comp.navigator().current().equals("files")
					&& accessible("files-detail-hero").startsWith("Barrows, "));
				check("trip summary: 8 Chests and 12,600 XP observed; unknowns unavailable", accessible("files-metrics").equals("Chests: 8, XP observed: 12,600")
					&& accessible("files-line-elapsed-time").equals("Elapsed time: Unavailable") && accessible("files-line-deaths").equals("Deaths: Unavailable"));
				check("loot tab lists observed loot", accessible("files-items").contains("Guthan's platebody ×1") && accessible("files-items").contains("Death rune ×420"));
				shot("03-files-detail-loot.png");
				click("files-detail-tab-supplies");
				check("supplies are not observed in the shared record (unknown, not zero)", texts(named("files-supplies")).contains("Not observed. Quantities are unknown."));
				click("files-detail-tab-details");
				check("C-10/C-11 details: Barrows has no account KC and 8 trip chests", accessible("files-line-account-wide-kc").equals("Account-wide KC: Unavailable")
					&& accessible("files-line-trip-chests").equals("Trip chests: 8"));
				click("files-open-chronicle");
				check("combat record opens the typed Boss Chronicle encounter", comp.navigator().currentRequest()
					.equals(RouteRequest.of("chronicle", RouteRequest.Param.ENCOUNTER, "barrows")));
				comp.shell().header().back().doClick();
				size(225);
				check("returning keeps the open record", page().hasLocalBack() && named("files-details") != null);
				comp.shell().header().back().doClick();
				size(225);
				check("header Back closes the record first and returns to Sessions", !page().hasLocalBack()
					&& comp.navigator().current().equals("files") && named("files-current") != null);
				comp.navigator().navigate(RouteRequest.of("files", RouteRequest.Param.SESSION, "a1"));
				size(225);
				check("typed SESSION payload opens that record", page().hasLocalBack() && accessible("files-detail-hero").startsWith("Vorkath, "));
				click("files-detail-tab-details");
				check("C-10 Vorkath: account-wide KC 48 beside 12 trip kills", accessible("files-line-account-wide-kc").equals("Account-wide KC: 48")
					&& accessible("files-line-trip-kills").equals("Trip kills: 12"));
				comp.shell().header().back().doClick();
				size(225);
				comp.navigator().navigate(RouteRequest.of("files", RouteRequest.Param.SESSION, "zz9"));
				size(225);
				check("an unknown SESSION id opens nothing", !page().hasLocalBack());
				check("same-scope revision keeps the view", fixtures.publish(frozenLike(2)) && page() == p);
				layout();
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.demoB(generation, 1));
				comp.navigator().navigate("files");
				size(225);
				check("new account scope discards the page: B sees a verified empty archive only", page() != p
					&& accessible("files-status").equals("Verified empty archive · Demo B") && named("files-row-a1") == null
					&& texts(comp.shell()).contains("No completed sessions in this verified archive."));
				shot("04-files-account-b.png");
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.signedOut(generation, 2));
				comp.navigator().navigate("files");
				size(225);
				check("signed out hides saved records", accessible("files-status").equals("Account unavailable")
					&& texts(comp.shell()).contains("No active account. Saved records are hidden."));
			} finally { comp.dispose(); }
		}

		private void layout() {
			String[][] views = {{"journal", null}, {"sessions", null}, {"detail", "a2"}};
			for (TextScale scale : TextScale.values()) {
				ui.setTextScale(scale);
				for (String[] v : views) {
					comp.navigator().navigate("files");
					if (page().hasLocalBack()) page().localBack();
					if (v[0].equals("sessions")) click("files-mode-sessions");
					if (v[0].equals("journal")) click("files-mode-journal");
					if (v[1] != null) comp.navigator().navigate(RouteRequest.of("files", RouteRequest.Param.SESSION, v[1]));
					for (int width : new int[] {225, 200}) {
						size(width);
						List<String> issues = new ArrayList<>();
						audit(page(), issues);
						check(scale + " " + v[0] + " at " + width + "px: no clipped or overflowing component " + issues, issues.isEmpty());
					}
				}
			}
			ui.setTextScale(TextScale.LARGE);
			size(225);
			shot("05-files-detail-large-text.png");
			ui.setTextScale(TextScale.DEFAULT);
			if (page().hasLocalBack()) page().localBack();
			click("files-mode-journal");
		}

		private FilesPanel page() { return find(comp.shell()); }

		private static FilesPanel find(Component c) {
			if (c instanceof FilesPanel) return (FilesPanel) c;
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
				FilesPanel p = find(k);
				if (p != null) return p;
			}
			return null;
		}

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

		private String accessible(String name) {
			JComponent c = named(name);
			return c == null ? "<missing " + name + ">" : String.valueOf(c.getAccessibleContext().getAccessibleName());
		}

		private static List<String> texts(Component root) {
			List<String> out = new ArrayList<>();
			collect(root, out);
			return out;
		}

		private static void collect(Component c, List<String> out) {
			if (c == null || !c.isVisible()) return;
			if (c instanceof com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Label)
				out.add(((com.feralenterprises.raccoon.playtest.r02.panel.home.AdventureUi.Label) c).text());
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) collect(k, out);
		}

		private void click(String name) {
			JComponent c = named(name);
			if (c == null) throw new AssertionError("missing " + name);
			if (c instanceof AbstractButton) ((AbstractButton) c).doClick();
			else activate(c);
			size(225);
		}

		private void activate(JComponent c) {
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
