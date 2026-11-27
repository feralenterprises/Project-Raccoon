package com.feralenterprises.raccoon.playtest.r02.acceptance;

import com.feralenterprises.raccoon.playtest.r02.assembly.R02Composition;
import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.Appearance;
import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.CaptureState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Coverage;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.EvidenceStatus;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Environment;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.asset.NativeSprite;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.SegmentedSelector;
import com.feralenterprises.raccoon.ui.component.TextFit;
import com.feralenterprises.raccoon.ui.shell.Environment;
import com.feralenterprises.raccoon.ui.shell.PageHost;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/** The shared-contract gaps reported by Lanes A, C, D and E (issue #1), each closed here, and what must stay true. */
final class SharedGapTests {
	private SharedGapTests() { }

	static void run(BiConsumer<String, Boolean> check) throws Exception {
		Instant t = TestFixtures.T0;
		// ---- Lane A
		ArchiveRecord unknowns = new ArchiveRecord("x1", null, null, "Saved session", ArchiveRecord.Kind.SKILLING, t,
			Evidence.unknown(), Evidence.unknown(), Evidence.unknown(), Evidence.unknown(), "actions", Evidence.unknown(),
			Evidence.unknown(), Evidence.unknown(), null);
		check.accept("A1: a saved record with unknown active time and count is representable",
			unknowns.activeTime().status() == EvidenceStatus.UNKNOWN && unknowns.count().status() == EvidenceStatus.UNKNOWN);
		check.accept("A1: the legacy active() reads null for an unknown active time (never zero); units() still refuses",
			unknowns.active() == null && throwsIt(unknowns::units));
		check.accept("A2: elapsed, deaths and supplies are evidence (unknown is not zero)", unknowns.elapsedTime().status() == EvidenceStatus.UNKNOWN
			&& unknowns.deaths().status() == EvidenceStatus.UNKNOWN && unknowns.suppliesLedger().status() == EvidenceStatus.UNKNOWN);
		check.accept("A3: Current Activity owns its environment (no shared header scene or footer)",
			R02Environment.of(RouteId.ACTIVITY).frameworkEnvironment() == Environment.PLAIN
			&& R02Environment.of(RouteId.HOME).frameworkEnvironment() == Environment.FULL);
		UiContext ui = new UiContext(new DefaultFontSource(), new NoAssets());
		AtomicReference<int[]> fill = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			JPanel shortPage = new JPanel(), plainPage = new JPanel();
			shortPage.putClientProperty(PageHost.FILL_VIEWPORT, Boolean.TRUE);
			int[] r = new int[2];
			int i = 0;
			for (JPanel p : Arrays.asList(shortPage, plainPage)) {
				PageHost h = new PageHost(ui, p, Environment.PLAIN);
				JViewport v = new JViewport();
				v.setView(h);
				v.setSize(240, 600);
				h.setSize(240, 600);
				h.doLayout();
				r[i++] = p.getHeight();
			}
			fill.set(r);
		});
		check.accept("A4: an opted-in page stretches to the visible height; others keep their own", fill.get()[0] > 500 && fill.get()[1] < 100);
		// ---- Lane C
		ContainerObservation hist = new ContainerObservation(Container.BANK, ContainerState.HISTORICAL, t.minus(Duration.ofDays(1)), "saved", Collections.singletonMap(995, 10L));
		ContainerObservation part = new ContainerObservation(Container.BANK, ContainerState.INCOMPLETE, t, "partial", Collections.singletonMap(995, 3L));
		ContainerObservation kept = part.withRetained(hist);
		check.accept("C1: an incomplete bank carries the earlier complete capture as saved", kept.retained() == hist
			&& kept.quantity(995).value() == 3L && kept.quantity(1511).status() == EvidenceStatus.UNKNOWN && part.retained() == null);
		check.accept("C1: only incomplete, historical, same container, not newer", throwsIt(() -> hist.withRetained(hist))
			&& throwsIt(() -> part.withRetained(part))
			&& throwsIt(() -> part.withRetained(new ContainerObservation(Container.INVENTORY, ContainerState.HISTORICAL, t, "s", Collections.emptyMap())))
			&& throwsIt(() -> part.withRetained(new ContainerObservation(Container.BANK, ContainerState.HISTORICAL, t.plusSeconds(5), "s", Collections.emptyMap()))));
		FixtureSnapshot contradiction = FixtureSnapshot.builder(AccountScope.of("A", 1)).container(hist)
			.capture("bank", Evidence.verified(CaptureState.VERIFIED, t, "contradicting flag")).build();
		check.accept("C2: the bank capture follows the bank container, never a contradicting flag",
			contradiction.capture("bank").status() == EvidenceStatus.STALE && contradiction.capture("bank").value() == CaptureState.VERIFIED);
		check.accept("C2: incomplete is partial, missing is unavailable, current is verified",
			FixtureSnapshot.builder(AccountScope.of("A", 1)).container(part).build().capture("bank").value() == CaptureState.PARTIAL
			&& FixtureSnapshot.builder(AccountScope.of("A", 1)).build().capture("bank").status() == EvidenceStatus.UNAVAILABLE
			&& TestFixtures.demoA(1, 1).capture("bank").status() == EvidenceStatus.VERIFIED);
		AtomicReference<boolean[]> fits = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			RButton b = new RButton(ui, "Observe account type");
			RDropdown<String> d = new RDropdown<>(ui, "Sort", Arrays.asList("Illustrative value", "Name"), s -> s, s -> { });
			SegmentedSelector s = new SegmentedSelector(ui, "Calculator", "Resources", "Options");
			boolean[] f = new boolean[6];
			f[0] = b.requiredTextWidth() > 0 && !b.textFits(20) && b.textFits(b.getPreferredSize().width);
			f[1] = !d.textFits(40) && d.textFits(d.getPreferredSize().width);
			f[2] = !s.textFits(30) && s.requiredTextWidth() == s.minimumContentWidth();
			f[3] = b instanceof TextFit && d instanceof TextFit;
			BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Expandable plain = new Expandable(ui, "Bank", new JPanel()), withIcon = new Expandable(ui, "Bank", icon, new JPanel());
			f[4] = withIcon.summary().getAccessibleContext().getAccessibleName().equals("Bank");
			f[5] = ((com.feralenterprises.raccoon.ui.layout.HeightForWidth) withIcon.summary()).heightForWidth(200)
				>= ((com.feralenterprises.raccoon.ui.layout.HeightForWidth) plain.summary()).heightForWidth(200);
			fits.set(f);
		});
		check.accept("C4: button, dropdown and selector report the width their text needs (reporting only)", fits.get()[0] && fits.get()[1] && fits.get()[2] && fits.get()[3]);
		check.accept("C5: an expandable can show an icon before its title, title still its name", fits.get()[4] && fits.get()[5]);
		// ---- Lane D
		FixtureSnapshot none = TestFixtures.demoA(1, 1);
		check.accept("D1: coverage unknown unless observed", none.questCoverage().status() == EvidenceStatus.UNKNOWN && none.diaryCoverage().status() == EvidenceStatus.UNKNOWN);
		check.accept("D1: coverage carried when observed", FixtureSnapshot.builder(AccountScope.of("A", 1))
			.questCoverage(Evidence.verified(Coverage.PARTIAL, t, "x")).build().questCoverage().value() == Coverage.PARTIAL);
		// ---- Lane E
		AtomicReference<Appearance[]> app = new AtomicReference<>();
		boolean[] scale = new boolean[3];
		SwingUtilities.invokeAndWait(() -> {
			FixtureDispatcher fx = new FixtureDispatcher(TestFixtures.registry(), TestFixtures.clock());
			AtomicReference<RouteContext> ctx = new AtomicReference<>();
			RouteRegistration settings = new RouteRegistration(RouteId.SETTINGS, Lane.COLLECTION.name(), c -> { ctx.set(c); return new JPanel(); });
			R02Composition comp = new R02Composition(ui, fx, R02Assets.none(), Collections.singletonList(settings));
			comp.start(TestFixtures.demoA(fx.bind(), 1));
			comp.shell().navigator().navigate(RouteId.SETTINGS.id());
			Appearance before = fx.appearance();
			Appearance[] seen = new Appearance[1];
			ctx.get().onAppearance(a -> seen[0] = a);
			Appearance next = before.equals(Appearance.DEFAULTS) ? alternate(before) : Appearance.DEFAULTS;
			ctx.get().setAppearance(next);
			app.set(new Appearance[] {before, fx.appearance(), seen[0], next});
			fx.setAppearance(fx.appearance().withSize(Appearance.Size.LARGE));
			scale[0] = ui.textScale() == com.feralenterprises.raccoon.ui.theme.TextScale.LARGE;
			fx.setAppearance(fx.appearance().withSize(Appearance.Size.DEFAULT));
			scale[1] = ui.textScale() == com.feralenterprises.raccoon.ui.theme.TextScale.DEFAULT;
			comp.dispose();
			int listeners = fx.listenerCount();
			fx.setAppearance(fx.appearance().withSize(Appearance.Size.LARGE));
			scale[2] = listeners == 0 && ui.textScale() == com.feralenterprises.raccoon.ui.theme.TextScale.DEFAULT;
		});
		check.accept("E1: Settings changes the shared appearance through its context (no dispatcher needed)",
			app.get()[1].equals(app.get()[3]) && app.get()[2] != null && app.get()[2].equals(app.get()[3]));
		check.accept("E2: any appearance size change reaches the shared text scale", scale[0] && scale[1]);
		check.accept("E2: after disposal the composition no longer listens", scale[2]);
		check.accept("E1: a context that cannot change appearance refuses", throwsIt(() -> new RouteContext() {
			@Override public RouteId route() { return null; }
			@Override public UiContext ui() { return null; }
			@Override public FixtureSnapshot snapshot() { return null; }
			@Override public com.feralenterprises.raccoon.playtest.r02.fixture.Registry registry() { return null; }
			@Override public R02Assets assets() { return null; }
			@Override public com.feralenterprises.raccoon.playtest.r02.fixture.FixtureClock clock() { return null; }
			@Override public Appearance appearance() { return null; }
			@Override public com.feralenterprises.raccoon.playtest.r02.fixture.Subscription onSnapshot(java.util.function.Consumer<FixtureSnapshot> l) { return null; }
			@Override public com.feralenterprises.raccoon.playtest.r02.fixture.Subscription onAppearance(java.util.function.Consumer<Appearance> l) { return null; }
			@Override public void navigate(RouteId r) { }
			@Override public void navigate(RouteId r, com.feralenterprises.raccoon.ui.shell.RouteRequest.Param p, String id) { }
			@Override public void dispatch(com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction a) { }
		}.setAppearance(Appearance.DEFAULTS)));
	}

	private static Appearance alternate(Appearance a) { return a.withAccent(Appearance.Accent.VERDIGRIS); }

	private static boolean throwsIt(Runnable r) {
		try { r.run(); return false; } catch (RuntimeException e) { return true; }
	}

	private static final class NoAssets implements AssetProvider {
		@Override public BufferedImage art(ArtKey k) { return null; }
		@Override public BufferedImage nativeSprite(NativeSprite s, Runnable r) { return null; }
		@Override public BufferedImage item(int id, int q, Runnable r) { return null; }
		@Override public String provenance() { return "none"; }
	}
}
