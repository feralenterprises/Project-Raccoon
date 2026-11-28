package com.feralenterprises.raccoon.playtest.r02.panel.outfit;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.EncounterDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.ItemDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.panel.outfit.CombatCatalog.Encounter;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.SegmentedSelector;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Headless Lane B regressions. Args: verified Lane Inputs ZIP, extracted frozen R02, optional evidence directory. */
public final class CombatLaneTests {
	private static int checks;
	private final CombatCatalog catalog;
	private final R02Composition comp;
	private final FixtureDispatcher fixtures;
	private final UiContext ui;
	private final int generation;
	private final Path evidence;

	private CombatLaneTests(CombatCatalog catalog, Registry registry, R02Assets assets, Path evidence) {
		this.catalog = catalog;
		this.evidence = evidence;
		ui = new UiContext(new DefaultFontSource(), new AssetProvider() {
			public BufferedImage art(ArtKey key) { return null; }
			public BufferedImage nativeSprite(NativeSprite sprite, Runnable onLoaded) { return null; }
			public BufferedImage item(int itemId, int quantity, Runnable onLoaded) { return null; }
			public String provenance() { return "Headless Lane B"; }
		});
		fixtures = new FixtureDispatcher(registry, TestFixtures.clock());
		List<RouteRegistration> pages = List.of(OutfitPanels.registration(catalog),
			com.feralenterprises.raccoon.playtest.r02.panel.chronicle.ChroniclePanels.registration(catalog),
			new RouteRegistration(RouteId.FILES, Lane.ADVENTURE.name(), c -> new JLabel("Files test receiver")),
			new RouteRegistration(RouteId.PATHFINDER, Lane.PROGRESSION.name(), c -> new JLabel("Pathfinder test receiver")));
		comp = new R02Composition(ui, fixtures, assets, pages);
		generation = fixtures.bind();
		comp.start(TestFixtures.demoA(generation, 1));
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) throw new IllegalArgumentException("Lane Inputs ZIP and extracted R02 required");
		Path input = Path.of(args[0]);
		Path frozen = Path.of(args[1]);
		CombatCatalog catalog = CombatCatalog.load(input, frozen);
		check("frozen Vorkath scene and surface resolve", catalog.art("frozen-stone", true) != null
			&& catalog.art("frozen-stone", false) != null);
		Registry registry = registry(input, catalog);
		FrozenPackageAssets realAssets = new FrozenPackageAssets(frozen);
		check("authentic Vorkath portrait and inventory sprite", realAssets.portrait("vorkath") != null && realAssets.item(9185) != null);
		check("exact Tormented Demon portrait accepted by the shared resolver", realAssets.portrait("tormented-demons") != null
			&& realAssets.refused().isEmpty());
		check("supplemental bolts resolve without a shared image", catalog.itemImage(R02Assets.none(), 892) != null);
		rejectDifferentArchive(input);
		Path out = args.length > 2 ? Path.of(args[2]) : null;
		check("production lane adapter registers both routes", OutfitPanels.registrations(input, frozen).size() == 2);
		SwingUtilities.invokeAndWait(() -> {
			CombatLaneTests tests = new CombatLaneTests(catalog, registry, realAssets, out);
			try {
				tests.outfit();
				tests.chronicle();
				tests.catalogAndLayout();
				tests.scopeIsolation();
			} finally {
				tests.comp.dispose();
			}
			CombatLaneTests fallback = new CombatLaneTests(catalog, registry, R02Assets.none(), null);
			try {
				fallback.navigate(RouteId.CHRONICLE, "vorkath");
				fallback.tabs(1);
				check("accessible missing-sprite fallback", findAccessible(fallback.page(), "Dragonbone necklace"));
				fallback.audit("missing assets at 225 content");
			} finally {
				fallback.comp.dispose();
			}
		});
		System.out.println("CombatLaneTests: " + checks + " passed, 0 failed");
		if (!GraphicsEnvironment.isHeadless()) {
			int priorChecks = checks;
			focus(catalog, registry, realAssets);
			System.out.println("CombatLaneFocusTests: " + (checks - priorChecks) + " passed, 0 failed");
		}
	}

	private static void focus(CombatCatalog catalog, Registry registry, R02Assets assets) throws Exception {
		CombatLaneTests[] tests = new CombatLaneTests[1];
		JFrame[] window = new JFrame[1];
		SwingUtilities.invokeAndWait(() -> {
			tests[0] = new CombatLaneTests(catalog, registry, assets, null);
			tests[0].navigate(RouteId.OUTFIT, "scurrius");
			window[0] = new JFrame("Lane B focus verification");
			window[0].setContentPane(tests[0].comp.shell());
			window[0].setSize(268, 900);
			window[0].setVisible(true);
		});
		CombatLaneTests lane = tests[0];
		try {
			Robot robot = new Robot();
			robot.waitForIdle();
			SwingUtilities.invokeAndWait(() -> lane.get("inventory-14", JComponent.class).requestFocusInWindow());
			robot.waitForIdle();
			SwingUtilities.invokeAndWait(() -> check("slot acquires keyboard focus", lane.get("inventory-14", JComponent.class).isFocusOwner()));
			robot.keyPress(KeyEvent.VK_ENTER);
			robot.keyRelease(KeyEvent.VK_ENTER);
			robot.waitForIdle();
			SwingUtilities.invokeAndWait(() -> check("Enter focuses the slot detail Close action",
				lane.get("outfit-slot-close", JComponent.class).isFocusOwner()));
			robot.keyPress(KeyEvent.VK_ESCAPE);
			robot.keyRelease(KeyEvent.VK_ESCAPE);
			robot.waitForIdle();
			SwingUtilities.invokeAndWait(() -> check("Escape restores focus to the originating slot",
				!lane.local().hasLocalBack() && lane.get("inventory-14", JComponent.class).isFocusOwner()));
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				lane.comp.dispose();
				window[0].dispose();
			});
		}
	}

	private static Registry registry(Path input, CombatCatalog catalog) throws IOException {
		// Test assembly only. The app consumes Claude 1's registry; Lane B never publishes account or registry data.
		List<ItemDef> items = new ArrayList<>();
		try (ZipFile zip = new ZipFile(input.toFile())) {
			String json = new String(zip.getInputStream(zip.getEntry("R02-Lane-Inputs/shared/item-names.json")).readAllBytes(), StandardCharsets.UTF_8);
			LaneJson.read(json).forEach((id, name) -> items.add(new ItemDef(Integer.parseInt(id), (String) name, false, "")));
		}
		List<EncounterDef> rows = new ArrayList<>();
		for (Encounter e : catalog.encounters()) rows.add(new EncounterDef(e.key, e.name, e.category, e.difficulty, e.key));
		return new Registry(items, rows);
	}

	private static void rejectDifferentArchive(Path input) throws Exception {
		Path bad = Files.createTempFile("combat-integrity-", ".zip");
		try {
			byte[] bytes = Files.readAllBytes(input);
			bytes[bytes.length - 1] ^= 1;
			Files.write(bad, bytes);
			try {
				CombatCatalog.load(bad);
				throw new AssertionError("modified archive accepted");
			} catch (IOException e) {
				check("modified archive refused by SHA-256", e.getMessage().contains("SHA-256"));
			}
		} finally {
			Files.delete(bad);
		}
	}

	private void outfit() {
		navigate(RouteId.OUTFIT, "vorkath");
		check("D-15 shared exact item names", fixtures.registry().itemName(1409).equals("Iban's staff")
			&& fixtures.registry().itemName(12494).equals("Ancient chaps")
			&& fixtures.registry().itemName(8013).equals("Teleport to house")
			&& fixtures.registry().itemName(153).equals("Fishing potion(2)"));
		check("11 gear positions and 28 inventory positions", count(page(), "gear-") == 11 && count(page(), "inventory-") == 28);
		check("Vorkath defaults to its archived Ranged kit", hasText("Ranged crossbow") && hasText("23 / 28"));
		screenshot("01-outfitter-vorkath.png");
		get("outfit-search", JTextField.class).setText("brother not present");
		check("Outfitter search empty state", hasText("No matching bosses."));
		get("outfit-search", JTextField.class).setText("Dharok");
		check("Outfitter supports encounter aliases", get("outfit-encounter", JComboBox.class).getItemCount() == 1);
		get("outfit-search", JTextField.class).postActionEvent();
		check("search Enter selects matching encounter", hasText("Barrows"));
		navigate(RouteId.OUTFIT, "scurrius");
		check("D-22 unsupported bronze knife slot is explicitly unknown",
			get("inventory-14", AbstractButton.class).getAccessibleContext().getAccessibleName().contains("Unknown"));
		invoke(get("inventory-14", JComponent.class), "slot");
		check("D-16 keyboard slot detail shows no evidence", hasText("Unknown · no source evidence") && local().hasLocalBack());
		invoke(page(), "closeSlot");
		check("D-16 Escape closes only slot detail", !local().hasLocalBack() && comp.navigator().current().equals("outfit"));
		click("inventory-27");
		check("D-16 omission versus explicit null", hasText("Confirmed empty"));
		comp.shell().header().back().doClick();
		check("header Back closes local detail first", !local().hasLocalBack() && comp.navigator().current().equals("outfit"));
		get("outfit-source", JComboBox.class).setSelectedIndex(1);
		check("Best in Bank uses authoritative bank status with no inferred plan", hasText("No account-aware plan available")
			&& unknown("gear-4"));
		get("outfit-source", JComboBox.class).setSelectedIndex(2);
		check("initial Custom is unavailable", unknown("gear-4"));
		click("outfit-custom");
		check("Custom copies the explicit archived fixture only", hasText("Custom · session demo") && !unknown("gear-4"));
		get("outfit-source", JComboBox.class).setSelectedIndex(0);
		get("outfit-source", JComboBox.class).setSelectedIndex(2);
		check("per-scope Custom survives source changes", !unknown("gear-4"));
		click("outfit-custom");
		check("clearing Custom does not infer Wiki gear", unknown("gear-4"));
		click("outfit-custom");
		navigate(RouteId.OUTFIT, "tormented-demons");
		check("no-kit encounter still opens with 39 unknown slots", hasText("No verified loadout fixture")
			&& count(page(), "inventory-") == 28 && unknown("inventory-0"));
		Encounter switchable = catalog.encounters().stream().filter(e -> !e.switchMode.isEmpty()).findFirst().orElseThrow();
		navigate(RouteId.OUTFIT, switchable.key);
		click("outfit-style-" + switchable.switchPrimary);
		click("outfit-switch");
		check("archived switch detail is expandable", get("outfit-switch", AbstractButton.class).getText().contains("Hide"));
		click("outfit-compare-" + switchable.switchTargets.get(0));
		check("comparison switches to the target archive", hasText(switchable.kits.get(switchable.switchTargets.get(0)).label));
	}

	private void chronicle() {
		comp.navigator().navigate("chronicle");
		check("Chronicle index lists all 60 encounters", count(page(), "chronicle-boss-") == 60);
		screenshot("02-chronicle-index.png");
		get("chronicle-sort", JComboBox.class).setSelectedIndex(2);
		check("Chronicle sorts A–Z", names(page(), "chronicle-boss-").get(0).equals("chronicle-boss-abyssal-sire"));
		get("chronicle-category", JComboBox.class).setSelectedIndex(2);
		check("category includes verified Wilderness vocabulary", count(page(), "chronicle-boss-") == 11);
		get("chronicle-category", JComboBox.class).setSelectedIndex(0);
		get("chronicle-approach", SegmentedSelector.class).setSelected(3);
		check("approaches filter documented magic without inventing a method", count(page(), "chronicle-boss-") < 60);
		get("chronicle-approach", SegmentedSelector.class).setSelected(0);
		get("chronicle-search", JTextField.class).setText("vorkath");
		click("chronicle-boss-vorkath");
		check("C-01/C-10 separate official 48 and archived 12", hasText("Official KC: 48") && hasText("Recorded trip kills: 12")
			&& hasText("Dragon Slayer II complete") && find(page(), "chronicle-plan") == null);
		tabs(1);
		check("archived Vorkath Drops use exact archived item and observed quantity", hasText("Dragonbone necklace")
			&& hasText("Coins × 280,000"));
		screenshot("03-chronicle-vorkath-drops.png");
		tabs(2);
		click("chronicle-trip-a1");
		check("D-13 typed archived Files navigation", comp.navigator().currentRequest().equals(RouteRequest.of("files", RouteRequest.Param.SESSION, "a1")));
		comp.shell().header().back().doClick();
		check("return from Files preserves Trips", get("chronicle-tabs", SegmentedSelector.class).selected() == 2
			&& find(page(), "chronicle-trip-a1") != null);
		click("chronicle-outfit");
		check("typed Vorkath request opens real Outfitter", comp.navigator().currentRequest().equals(RouteRequest.of("outfit", RouteRequest.Param.ENCOUNTER, "vorkath"))
			&& hasText("Ranged crossbow"));
		comp.shell().header().back().doClick();
		check("return from Outfitter restores Chronicle details", get("chronicle-tabs", SegmentedSelector.class).selected() == 2);
		comp.shell().header().back().doClick();
		check("local Chronicle Back preserves query and sort", get("chronicle-search", JTextField.class).getText().equals("vorkath")
			&& get("chronicle-sort", JComboBox.class).getSelectedIndex() == 2 && count(page(), "chronicle-boss-") == 1);
		navigate(RouteId.CHRONICLE, "scurrius");
		check("C-04 same authoritative milestone KC", hasText("Official KC: 12"));
		navigate(RouteId.CHRONICLE, "brutus");
		check("C-03 explicit unmet observation alone can lock access", hasText("Locked · The Ides of Milk unmet"));
		click("chronicle-plan");
		check("typed Pathfinder requirement target", comp.navigator().currentRequest().equals(
			RouteRequest.of("pathfinder", RouteRequest.Param.MILESTONE, "the-ides-of-milk")));
		senderBack();
		navigate(RouteId.CHRONICLE, "dagannoth-kings");
		check("D-18 three independently unknown Dagannoth counts", hasText("Rex KC: Unknown") && hasText("Prime KC: Unknown")
			&& hasText("Supreme KC: Unknown"));
		navigate(RouteId.CHRONICLE, "hespori");
		check("D-18 explicit unknown difficulty distinct from missing KC", hasText("Difficulty unavailable") && hasText("Official KC: Unknown"));
	}

	/**
	 * Integrated-repair regression (Solis checkpoint 2026-11-27): a typed ENCOUNTER entry into Chronicle is the sender's
	 * context, as in Outfitter and Pathfinder, so one Back returns to the sender; a detail opened from the index stays a
	 * local level; a plain visit drops a typed detail and Back-returning keeps it.
	 */
	private void senderBack() {
		comp.navigator().navigate("outfit");
		navigate(RouteId.CHRONICLE, "vorkath");
		check("typed ENCOUNTER opens the Chronicle detail", find(page(), "chronicle-tabs") != null && !local().hasLocalBack());
		RouteRequest typedChronicle = comp.navigator().currentRequest();
		int senderHistoryDepth = comp.navigator().historyDepth();
		check("reselecting the current destination preserves the typed detail and history",
			!comp.navigator().navigate("chronicle") && typedChronicle.equals(comp.navigator().currentRequest())
				&& comp.navigator().historyDepth() == senderHistoryDepth && find(page(), "chronicle-tabs") != null);
		tabs(2);
		click("chronicle-outfit");
		check("the Chronicle action opens the typed Outfitter destination",
			RouteRequest.of("outfit", RouteRequest.Param.ENCOUNTER, "vorkath").equals(comp.navigator().currentRequest()));
		comp.shell().header().back().doClick();
		check("Back from Outfitter restores the typed Chronicle request and its selected tab",
			typedChronicle.equals(comp.navigator().currentRequest())
				&& get("chronicle-tabs", SegmentedSelector.class).selected() == 2 && !local().hasLocalBack());
		comp.shell().header().back().doClick();
		check("one Back after a typed Chronicle entry returns to the sender", "outfit".equals(comp.navigator().current()));
		comp.navigator().navigate("chronicle");
		navigate(RouteId.OUTFIT, "vorkath");
		comp.shell().header().back().doClick();
		check("one Back after a typed Outfitter entry returns to the sender (same rule)", "chronicle".equals(comp.navigator().current()));
		navigate(RouteId.CHRONICLE, "vorkath");
		comp.navigator().navigate("home");
		comp.navigator().navigate("chronicle");
		check("a plain Chronicle visit drops the sender's typed detail", find(page(), "chronicle-tabs") == null && count(page(), "chronicle-boss-") > 0);
		get("chronicle-search", JTextField.class).setText("vorkath");
		click("chronicle-boss-vorkath");
		check("a detail opened from the index is a local Back level", local().hasLocalBack());
		comp.shell().header().back().doClick();
		check("local Back closes the index-opened detail and stays in Chronicle", "chronicle".equals(comp.navigator().current())
			&& find(page(), "chronicle-tabs") == null);
		get("chronicle-search", JTextField.class).setText("");
	}

	private void catalogAndLayout() {
		int rewardRows = 0;
		for (Encounter e : catalog.encounters()) {
			navigate(RouteId.CHRONICLE, e.key);
			check("Overview " + e.key, hasText("Your record"));
			tabs(1);
			int rows = count(page(), "chronicle-reward");
			check("Drops " + e.key, rows == e.rewards.size());
			rewardRows += rows;
			tabs(2);
			check("Trips " + e.key, hasText("Recorded sessions"));
		}
		check("D-17 all 180 combinations and 289 archived reward rows", rewardRows == 289);
		navigate(RouteId.CHRONICLE, "barrows");
		tabs(2);
		for (TextScale scale : TextScale.values()) {
			ui.setTextScale(scale);
			audit("D-12 Barrows Trips " + scale);
			RButton outfitter = get("chronicle-outfit", RButton.class);
			check("D-12 Outfitter CTA intrinsic and reachable " + scale, outfitter.getHeight() == outfitter.heightForWidth(outfitter.getWidth())
				&& outfitter.isEnabled());
		}
		ui.setTextScale(TextScale.DEFAULT);
		for (Encounter e : catalog.encounters()) {
			navigate(RouteId.OUTFIT, e.key);
			for (String style : e.kits.keySet()) {
				click("outfit-style-" + style);
				check("frozen " + e.key + " " + style + " available", hasText(e.kits.get(style).label));
				audit("Outfitter " + e.key + " " + style);
			}
		}
	}

	private void scopeIsolation() {
		ArchiveRecord a2 = new ArchiveRecord("a2","barrows","barrows",TestFixtures.T0,
			Duration.ofMinutes(47),8,"chests",Evidence.unavailable(),Map.of());
		FixtureSnapshot barrows = FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(2)
			.archive(Evidence.verified(true,TestFixtures.T0,"Barrows archive test"),List.of(a2)).build();
		navigate(RouteId.OUTFIT, "scurrius");
		get("outfit-source", JComboBox.class).setSelectedIndex(1);
		click("inventory-14");
		check("same-scope revision refreshes bank evidence without discarding slot detail", fixtures.publish(barrows)
			&& hasText("Bank snapshot unavailable") && local().hasLocalBack());
		invoke(page(), "closeSlot");
		comp.navigator().navigate("chronicle");
		if (local().hasLocalBack()) comp.shell().header().back().doClick();
		check("D-11 all unknown never reports a zero completion headline", hasText("Boss KC unavailable") && !hasText("0 / 60 bosses defeated"));
		navigate(RouteId.CHRONICLE, "barrows");
		check("C-11 archive chest count does not create official KC", hasText("Recorded trip chests: 8") && hasText("Official KC: Unknown"));
		tabs(2);
		check("C-11 saved row preserves eight chests", get("chronicle-trip-a2", JComponent.class).getAccessibleContext().getAccessibleName().contains("8 chests"));
		screenshot("04-chronicle-barrows-trips.png");
		ArchiveRecord mislabeled = new ArchiveRecord("a2", "barrows", "barrows", TestFixtures.T0,
			Duration.ofMinutes(47), 8, "kills", Evidence.unavailable(), Map.of());
		FixtureSnapshot partial = FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(3)
			.archive(Evidence.verified(true, TestFixtures.T0, "Barrows invalid unit test"), List.of(mislabeled)).build();
		check("C-11 Barrows refuses a mislabeled kill count without changing the open tab", fixtures.publish(partial)
			&& get("chronicle-tabs", SegmentedSelector.class).selected() == 2
			&& get("chronicle-trip-a2", JComponent.class).getAccessibleContext().getAccessibleName().contains("Count unavailable"));
		tabs(0);
		check("C-11 conflicting record does not establish chest count or KC",
			hasText("Recorded trip chests: Unknown") && hasText("Official KC: Unknown"));
		navigate(RouteId.OUTFIT, "scurrius");
		get("outfit-source", JComboBox.class).setSelectedIndex(2);
		check("A scoped copy survives within A", !unknown("gear-4"));
		click("inventory-14");
		JComponent retired = page();
		check("scope switch to B", fixtures.publish(TestFixtures.demoB(generation, 1)));
		check("scope change returns Home and clears historic Back", "home".equals(comp.navigator().current()) && !comp.shell().header().back().isEnabled());
		navigate(RouteId.OUTFIT, "scurrius");
		get("outfit-source", JComboBox.class).setSelectedIndex(2);
		check("B neither retains A custom nor open slot", page() != retired && !local().hasLocalBack() && unknown("gear-4"));
		navigate(RouteId.CHRONICLE, "vorkath");
		check("C-01/C-10 B verified zero and quest unmet, no A count", hasText("Official KC: 0")
			&& hasText("Locked · Dragon Slayer II unmet") && !hasText("48"));
		tabs(2);
		check("B has no A record", find(page(), "chronicle-trip-a1") == null);
		navigate(RouteId.CHRONICLE, "brutus");
		check("C-03 unknown quest stays unverified", hasText("Access unverified") && find(page(), "chronicle-plan") == null);
		navigate(RouteId.CHRONICLE, "scurrius");
		check("C-04 B official 3 is its own observation", hasText("Official KC: 3"));
		check("old generation is refused", !fixtures.publish(TestFixtures.demoA(generation - 1, 99)));
		check("stale B revision is refused", !fixtures.publish(TestFixtures.demoB(generation, 0)));
		check("logout retires pages and subscriptions", fixtures.publish(TestFixtures.signedOut(generation, 2))
			&& comp.openPageSubscriptions() == 0);
	}

	private void tabs(int n) { get("chronicle-tabs", SegmentedSelector.class).setSelected(n); }
	private LocalBack local() { return (LocalBack) page(); }
	private void navigate(RouteId route, String encounter) {
		comp.navigator().navigate(RouteRequest.of(route.id(), RouteRequest.Param.ENCOUNTER, encounter));
	}
	private JComponent page() { return comp.shell().currentHost().content(); }
	private boolean unknown(String button) {
		return get(button, JComponent.class).getAccessibleContext().getAccessibleName().contains("Unknown");
	}
	private void click(String name) { get(name, AbstractButton.class).doClick(0); }
	private <T extends Component> T get(String name, Class<T> type) {
		Component c = find(page(), name);
		if (c == null) throw new AssertionError("No visible component " + name);
		return type.cast(c);
	}

	private boolean hasText(String fragment) { return texts(page()).stream().anyMatch(t -> t.contains(fragment)); }
	private static List<String> texts(Container c) {
		List<String> values = new ArrayList<>();
		for (Component child : c.getComponents()) if (child.isVisible()) {
			if (child instanceof TextBlock) values.add(((TextBlock) child).text());
			if (child instanceof Container) values.addAll(texts((Container) child));
		}
		return values;
	}
	private static Component find(Container c, String name) {
		for (Component child : c.getComponents()) if (child.isVisible()) {
			if (name.equals(child.getName())) return child;
			if (child instanceof Container) {
				Component hit = find((Container) child, name);
				if (hit != null) return hit;
			}
		}
		return null;
	}
	private static boolean findAccessible(Container c, String name) {
		for (Component child : c.getComponents()) if (child.isVisible()) {
			if (child.getAccessibleContext() != null && name.equals(child.getAccessibleContext().getAccessibleName())) return true;
			if (child instanceof Container && findAccessible((Container) child, name)) return true;
		}
		return false;
	}
	private static List<String> names(Container c, String prefix) {
		List<String> values = new ArrayList<>();
		for (Component child : c.getComponents()) if (child.isVisible()) {
			if (child.getName() != null && child.getName().startsWith(prefix)) values.add(child.getName());
			if (child instanceof Container) values.addAll(names((Container) child, prefix));
		}
		return values;
	}
	private static int count(Container c, String prefix) { return names(c, prefix).size(); }
	private static void invoke(JComponent c, String action) {
		c.getActionMap().get(action).actionPerformed(new java.awt.event.ActionEvent(c, 0, action));
	}
	private void sizeShell() {
		comp.shell().setSize(268, 900);
		for (int pass = 0; pass < 3; pass++) layout(comp.shell());
	}

	private static void layout(Container parent) {
		parent.doLayout();
		for (Component child : parent.getComponents()) {
			if (child.isVisible() && child instanceof Container) layout((Container) child);
		}
	}

	private static void measure(Container parent, List<String> issues) {
		for (Component child : parent.getComponents()) {
			if (!child.isVisible()) continue;
			if (child instanceof HeightForWidth && child.getWidth() > 0
				&& child.getHeight() < ((HeightForWidth) child).heightForWidth(child.getWidth())) {
				issues.add(child.getClass().getSimpleName() + " " + child.getName());
			}
			if (child instanceof Container) measure((Container) child, issues);
		}
	}

	private void audit(String name) {
		sizeShell();
		List<String> issues = new ArrayList<>();
		measure(page(), issues);
		check(name + " clipped: " + issues, issues.isEmpty());
	}

	private void screenshot(String name) {
		if (evidence == null) return;
		sizeShell();
		BufferedImage image = new BufferedImage(comp.shell().getWidth(), comp.shell().getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			comp.shell().printAll(graphics);
			Files.createDirectories(evidence);
			ImageIO.write(image, "png", evidence.resolve(name).toFile());
		} catch (IOException e) {
			throw new AssertionError(e);
		} finally {
			graphics.dispose();
		}
	}
	private static void check(String name, boolean ok) {
		if (!ok) throw new AssertionError(name);
		checks++;
	}
}
