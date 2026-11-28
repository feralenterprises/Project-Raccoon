package com.feralenterprises.raccoon.playtest.launcher;

import com.feralenterprises.raccoon.playtest.host.test.Check;
import com.feralenterprises.raccoon.playtest.host.test.Compile;
import com.feralenterprises.raccoon.playtest.r02.host.PackagePin;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * The playtest launcher end to end, offline, with a fake RuneLite: a fake client JAR (runelite.properties only) and a
 * fake host JAR whose R02PlaytestMain reports what the child JVM actually received. Args: work dir, optional extracted
 * frozen package (only its SHA256SUMS.txt is copied). The real client is never started.
 */
public final class LauncherTests {
	private static final Check T = new Check("LauncherTests");
	private static final String SECRET = "SECRET-VALUE-7f3c";
	private static Path work, repo, home, libs, classpath, lock, pkg, isolationJar;
	private static String commit;

	public static void main(String[] args) throws Exception {
		work = Paths.get(args[0]).toAbsolutePath();
		deleteTree(work);
		Files.createDirectories(work);
		isolationJar = Paths.get(args[1]).toAbsolutePath();
		Path frozen = args.length > 2 ? Paths.get(args[2]) : null;
		home = Files.createDirectories(work.resolve("home"));
		setUpRepo();
		setUpJars("1.13.8");
		pkg = Files.createDirectories(work.resolve("outside/Project-Raccoon-R02"));
		syntheticPackage();
		staticRules();
		jdkRules();
		preferences();
		recordLock();
		ready();
		launchHappyPath();
		teardownOnInterrupt();
		signInLeftBehind();
		singleInstance();
		refusals();
		isolationLatch();
		wipe();
		if (frozen == null) System.out.println("LauncherTests: frozen package not given; the accepted package's own verification SKIPPED");
		else frozenPackage(frozen);
		System.exit(T.finish());
	}

	// ------------------------------------------------------------------ fixtures

	private static String git(Path dir, String... a) throws Exception {
		List<String> c = new ArrayList<>(Arrays.asList("git", "-C", dir.toString(), "-c", "user.name=test", "-c", "user.email=test@example.invalid", "-c", "commit.gpgsign=false"));
		c.addAll(Arrays.asList(a));
		Process p = new ProcessBuilder(c).redirectErrorStream(true).start();
		String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (p.waitFor() != 0) throw new IOException("git " + Arrays.toString(a) + ": " + out);
		return out.trim();
	}

	private static void setUpRepo() throws Exception {
		repo = Files.createDirectories(work.resolve("repo"));
		git(repo, "init", "-q");
		Files.write(repo.resolve(".gitignore"), "build/\n".getBytes(StandardCharsets.UTF_8));
		Files.write(repo.resolve("README.md"), "x\n".getBytes(StandardCharsets.UTF_8));
		git(repo, "add", "-A");
		git(repo, "commit", "-q", "-m", "base");
		git(repo, "remote", "add", "origin", "https://github.com/feralenterprises/Project-Raccoon.git");
		git(repo, "update-ref", "refs/remotes/origin/dev/r02-ui", "HEAD");
		commit = git(repo, "rev-parse", "HEAD");
		libs = Files.createDirectories(work.resolve("gradle-cache"));
		classpath = repo.resolve("build/playtest/classpath.txt");
		lock = repo.resolve("playtest/runelite-1.13.8.lock");
	}

	private static final String FAKE_MAIN = "package com.feralenterprises.raccoon.playtest.r02.runelite;\n"
		+ "import java.nio.file.*; import java.util.*;\n"
		+ "public final class R02PlaytestMain {\n"
		+ "  public static void main(String[] a) throws Exception {\n"
		+ "    Path home = Paths.get(System.getProperty(\"user.home\"));\n"
		+ "    StringBuilder b = new StringBuilder();\n"
		+ "    b.append(\"user.home=\").append(home).append('\\n');\n"
		+ "    for (String k : new String[] {\"raccoon.r02.profile\", \"raccoon.r02.package\", \"raccoon.r02.run\", \"raccoon.r02.commit\", \"raccoon.r02.host.sha256\", \"raccoon.r02.lock.sha256\"})\n"
		+ "      b.append(k).append('=').append(System.getProperty(k)).append('\\n');\n"
		+ "    b.append(\"env=\").append(new TreeSet<>(System.getenv().keySet())).append('\\n');\n"
		+ "    b.append(\"envvalues=\").append(System.getenv().values()).append('\\n');\n"
		+ "    b.append(\"args=\").append(Arrays.toString(a)).append('\\n');\n"
		+ "    boolean ea = false; assert ea = true; b.append(\"assertions=\").append(ea).append('\\n');\n"
		+ "    b.append(\"jvm=\").append(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()).append('\\n');\n"
		+ "    b.append(\"cwd=\").append(System.getProperty(\"user.dir\")).append('\\n');\n"
		+ "    b.append(\"tmpdir=\").append(System.getProperty(\"java.io.tmpdir\")).append('\\n');\n"
		+ "    b.append(\"TEMP=\").append(System.getenv(\"TEMP\")).append('\\n');\n"
		+ "    java.util.prefs.Preferences pr = java.util.prefs.Preferences.userRoot().node(\"raccoon-test\").node(\"a b/c\".replace('/', '_'));\n"
		+ "    pr.put(\"k\", \"v\"); pr.flush();\n"
		+ "    b.append(\"prefsFactory=\").append(System.getProperty(\"java.util.prefs.PreferencesFactory\")).append('\\n');\n"
		+ "    Files.write(home.resolve(\"child-report.txt\"), b.toString().getBytes());\n"
		+ "    String mode = System.getenv(\"RACCOON_FAKE_MODE\");\n"
		+ "    if (\"spawn\".equals(mode)) {\n"
		+ "      Process g = new ProcessBuilder(Paths.get(System.getProperty(\"java.home\"), \"bin\", \"java\").toString(), \"-cp\", System.getProperty(\"java.class.path\"),\n"
		+ "        \"com.feralenterprises.raccoon.playtest.r02.runelite.Sleeper\").start();\n"
		+ "      Files.write(home.resolve(\"pids.txt\"), (ProcessHandle.current().pid() + \" \" + g.pid()).getBytes());\n"
		+ "      Thread.sleep(120000);\n"
		+ "    }\n"
		+ "    if (\"touchhome\".equals(mode)) { Path r = Paths.get(System.getenv(\"RACCOON_FAKE_REALHOME\")); Files.createDirectories(r.resolve(\"jagexcache\")); Files.write(r.resolve(\"jagexcache/x\"), new byte[] {1}); }\n"
		+ "    if (\"webview\".equals(mode)) Files.createDirectories(Paths.get(System.getProperty(\"java.io.tmpdir\"), \"runelite_jagexauth123.WebView2\"));\n"
		+ "    if (\"signin\".equals(mode)) { Files.createDirectories(home.resolve(\".runelite\")); Files.write(home.resolve(\".runelite/jagex-accounts.dat\"), new byte[] {1}); }\n"
		+ "    System.exit(\"exit7\".equals(mode) ? 7 : 0);\n"
		+ "  }\n"
		+ "}\n";

