package com.feralenterprises.raccoon.playtest.r02.panel.bank;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.SearchField;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Lane C Bank Memory regressions on the accepted shared composition (simulated accounts, no RuneLite). Args: the
 * accepted Lane Inputs ZIP, the extracted frozen {@code Project-Raccoon-R02/}, optional evidence directory.
 */
public final class BankLaneTests {
	private static int checks, failures;
	private static final Instant T0 = TestFixtures.T0;

	public static void main(String[] args) throws Exception {
		if (args.length < 2) throw new IllegalArgumentException("Lane Inputs ZIP and extracted Project-Raccoon-R02 required");
		Path zip = Paths.get(args[0]), frozen = Paths.get(args[1]);
		Path evidence = args.length > 2 ? Paths.get(args[2]) : null;
		BankInputs in = BankInputs.load(LaneInputs.open(zip));
		check("lane-input archive verifies (" + in.lane().verifiedCount() + " files)", in.lane().verifiedCount() == 939);
		check("frozen bank catalog has 37 exact item IDs", in.catalog.size() == 37);
		check("frozen state copy present", in.states.get("incomplete")[0].equals("Incomplete observation"));
		Path dir = Files.createTempDirectory("bank-tamper");
		byte[] b = Files.readAllBytes(zip);
		b[b.length / 3] ^= 1;
		Files.write(dir.resolve("x.zip"), b);
		boolean refused;
		try { LaneInputs.open(dir.resolve("x.zip")); refused = false; } catch (IOException e) { refused = e.getMessage().contains("SHA-256"); }
		check("tampered lane-input archive is refused", refused);
		model(in);
		FrozenPackageAssets assets = new FrozenPackageAssets(frozen);
		SwingUtilities.invokeAndWait(() -> new Mounted(in, assets, evidence).run());
		System.out.println("Bank Memory lane: " + (checks - failures) + "/" + checks + " checks passed");
		if (failures > 0) System.exit(1);
	}

	static Map<Integer, Long> frozenBank(BankInputs in) {
		Map<Integer, Long> items = new LinkedHashMap<>();
		try {
			for (Object o : MiniJson.list(in.lane().json("shared/bank-and-capture.json"), "bankCatalog"))
				items.put((int) MiniJson.num(o, "id"), MiniJson.num(o, "quantity"));
		} catch (IOException e) { throw new AssertionError(e); }
		return items;
	}

	private static FixtureSnapshot bank(String account, int epoch, ContainerState state, Map<Integer, Long> items) {
		return FixtureSnapshot.builder(AccountScope.of(account, epoch)).generation(1).revision(1)
			.container(new ContainerObservation(Container.BANK, state, T0, "test", items)).build();
	}

