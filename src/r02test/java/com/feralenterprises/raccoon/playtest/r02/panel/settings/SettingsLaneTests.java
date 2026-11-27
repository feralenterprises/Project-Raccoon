package com.feralenterprises.raccoon.playtest.r02.panel.settings;

import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.Appearance;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
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
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

public final class SettingsLaneTests {
	private static int passed;
	private final UiContext ui = new UiContext(new DefaultFontSource(), new AssetProvider() {
		public BufferedImage art(ArtKey key) { return null; }
		public BufferedImage nativeSprite(NativeSprite sprite, Runnable loaded) { return null; }
		public BufferedImage item(int id, int quantity, Runnable loaded) { return null; }
		public String provenance() { return "Lane E headless tests"; }
	});
	private final FixtureDispatcher fixtures = new FixtureDispatcher(TestFixtures.registry(), TestFixtures.clock());
	private final int generation = fixtures.bind();
	private final List<FixtureAction> actions = new ArrayList<>();
	private final R02Composition comp = new R02Composition(ui, fixtures, R02Assets.none(), List.of(
		SettingsPanels.registration(fixtures), new RouteRegistration(RouteId.TOOLS, Lane.ADVENTURE.name(), context -> {
			JLabel label = new JLabel(context.appearance().size().name());
			context.onAppearance(a -> label.setText(a.size().name()));
			return label;
		})));