	private static void jar(Path out, Map<String, byte[]> entries) throws IOException {
		Files.createDirectories(out.getParent());
		try (JarOutputStream j = new JarOutputStream(Files.newOutputStream(out))) {
			for (Map.Entry<String, byte[]> e : entries.entrySet()) { j.putNextEntry(new JarEntry(e.getKey())); j.write(e.getValue()); j.closeEntry(); }
		}
	}

	private static Path hostJar;
	private static Path clientJar;
	private static Path depJar;

	private static void setUpJars(String clientVersion) throws Exception {
		Path classes = Files.createTempDirectory(work, "fake-host");
		Map<String, String> src = new LinkedHashMap<>();
		src.put("com.feralenterprises.raccoon.playtest.r02.runelite.R02PlaytestMain", FAKE_MAIN);
		src.put("com.feralenterprises.raccoon.playtest.r02.runelite.Sleeper", "package com.feralenterprises.raccoon.playtest.r02.runelite;\n"
			+ "public final class Sleeper { public static void main(String[] a) throws Exception { Thread.sleep(120000); } }\n");
		src.put("test.LockHolder", "package test;\nimport java.nio.channels.*; import java.nio.file.*;\n"
			+ "public final class LockHolder { public static void main(String[] a) throws Exception {\n"
			+ "  FileChannel c = FileChannel.open(Paths.get(a[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock l = c.lock();\n"
			+ "  Files.write(Paths.get(a[1]), new byte[] {1}); Thread.sleep(120000); } }\n");
		Compile.into(classes, null, src);
		Map<String, byte[]> host = new LinkedHashMap<>();
		try (Stream<Path> w = Files.walk(classes)) {
			for (Path p : (Iterable<Path>) w.filter(Files::isRegularFile)::iterator) host.put(classes.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
		}
		hostJar = repo.resolve("build/libs/raccoon-r02-playtest-host.jar");
		jar(hostJar, host);
		clientJar = libs.resolve("client-" + clientVersion + ".jar");
		jar(clientJar, Map.of("net/runelite/client/runelite.properties", ("runelite.version=" + clientVersion + "\n").getBytes(StandardCharsets.UTF_8)));
		depJar = libs.resolve("guice-4.2.3.jar");
		jar(depJar, Map.of("com/google/inject/Marker.txt", "guice".getBytes(StandardCharsets.UTF_8)));
		writeClasspath(commit, clientVersion, null);
	}

	private static void writeClasspath(String builtFrom, String clientVersion, String extraLine) throws IOException {
		List<String> l = new ArrayList<>();
		l.add("# Raccoon R02 playtest resolved classpath (generated; not committed)");
		l.add("# commit " + builtFrom);
		l.add(PlaytestLauncher.sha256(hostJar) + " host " + hostJar);
		l.add(PlaytestLauncher.sha256(depJar) + " com.google.inject:guice:4.2.3 " + depJar);
		l.add(PlaytestLauncher.sha256(clientJar) + " net.runelite:client:" + clientVersion + " " + clientJar);
		if (extraLine != null) l.add(extraLine);
		Files.createDirectories(classpath.getParent());
		Files.write(classpath, (String.join("\n", l) + "\n").getBytes(StandardCharsets.UTF_8));
	}

	/** SHA-256 of the synthetic package's SHA256SUMS.txt; the launch path is tested against it, never the private package. */
	private static String pin;

	/** A small package laid out like the frozen review: a page, assets and SHA256SUMS.txt (no private content). */
	private static void syntheticPackage() throws IOException {
		Files.createDirectories(pkg.resolve("assets"));
		Files.write(pkg.resolve("Project-Raccoon-R02.html"), "<!doctype html><title>synthetic</title>\n".getBytes(StandardCharsets.UTF_8));
		Files.write(pkg.resolve("assets/a.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
		Files.write(pkg.resolve("assets/b.json"), "{\"synthetic\":true}\n".getBytes(StandardCharsets.UTF_8));
		writeSums();
	}

	private static void writeSums() throws IOException {
		StringBuilder b = new StringBuilder();
		for (String f : new String[] {"Project-Raccoon-R02.html", "assets/a.png", "assets/b.json"})
			b.append(PlaytestLauncher.sha256(pkg.resolve(f))).append("  ").append(f).append('\n');
		Files.write(pkg.resolve("SHA256SUMS.txt"), b.toString().getBytes(StandardCharsets.UTF_8));
		pin = PlaytestLauncher.sha256(pkg.resolve("SHA256SUMS.txt"));
	}

	/** The accepted private package, read only: the production pin and every payload hash verify. */
	private static void frozenPackage(Path frozen) throws Exception {
		PlaytestLauncher.Settings s = settings();
		s.packageDir = PackagePin.verify(frozen);
		s.packageSumsSha256 = PlaytestLauncher.PACKAGE_SUMS_SHA256;
		boolean ok = new PlaytestLauncher(s).check(true);
		if (!ok) System.out.println(out());
		T.that("frozen: the accepted package verifies under the production pin, payloads included", ok && out().contains("frozen package is the accepted R02 package"));
	}

	private static final ByteArrayOutputStream OUT = new ByteArrayOutputStream();

	private static PlaytestLauncher.Settings settings() {
		Map<String, String> env = new LinkedHashMap<>();
		env.put("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
		env.put("JX_SESSION_ID", SECRET);
		env.put("JX_CHARACTER_ID", SECRET);
		env.put("jx_display_name", SECRET);
		env.put("JAVA_TOOL_OPTIONS", "-javaagent:/nonexistent/" + SECRET + ".jar");
		env.put("_JAVA_OPTIONS", "-Duser.home=/tmp/" + SECRET);
		env.put("JDK_JAVA_OPTIONS", "-D" + SECRET);
		env.put("CLASSPATH", "/tmp/" + SECRET);
		PlaytestLauncher.Settings s = PlaytestLauncher.Settings.production(repo, env);
		s.classpathFile = classpath;
		s.lockFile = lock;
		s.realHome = home;
		s.profile = home.resolve(".raccoon-r02-playtest/profile");
		s.packageDir = pkg.getParent();
		s.packageSumsSha256 = pin;
		// The launch path runs on whatever JDK runs the tests; jdkRules() proves production still demands the pinned one.
		s.jdkVersion = System.getProperty("java.version");
		s.jdkVendor = System.getProperty("java.vendor");
		s.isolationJar = isolationJar;
		try { s.isolationSha = PlaytestLauncher.sha256(isolationJar); } catch (IOException e) { throw new RuntimeException(e); }
		s.windows = false;
		s.account = () -> new String[] {"RaccoonTest1", "no"};
		s.processes = Collections::emptyList;
		OUT.reset();
		s.out = new PrintStream(OUT, true);
		return s;
	}

	private static String out() { return new String(OUT.toByteArray(), StandardCharsets.UTF_8); }

	// ------------------------------------------------------------------ cases

	private static void jdkRules() {
		PlaytestLauncher.Settings p = PlaytestLauncher.Settings.production(repo, new LinkedHashMap<>());
		T.that("jdk: production requires Temurin 11.0.33", "11.0.33".equals(p.jdkVersion) && "Eclipse Adoptium".equals(p.jdkVendor));
		PlaytestLauncher.Settings s = settings();
		s.jdkVersion = "0.0.0";
		T.that("jdk: any other JDK is refused", !new PlaytestLauncher(s).check(true) && out().contains("the playtest needs"));
	}

	private static void staticRules() {
		PlaytestLauncher l = new PlaytestLauncher(settings());
		List<String> removed = new ArrayList<>();
		Map<String, String> env = l.childEnvironment(removed);
		T.that("env: Jagex launcher session variables removed (any case)", !env.containsKey("JX_SESSION_ID") && !env.containsKey("JX_CHARACTER_ID") && !env.containsKey("jx_display_name"));
		T.that("env: injected JVM options and CLASSPATH removed", !env.containsKey("JAVA_TOOL_OPTIONS") && !env.containsKey("_JAVA_OPTIONS") && !env.containsKey("JDK_JAVA_OPTIONS") && !env.containsKey("CLASSPATH"));
		T.that("env: ordinary variables kept", env.containsKey("PATH"));
		T.that("env: removed names reported, values never", removed.size() == 7 && !String.join(",", removed).contains(SECRET));
		T.that("args: fixed developer-mode client arguments", PlaytestLauncher.CLIENT_ARGS.equals(Arrays.asList("--developer-mode", "--disable-telemetry")));
		for (String a : PlaytestLauncher.CLIENT_ARGS) T.that("args: no credential or profile flag " + a, !a.contains("insecure") && !a.contains("session") && !a.equals("--profile") && !a.contains("jav_config"));
		T.that("remote: feralenterprises GitHub URLs accepted", PlaytestLauncher.GITHUB_REMOTE.matcher("https://github.com/feralenterprises/Project-Raccoon.git").matches()
			&& PlaytestLauncher.GITHUB_REMOTE.matcher("git@github.com:feralenterprises/Project-Raccoon.git").matches());
		T.that("remote: other owners, hosts and look-alikes refused", !PlaytestLauncher.GITHUB_REMOTE.matcher("https://github.com/someone/Project-Raccoon.git").matches()
			&& !PlaytestLauncher.GITHUB_REMOTE.matcher("https://github.com.evil.example/feralenterprises/x").matches()
			&& !PlaytestLauncher.GITHUB_REMOTE.matcher("/home/claude/repin/in/raccoon.bundle").matches());
		T.that("display: home shown as ~", l.home(home.resolve("a/b")).equals("~" + java.io.File.separator + "a" + java.io.File.separator + "b"));
		T.that("pin: package digest agrees with the host", PlaytestLauncher.PACKAGE_SUMS_SHA256.equals(PackagePin.SUMS_SHA256));
		T.that("pin: lane inputs digest agrees with the host", PlaytestLauncher.LANE_INPUTS_SHA256.equals(com.feralenterprises.raccoon.playtest.r02.host.LaneDiscovery.LANE_INPUTS_SHA256));
		List<String> envRemoved = new ArrayList<>();
		Map<String, String> childEnv = l.childEnvironment(envRemoved);
		T.that("env: TEMP, TMP and TMPDIR point into the profile", childEnv.get("TEMP").endsWith("tmp") && childEnv.get("TMP").equals(childEnv.get("TEMP"))
			&& childEnv.get("TMPDIR").equals(childEnv.get("TEMP")));
	}

	private static void recordLock() throws Exception {
		PlaytestLauncher l = new PlaytestLauncher(settings());
		T.that("lock: missing lock refuses check", !l.check(true) && out().contains("no classpath lock"));
		T.that("lock: recorded from the resolved classpath", new PlaytestLauncher(settings()).recordLock() && Files.isRegularFile(lock));
		String text = new String(Files.readAllBytes(lock), StandardCharsets.UTF_8);
		T.that("lock: marked unreviewed, host JAR excluded, no absolute paths", text.contains("# reviewed: no") && !text.contains(" host ")
			&& !text.contains(libs.toString()) && text.contains("net.runelite:client:1.13.8 client-1.13.8.jar"));
		T.that("lock: never overwritten", !new PlaytestLauncher(settings()).recordLock() && out().contains("never overwritten"));
		git(repo, "add", "-A");
		git(repo, "commit", "-q", "-m", "unreviewed lock");
		git(repo, "update-ref", "refs/remotes/origin/dev/r02-ui", "HEAD");
		writeClasspath(git(repo, "rev-parse", "HEAD"), "1.13.8", null);
		if (pkg != null && Files.exists(pkg.resolve("SHA256SUMS.txt"))) {
			PlaytestLauncher u = new PlaytestLauncher(settings());
			T.that("lock: an unreviewed lock refuses the launch", u.launch() == 1 && out().contains("is not marked independently reviewed") && u.child() == null);
		}
		Files.write(lock, new String(Files.readAllBytes(lock), StandardCharsets.UTF_8).replace("# reviewed: no", "# reviewed: yes").getBytes(StandardCharsets.UTF_8));
		git(repo, "commit", "-q", "-am", "reviewed lock");
		git(repo, "update-ref", "refs/remotes/origin/dev/r02-ui", "HEAD");
		commit = git(repo, "rev-parse", "HEAD");
		writeClasspath(commit, "1.13.8", null);
	}

	private static void ready() {
		PlaytestLauncher l = new PlaytestLauncher(settings());
		boolean ok = l.check(true);
		if (!ok) System.out.println(out());
		T.that("check: READY with everything in order", ok && out().contains("RESULT: READY"));
		T.that("check: reports the reviewed lock", out().contains("independently reviewed"));
		T.that("check: reports the test account, processes and isolation JAR", out().contains("PASS  dedicated standard test account RaccoonTest1")
			&& out().contains("PASS  RuneLite and the Jagex Launcher are not running") && out().contains("PASS  preferences-isolation JAR"));
		T.that("check: reports HEAD on the GitHub remote", out().contains("is on origin/dev/r02-ui"));
		T.that("check: starts nothing and creates no profile", l.child() == null && !Files.exists(home.resolve(".raccoon-r02-playtest")));
	}

	private static Map<String, String> report(Path profile) throws IOException {
		Map<String, String> m = new LinkedHashMap<>();
		for (String line : Files.readAllLines(profile.resolve("child-report.txt"))) { int i = line.indexOf('='); m.put(line.substring(0, i), line.substring(i + 1)); }
		return m;
	}

	private static Path runDir(Path profile) throws IOException {
		try (Stream<Path> s = Files.list(profile.resolve(".runelite/raccoon-r02-playtest"))) {
			Optional<Path> last = s.sorted().reduce((a, b) -> b);
			return last.orElseThrow(IOException::new);
		}
	}

	private static void launchHappyPath() throws Exception {
		PlaytestLauncher.Settings s = settings();
		int rc = new PlaytestLauncher(s).launch();
		Path profile = s.profile;
		T.that("launch: child ran and exited 0", rc == 0 && Files.exists(profile.resolve("child-report.txt")));
		Map<String, String> r = report(profile);
		T.that("isolation: child user.home is the profile", profile.toString().equals(r.get("user.home")) && profile.toString().equals(r.get("raccoon.r02.profile")));
		T.that("isolation: child runs in the profile folder", profile.toString().equals(r.get("cwd")));
		T.that("isolation: marker written", PlaytestLauncher.isOurs(profile));
		T.that("child: package, run, commit and hashes passed", pkg.toString().equals(r.get("raccoon.r02.package")) && r.get("raccoon.r02.run").startsWith("run-")
			&& commit.equals(r.get("raccoon.r02.commit")) && r.get("raccoon.r02.host.sha256").equals(PlaytestLauncher.sha256(hostJar))
			&& r.get("raccoon.r02.lock.sha256").equals(PlaytestLauncher.sha256(lock)));
		T.that("child: no Jagex session variable or injected option arrived", !r.get("env").contains("JX_") && !r.get("env").toLowerCase().contains("jx_")
			&& !r.get("env").contains("JAVA_TOOL_OPTIONS") && !r.get("env").contains("_JAVA_OPTIONS") && !r.get("env").contains("CLASSPATH") && !r.get("envvalues").contains(SECRET));
		T.that("child: JVM options are exactly the launcher's", !r.get("jvm").contains(SECRET) && r.get("jvm").contains("-ea") && r.get("jvm").contains("-XX:+DisableAttachMechanism"));
		T.that("child: assertions on (developer mode needs -ea)", "true".equals(r.get("assertions")));
		T.that("isolation: temporary files inside the profile", profile.resolve("tmp").toString().equals(r.get("tmpdir")) && profile.resolve("tmp").toString().equals(r.get("TEMP")));
		T.that("isolation: Java preferences are files in the profile", "com.feralenterprises.raccoon.playtest.isolation.FilePreferencesFactory".equals(r.get("prefsFactory"))
			&& Files.isRegularFile(profile.resolve("java-prefs/user/nraccoon-test/na_0020b_005fc/prefs.properties")));
		T.that("child: arguments exactly developer mode and telemetry off", "[--developer-mode, --disable-telemetry]".equals(r.get("args")));
		Path run = runDir(profile);
		String rec = new String(Files.readAllBytes(run.resolve("launcher.txt")), StandardCharsets.UTF_8);
		T.that("record: launcher.txt lists checks, removed names and exit", rec.contains("PASS  JDK") && rec.contains("JX_SESSION_ID") && rec.contains("client exit code 0")
			&& rec.contains("no sign-in files left") && rec.contains("PASS  protected locations outside the profile unchanged"));
		T.that("record: no secret value and no home path in the run folder", !treeContains(run, SECRET) && !treeContains(run, home.toString() + java.io.File.separator + "."));
		T.that("launch: instance lock released", lockFree(profile));
		PlaytestLauncher.Settings s2 = settings();
		s2.environment.put("RACCOON_FAKE_MODE", "exit7");
		T.that("launch: child exit code returned", new PlaytestLauncher(s2).launch() == 7);
		PlaytestLauncher.Settings s3 = settings();
		s3.scale = "1.5";
		new PlaytestLauncher(s3).launch();
		Map<String, String> r3 = report(profile);
		T.that("scale: L-scale properties passed", r3.get("jvm").contains("-Dsun.java2d.dpiaware=true") && r3.get("jvm").contains("-Dsun.java2d.uiScale=1.5"));
	}

	private static boolean treeContains(Path dir, String needle) throws IOException {
		try (Stream<Path> s = Files.walk(dir)) {
			for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator)
				if (new String(Files.readAllBytes(p), StandardCharsets.UTF_8).contains(needle)) { System.out.println("  found in " + p); return true; }
		}
		return false;
	}

	private static boolean lockFree(Path profile) throws IOException {
		try (java.nio.channels.FileChannel c = java.nio.channels.FileChannel.open(new PlaytestLauncher(settings()).lockPath(), java.nio.file.StandardOpenOption.WRITE)) {
			java.nio.channels.FileLock l = c.tryLock();
			if (l == null) return false;
			l.release();
			return true;
		}
	}

	private static void teardownOnInterrupt() throws Exception {
		PlaytestLauncher.Settings s = settings();
		s.environment.put("RACCOON_FAKE_MODE", "spawn");
		Files.deleteIfExists(s.profile.resolve("pids.txt"));
		PlaytestLauncher l = new PlaytestLauncher(s);
		int[] rc = {-1};
		Thread t = new Thread(() -> { try { rc[0] = l.launch(); } catch (Exception e) { e.printStackTrace(); } });
		t.start();
		Path pids = s.profile.resolve("pids.txt");
		long end = System.currentTimeMillis() + 30000;
		while (!Files.exists(pids) && System.currentTimeMillis() < end) Thread.sleep(50);
		Thread.sleep(200);
		String[] p = new String(Files.readAllBytes(pids), StandardCharsets.UTF_8).trim().split(" ");
		ProcessHandle child = ProcessHandle.of(Long.parseLong(p[0])).orElse(null), grand = ProcessHandle.of(Long.parseLong(p[1])).orElse(null);
		T.that("teardown: client and its own child running", child != null && child.isAlive() && grand != null && grand.isAlive());
		l.terminate();
		t.join(30000);
		T.that("teardown: launcher stop ends the client and every process it started", child != null && !child.isAlive() && grand != null && !grand.isAlive());
		T.that("teardown: launch returns and releases the lock", !t.isAlive() && rc[0] != 0 && lockFree(s.profile));
		l.terminate();
		T.that("teardown: idempotent", true);
	}

	private static void signInLeftBehind() throws Exception {
		PlaytestLauncher.Settings s = settings();
		s.environment.put("RACCOON_FAKE_MODE", "signin");
		new PlaytestLauncher(s).launch();
		T.that("post-run: a sign-in made during the playtest is reported", out().contains("WARNING: the profile now holds sign-in files"));
		String rec = new String(Files.readAllBytes(runDir(s.profile).resolve("launcher.txt")), StandardCharsets.UTF_8);
		T.that("post-run: recorded as a warning", rec.contains("WARN  sign-in files now in the profile"));
		PlaytestLauncher next = new PlaytestLauncher(settings());
		T.that("post-run: next launch refused until wiped", next.launch() == 1 && out().contains("saved sign-in files"));
		Files.delete(s.profile.resolve(".runelite/jagex-accounts.dat"));
	}

	private static void singleInstance() throws Exception {
		PlaytestLauncher.Settings s = settings();
		Path flag = work.resolve("locked.flag");
		Files.deleteIfExists(flag);
		Path holderJar = hostJar;
		Process holder = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(), "-cp", holderJar.toString(), "test.LockHolder",
			new PlaytestLauncher(s).lockPath().toString(), flag.toString()).start();
		long end = System.currentTimeMillis() + 20000;
		while (!Files.exists(flag) && System.currentTimeMillis() < end) Thread.sleep(50);
		Files.deleteIfExists(s.profile.resolve("child-report.txt"));
		int rc = new PlaytestLauncher(s).launch();
		T.that("instance: second playtest in the same profile refused", rc == 1 && out().contains("already running") && !Files.exists(s.profile.resolve("child-report.txt")));
		T.that("instance: wipe refused while a playtest runs", !new PlaytestLauncher(settings()).wipe() && out().contains("is running"));
		holder.destroyForcibly().waitFor();
	}

	private interface Mutation { void apply(PlaytestLauncher.Settings s) throws Exception; }

	private static void refused(String name, String expect, Mutation m, Runnable undo) throws Exception {
		PlaytestLauncher.Settings s = settings();
		m.apply(s);
		Files.deleteIfExists(s.profile.resolve("child-report.txt"));
		PlaytestLauncher l = new PlaytestLauncher(s);
		int rc = l.launch();
		boolean ok = rc == 1 && out().contains("RESULT: REFUSED") && out().contains(expect) && l.child() == null && !Files.exists(s.profile.resolve("child-report.txt"));
		if (!ok) System.out.println(out());
		T.that("refuse: " + name, ok);
		if (undo != null) undo.run();
	}

	private static Runnable io(Mutation m) { return () -> { try { m.apply(null); } catch (Exception e) { throw new RuntimeException(e); } }; }

	private static void refusals() throws Exception {
		Path junk = repo.resolve("untracked.java");
		refused("untracked file in the checkout", "uncommitted or untracked", s -> Files.write(junk, new byte[] {1}), io(x -> Files.delete(junk)));
		refused("HEAD not on the GitHub remote", "is not on a fetched feralenterprises GitHub remote", s -> {
			Files.write(repo.resolve("README.md"), "y\n".getBytes(StandardCharsets.UTF_8));
			git(repo, "commit", "-q", "-am", "local only");
			writeClasspath(git(repo, "rev-parse", "HEAD"), "1.13.8", null);
		}, io(x -> { git(repo, "reset", "-q", "--hard", commit); writeClasspath(commit, "1.13.8", null); }));
		refused("remote owned by someone else", "is not on a fetched feralenterprises GitHub remote",
			s -> git(repo, "remote", "set-url", "origin", "https://github.com/someone-else/Project-Raccoon.git"),
			io(x -> git(repo, "remote", "set-url", "origin", "https://github.com/feralenterprises/Project-Raccoon.git")));
		refused("host JAR built from another commit", "host JAR was built from", s -> writeClasspath("0000000000000000000000000000000000000000", "1.13.8", null),
			io(x -> writeClasspath(commit, "1.13.8", null)));
		byte[] dep = Files.readAllBytes(depJar);
		refused("a classpath JAR changed after resolution", "changed since it was resolved", s -> Files.write(depJar, "tampered".getBytes(StandardCharsets.UTF_8)),
			io(x -> Files.write(depJar, dep)));
		refused("a JAR whose resolved hash differs from the lock", "different hash than the lock", s -> {
			Files.write(depJar, "other build".getBytes(StandardCharsets.UTF_8));
			writeClasspath(commit, "1.13.8", null);
		}, io(x -> { Files.write(depJar, dep); writeClasspath(commit, "1.13.8", null); }));
		Path extra = libs.resolve("extra.jar");
		jar(extra, Map.of("x", new byte[] {1}));
		refused("a JAR not in the lock", "classpath differs from the lock", s -> writeClasspath(commit, "1.13.8", PlaytestLauncher.sha256(extra) + " evil:extra:1 " + extra),
			io(x -> writeClasspath(commit, "1.13.8", null)));
		byte[] client = Files.readAllBytes(clientJar);
		refused("client JAR is not 1.13.8 inside", "runelite.version=1.13.9", s -> {
			jar(clientJar, Map.of("net/runelite/client/runelite.properties", "runelite.version=1.13.9\n".getBytes(StandardCharsets.UTF_8)));
			String lockText = new String(Files.readAllBytes(lock), StandardCharsets.UTF_8);
			Files.write(lock, lockText.replaceAll("[0-9a-f]{64} net.runelite:client", PlaytestLauncher.sha256(clientJar) + " net.runelite:client").getBytes(StandardCharsets.UTF_8));
			writeClasspath(commit, "1.13.8", null);
		}, null);
		Files.write(clientJar, client);
		git(repo, "checkout", "-q", "--", "playtest/runelite-1.13.8.lock");
		writeClasspath(commit, "1.13.8", null);
		byte[] sums = Files.readAllBytes(pkg.resolve("SHA256SUMS.txt"));
		refused("another frozen package", "not the accepted R02 package", s -> Files.write(pkg.resolve("SHA256SUMS.txt"), "00  x\n".getBytes(StandardCharsets.UTF_8)),
			io(x -> Files.write(pkg.resolve("SHA256SUMS.txt"), sums)));
		Path inRepo = repo.resolve("build/pkg/Project-Raccoon-R02");
		Files.createDirectories(inRepo);
		Files.write(inRepo.resolve("SHA256SUMS.txt"), sums);
		refused("frozen package inside the repository", "inside the repository", s -> s.packageDir = inRepo, null);
		refused("no package given", "no --package given", s -> s.packageDir = null, null);
		refused("profile is the real home", "is not an isolated folder", s -> s.profile = home, null);
		refused("profile inside the real .runelite", "is not an isolated folder", s -> s.profile = home.resolve(".runelite/profile"), null);
		refused("profile inside the repository", "is not an isolated folder", s -> s.profile = repo.resolve("build/profile"), null);
		Path foreign = Files.createDirectories(home.resolve("Documents"));
		refused("a folder the launcher did not make", "was not made by this launcher", s -> s.profile = foreign, null);
		for (String f : PlaytestLauncher.SIGN_IN_FILES) {
			Path prof = home.resolve(".raccoon-r02-playtest/profile");
			refused("saved sign-in " + f, "saved sign-in files", s -> { Files.createDirectories(prof.resolve(f).getParent()); Files.write(prof.resolve(f), new byte[] {1}); },
				io(x -> Files.delete(prof.resolve(f))));
		}
		refused("unknown scaling mode", "--scale must be", s -> s.scale = "huge", null);
		refused("not the dedicated test account", "not the dedicated test account", s -> s.account = () -> new String[] {"owner", "no"}, null);
		refused("an administrator account", "is an administrator", s -> s.account = () -> new String[] {"RaccoonTest1", "yes"}, null);
		refused("account facts unreadable", "could not read the operating-system account", s -> s.account = () -> new String[] {"RaccoonTest1", null}, null);
		refused("the account has its own RuneLite folder", "has its own RuneLite folder", s -> Files.createDirectories(home.resolve(".runelite")),
			io(x -> Files.delete(home.resolve(".runelite"))));
		refused("RuneLite running", "close RuneLite and the Jagex Launcher", s -> s.processes = () -> Arrays.asList("java", "RuneLite.exe"), null);
		refused("Jagex Launcher running", "close RuneLite and the Jagex Launcher", s -> s.processes = () -> Collections.singletonList("JagexLauncher.exe"), null);
		refused("preferences-isolation JAR changed", "preferences-isolation JAR changed", s -> s.isolationSha = "00" + s.isolationSha.substring(2), null);
		refused("launcher not run from its JAR", "run the launcher from its JAR", s -> s.isolationSha = null, null);
		Path otherInputs = work.resolve("outside/lane-inputs.zip");
		Files.write(otherInputs, "not the lane inputs".getBytes(StandardCharsets.UTF_8));
		refused("lane inputs other than the accepted ZIP", "lane inputs ZIP is not the accepted one", s -> s.laneInputs = otherInputs, null);
		Path inRepoInputs = repo.resolve("build/lane-inputs.zip");
		Files.write(inRepoInputs, "x".getBytes(StandardCharsets.UTF_8));
		refused("lane inputs inside the repository", "inside the repository", s -> s.laneInputs = inRepoInputs, null);
		Path local = Files.createDirectories(work.resolve("localappdata"));
		refused("Windows: RuneLite installed in the account", "RuneLite is installed in this account", s -> {
			s.windows = true; s.environment.put("LOCALAPPDATA", local.toString()); Files.createDirectories(local.resolve("RuneLite")); },
			io(x -> Files.delete(local.resolve("RuneLite"))));
		refused("Windows: protected locations unreadable", "protected locations could not be read", s -> {
			s.windows = true; s.environment.put("LOCALAPPDATA", local.toString()); s.environment.put("APPDATA", local.toString()); s.command = c -> null; }, null);
		refused("wrong JDK home (no java executable)", "no java executable", s -> s.javaHome = work.resolve("no-jdk"), null);
		Path noGit = work.resolve("not-a-repo");
		Files.createDirectories(noGit);
		refused("not a git checkout", "git unavailable or not a repository", s -> s.repo = noGit, null);
		T.that("refuse: after all refusals a launch is READY again", new PlaytestLauncher(settings()).check(true));
	}

	/** The file-backed preferences store, directly (the child test above proves the client JVM uses it). */
	private static void preferences() throws Exception {
		Path dir = work.resolve("prefs-unit");
		System.setProperty("raccoon.r02.prefsDir", dir.toString());
		java.util.prefs.PreferencesFactory f = (java.util.prefs.PreferencesFactory) Class.forName(PlaytestLauncher.ISOLATION_MAIN_FACTORY).getDeclaredConstructor().newInstance();
		java.util.prefs.Preferences root = f.userRoot();
		java.util.prefs.Preferences n = root.node("com/formdev/flatlaf").node("odd:name \u00e9");
		n.put("theme", "dark");
		n.putInt("size", 3);
		n.flush();
		T.that("prefs: values stored under the given folder", Files.isRegularFile(dir.resolve("user/ncom/nformdev/nflatlaf/nodd_003aname_0020_00e9/prefs.properties")));
		java.util.prefs.PreferencesFactory g = (java.util.prefs.PreferencesFactory) Class.forName(PlaytestLauncher.ISOLATION_MAIN_FACTORY).getDeclaredConstructor().newInstance();
		T.that("prefs: values read back, names decoded", "dark".equals(g.userRoot().node("com/formdev/flatlaf/odd:name \u00e9").get("theme", null))
			&& Arrays.asList(g.userRoot().node("com/formdev/flatlaf").childrenNames()).contains("odd:name \u00e9"));
		n.removeNode();
		root.flush();
		T.that("prefs: removed node deleted", !Files.exists(dir.resolve("user/ncom/nformdev/nflatlaf/nodd_003aname_0020_00e9/prefs.properties")));
		T.that("prefs: system root also file-backed", f.systemRoot() != null && !f.systemRoot().equals(root));
		System.clearProperty("raccoon.r02.prefsDir");
		java.lang.reflect.Field u = Class.forName(PlaytestLauncher.ISOLATION_MAIN_FACTORY).getDeclaredField("user");
		u.setAccessible(true);
		u.set(null, null);
		T.throwsOn("prefs: refuses without the profile folder (never the shared store)", f::userRoot, "refusing to use the shared preference store");
	}

	/** A run that changes something outside the profile latches the launcher; a residual sign-in browser is flagged. */
	private static void isolationLatch() throws Exception {
		PlaytestLauncher.Settings w = settings();
		w.environment.put("RACCOON_FAKE_MODE", "webview");
		new PlaytestLauncher(w).launch();
		T.that("post-run: Jagex sign-in browser data in the profile temp is flagged", out().contains("tmp/runelite_jagexauth123.WebView2"));
		T.that("post-run: next launch refused until wiped", new PlaytestLauncher(settings()).launch() == 1 && out().contains("saved sign-in files"));
		T.that("wipe: removes the sign-in residue", new PlaytestLauncher(settings()).wipe());
		PlaytestLauncher.Settings t = settings();
		t.environment.put("RACCOON_FAKE_MODE", "touchhome");
		t.environment.put("RACCOON_FAKE_REALHOME", home.toString());
		new PlaytestLauncher(t).launch();
		Path latch = home.resolve(".raccoon-r02-playtest/" + PlaytestLauncher.LATCH);
		T.that("latch: a change outside the profile stops the launcher", Files.exists(latch) && out().contains("STOPPED")
			&& new String(Files.readAllBytes(latch), StandardCharsets.UTF_8).contains("home/jagexcache"));
		String rec = new String(Files.readAllBytes(runDir(t.profile).resolve("launcher.txt")), StandardCharsets.UTF_8);
		T.that("latch: recorded as a failure in the run", rec.contains("FAIL  protected locations changed"));
		PlaytestLauncher next = new PlaytestLauncher(settings());
		T.that("latch: every later launch refused", next.launch() == 1 && out().contains("changed a protected location") && next.child() == null);
		T.that("latch: wipe does not clear it", new PlaytestLauncher(settings()).wipe() && Files.exists(latch));
		Files.delete(latch);
		deleteTree(home.resolve("jagexcache"));
		T.that("latch: READY again once the reviewer removes it", new PlaytestLauncher(settings()).check(true));
	}

	private static void wipe() throws Exception {
		PlaytestLauncher.Settings s = settings();
		Path foreign = home.resolve("Documents");
		s.profile = foreign;
		T.that("wipe: refuses a folder without the marker", !new PlaytestLauncher(s).wipe() && Files.exists(foreign));
		PlaytestLauncher.Settings h = settings();
		h.profile = home;
		T.that("wipe: refuses the real home", !new PlaytestLauncher(h).wipe() && Files.exists(home));
		PlaytestLauncher.Settings ok = settings();
		Files.createDirectories(ok.profile);
		if (!PlaytestLauncher.isOurs(ok.profile)) Files.write(ok.profile.resolve(PlaytestLauncher.MARKER), PlaytestLauncher.MARKER_CONTENT.getBytes(StandardCharsets.UTF_8));
		Path outside = Files.createDirectories(work.resolve("outside-keep"));
		Files.write(outside.resolve("keep.txt"), new byte[] {1});
		Files.createDirectories(ok.profile.resolve("tmp"));
		Files.createSymbolicLink(ok.profile.resolve("tmp/redirect"), outside);
		T.that("wipe: refuses a profile containing a link and deletes nothing", !new PlaytestLauncher(ok).wipe() && out().contains("links, junctions")
			&& Files.exists(outside.resolve("keep.txt")) && PlaytestLauncher.isOurs(ok.profile));
		Files.delete(ok.profile.resolve("tmp/redirect"));
		T.that("wipe: deletes the launcher's profile", new PlaytestLauncher(ok).wipe() && !Files.exists(ok.profile) && Files.exists(outside.resolve("keep.txt")));
		T.that("wipe: nothing to wipe is fine", new PlaytestLauncher(settings()).wipe());
	}

	private static void deleteTree(Path p) throws IOException {
		if (!Files.exists(p)) return;
		try (Stream<Path> s = Files.walk(p)) {
			for (Path x : (Iterable<Path>) s.sorted((a, b) -> b.compareTo(a))::iterator) Files.delete(x);
		}
	}
}