	private static void model(BankInputs in) {
		Registry reg = TestFixtures.registry();
		BankModel a = BankModel.of(TestFixtures.demoA(1, 1), reg, in);
		check("demo A current bank: verified, five exact item types", a.state == BankModel.State.POPULATED && a.current
			&& a.statusLabel.equals("Verified · bank open") && a.countText.equals("5") && a.countLabel.equals("Verified item types")
			&& a.quantityLabel.equals("Quantity"));
		check("exact identities and quantities from the shared observation", a.items.stream().map(r -> r.id + "=" + r.quantity)
			.collect(Collectors.joining(",")).equals("995=2350840,1511=1290,10828=1,21512=16,29589=1"));
		check("names come from the frozen catalog for the same ID", a.items.get(0).name.equals("Coins") && a.items.get(1).name.equals("Logs"));
		BankModel unknownId = BankModel.of(bank("A", 1, ContainerState.CURRENT, Collections.singletonMap(99_999, 3L)), reg, in);
		check("an unknown ID keeps its exact identity (never substituted)", unknownId.items.get(0).name.equals("Item 99999")
			&& unknownId.items.get(0).id == 99_999 && unknownId.items.get(0).unitPrice == null && unknownId.valueText.equals("Unknown"));

		BankModel full = BankModel.of(bank("A", 1, ContainerState.CURRENT, frozenBank(in)), reg, in);
		check("frozen-equivalent: 37 types, partial estimate 6.96m gp, 27/37 priced", full.countText.equals("37")
			&& full.valueText.equals("6.96m gp") && full.valueLabel.equals("Partial estimate")
			&& full.valueNote.equals("27/37 types priced · Illustrative prices, not live GE values."));
		check("search by name and exact ID", full.rows("All", "shark", BankModel.Sort.ID).size() == 1
			&& full.rows("All", "385", BankModel.Sort.ID).get(0).name.equals("Shark") && full.rows("All", "38", BankModel.Sort.ID).isEmpty());
		check("category filter uses the frozen grouping", full.rows("Gear", "", BankModel.Sort.ID).stream().allMatch(r -> r.category.equals("Gear"))
			&& !full.rows("Gear", "", BankModel.Sort.ID).isEmpty() && full.rows("Supplies", "", BankModel.Sort.ID).stream().anyMatch(r -> r.id == 385));
		List<BankModel.Row> byQty = full.rows("All", "", BankModel.Sort.QUANTITY);
		check("sort by quantity, ties by ID", byQty.get(0).id == 995 && byQty.get(byQty.size() - 1).quantity == 1);
		check("result count text", full.resultCount(3).equals("3 of 37 types") && full.resultCount(37).equals("37 item types"));

		BankModel hist = BankModel.of(bank("A", 1, ContainerState.HISTORICAL, frozenBank(in)), reg, in);
		check("historical: last seen counts only, never current", hist.state == BankModel.State.HISTORICAL && !hist.current
			&& hist.countLabel.equals("Last seen types") && hist.quantityLabel.equals("Last seen")
			&& hist.accountNote.equals("Demo account A · Last verified only") && hist.statusLabel.equals("Historical · bank closed"));
		BankModel inc = BankModel.of(bank("A", 1, ContainerState.INCOMPLETE, frozenBank(in)), reg, in);
		check("incomplete: every record withheld, no ownership claim", inc.items == null && inc.valueText.equals("Unknown")
			&& inc.message(0)[0].equals("No complete snapshot") && inc.message(0)[1].equals("Partial records withheld. No ownership claims can be made.")
			&& !inc.controlsEnabled());
		BankModel empty = BankModel.of(bank("A", 1, ContainerState.CURRENT, Collections.emptyMap()), reg, in);
		check("verified empty bank", empty.state == BankModel.State.EMPTY && empty.statusLabel.equals("Verified empty · bank open")
			&& empty.message(0)[0].equals("Bank is empty") && empty.valueText.equals("0 gp") && !empty.controlsEnabled());
		BankModel histEmpty = BankModel.of(bank("A", 1, ContainerState.HISTORICAL, Collections.emptyMap()), reg, in);
		check("historical empty: current contents unknown", histEmpty.message(0)[0].equals("Last verified bank was empty"));
		BankModel awaiting = BankModel.of(FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(1).revision(1).build(), reg, in);
		check("no observation: awaiting verification", awaiting.state == BankModel.State.AWAITING && awaiting.items == null
			&& awaiting.message(0)[0].equals("No bank captured") && awaiting.accountNote.equals("Demo account A · No verified snapshot"));
		BankModel out = BankModel.of(TestFixtures.signedOut(1, 2), reg, in);
		check("signed out: nothing exposed", out.state == BankModel.State.LOGOUT && out.items == null
			&& out.message(0)[0].equals("Account unavailable") && out.accountNote.equals("No active account · Offline demo"));
		BankModel bAccount = BankModel.of(TestFixtures.demoB(1, 1), reg, in);
		check("account B shows only its own observation", bAccount.account.equals("B")
			&& (bAccount.items == null || bAccount.items.stream().noneMatch(r -> r.id == 29589 && r.quantity == 1 && a.account.equals("B"))));
	}

	private static final class Mounted {
		private final UiContext ui;
		private final FixtureDispatcher fixtures;
		private final R02Composition comp;
		private final Path evidence;
		private final BankInputs in;
		private int generation;