	public static void main(String[] args) throws Exception {
		if (args.length != 1) throw new IllegalArgumentException("Provide verified Lane Inputs ZIP");
		Path input = Path.of(args[0]);
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input));
		check("exact Lane Inputs archive", new java.math.BigInteger(1, digest).toString(16)
			.equals("1f6a821d37705b7c7acdca9d598c2c5e7f74f7462536c9bf0f9039086545cece"));
		try (ZipFile zip = new ZipFile(input.toFile())) {
			check("frozen Settings input included", zip.getEntry("R02-Lane-Inputs/E/settings.json").getSize() > 0);
		}
		SwingUtilities.invokeAndWait(() -> {
			SettingsLaneTests t = new SettingsLaneTests();
			try { t.run(); } finally { t.comp.dispose(); }
		});
		System.out.println("SettingsLaneTests: " + passed + " passed, 0 failed");
	}

	private void run() {
		FixtureSnapshot a = TestFixtures.demoA(generation, 1);
		fixtures.setActionHandler(action -> {
			actions.add(action);
			if (action.kind() == FixtureAction.Kind.RESET_OBSERVATIONS)
				fixtures.publish(FixtureSnapshot.builder(a.scope()).generation(generation).revision(2).build());
		});
		comp.start(a);
		comp.shell().header().settings().doClick(0);
		check("real header opens Settings", route(RouteId.SETTINGS));
		check("frozen tabs and default controls", get("settings-interface", AbstractButton.class).isSelected()
			&& pick("size").getSelectedItem().equals("Default") && pick("surface").getSelectedItem().equals("Stone"));
		check("frozen appearance option counts", pick("size").getItemCount() == 4 && pick("accent").getItemCount() == 4
			&& pick("weight").getItemCount() == 2 && pick("surface").getItemCount() == 2);
		click("settings-tools");
		JLabel observer = (JLabel) page();
		check("D-14 Tools route opens", route(RouteId.TOOLS));
		comp.shell().header().back().doClick(0);
		check("Back returns Settings", route(RouteId.SETTINGS));
		pick("size").setSelectedItem("Large");
		pick("accent").setSelectedItem(Appearance.Accent.VERDIGRIS);
		pick("weight").setSelectedItem(Appearance.Weight.BOLD);
		Appearance custom = new Appearance(Appearance.Size.LARGE, Appearance.Accent.VERDIGRIS,
			Appearance.Weight.BOLD, Appearance.Surface.STONE);
		check("all supported choices publish through the shared model", fixtures.appearance().equals(custom));
		check("size repaints shared UI and open subscribers", ui.textScale() == TextScale.LARGE && observer.getText().equals("LARGE"));
		check("appearance changes leave observations untouched", fixtures.current() == a && actions.isEmpty());
		pick("size").setSelectedItem("Compact");
		check("unsupported Compact is refused and previous selection returns", pick("size").getSelectedItem().equals("Large")
			&& fixtures.appearance().equals(custom));
		pick("size").setSelectedItem("Extra large");
		check("unsupported Extra large is labeled", get("settings-appearance-status", TextBlock.class).text().contains("Extra large"));
		pick("surface").setSelectedItem("Bronze");
		check("Bronze cannot silently map to Timber", pick("surface").getSelectedItem().equals("Stone")
			&& fixtures.appearance().surface() == Appearance.Surface.STONE);
		fixtures.setAppearance(custom.withSurface(Appearance.Surface.CANVAS));
		check("external surface observation is reflected", pick("surface").getSelectedItem().equals("Canvas"));
		pick("surface").setSelectedItem("Stone");
		check("Stone restores accepted default surface", fixtures.appearance().equals(custom));
		click("settings-tracking");
		Expandable details = get("settings-best-in-bank", Expandable.class);
		check("native options start collapsed", !details.expanded());
		details.setExpanded(true);
		check("native options are display-only", details.expanded() && find(page(), "settings-outfit") != null);
		for (RouteId target : List.of(RouteId.OUTFIT, RouteId.CAPTURE, RouteId.BANK)) {
			click("settings-" + target.id());
			check("settings opens " + target, route(target));
			comp.shell().header().back().doClick(0);
			check("Back preserves Combat & Tracking from " + target, route(RouteId.SETTINGS)
				&& get("settings-tracking", AbstractButton.class).isSelected() && details.expanded());
		}
		click("settings-reset-preferences");
		check("confirmation is a local view with Cancel", get("settings-cancel", AbstractButton.class).isEnabled()
			&& fixtures.appearance().equals(custom));
		click("settings-cancel");
		check("cancel writes nothing", fixtures.appearance().equals(custom) && actions.isEmpty() && fixtures.current() == a);
		click("settings-reset-observations");
		comp.shell().header().back().doClick(0);
		check("header Back cancels without consuming route history", route(RouteId.SETTINGS) && actions.isEmpty());
		click("settings-reset-observations");
		click("settings-confirm");
		check("confirm publishes only the typed observation reset", actions.size() == 1
			&& actions.get(0).kind() == FixtureAction.Kind.RESET_OBSERVATIONS && fixtures.current().revision() == 2);
		check("observation reset preserves preferences and the same-scope tab", fixtures.appearance().equals(custom)
			&& get("settings-tracking", AbstractButton.class).isSelected());
		FixtureSnapshot reset = fixtures.current();
		click("settings-reset-preferences");
		click("settings-confirm");
		check("preference reset leaves observation snapshot and action count untouched", fixtures.current() == reset
			&& actions.size() == 1 && fixtures.appearance().equals(Appearance.DEFAULTS) && ui.textScale() == TextScale.DEFAULT);
		check("preference reset returns Interface", get("settings-interface", AbstractButton.class).isSelected());

		for (int width : new int[]{200, 225}) for (Appearance.Size size : Appearance.Size.values()) {
			fixtures.setAppearance(Appearance.DEFAULTS.withSize(size));
			click("settings-interface");
			audit(width, "Interface " + size);
			click("settings-tracking");
			audit(width, "Tracking " + size);
			click("settings-reset-observations");
			audit(width, "Confirmation " + size);
			click("settings-cancel");
		}
		click("settings-reset-observations");
		AbstractButton retiredConfirm = get("settings-confirm", AbstractButton.class);
		JComponent retired = page();
		check("B accepted by the shared dispatcher", fixtures.publish(TestFixtures.demoB(generation, 1)));
		check("scope change clears old history and returns Home", route(RouteId.HOME) && comp.openPageSubscriptions() == 0);
		retiredConfirm.doClick(0);
		check("retired A confirmation cannot mutate B", actions.size() == 1 && fixtures.current().scope().equals(TestFixtures.demoB(generation, 1).scope()));
		comp.shell().header().settings().doClick(0);
		check("B uses new page and shared preferences", page() != retired && get("settings-interface", AbstractButton.class).isSelected()
			&& pick("size").getSelectedItem().equals("Large"));
		check("logout retires page subscriptions", fixtures.publish(TestFixtures.signedOut(generation, 2))
			&& route(RouteId.HOME) && comp.openPageSubscriptions() == 0);
	}

	private boolean route(RouteId route) { return route.id().equals(comp.navigator().current()); }
	private JComponent page() { return comp.shell().currentHost().content(); }
	private JComboBox<?> pick(String id) { return get("settings-" + id, JComboBox.class); }
	private void click(String id) { get(id, AbstractButton.class).doClick(0); }
	private <T extends Component> T get(String id, Class<T> type) {
		Component hit = find(page(), id);
		if (hit == null) throw new AssertionError("Missing visible " + id);
		return type.cast(hit);
	}

	private static Component find(Container root, String id) {
		for (Component c : root.getComponents()) {
			if (!c.isVisible()) continue;
			if (id.equals(c.getName())) return c;
			if (c instanceof Container) {
				Component hit = find((Container) c, id);
				if (hit != null) return hit;
			}
		}
		return null;
	}

	private void audit(int width, String state) {
		comp.shell().setContentWidth(width);
		comp.shell().setSize(comp.shell().getPreferredSize().width, 900);
		List<String> clipped = new ArrayList<>();
		for (int i = 0; i < 3; i++) layout(comp.shell());
		measure(page(), clipped);
		check(state + " at " + width + ": " + clipped, clipped.isEmpty());
	}

	private static void layout(Container parent) {
		parent.doLayout();
		for (Component child : parent.getComponents()) if (child.isVisible() && child instanceof Container) layout((Container) child);
	}

	private static void measure(Container parent, List<String> clipped) {
		for (Component child : parent.getComponents()) {
			if (!child.isVisible()) continue;
			if (child instanceof HeightForWidth && (child.getWidth() <= 0
				|| child.getHeight() < ((HeightForWidth) child).heightForWidth(child.getWidth()))) clipped.add(child.getName());
			if (child instanceof Container) measure((Container) child, clipped);
		}
	}

	private static void check(String name, boolean value) {
		if (!value) throw new AssertionError(name);
		passed++;
	}
}
