package com.feralenterprises.raccoon.playtest.host.test;

import com.feralenterprises.raccoon.playtest.launcher.PlaytestLauncher;
import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios.ArchiveState;
import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios.BankState;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.ItemDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.host.ClientPhase;
import com.feralenterprises.raccoon.playtest.r02.host.HostAssets;
import com.feralenterprises.raccoon.playtest.r02.host.HostLifecycle;
import com.feralenterprises.raccoon.playtest.r02.host.LaneDiscovery;
import com.feralenterprises.raccoon.playtest.r02.host.NameAudit;
import com.feralenterprises.raccoon.playtest.r02.host.PackagePin;
import com.feralenterprises.raccoon.playtest.r02.host.PlaytestControls;
import com.feralenterprises.raccoon.playtest.r02.host.PlaytestSession;
import com.feralenterprises.raccoon.playtest.r02.host.ProfileGuard;
import com.feralenterprises.raccoon.playtest.r02.host.RunRecord;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.ArtKey;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.StateNotice;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import java.awt.Component;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * R02 native host, offline. Args: build dir, optional extracted frozen package. Without the package only the
 * package-independent checks run (and the count says so).
 */
public final class HostTests {
	private static final Check T = new Check("HostTests");
	private static Path build;
	private static Path pkgDir;
	private static Path work;

	public static void main(String[] args) throws Exception {
		build = Paths.get(args[0]);
		pkgDir = args.length > 1 ? Paths.get(args[1]) : null;
		work = Files.createTempDirectory("r02-hosttests");
		phaseMapping();
		profileGuard();
		laneDiscovery();
		records();
		pins();
		nameAudit();
		assetIds();
		forbiddenApis();
		artifacts();
		if (pkgDir != null) {
			PlaytestSession.Package pkg = PlaytestSession.Package.load(pkgDir);
			packageLoad(pkg);
			session(pkg);
			startSuspended(pkg);
			controls(pkg);
			lifecycle();
			plugin();
		} else {
			System.out.println("HostTests: frozen package not given; session, lifecycle and plugin checks SKIPPED");
		}
		System.exit(T.finish());
	}

	// ------------------------------------------------------------------ package independent

	private static void phaseMapping() {
		Set<String> open = new HashSet<>(Arrays.asList("STARTING", "LOGIN_SCREEN", "LOGIN_SCREEN_AUTHENTICATOR"));
		for (String s : new String[] {"UNKNOWN", "STARTING", "LOGIN_SCREEN", "LOGIN_SCREEN_AUTHENTICATOR", "LOGGING_IN", "LOADING", "LOGGED_IN", "CONNECTION_LOST", "HOPPING"})
			T.that("phase " + s, ClientPhase.ofGameState(s) == (open.contains(s) ? ClientPhase.OPEN : ClientPhase.SUSPENDED));
		T.that("phase null fails closed", ClientPhase.ofGameState(null) == ClientPhase.SUSPENDED);
		T.that("phase from a later client fails closed", ClientPhase.ofGameState("LOGGED_IN_V2") == ClientPhase.SUSPENDED);
	}

	private static void profileGuard() throws IOException {
		Path profile = work.resolve("guard-profile");
		Files.createDirectories(profile);
		Path rl = profile.resolve(".runelite");
		T.throwsOn("guard: launcher property unset", () -> ProfileGuard.check(profile.toString(), rl, null), "not started by the R02 playtest launcher");
		T.throwsOn("guard: user.home elsewhere", () -> ProfileGuard.check(work.toString(), rl, profile.toString()), "user.home");
		T.throwsOn("guard: RuneLite folder elsewhere", () -> ProfileGuard.check(profile.toString(), work.resolve(".runelite"), profile.toString()), "RuneLite folder");
		T.throwsOn("guard: no marker", () -> ProfileGuard.check(profile.toString(), rl, profile.toString()), "marker");
		Files.write(profile.resolve(ProfileGuard.MARKER), "something else\n".getBytes(StandardCharsets.UTF_8));
		T.throwsOn("guard: wrong marker", () -> ProfileGuard.check(profile.toString(), rl, profile.toString()), "marker");
		Files.write(profile.resolve(ProfileGuard.MARKER), ProfileGuard.MARKER_CONTENT.getBytes(StandardCharsets.UTF_8));
		T.that("guard: isolated profile accepted", ProfileGuard.check(profile.toString(), rl, profile.toString()).equals(profile.toAbsolutePath().normalize()));
		T.that("guard: marker identical to the launcher's", ProfileGuard.MARKER.equals(PlaytestLauncher.MARKER) && ProfileGuard.MARKER_CONTENT.equals(PlaytestLauncher.MARKER_CONTENT));
	}

	private static final String LANE_BASE = "com.feralenterprises.raccoon.playtest.r02.panel.";