		Mounted(BankInputs in, FrozenPackageAssets assets, Path evidence) {
			this.in = in;
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
			pages.add(BankPanels.registration(in));
			pages.add(new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), c -> new JLabel("Home test receiver")));
			comp = new R02Composition(ui, fixtures, assets, pages);
			generation = fixtures.bind();
			comp.start(frozenLike(1));
		}

		private FixtureSnapshot frozenLike(long revision) {
			return FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(revision)
				.container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "frozen-equivalent", frozenBank(in))).build();
		}

		void run() {
			try {
				comp.navigator().navigate("bank");
				size(225);
				BankPanel p = page();
				check("composition mounts Lane C Bank Memory", p != null && comp.navigator().current().equals("bank"));
				check("status, count and estimate render", text("bank-status").equals("Verified · bank open") && text("bank-count").equals("37")
					&& text("bank-value").equals("6.96m gp") && text("bank-result-count").equals("37 item types"));
				check("tiles name exact items with verified quantity", accessible("bank-item-995").equals("Coins, verified quantity 2,350,840"));
				check("authentic item sprites resolve for every frozen item (shared assets, then verified lane sprites by exact ID)",
					frozenBank(in).keySet().stream().noneMatch(id -> accessible("bank-item-" + id).contains("sprite unavailable")));
				check("lane sprite fallback is exact-ID only", in.sprite(23971) != null && in.sprite(99_999) == null);
				shot("01-bank-grid.png");
				activate("bank-item-385");
				check("a tile opens its item detail", text("bank-selection-id").equals("Item ID: 385") && text("bank-selection-qty").startsWith("× ")
					&& text("bank-selection-note").startsWith("Observed in open bank."));
				shot("02-bank-selection.png");
				activate("bank-item-385");
				check("activating again closes it", named("bank-selection") == null);
				((SearchField) named("bank-search")).setText("rune");
				size(225);
				check("search narrows and keeps the field", named("bank-item-995") == null && text("bank-result-count").contains(" of 37 types"));
				((RButton) named("bank-clear")).doClick();
				size(225);
				check("clear resets search and category", text("bank-result-count").equals("37 item types"));
				((RButton) named("bank-category-gear")).doClick();
				size(225);
				check("Gear category", named("bank-item-995") == null && named("bank-item-25867") != null);
				((SegmentedSelector) named("bank-mode")).setSelected(1);
				size(225);
				check("list mode shows full quantities", named("bank-list") != null && named("bank-grid") == null);
				shot("03-bank-list-gear.png");
				check("same-scope revision keeps filter and mode", fixtures.publish(frozenLike(2)) && page() == p && size(225)
					&& named("bank-list") != null && named("bank-item-995") == null);
				check("an older revision is refused", !fixtures.publish(frozenLike(1)));
				FixtureSnapshot hist = FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(3)
					.container(new ContainerObservation(Container.BANK, ContainerState.HISTORICAL, T0, "x", frozenBank(in))).build();
				fixtures.publish(hist);
				size(225);
				check("bank closed: historical counts relabelled last seen", text("bank-status").equals("Historical · bank closed")
					&& text("bank-qty-label").equals("Last seen") && accessible("bank-item-25867").startsWith("Bow of Faerdhinen (c), last verified quantity 1"));
				FixtureSnapshot inc = FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(4)
					.container(new ContainerObservation(Container.BANK, ContainerState.INCOMPLETE, T0, "x", frozenBank(in))).build();
				fixtures.publish(inc);
				size(225);
				check("incomplete capture withholds every item and disables controls", named("bank-item-995") == null && named("bank-item-25867") == null
					&& text("bank-message-title").equals("No complete snapshot") && !named("bank-search").isEnabled());
				shot("04-bank-incomplete.png");
				layout();
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.demoB(generation, 1));
				comp.navigator().navigate("bank");
				size(225);
				check("a new account scope discards the page and its filters", page() != p && text("bank-account-note").startsWith("Demo account B"));
				check("no Account A item survives into B", named("bank-item-25867") == null);
				shot("05-bank-account-b.png");
				generation = fixtures.bind();
				fixtures.publish(TestFixtures.signedOut(generation, 2));
				comp.navigator().navigate("bank");
				size(225);
				check("signed out hides bank contents", text("bank-message-title").equals("Account unavailable") && text("bank-count").equals("Unknown"));
			} finally { comp.dispose(); }
		}

		private void layout() {
			fixtures.publish(FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(generation).revision(10)
				.container(new ContainerObservation(Container.BANK, ContainerState.CURRENT, T0, "x", frozenBank(in))).build());
			for (TextScale scale : TextScale.values()) {
				ui.setTextScale(scale);
				for (int m = 0; m < 2; m++) {
					((SegmentedSelector) named("bank-mode")).setSelected(m);
					for (int width : new int[] {225, 200}) {
						size(width);
						List<String> issues = new ArrayList<>();
						audit(page(), issues);
						controls(issues);
						check(scale + (m == 0 ? " grid" : " list") + " at " + width + "px: no clipped or overflowing component " + issues, issues.isEmpty());
					}
				}
			}
			ui.setTextScale(TextScale.LARGE);
			((SegmentedSelector) named("bank-mode")).setSelected(0);
			size(225);
			shot("06-bank-large-text.png");
			ui.setTextScale(TextScale.DEFAULT);
		}

		/** Shared controls report no minimum width: each must get at least its preferred width (no truncation). */
		private void controls(List<String> issues) {
			for (String n : new String[] {"bank-clear", "bank-sort", "bank-mode"}) {
				JComponent c = named(n);
				if (c != null && c.getWidth() + 1 < Math.min(c.getPreferredSize().width, page().getWidth())) issues.add(n + " truncated");
			}
		}

		private BankPanel page() { return find(comp.shell()); }

		private static BankPanel find(Component c) {
			if (c instanceof BankPanel) return (BankPanel) c;
			if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
				BankPanel p = find(k);
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
			return c instanceof TextBlock ? ((TextBlock) c).getAccessibleContext().getAccessibleName() : "<missing " + name + ">";
		}

		private String accessible(String name) {
			JComponent c = named(name);
			return c == null ? "<missing " + name + ">" : String.valueOf(c.getAccessibleContext().getAccessibleName());
		}

		private void activate(String name) {
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
