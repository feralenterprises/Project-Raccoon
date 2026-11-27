package com.feralenterprises.raccoon.playtest.r02.panel.friends;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.Ownership;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.panel.friends.FriendsCatalog.Pet;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class FriendsLaneTests {
	private static int passed;
	private final FriendsCatalog catalog;
	private final UiContext ui = new UiContext(new DefaultFontSource(), new AssetProvider() {
		public BufferedImage art(ArtKey key) { return null; }
		public BufferedImage nativeSprite(NativeSprite sprite, Runnable loaded) { return null; }
		public BufferedImage item(int id, int quantity, Runnable loaded) { return null; }
		public String provenance() { return "Lane E headless tests"; }
	});
	private final FixtureDispatcher fixtures = new FixtureDispatcher(TestFixtures.registry(), TestFixtures.clock());
	private final int generation = fixtures.bind();
	private final R02Composition comp;

	private FriendsLaneTests(FriendsCatalog catalog, R02Assets assets) {
		this.catalog = catalog;
		comp = new R02Composition(ui, fixtures, assets, List.of(FriendsPanels.registration(catalog),
			new RouteRegistration(RouteId.CHRONICLE, Lane.COMBAT.name(), context -> new JLabel("Chronicle intent receiver")),
			new RouteRegistration(RouteId.CAPTURE, Lane.ACCOUNT.name(), context -> new JLabel("Capture intent receiver"))));
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 2) throw new IllegalArgumentException("Provide verified Lane Inputs ZIP and extracted frozen R02");
		Path inputs = Path.of(args[0]), reference = Path.of(args[1]);
		FriendsCatalog catalog = FriendsCatalog.load(inputs, reference);
		check("72 canonical pet rows", catalog.roster.size() == 72 && catalog.roster.stream().map(p -> p.key).distinct().count() == 72);
		check("canonical TSV classifies Orion as Raid", catalog.pet("orion").category.equals("RAID"));
		for (Pet pet : catalog.roster)
			check("offline exact sprite for " + pet.key, catalog.image(R02Assets.none(), pet.key) != null);
		check("exact specimen and companion IDs", catalog.spriteId("rocky") == 20663 && catalog.spriteId("beef") == 33124
			&& catalog.spriteId("cat") == 1561 && catalog.spriteId("dog") == 34431);
		tamper(inputs, reference);
		FrozenPackageAssets assets = new FrozenPackageAssets(reference);
		SwingUtilities.invokeAndWait(() -> {
			FriendsLaneTests tests = new FriendsLaneTests(catalog, assets);
			try {
				check("public host wiring covers exactly the two Lane E routes", collectionRoutes(inputs, reference, tests.fixtures));
				tests.functional();
				tests.scope();
			} finally {
				tests.comp.dispose();
			}
		});
		System.out.println("FriendsLaneTests: " + passed + " passed, 0 failed");
	}

	private static boolean collectionRoutes(Path inputs, Path reference, FixtureDispatcher fixtures) {
		try {
			return CollectionPanels.registrations(inputs, reference, fixtures).stream().map(RouteRegistration::route)
				.collect(java.util.stream.Collectors.toSet()).equals(Lane.COLLECTION.routes());
		} catch (IOException ex) {
			throw new AssertionError(ex);
		}
	}

	private static void tamper(Path inputs, Path reference) throws IOException {
		Path temp = Files.createTempDirectory("friends-input-test");
		try {
			Path bad = temp.resolve("bad.zip");
			byte[] bytes = Files.readAllBytes(inputs);
			bytes[bytes.length - 1] ^= 1;
			Files.write(bad, bytes);
			try {
				FriendsCatalog.load(bad, reference);
				throw new AssertionError("Tampered lane pack was accepted");
			} catch (IOException ex) {
				check("same-length altered lane ZIP fails closed", ex.getMessage().contains("SHA-256 mismatch"));
			}
			Path html = reference.resolve("Project-Raccoon-R02.html");
			byte[] wrong = Files.readAllBytes(html);
			wrong[1024] ^= 1;
			Files.write(temp.resolve(html.getFileName()), wrong);
			try {
				FriendsCatalog.load(inputs, temp);
				throw new AssertionError("Tampered frozen review was accepted");
			} catch (IOException ex) {
				check("same-length altered frozen review fails closed", ex.getMessage().contains("SHA-256 mismatch"));
			}
		} finally {
			Files.deleteIfExists(temp.resolve("bad.zip"));
			Files.deleteIfExists(temp.resolve("Project-Raccoon-R02.html"));
			Files.delete(temp);
		}
	}

	private void functional() {
		FixtureSnapshot a = TestFixtures.demoA(generation, 1);
		comp.start(a);
		comp.navigator().navigate(RouteId.FRIENDS.id());
		check("frozen Home collection cover and ledger", has("The sanctuary") && has("Collection observation pending")
			&& has("0 of 72 individual ownership states observed") && has("—"));
		audit("Friends Home");
		click("friends-arrivals");
		check("ownership never creates arrival history", has("No acquisition observations are available."));
		back();
		click("friends-companions");
		get("friends-trade-cat", Expandable.class).setExpanded(true);
		get("friends-trade-dog", Expandable.class).setExpanded(true);
		check("cats retain frozen trade details", has("West Ardougne") && has("Ardougne Easy Diary · 200 death runes"));
		check("dogs retain frozen trade details", has("Ver Sinhaza") && has("Morytania Medium Diary · 200 blood runes"));
		check("companions display unavailable ownership and event counters", has("Current ownership · Unknown") && has("Confirmed trade-ins · Pending"));
		audit("Companions");
		back();
		click("friends-family-album");
		check("frozen full album", count(page(), "friends-open-") == 72);
		audit("Album");
		select("friends-category", "SKILLING");
		check("combined album filters", count(page(), "friends-open-") < 72 && find(page(), "friends-open-rocky") != null
			&& find(page(), "friends-open-beef") == null);
		select("friends-status-filter", "OBTAINED");
		check("unknown account does not show unobserved pets as obtained", count(page(), "friends-open-") == 0 && has("No friends match"));
		select("friends-status-filter", "ALL");
		select("friends-category", "");
		search("missing label");
		check("album search has empty result state", has("No friends match"));
		search("rocky");
		check("album search renders Rocky", count(page(), "friends-open-") == 1);
		click("friends-pin-rocky");
		check("pin updates wishlist count while preserving filters", get("friends-album-wishlist", AbstractButton.class).getText().contains("(1)")
			&& get("friends-search", JTextField.class).getText().equals("rocky"));
		click("friends-album-wishlist");
		check("wishlist lists only the saved pet", count(page(), "friends-open-") == 1 && find(page(), "friends-open-rocky") != null);
		back();
		click("friends-open-rocky");
		check("intent never fabricates ownership or actions", has("No individual collection observation") && has("Observed actions · Unavailable"));
		click("friends-start");
		check("only one active hunt is local intent", has("Active hunt") && fixtures.current() == a
			&& has("Verified eligible rolls · Unavailable") && has("Estimated opportunities · Unavailable"));
		click("friends-help");
		check("help distinguishes three evidence types", has("are separate evidence"));
		click("friends-close-help");
		check("help closes to same pet", find(page(), "friends-stop") != null && has("Rocky"));
		click("friends-view-active");
		check("Hunt reuses the exact active pet and method reference", has("Rocky") && get("friends-method-Seed stall", Expandable.class) != null);
		get("friends-method-Seed stall", Expandable.class).setExpanded(true);
		check("reference rates are labeled archived", has("Archived example per eligible roll · 1 / 34,565") && has("Pet chance / hour · Unknown"));
		audit("Active Hunt");
		click("friends-manage-hunt");
		click("friends-stop");
		check("stop keeps wishlist and removes hunt", find(page(), "friends-stop") == null
			&& get("friends-detail-pin", AbstractButton.class).getText().contains("Remove"));
		click("friends-hunt");
		check("Hunt empty after stop", has("No active hunt yet"));
		click("friends-album");
		search("");
		for (Pet pet : catalog.roster) {
			click("friends-open-" + pet.key);
			click("friends-methods");
			check("frozen source can be opened for " + pet.key, has("Hunting methods") && has("Source: " + pet.source)
				&& has("verified roll history and account odds are unavailable"));
			back();
			back();
		}
		search("beef");
		click("friends-open-beef");
		click("friends-methods");
		get("friends-source-brutus", Expandable.class).setExpanded(true);
		click("friends-chronicle-brutus");
		check("exact pet reward ID opens typed Brutus", comp.navigator().currentRequest().equals(
			RouteRequest.of(RouteId.CHRONICLE.id(), RouteRequest.Param.ENCOUNTER, "brutus")));
		back();
		check("cross-route Back preserves current method view", has("Hunting methods") && has("Source: Brutus"));
		back();
		back();
		click("friends-capture");
		check("Account Capture opens via canonical route", route(RouteId.CAPTURE));
		back();
		check("Capture Back restores album search", get("friends-search", JTextField.class).getText().equals("beef"));
	}

	private void scope() {
		search("rocky");
		click("friends-open-rocky");
		click("friends-start");
		AbstractButton retired = get("friends-stop", AbstractButton.class);
		JComponent a = page();
		check("B publishes and discards A's page and history", fixtures.publish(TestFixtures.demoB(generation, 1))
			&& route(RouteId.HOME) && comp.openPageSubscriptions() == 0);
		retired.doClick(0);
		comp.navigator().navigate(RouteId.FRIENDS.id());
		check("B starts fresh with one observed row", page() != a && has("Partial collection observed")
			&& has("1 of 72 individual ownership states observed") && has("No saved pets")
			&& !((LocalBack) page()).hasLocalBack());
		click("friends-hunt");
		check("A hunt does not leak to B", has("No active hunt yet"));
		click("friends-album");
		select("friends-status-filter", "OBTAINED");
		check("B shows exact Beef observation only", count(page(), "friends-open-") == 1 && find(page(), "friends-open-beef") != null);
		click("friends-open-beef");
		check("verified obtained cannot start a hunt and is not a roll or arrival", find(page(), "friends-start") == null
			&& has("Obtained") && has("Verified eligible rolls · Unavailable"));
		selectPetThroughAlbum("Rocky");
		check("B has no archived A wishlist", get("friends-detail-pin", AbstractButton.class).getText().contains("Save"));

		FixtureSnapshot partial = FixtureSnapshot.builder(AccountScope.of("B", 1)).generation(generation).revision(2)
			.ownership("beef", Evidence.stale(Ownership.OBTAINED, TestFixtures.T0, "Earlier collection log"))
			.ownership("rocky", Evidence.absent(TestFixtures.T0, "Observed empty collection slot"))
			.ownership("heron", Evidence.notApplicable())
			.ownership("beaver", Evidence.unavailable()).build();
		searchFromAlbum("Rocky");
		check("scope refresh preserves the same album search field",
			fixtures.publish(partial) && get("friends-search", JTextField.class).getText().equals("Rocky") && has("Not yet obtained"));
		select("friends-status-filter", "ABSENT");
		check("verified absence is filterable and shown with source", count(page(), "friends-open-") == 1);
		click("friends-open-rocky");
		check("verified absence row keeps provenance", has("Observed empty collection slot"));
		searchFromAlbum("Beef");
		check("stale ownership is not counted as current", count(page(), "friends-open-") == 1 && has("Last seen obtained"));
		click("friends-open-beef");
		check("stale detail shows prior timestamp and no claim to current status", has("Last observed") && has("Earlier collection log"));
		searchFromAlbum("Heron");
		check("individual not mapped remains a distinct filter", has("Not mapped"));
		select("friends-status-filter", "UNMAPPED");
		check("not mapped filter finds only unmapped result", count(page(), "friends-open-") == 1);
		check("stale revision refused", !fixtures.publish(TestFixtures.demoB(generation, 1)) && has("Not mapped"));
		searchFromAlbum("Beaver");
		check("unavailable collection is explicit and remains unknown", has("Unknown · observation unavailable"));
		click("friends-open-beaver");
		click("friends-methods");
		Expandable willow = get("friends-method-Willow trees", Expandable.class);
		willow.setExpanded(true);
		FixtureSnapshot next = FixtureSnapshot.builder(partial.scope()).generation(generation).revision(3).build();
		check("same-scope observation preserves expanded reference methods", fixtures.publish(next)
			&& get("friends-method-Willow trees", Expandable.class) == willow && willow.expanded());
		check("wrong generation refused", !fixtures.publish(TestFixtures.demoA(generation - 1, 99)));
		check("logout clears all local subscriptions and history", fixtures.publish(TestFixtures.signedOut(generation, 2))
			&& route(RouteId.HOME) && comp.openPageSubscriptions() == 0);
		comp.navigator().navigate(RouteId.FRIENDS.id());
		selectPetThroughAlbum("Rocky");
		check("signed out can browse but cannot set private intent", !get("friends-start", AbstractButton.class).isEnabled()
			&& !get("friends-detail-pin", AbstractButton.class).isEnabled() && has("Unknown"));
	}

	private void searchFromAlbum(String text) {
		click("friends-album");
		select("friends-status-filter", "ALL");
		search(text);
	}

	private void selectPetThroughAlbum(String name) {
		searchFromAlbum(name);
		click("friends-open-" + name.toLowerCase(java.util.Locale.ROOT));
	}

	private void search(String s) { get("friends-search", JTextField.class).setText(s); }
	private void select(String id, String value) {
		JComboBox<?> field = get(id, JComboBox.class);
		for (int i = 0; i < field.getItemCount(); i++) if (value.equals(field.getItemAt(i).toString())) {
			field.setSelectedIndex(i);
			return;
		}
		throw new AssertionError("Missing filter " + value);
	}
	private JComponent page() { return comp.shell().currentHost().content(); }
	private boolean route(RouteId id) { return id.id().equals(comp.navigator().current()); }
	private void click(String id) { get(id, AbstractButton.class).doClick(0); }
	private void back() { comp.shell().header().back().doClick(0); }

	private <T extends Component> T get(String id, Class<T> type) {
		Component found = find(page(), id);
		if (found == null) throw new AssertionError("Missing visible " + id);
		return type.cast(found);
	}

	private boolean has(String expected) { return contains(page(), expected); }
	private static boolean contains(Container root, String str) {
		for (Component child : root.getComponents()) if (child.isVisible()) {
			if (child instanceof TextBlock && ((TextBlock) child).text().contains(str)) return true;
			if (child instanceof Container && contains((Container) child, str)) return true;
		}
		return false;
	}

	private static Component find(Container root, String id) {
		for (Component child : root.getComponents()) if (child.isVisible()) {
			if (id.equals(child.getName())) return child;
			if (child instanceof Container) {
				Component found = find((Container) child, id);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static int count(Container root, String prefix) {
		int count = 0;
		for (Component child : root.getComponents()) if (child.isVisible()) {
			if (child.getName() != null && child.getName().startsWith(prefix)) count++;
			if (child instanceof Container) count += count((Container) child, prefix);
		}
		return count;
	}

	private void audit(String state) {
		for (int width : new int[]{200, 225}) for (TextScale scale : TextScale.values()) {
			ui.setTextScale(scale);
			comp.shell().setContentWidth(width);
			comp.shell().setSize(comp.shell().getPreferredSize().width, 900);
			for (int pass = 0; pass < 3; pass++) layout(comp.shell());
			List<String> issues = new ArrayList<>();
			measure(page(), issues);
			check(state + " fits at " + width + " " + scale + ": " + issues, issues.isEmpty());
		}
		ui.setTextScale(TextScale.DEFAULT);
	}

	private static void layout(Container root) {
		root.doLayout();
		for (Component child : root.getComponents()) if (child.isVisible() && child instanceof Container) layout((Container) child);
	}

	private static void measure(Container root, List<String> issues) {
		for (Component child : root.getComponents()) {
			if (!child.isVisible()) continue;
			if (child instanceof HeightForWidth && (child.getX() < 0 || child.getWidth() <= 0
				|| child.getX() + child.getWidth() > root.getWidth()
				|| child.getHeight() < ((HeightForWidth) child).heightForWidth(child.getWidth())))
				issues.add(child.getClass().getSimpleName() + " " + child.getName());
			if (child instanceof Container) measure((Container) child, issues);
		}
	}

	private static void check(String description, boolean result) {
		if (!result) throw new AssertionError(description);
		passed++;
	}
}