	private static ClassLoader lanes(Map<String, String> sources) throws IOException {
		Path out = Files.createTempDirectory(work, "lanes");
		Compile.into(out, System.getProperty("java.class.path"), sources);
		// Hide real lane classes (an assembled tree has them) so each test sees only its stand-in lanes.
		ClassLoader withoutLanes = new ClassLoader(HostTests.class.getClassLoader()) {
			@Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if (name.startsWith(LANE_BASE)) throw new ClassNotFoundException(name);
				return super.loadClass(name, resolve);
			}
		};
		return new URLClassLoader(new URL[] {out.toUri().toURL()}, withoutLanes);
	}

	/** Lane A stand-in: the real entry shape, one Home page that records its scope and every snapshot it receives. */
	private static final String ADVENTURE_ENTRY =
		"package com.feralenterprises.raccoon.playtest.r02.panel.home;\n"
		+ "import com.feralenterprises.raccoon.playtest.r02.shared.*;\n"
		+ "public final class AdventurePanels {\n"
		+ "  public static final java.util.List<String> LOG = new java.util.concurrent.CopyOnWriteArrayList<>();\n"
		+ "  public static java.util.List<RouteRegistration> registrations(java.nio.file.Path inputs, java.nio.file.Path review) throws java.io.IOException {\n"
		+ "    if (!java.nio.file.Files.isRegularFile(review.resolve(\"SHA256SUMS.txt\"))) throw new java.io.IOException(\"no review\");\n"
		+ "    LOG.add(\"loaded on EDT=\" + javax.swing.SwingUtilities.isEventDispatchThread());\n"
		+ "    return java.util.Collections.singletonList(new RouteRegistration(RouteId.HOME, Lane.ADVENTURE.name(), AdventurePanels::page));\n"
		+ "  }\n"
		+ "  static javax.swing.JComponent page(RouteContext c) {\n"
		+ "    String born = String.valueOf(c.snapshot().scope());\n"
		+ "    LOG.add(\"create \" + born);\n"
		+ "    c.onSnapshot(s -> LOG.add(\"page \" + born + \" got \" + s.scope() + \" r\" + s.revision()));\n"
		+ "    return new javax.swing.JPanel();\n"
		+ "  }\n"
		+ "}\n";

	private static String entry(String pkg, String cls, String body) {
		return "package com.feralenterprises.raccoon.playtest.r02.panel." + pkg + ";\nimport com.feralenterprises.raccoon.playtest.r02.shared.*;\n"
			+ "import java.nio.file.Path; import java.util.*;\npublic final class " + cls + " {\n" + body + "\n}\n";
	}

	private static Path laneInputs;
	private static boolean lanesOnClasspath;
	private static String laneInputsSha;

	private static void laneDiscovery() throws Exception {
		laneInputs = work.resolve("stand-in-lane-inputs.zip");
		Files.write(laneInputs, "stand-in lane inputs".getBytes(StandardCharsets.UTF_8));
		laneInputsSha = com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets.sha256(Files.readAllBytes(laneInputs));
		Path review = work.resolve("stand-in-review");
		Files.createDirectories(review);
		Files.write(review.resolve("SHA256SUMS.txt"), new byte[] {1});
		ClassLoader plain = HostTests.class.getClassLoader();
		T.that("lanes: entry classes are the lanes' own (A AdventurePanels, B OutfitPanels)",
			Lane.ADVENTURE.entryClass().equals(LANE_BASE + "home.AdventurePanels") && Lane.COMBAT.entryClass().equals(LANE_BASE + "outfit.OutfitPanels")
			&& Lane.ACCOUNT.entryClass().equals(LANE_BASE + "bank.AccountPanels"));
		for (Lane l : Lane.values()) T.that("lanes: " + l + " entry lives in its own write scope",
			l.entryClass().startsWith(LANE_BASE) && l.routes().stream().anyMatch(r -> l.entryClass().startsWith(LANE_BASE + r.id() + ".")));
		lanesOnClasspath = !LaneDiscovery.inspect(plain).entries.isEmpty();
		if (lanesOnClasspath) System.out.println("HostTests: lane entries on the classpath (assembled tree) " + LaneDiscovery.inspect(plain).entries.keySet());
		else T.that("lanes: none on the plain classpath", LaneDiscovery.inspect(plain).absent.size() == Lane.values().length
			&& LaneDiscovery.load(plain, laneInputs, review, null, laneInputsSha).isEmpty());
		Map<String, String> ok = new LinkedHashMap<>();
		ok.put(LANE_BASE + "home.AdventurePanels", ADVENTURE_ENTRY);
		ok.put(LANE_BASE + "outfit.OutfitPanels", entry("outfit", "OutfitPanels",
			"public static List<RouteRegistration> registrations(Path a, Path b) { return Arrays.asList(new RouteRegistration(RouteId.OUTFIT, Lane.COMBAT.name(), c -> new javax.swing.JPanel()), new RouteRegistration(RouteId.CHRONICLE, Lane.COMBAT.name(), c -> new javax.swing.JPanel())); }"));
		ClassLoader two = lanes(ok);
		LaneDiscovery.Inspection in = LaneDiscovery.inspect(two);
		T.that("lanes: present and absent lanes reported", in.entries.keySet().equals(EnumSet.of(Lane.ADVENTURE, Lane.COMBAT)) && in.absent.contains(Lane.ACCOUNT));
		T.that("lanes: no inputs, no lane is called", LaneDiscovery.load(two, null, review, null).isEmpty() && homeLog(two).isEmpty());
		List<RouteRegistration> regs = LaneDiscovery.load(two, laneInputs, review, null, laneInputsSha);
		T.that("lanes: both lanes' routes registered", regs.size() == 3);
		T.that("lanes: entry called off the EDT", homeLog(two).contains("loaded on EDT=false"));
		try { LaneDiscovery.load(two, laneInputs, review, null); T.that("lanes: inputs other than the accepted ZIP refused", false); }
		catch (IOException e) { T.that("lanes: inputs other than the accepted ZIP refused", e.getMessage().contains("not the accepted")); }
		try { LaneDiscovery.load(two, laneInputs, work.resolve("missing-review"), null, laneInputsSha); T.that("lanes: a lane's input failure stops the start", false); }
		catch (IOException e) { T.that("lanes: a lane's input failure stops the start", e.getMessage().contains("ADVENTURE lane could not load")); }
		ClassLoader foreign = lanes(Collections.singletonMap(LANE_BASE + "bank.AccountPanels", entry("bank", "AccountPanels",
			"public static List<RouteRegistration> registrations(Path a, Path b) { return Collections.singletonList(new RouteRegistration(RouteId.OUTFIT, Lane.ACCOUNT.name(), c -> null)); }")));
		T.throwsOn("lanes: registering another lane's route stops the start", () -> load(foreign, review), "does not own");
		ClassLoader owner = lanes(Collections.singletonMap(LANE_BASE + "bank.AccountPanels", entry("bank", "AccountPanels",
			"public static List<RouteRegistration> registrations(Path a, Path b) { return Collections.singletonList(new RouteRegistration(RouteId.BANK, Lane.COMBAT.name(), c -> null)); }")));
		T.throwsOn("lanes: wrong owner name stops the start", () -> load(owner, review), "under owner");
		ClassLoader twice = lanes(Collections.singletonMap(LANE_BASE + "bank.AccountPanels", entry("bank", "AccountPanels",
			"public static List<RouteRegistration> registrations(Path a, Path b) { RouteRegistration r = new RouteRegistration(RouteId.BANK, Lane.ACCOUNT.name(), c -> null); return Arrays.asList(r, r); }")));
		T.throwsOn("lanes: one route registered twice stops the start", () -> load(twice, review), "twice");
		ClassLoader bad = lanes(Collections.singletonMap(LANE_BASE + "bank.AccountPanels", entry("bank", "AccountPanels",
			"public static RouteRegistration registration() { return null; }")));
		T.throwsOn("lanes: old single-route shape refused clearly", () -> LaneDiscovery.inspect(bad), "registrations(Path laneInputsZip, Path frozenReview)");
		ClassLoader boom = lanes(Collections.singletonMap(LANE_BASE + "bank.AccountPanels", entry("bank", "AccountPanels",
			"public static List<RouteRegistration> registrations(Path a, Path b) { throw new IllegalStateException(\"lane bug\"); }")));
		T.throwsOn("lanes: a lane that throws stops the start", () -> load(boom, review), "ACCOUNT lane entry failed");
	}

	private static List<RouteRegistration> load(ClassLoader l, Path review) {
		try { return LaneDiscovery.load(l, laneInputs, review, null, laneInputsSha); } catch (IOException e) { throw new RuntimeException(e); }
	}

	private static void records() throws IOException {
		RunRecord r = RunRecord.memory();
		r.event("scope", "A:1 generation=1");
		r.event("phase", "evil\nline");
		T.that("record: safe event kept", r.has("scope"));
		T.that("record: unsafe text refused, not written", r.has("refused-record") && !String.join("\n", r.lines()).contains("evil"));
		T.throwsOn("record: file names restricted", () -> { try { r.file("../x.txt", "x"); } catch (IOException e) { throw new RuntimeException(e); } }, null);
	}

	private static void pins() throws IOException {
		T.that("pin: launcher and host agree on the package digest", PackagePin.SUMS_SHA256.equals(PlaytestLauncher.PACKAGE_SUMS_SHA256));
		Path fake = work.resolve("fake-package");
		Files.createDirectories(fake);
		try { PackagePin.verify(fake); T.that("pin: folder without sums refused", false); } catch (IOException e) { T.that("pin: folder without sums refused", e.getMessage().contains("SHA256SUMS")); }
		Files.write(fake.resolve("SHA256SUMS.txt"), "00  Project-Raccoon-R02.html\n".getBytes(StandardCharsets.UTF_8));
		Files.write(fake.resolve("Project-Raccoon-R02.html"), "<html></html>".getBytes(StandardCharsets.UTF_8));
		try { PackagePin.verify(fake); T.that("pin: other package refused", false); } catch (IOException e) { T.that("pin: other package refused", e.getMessage().contains("not the accepted R02 package")); }
		try { PlaytestSession.Package.load(fake); T.that("pin: session load refuses other package", false); } catch (IOException e) { T.that("pin: session load refuses other package", true); }
	}

	private static void nameAudit() {
		Registry r = new Registry(Arrays.asList(new ItemDef(4550, "Bullseye lantern", false, "x"), new ItemDef(1, "Toolkit, old", false, "x"),
			new ItemDef(2, "Cannonball", false, "x")), Collections.emptyList());
		Map<Integer, String> nat = new LinkedHashMap<>();
		nat.put(4550, "Bullseye lantern");
		nat.put(1, "Toolkit");
		NameAudit.Result a = NameAudit.run(r, nat::get);
		T.that("name audit: counts", a.checked == 3 && a.mismatches == 1 && a.unresolved == 1);
		T.that("name audit: csv", a.csv.startsWith("item_id,fixture_name,native_name,verdict\n1,\"Toolkit, old\",Toolkit,MISMATCH\n2,Cannonball,,UNRESOLVED\n4550,Bullseye lantern,Bullseye lantern,MATCH\n"));
	}

	private static void assetIds() {
		T.that("assets: frozen ids", "PR-028".equals(HostAssets.assetId(ArtKey.SURFACE_FIELDSTONE)) && "SIG-03".equals(HostAssets.assetId(ArtKey.SCENE_FOOTER)));
		T.that("assets: non-frozen kit files resolve to fallback", HostAssets.assetId(ArtKey.FRAME_STONE_NINE_SLICE) == null && HostAssets.assetId(ArtKey.DIVIDER_PAW) == null);
	}

	/** What the host, the RuneLite glue and the launcher can reach, read from the compiled bytecode. */
	private static void forbiddenApis() throws IOException {
		Path classes = build.resolve("classes/java");
		ClassScan r02 = ClassScan.of(classes.resolve("r02"));
		ClassScan glue = ClassScan.of(classes.resolve("hostPlugin"));
		ClassScan launcher = ClassScan.of(classes.resolve("launcher"));
		ClassScan isolation = ClassScan.of(classes.resolve("isolation"));
		T.that("scan: preferences isolation uses no RuneLite, Raccoon UI or network class", isolation.classes == 2
			&& none(isolation.types, "net/runelite/", "com/feralenterprises/raccoon/ui/", "com/feralenterprises/raccoon/playtest/r02/", "java/net/"));
		T.that("scan: classes found", r02.classes > 40 && glue.classes >= 4 && launcher.classes >= 2);
		T.that("scan: R02 module has no RuneLite reference", none(r02.types, "net/runelite/"));
		for (ClassScan s : Arrays.asList(r02, glue, launcher)) {
			T.that("scan: no network classes", none(s.types, "java/net/Socket", "java/net/URL", "java/net/HttpURLConnection", "java/net/http/",
				"java/net/DatagramSocket", "java/net/InetAddress", "javax/net/", "okhttp3/", "java/rmi/"));
		}
		Set<String> allowed = new HashSet<>(Arrays.asList("net/runelite/api/Client", "net/runelite/api/GameState", "net/runelite/api/ItemComposition",
			"net/runelite/api/events/GameStateChanged", "net/runelite/client/RuneLite", "net/runelite/client/callback/ClientThread",
			"net/runelite/client/eventbus/Subscribe", "net/runelite/client/game/ItemManager", "net/runelite/client/game/SpriteManager",
			"net/runelite/client/plugins/Plugin", "net/runelite/client/plugins/PluginDescriptor", "net/runelite/client/ui/ClientToolbar",
			"net/runelite/client/ui/NavigationButton", "net/runelite/client/ui/NavigationButton$Builder", "net/runelite/client/ui/PluginPanel",
			"net/runelite/client/ui/FontManager", "net/runelite/client/util/AsyncBufferedImage", "net/runelite/client/externalplugins/ExternalPluginManager"));
		List<String> extra = new ArrayList<>();
		for (String t : glue.types) if (t.startsWith("net/runelite/") && !allowed.contains(t)) extra.add(t);
		T.that("scan: RuneLite glue uses only the allowed types " + extra, extra.isEmpty());
		List<String> clientCalls = new ArrayList<>();
		for (String m : glue.methods) if (m.startsWith("net/runelite/api/Client.")) clientCalls.add(m);
		T.that("scan: the only Client call is getGameState " + clientCalls, clientCalls.equals(Collections.singletonList("net/runelite/api/Client.getGameState")));
		T.that("scan: item manager used only for names and images", subset(glue.methods, "net/runelite/client/game/ItemManager.", "getItemComposition", "getImage"));
		T.that("scan: launcher uses no RuneLite or host class", none(launcher.types, "net/runelite/", "com/feralenterprises/raccoon/playtest/r02/", "com/feralenterprises/raccoon/ui/"));
	}

	private static boolean none(Set<String> types, String... prefixes) {
		for (String t : types) for (String p : prefixes) if (t.startsWith(p)) { System.out.println("  forbidden reference " + t); return false; }
		return true;
	}

	private static boolean subset(Set<String> methods, String owner, String... names) {
		Set<String> ok = new HashSet<>(Arrays.asList(names));
		for (String m : methods) if (m.startsWith(owner) && !ok.contains(m.substring(owner.length()))) { System.out.println("  unexpected call " + m); return false; }
		return true;
	}

	private static void artifacts() throws IOException {
		for (String jar : new String[] {"raccoon-ui-framework.jar", "raccoon-ui-workbench.jar"}) {
			Path p = build.resolve("libs").resolve(jar);
			if (!Files.exists(p)) { T.that(jar + " built", false); continue; }
			try (JarFile j = new JarFile(p.toFile())) {
				T.that(jar + " has no playtest, host or launcher classes", j.stream().noneMatch(e -> e.getName().contains("/playtest/") || e.getName().contains("net/runelite")));
			}
		}
	}

	// ------------------------------------------------------------------ with the frozen package

	private static <V> V edt(Callable<V> c) throws Exception {
		AtomicReference<V> out = new AtomicReference<>();
		AtomicReference<Exception> err = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> { try { out.set(c.call()); } catch (Exception e) { err.set(e); } });
		if (err.get() != null) throw err.get();
		return out.get();
	}

	private static void flush() throws Exception { edt(() -> null); }

	private static void packageLoad(PlaytestSession.Package pkg) throws Exception {
		T.that("package: verified root", Files.isRegularFile(pkg.root.resolve("SHA256SUMS.txt")));
		T.that("package: parent folder also accepted", PackagePin.verify(pkg.root.getParent()).equals(pkg.root) || !Files.isDirectory(pkg.root.getParent().resolve("Project-Raccoon-R02")));
		try {
			edt(() -> PlaytestSession.Package.load(pkgDir));
			T.that("package: load refused on the EDT", false);
		} catch (IllegalStateException e) {
			T.that("package: load refused on the EDT", e.getMessage().contains("off the EDT"));
		}
		Registry r = pkg.data.registry();
		T.that("native naming: 4550 is Bullseye lantern", "Bullseye lantern".equals(r.itemName(4550)));
		T.that("native naming: earlier corrections kept", "Bird house".equals(r.itemName(21512)) && "Iban's staff".equals(r.itemName(1409)) && "Fishing potion(2)".equals(r.itemName(153)));
		T.that("tradeability: Helm of Neitiznot tradeable", r.item(10828).map(ItemDef::tradeable).orElse(false));
		T.that("assets: no frozen file refused", pkg.assets.refused().isEmpty());
		// Lane C: lane item sprites as the last source, exact id, only when verified by the archive's own sums.
		java.awt.image.BufferedImage png = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.io.ByteArrayOutputStream pngBytes = new java.io.ByteArrayOutputStream();
		javax.imageio.ImageIO.write(png, "png", pngBytes);
		byte[] good = pngBytes.toByteArray();
		Path zip = work.resolve("stand-in-lane-pack.zip");
		try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
			String sums = com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets.sha256(good) + "  shared/osrs-items/12788.png\n"
				+ com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets.sha256(good) + "  shared/osrs-items/23971.png\n";
			for (String[] e : new String[][] {{"R02-Lane-Inputs/SHA256SUMS", null}, {"R02-Lane-Inputs/shared/osrs-items/12788.png", "good"}, {"R02-Lane-Inputs/shared/osrs-items/23971.png", "bad"}}) {
				z.putNextEntry(new java.util.zip.ZipEntry(e[0]));
				z.write(e[1] == null ? sums.getBytes(StandardCharsets.UTF_8) : "good".equals(e[1]) ? good : new byte[] {1, 2, 3});
				z.closeEntry();
			}
		}
		String zipSha = com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets.sha256(Files.readAllBytes(zip));
		T.that("assets: bank item without a package sprite has none before the lane pack", pkg.assets.item(12788) == null);
		try { com.feralenterprises.raccoon.playtest.r02.assembly.LaneInputsArchive.open(zip); T.that("lane pack: other archive refused", false); }
		catch (IOException e) { T.that("lane pack: other archive refused", e.getMessage().contains("not the accepted")); }
		com.feralenterprises.raccoon.playtest.r02.assembly.LaneInputsArchive pack = com.feralenterprises.raccoon.playtest.r02.assembly.LaneInputsArchive.open(zip, zipSha);
		java.awt.image.BufferedImage packaged = pkg.assets.item(359);
		pkg.assets.withLaneSprites(pack);
		T.that("lane pack: verified sprite fills the gap by exact id", pkg.assets.item(12788) != null && pkg.assets.item(12788).getWidth() == 4);
		T.that("lane pack: a file that fails its sum is never used", pack.bytes("shared/osrs-items/23971.png") == null && pkg.assets.item(23971) == null);
		T.that("lane pack: package sprites still come first", packaged != null && pkg.assets.item(359) != null && pkg.assets.item(359).getWidth() == packaged.getWidth());
		java.awt.image.BufferedImage stone = new HostAssets(pkg.assets, null).art(ArtKey.SURFACE_FIELDSTONE);
		T.that("assets: PR-028 fieldstone bound to its embedded copy by manifest hash", stone != null && stone.getWidth() == 160 && stone.getHeight() == 160);
		T.that("assets: an id with no file and no hashed copy stays unresolved", pkg.assets.art("PR-999") == null);
	}

	@SuppressWarnings("unchecked")
	private static List<String> homeLog(ClassLoader l) throws Exception {
		return (List<String>) l.loadClass(LANE_BASE + "home.AdventurePanels").getField("LOG").get(null);
	}

	private static void session(PlaytestSession.Package pkg) throws Exception {
		ClassLoader laneLoader = lanes(Collections.singletonMap(LANE_BASE + "home.AdventurePanels", ADVENTURE_ENTRY));
		List<RouteRegistration> regs = LaneDiscovery.load(laneLoader, laneInputs, pkg.root, null, laneInputsSha);
		List<String> log = homeLog(laneLoader);
		RunRecord rec = RunRecord.memory();
		PlaytestSession s = edt(() -> new PlaytestSession(pkg, new DefaultFontSource(), null, regs, rec));
		T.that("session: 13 routes awaiting lanes with Home registered", s.composition().unregisteredRoutes().size() == RouteId.values().length - 1);
		edt(() -> { s.start(ClientPhase.OPEN); return null; });
		FixtureSnapshot a1 = s.fixtures().current();
		T.that("session: starts on Demo A", a1 != null && "A".equals(a1.scope().account()) && a1.revision() == 1);
		T.that("session: shell shows account data", s.shell().presence() == null && a1.scope().equals(s.shell().scope()));
		T.that("session: Home page created for A", log.contains("create A:1"));
		int discards = s.shell().pageDiscards();
		edt(() -> s.vary("bank", x -> x.withBank(BankState.HISTORICAL)));
		T.that("session: same-scope variation is a newer revision", s.fixtures().current().revision() == 2 && s.fixtures().current().scope().equals(a1.scope()));
		T.that("session: variation keeps pages and reaches them", s.shell().pageDiscards() == discards && log.contains("page A:1 got A:1 r2"));
		T.throwsOn("session: a variation cannot change the scope", () -> { try { edt(() -> s.vary("x", x -> com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios.Selection.demoB(99))); } catch (Exception e) { throw new RuntimeException(e.getMessage(), e); } }, "keep the scope");
		edt(() -> { s.shell().navigator().navigate(RouteId.BANK.id()); return null; });
		edt(() -> s.selectAccount("B"));
		FixtureSnapshot b = s.fixtures().current();
		T.that("session: B is a new scope", "B".equals(b.scope().account()) && b.scope().epoch() > a1.scope().epoch() && b.revision() == 1);
		T.that("session: switching account discards pages and resets to Home", s.shell().pageDiscards() > discards && RouteId.HOME.id().equals(s.shell().navigator().current()));
		edt(() -> s.vary("archive", x -> x.withArchive(ArchiveState.PARTIAL)));
		T.that("session: A's page never receives B's data", log.stream().noneMatch(l -> l.startsWith("page A:1 got B")));
		T.that("session: B's page receives B's revision", log.stream().anyMatch(l -> l.matches("page B:\\d+ got B:\\d+ r2")));
		T.that("session: B has no page subscription leak", s.composition().openPageSubscriptions() == 1);
		// Account-scope guard.
		int genBefore = s.fixtures().generation();
		FixtureSnapshot queued = b;
		edt(() -> { s.clientPhase(ClientPhase.SUSPENDED); return null; });
		T.that("guard: suspension hides every value", s.shell().presence() == StateNotice.Presence.UNAVAILABLE && s.suspended());
		T.that("guard: suspension closes page subscriptions", s.composition().openPageSubscriptions() == 0);
		T.that("guard: suspension retires the generation", s.fixtures().generation() > genBefore);
		int rejected = s.fixtures().rejected();
		edt(() -> s.fixtures().publish(queued));
		T.that("guard: a queued publication from before is refused", s.fixtures().rejected() == rejected + 1);
		T.that("guard: controls refused while suspended", !edt(() -> s.selectAccount("A")) && !edt(() -> s.vary("bank", x -> x)) && rec.has("refused"));
		edt(() -> { s.clientPhase(ClientPhase.SUSPENDED); return null; });
		int logSize = log.size();
		edt(() -> { s.clientPhase(ClientPhase.OPEN); return null; });
		FixtureSnapshot b2 = s.fixtures().current();
		T.that("guard: resume publishes a fresh scope for the same simulated account", "B".equals(b2.scope().account()) && b2.scope().epoch() > b.scope().epoch() && s.shell().presence() == null);
		T.that("guard: resume keeps the chosen variations", s.selection().archive == ArchiveState.PARTIAL);
		T.that("guard: resume rebuilds pages", log.subList(logSize, log.size()).contains("create " + b2.scope()));
		edt(() -> s.selectAccount("A"));
		T.that("session: A again is never A's first scope", s.fixtures().current().scope().epoch() > b2.scope().epoch() && !s.fixtures().current().scope().equals(a1.scope()));
		edt(() -> s.selectAccount(null));
		T.that("session: signed out", !s.fixtures().current().scope().signedIn());
		edt(() -> { s.fixtures().dispatch(com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction.of(
			com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction.Kind.SWITCH_SCENARIO, "B")); return null; });
		T.that("session: SWITCH_SCENARIO action switches the simulated account", "B".equals(s.fixtures().current().scope().account()));
		edt(() -> s.vary("bank", x -> x.withBank(BankState.EMPTY)));
		int epochBeforeReset = s.fixtures().current().scope().epoch();
		edt(() -> { s.fixtures().dispatch(com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction.of(
			com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction.Kind.RESET_OBSERVATIONS)); return null; });
		T.that("session: Settings reset restores the account's default observations as a fresh scope",
			"B".equals(s.fixtures().current().scope().account()) && s.selection().bank == BankState.POPULATED
			&& s.fixtures().current().scope().epoch() > epochBeforeReset && s.fixtures().current().revision() == 1);
		// Teardown.
		FixtureSnapshot beforeDispose = s.fixtures().current();
		int rejectedBefore = s.fixtures().rejected();
		edt(() -> {
			// Queued by a lane or the controls before the final disposal, delivered after it (Feral's disposal fence).
			s.fixtures().publishLater(FixtureSnapshot.builder(beforeDispose.scope()).generation(beforeDispose.generation()).revision(beforeDispose.revision() + 1).build());
			s.dispose();
			return null;
		});
		flush();
		Map<String, Integer> t = s.teardownReport();
		T.that("teardown: everything released " + t, PlaytestSession.clean(t) && t.size() == 5 && Integer.valueOf(0).equals(t.get("dispatcherNotRetired")));
		T.that("fence: a publication queued before the final disposal never applies", s.fixtures().current() == beforeDispose
			&& s.fixtures().rejected() == rejectedBefore + 1 && s.fixtures().retired());
		edt(() -> { s.dispose(); s.clientPhase(ClientPhase.SUSPENDED); return null; });
		T.that("teardown: idempotent; later calls ignored", s.teardownReport() == t && !edt(() -> s.selectAccount("A")));
		T.that("record: session events written", rec.has("session") && rec.has("scope") && rec.has("phase") && rec.has("teardown"));
	}

	private static void startSuspended(PlaytestSession.Package pkg) throws Exception {
		PlaytestSession s = edt(() -> new PlaytestSession(pkg, new DefaultFontSource(), null, Collections.emptyList(), RunRecord.memory()));
		edt(() -> { s.start(ClientPhase.SUSPENDED); return null; });
		T.that("guard: started while signed in shows nothing", s.fixtures().current() == null && s.shell().presence() == StateNotice.Presence.UNAVAILABLE);
		edt(() -> { s.clientPhase(ClientPhase.OPEN); return null; });
		T.that("guard: first OPEN publishes Demo A", s.fixtures().current() != null && "A".equals(s.fixtures().current().scope().account()));
		edt(() -> { s.dispose(); return null; });
		T.that("teardown: clean after suspended start", PlaytestSession.clean(s.teardownReport()));
	}

	@SuppressWarnings("unchecked")
	private static void controls(PlaytestSession.Package pkg) throws Exception {
		PlaytestSession s = edt(() -> new PlaytestSession(pkg, new DefaultFontSource(), null, Collections.emptyList(), RunRecord.memory()));
		PlaytestControls c = edt(() -> { s.start(ClientPhase.OPEN); return new PlaytestControls(s); });
		List<RDropdown<?>> d = new ArrayList<>();
		for (Component k : c.getComponents()) if (k instanceof RDropdown) d.add((RDropdown<?>) k);
		T.that("controls: six dropdowns", d.size() == 6);
		RDropdown<String> account = (RDropdown<String>) d.get(0);
		RDropdown<BankState> bank = (RDropdown<BankState>) d.get(1);
		edt(() -> account.select("B"));
		T.that("controls: account dropdown switches the simulated account", "B".equals(s.selection().account));
		edt(() -> bank.select(BankState.EMPTY));
		T.that("controls: bank dropdown varies the scope", s.selection().bank == BankState.EMPTY && s.fixtures().current().revision() == 2);
		edt(() -> { s.clientPhase(ClientPhase.SUSPENDED); return null; });
		T.that("controls: disabled while suspended", d.stream().noneMatch(Component::isEnabled));
		edt(() -> { s.clientPhase(ClientPhase.OPEN); return null; });
		T.that("controls: enabled again and show the restored selection", d.stream().allMatch(Component::isEnabled) && "B".equals(account.selected()) && bank.selected() == BankState.EMPTY);
		edt(() -> { s.selectAccount(null); return null; });
		T.that("controls: signed out leaves only the account control", d.get(0).isEnabled() && d.subList(1, 6).stream().noneMatch(Component::isEnabled));
		edt(() -> { s.dispose(); return null; });
		T.that("teardown: clean with controls mounted", PlaytestSession.clean(s.teardownReport()));
	}

	private static Path profile(String name) throws IOException {
		Path p = work.resolve(name);
		Files.createDirectories(p);
		Files.write(p.resolve(ProfileGuard.MARKER), ProfileGuard.MARKER_CONTENT.getBytes(StandardCharsets.UTF_8));
		return p;
	}

	private static Properties props(Path profile, String pkg) {
		Properties p = new Properties();
		p.setProperty("user.home", profile.toString());
		p.setProperty(ProfileGuard.PROFILE_PROPERTY, profile.toString());
		if (pkg != null) p.setProperty(HostLifecycle.PACKAGE, pkg);
		p.setProperty(HostLifecycle.RUN, "run-test");
		p.setProperty(HostLifecycle.COMMIT, "0123456789abcdef0123456789abcdef01234567");
		return p;
	}

	private static boolean await(Callable<Boolean> c, long ms) throws Exception {
		long end = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < end) { if (edt(c)) return true; Thread.sleep(25); }
		return false;
	}

	private static void lifecycle() throws Exception {
		Path prof = profile("life-profile");
		Path rl = prof.resolve(".runelite");
		JPanel shell = new JPanel(), ctl = new JPanel();
		HostLifecycle h = new HostLifecycle(props(prof, pkgDir.toString()), new DefaultFontSource(), null, HostTests.class.getClassLoader(), shell, ctl);
		edt(() -> { h.start(rl, () -> ClientPhase.OPEN); return null; });
		T.that("lifecycle: loading placeholder shown at once", shell.getComponentCount() == 1 && h.session() == null);
		T.that("lifecycle: mounts after the background load", await(() -> h.session() != null, 30000));
		T.that("lifecycle: shell and controls mounted", shell.getComponent(0) == h.session().shell() && ctl.getComponent(0) instanceof PlaytestControls);
		Thread client = new Thread(() -> h.gameState("LOGGED_IN"), "client-thread");
		client.start();
		client.join();
		flush();
		T.that("lifecycle: GameState from the client thread suspends", h.session().suspended());
		h.gameState("LOGIN_SCREEN");
		flush();
		T.that("lifecycle: login screen resumes", !h.session().suspended());
		Map<String, Integer> t = edt(h::stop);
		T.that("lifecycle: stop is clean and empties both tabs", PlaytestSession.clean(t) && shell.getComponentCount() == 0 && ctl.getComponentCount() == 0);
		T.that("lifecycle: stop is idempotent", edt(h::stop) == t && !h.running());
		T.that("lifecycle: size listener removed", shell.getComponentListeners().length == 0);
		Path log = rl.resolve("raccoon-r02-playtest/run-test/events.log");
		String events = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
		T.that("record: run events on disk", events.contains(" start ") && events.contains(" scope ") && events.contains(" phase SUSPENDED") && events.contains("teardown clean"));
		T.that("record: no package path or home path written", !events.contains(pkgDir.toString()) && !events.contains(System.getProperty("user.home")) && !events.contains(work.toString()));

		// Stop before the load finishes: the late load is discarded, nothing mounts.
		Path prof2 = profile("early-profile");
		JPanel s2 = new JPanel(), c2 = new JPanel();
		HostLifecycle h2 = new HostLifecycle(props(prof2, pkgDir.toString()), new DefaultFontSource(), null, HostTests.class.getClassLoader(), s2, c2);
		edt(() -> { h2.start(prof2.resolve(".runelite"), () -> ClientPhase.OPEN); h2.stop(); return null; });
		T.that("lifecycle: late load discarded after an early stop", await(() -> h2.record().has("discarded"), 30000) && h2.session() == null && s2.getComponentCount() == 0);

		// A refused package: message shown, path kept out of the record.
		Path prof3 = profile("bad-profile");
		JPanel s3 = new JPanel(), c3 = new JPanel();
		Path badPkg = work.resolve("fake-package");
		HostLifecycle h3 = new HostLifecycle(props(prof3, badPkg.toString()), new DefaultFontSource(), null, HostTests.class.getClassLoader(), s3, c3);
		edt(() -> { h3.start(prof3.resolve(".runelite"), () -> ClientPhase.OPEN); return null; });
		T.that("lifecycle: refused package reported", await(() -> h3.failure() != null, 30000) && h3.session() == null);
		String ev3 = new String(Files.readAllBytes(prof3.resolve(".runelite/raccoon-r02-playtest/run-test/events.log")), StandardCharsets.UTF_8);
		T.that("record: refusal written without the path", ev3.contains("refused package") && !ev3.contains(work.toString()));
		edt(h3::stop);

		// Lanes present with inputs that are not the accepted ZIP: the host does not start (production pin).
		Path prof5 = profile("lanes-profile");
		Properties p5 = props(prof5, pkgDir.toString());
		p5.setProperty(HostLifecycle.LANE_INPUTS, laneInputs.toString());
		HostLifecycle h5 = new HostLifecycle(p5, new DefaultFontSource(), null,
			lanes(Collections.singletonMap(LANE_BASE + "home.AdventurePanels", ADVENTURE_ENTRY)), new JPanel(), new JPanel());
		edt(() -> { h5.start(prof5.resolve(".runelite"), () -> ClientPhase.OPEN); return null; });
		T.that("lifecycle: lane inputs other than the accepted ZIP stop the start", await(() -> h5.failure() != null, 30000)
			&& h5.failure().contains("not the accepted") && h5.session() == null);
		edt(h5::stop);
		T.that("record: lanes found and their state recorded", events.contains(" lanes present=" + LaneDiscovery.inspect(HostTests.class.getClassLoader()).entries.keySet())
			&& events.contains("not-called=no-lane-inputs") && (lanesOnClasspath || events.contains("absent=[ADVENTURE, COMBAT, ACCOUNT, PROGRESSION, COLLECTION]")));

		// Outside an isolated profile nothing starts and nothing is written.
		Path plain = work.resolve("plain-home");
		Files.createDirectories(plain);
		Properties p4 = props(plain, pkgDir.toString());
		p4.remove(ProfileGuard.PROFILE_PROPERTY);
		HostLifecycle h4 = new HostLifecycle(p4, new DefaultFontSource(), null, HostTests.class.getClassLoader(), new JPanel(), new JPanel());
		try {
			edt(() -> { h4.start(plain.resolve(".runelite"), () -> ClientPhase.OPEN); return null; });
			T.that("lifecycle: refuses outside the launcher profile", false);
		} catch (IllegalStateException e) {
			T.that("lifecycle: refuses outside the launcher profile", !h4.running() && !Files.exists(plain.resolve(".runelite")));
		}
	}

	/** The RuneLite glue on signature doubles: buttons, guard, name audit, teardown. Sets user.home (last section). */
	private static void plugin() throws Exception {
		Path prof = profile("plugin-profile");
		System.setProperty("user.home", prof.toString());
		System.setProperty(ProfileGuard.PROFILE_PROPERTY, prof.toString());
		System.setProperty(HostLifecycle.PACKAGE, pkgDir.toString());
		System.setProperty(HostLifecycle.RUN, "run-plugin");
		Class<?> pc = Class.forName("com.feralenterprises.raccoon.playtest.r02.runelite.R02PlaytestPlugin");
		Object plugin = pc.getDeclaredConstructor().newInstance();
		net.runelite.client.ui.ClientToolbar toolbar = new net.runelite.client.ui.ClientToolbar();
		net.runelite.client.callback.ClientThread ct = new net.runelite.client.callback.ClientThread();
		net.runelite.client.game.ItemManager items = new net.runelite.client.game.ItemManager();
		items.names.put(4550, "Bullseye lantern");
		items.names.put(21512, "Bird house");
		AtomicReference<net.runelite.api.GameState> state = new AtomicReference<>(net.runelite.api.GameState.LOGIN_SCREEN);
		net.runelite.api.Client client = state::get;
		inject(plugin, "clientToolbar", toolbar);
		inject(plugin, "client", client);
		inject(plugin, "clientThread", ct);
		inject(plugin, "itemManager", items);
		inject(plugin, "spriteManager", new net.runelite.client.game.SpriteManager());
		T.that("plugin: descriptor is a developer plugin", pc.getAnnotation(net.runelite.client.plugins.PluginDescriptor.class).developerPlugin());
		java.lang.reflect.Method startUp = pc.getDeclaredMethod("startUp"), shutDown = pc.getDeclaredMethod("shutDown");
		java.lang.reflect.Method host = pc.getDeclaredMethod("host");
		java.lang.reflect.Method onState = pc.getMethod("onGameStateChanged", net.runelite.api.events.GameStateChanged.class);
		startUp.setAccessible(true); shutDown.setAccessible(true); host.setAccessible(true);
		edt(() -> startUp.invoke(plugin));
		T.that("plugin: two sidebar tabs added", toolbar.buttons.size() == 2 && toolbar.buttons.get(0).getTooltip().equals("Raccoon R02 playtest"));
		HostLifecycle h = (HostLifecycle) host.invoke(plugin);
		T.that("plugin: session mounts", await(() -> h.session() != null, 30000));
		T.that("plugin: name audit queued on the client thread at the login screen", ct.queue.size() == 1);
		net.runelite.api.events.GameStateChanged ev = new net.runelite.api.events.GameStateChanged();
		ev.setGameState(net.runelite.api.GameState.LOGGED_IN);
		onState.invoke(plugin, ev);
		flush();
		T.that("plugin: login hides simulated data", h.session().suspended() && h.session().shell().presence() == StateNotice.Presence.UNAVAILABLE);
		ev.setGameState(net.runelite.api.GameState.LOGIN_SCREEN);
		onState.invoke(plugin, ev);
		flush();
		T.that("plugin: back at the login screen shows it again", !h.session().suspended());
		ct.queue.remove(0).run();
		flush();
		Path csv = prof.resolve(".runelite/raccoon-r02-playtest/run-plugin/native-name-audit.csv");
		String audit = Files.exists(csv) ? new String(Files.readAllBytes(csv), StandardCharsets.UTF_8) : "";
		T.that("plugin: native name audit written", audit.contains("4550,Bullseye lantern,Bullseye lantern,MATCH") && audit.contains("21512,Bird house,Bird house,MATCH"));
		edt(() -> shutDown.invoke(plugin));
		T.that("plugin: shutDown removes both tabs and tears down cleanly", toolbar.buttons.isEmpty() && host.invoke(plugin) == null && !h.running()
			&& h.record().has("stopped") && String.join("\n", h.record().lines()).contains("teardown clean"));
		edt(() -> shutDown.invoke(plugin));
		T.that("plugin: shutDown twice is harmless", toolbar.buttons.isEmpty());

		System.clearProperty(ProfileGuard.PROFILE_PROPERTY);
		Object p2 = pc.getDeclaredConstructor().newInstance();
		net.runelite.client.ui.ClientToolbar tb2 = new net.runelite.client.ui.ClientToolbar();
		inject(p2, "clientToolbar", tb2);
		inject(p2, "client", client);
		inject(p2, "clientThread", ct);
		inject(p2, "itemManager", items);
		inject(p2, "spriteManager", new net.runelite.client.game.SpriteManager());
		try {
			edt(() -> startUp.invoke(p2));
			T.that("plugin: refuses to start outside the isolated profile", false);
		} catch (java.lang.reflect.InvocationTargetException e) {
			T.that("plugin: refuses to start outside the isolated profile", tb2.buttons.isEmpty() && e.getCause() instanceof IllegalStateException);
		}
	}

	private static void inject(Object target, String field, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}
}
