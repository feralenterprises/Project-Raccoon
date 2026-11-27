package com.feralenterprises.raccoon.playtest.r02.panel.bank;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Lane ACCOUNT host entry contract: {@code AccountPanels.registrations(laneInputsZip, frozenReview)} registers exactly
 * {@code Lane.ACCOUNT.routes()}, owned by that lane, fails closed on a different archive, and every route mounts through
 * the accepted shared composition for signed-in, other-scope and signed-out snapshots (partner routes are test receivers).
 * Args: the accepted Lane Inputs ZIP and the extracted frozen {@code Project-Raccoon-R02/}.
 */
public final class AccountEntryTests {
	private static int checks, failures;

	public static void main(String[] args) throws Exception {
		Path zip = Paths.get(args[0]), frozen = Paths.get(args[1]);
		List<RouteRegistration> regs = AccountPanels.registrations(zip, frozen);
		Set<RouteId> routes = regs.stream().map(RouteRegistration::route).collect(Collectors.toCollection(() -> EnumSet.noneOf(RouteId.class)));
		check("registers exactly Lane.ACCOUNT.routes(), once each", routes.equals(Lane.ACCOUNT.routes()) && regs.size() == routes.size());
		check("every registration is owned by ACCOUNT", regs.stream().allMatch(r -> r.owner().equals(Lane.ACCOUNT.name())));
		check("signature parity: static List<RouteRegistration> registrations(Path, Path) throws IOException",
			AccountPanels.class.getMethod("registrations", Path.class, Path.class).getExceptionTypes()[0] == IOException.class);
		Path wrong = Files.createTempFile("not-lane-inputs", ".zip");
		try {
			Files.write(wrong, new byte[] {'P', 'K', 3, 4});
			boolean refused;
			try { AccountPanels.registrations(wrong, frozen); refused = false; } catch (IOException e) { refused = true; }
			check("a different archive is refused before any registration exists", refused);
		} finally { Files.deleteIfExists(wrong); }
		FrozenPackageAssets assets = new FrozenPackageAssets(frozen);
		SwingUtilities.invokeAndWait(() -> mount(regs, assets));
		System.out.println("Lane ACCOUNT entry: " + (checks - failures) + "/" + checks + " checks passed");
		if (failures > 0) System.exit(1);
	}

	private static void mount(List<RouteRegistration> regs, FrozenPackageAssets assets) {
		UiContext ui = new UiContext(new DefaultFontSource(), new AssetProvider() {
			public BufferedImage art(ArtKey key) {
				String f = key.candidateFile();
				return f.matches("(PR|SIG)-\\d+_.*") ? assets.art(f.substring(0, f.indexOf('_'))) : null;
			}
			public BufferedImage nativeSprite(NativeSprite sprite, Runnable onLoaded) { return null; }
			public BufferedImage item(int itemId, int quantity, Runnable onLoaded) { return null; }
			public String provenance() { return "Headless entry contract"; }
		});
		FixtureDispatcher fixtures = new FixtureDispatcher(TestFixtures.registry(), TestFixtures.clock());
		List<RouteRegistration> pages = new ArrayList<>(regs);
		for (Lane other : Lane.values()) if (other != Lane.ACCOUNT) for (RouteId r : other.routes())
			pages.add(new RouteRegistration(r, other.name(), c -> new JLabel(r.id() + " test receiver")));
		R02Composition comp = new R02Composition(ui, fixtures, assets, pages);
		try {
			int g = fixtures.bind();
			comp.start(TestFixtures.demoA(g, 1));
			visit(comp, "account A");
			g = fixtures.bind();
			fixtures.publish(TestFixtures.demoB(g, 1));
			visit(comp, "account B");
			g = fixtures.bind();
			fixtures.publish(TestFixtures.signedOut(g, 2));
			visit(comp, "signed out");
		} finally { comp.dispose(); }
	}

	private static void visit(R02Composition comp, String label) {
		for (RouteId r : Lane.ACCOUNT.routes()) {
			comp.navigator().navigate("home");
			comp.navigator().navigate(r.id());
			comp.shell().setSize(268, 900);
			layoutAll(comp.shell());
			check(label + ": " + r.id() + " mounts its lane page", comp.navigator().current().equals(r.id()) && named(comp.shell(), r.id()) != null);
		}
	}

	private static void layoutAll(java.awt.Container parent) {
		parent.doLayout();
		for (Component child : parent.getComponents()) if (child instanceof java.awt.Container) layoutAll((java.awt.Container) child);
	}

	private static JComponent named(Component c, String name) {
		if (!c.isVisible()) return null;
		if (name.equals(c.getName()) && c instanceof JComponent) return (JComponent) c;
		if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) {
			JComponent j = named(k, name);
			if (j != null) return j;
		}
		return null;
	}

	private static void check(String name, boolean ok) {
		checks++;
		if (!ok) failures++;
		System.out.println((ok ? "  PASS " : "  FAIL ") + name);
	}
}
