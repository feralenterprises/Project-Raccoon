package com.feralenterprises.raccoon.playtest.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * R02 native playtest launcher (DEV ONLY). Starts the RuneLite 1.13.8 client in developer mode with the R02 playtest
 * host, and nothing else, in an isolated profile. Every launch first proves: the pinned JDK; a clean checkout whose
 * HEAD is on a GitHub remote of feralenterprises; the host JAR built from that HEAD; every classpath JAR matching the
 * lock by SHA-256; the RuneLite client really being 1.13.8; the frozen package being the accepted one; and a profile
 * that is ours and holds no saved sign-in. The child gets a sanitised environment (no Jagex launcher session
 * variables, no injected JVM options) and is torn down with its whole process tree when the launcher stops.
 *
 * <pre>
 * check       verify everything, change nothing, start nothing
 * launch      verify, then start the client (needs separate owner clearance)
 * record-lock write the lock from the resolved classpath (first time only; marked unreviewed)
 * wipe        delete the isolated profile (only a folder carrying the launcher's marker)
 * </pre>
 */
public final class PlaytestLauncher {
	public static final String RUNELITE_VERSION = "1.13.8";
	public static final String JDK_VERSION = "11.0.33";
	public static final String JDK_VENDOR = "Eclipse Adoptium";
	/** Same digest as {@code r02.host.PackagePin.SUMS_SHA256} (a test asserts they agree). */
	public static final String PACKAGE_SUMS_SHA256 = "4266612cc8d66c71369397032d010f88fdb419bb0fe64fe87da75632265722c4";
	/** Same digest as {@code r02.host.LaneDiscovery.LANE_INPUTS_SHA256} (a test asserts they agree). */
	public static final String LANE_INPUTS_SHA256 = "1f6a821d37705b7c7acdca9d598c2c5e7f74f7462536c9bf0f9039086545cece";
	public static final String MAIN_CLASS = "com.feralenterprises.raccoon.playtest.r02.runelite.R02PlaytestMain";
	public static final String MARKER = "RACCOON-R02-PLAYTEST-PROFILE";
	public static final String MARKER_CONTENT = "raccoon-r02-playtest-profile v1\n";
	public static final Pattern GITHUB_REMOTE = Pattern.compile("^(https://github\\.com/|git@github\\.com:|ssh://git@github\\.com/)feralenterprises/[A-Za-z0-9._-]+?(\\.git)?/?$");
	/** Windows account name prefix of the dedicated standard test account (same rule as the FR-006 kit). */
	public static final String TEST_ACCOUNT_PREFIX = "RaccoonTest";
	/** BUILTIN\Administrators; listed by whoami /groups also as deny-only for a non-elevated administrator. */
	static final String ADMINISTRATORS_SID = "S-1-5-32-544";
	public static final String LATCH = "PLAYTEST-STOPPED.txt";
	public static final String ISOLATION_MAIN_FACTORY = "com.feralenterprises.raccoon.playtest.isolation.FilePreferencesFactory";
	/** Files that mean a real account session could be reused. Their presence blocks a launch. */
	public static final List<String> SIGN_IN_FILES = Collections.unmodifiableList(Arrays.asList(
		".runelite/credentials.properties", ".runelite/jagex-accounts.dat", ".runelite/session"));
	/** Child environment variables removed (names only are reported). */
	static final List<String> STRIPPED_EXACT = Collections.unmodifiableList(Arrays.asList(
		"JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH", "JAVA_OPTIONS"));
	static final String STRIPPED_PREFIX = "JX_";
	/** Fixed client arguments. Never --insecure-write-credentials, --sessionfile, --profile, --jav_config. */
	public static final List<String> CLIENT_ARGS = Collections.unmodifiableList(Arrays.asList("--developer-mode", "--disable-telemetry"));

	/** Everything a test may vary. Production values come from {@link #production}. */
	public static final class Settings {
		Path repo;
		Path classpathFile;
		Path lockFile;
		Path profile;
		Path realHome;
		Path packageDir;
		Path laneInputs;
		String scale = "java";
		Map<String, String> environment;
		Pattern allowedRemote = GITHUB_REMOTE;
		String git = "git";
		Path javaHome = Paths.get(System.getProperty("java.home"));
		PrintStream out = System.out;
		/** The preferences-isolation JAR and its expected SHA-256 (production: from this launcher JAR's manifest). */
		Path isolationJar;
		String isolationSha;
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
		/** Runs an operating-system command and returns its output, or null when it fails (tests replace it). */
		java.util.function.Function<List<String>, String> command = PlaytestLauncher::run;
		/** Names of running processes (tests replace it). */
		java.util.function.Supplier<List<String>> processes = PlaytestLauncher::processNames;
		/** The OS account: name, and whether it is an administrator (null = could not be read). Tests replace it. */
		java.util.function.Supplier<String[]> account;

		public static Settings production(Path repo, Map<String, String> env) {
			Settings s = new Settings();
			s.repo = repo;
			s.classpathFile = repo.resolve("playtest/host-plugin/build/playtest/classpath.txt");
			s.lockFile = repo.resolve("playtest/runelite-" + RUNELITE_VERSION + ".lock");
			s.realHome = Paths.get(System.getProperty("user.home"));
			s.profile = s.realHome.resolve(".raccoon-r02-playtest").resolve("profile");
			s.environment = env;
			s.isolationJar = repo.resolve("build/libs/raccoon-r02-isolation.jar");
			s.isolationSha = ownManifest("Isolation-SHA-256");
			s.account = () -> accountFacts(s);
			return s;
		}
	}

	/** One resolved classpath entry. */
	static final class Entry {
		final String sha256, coordinate;
		final Path path;
		Entry(String sha256, String coordinate, Path path) { this.sha256 = sha256; this.coordinate = coordinate; this.path = path; }
	}

	private final Settings s;
	private final List<String> report = new ArrayList<>();
	private boolean failed;
	private String commit, lockDigest, hostSha, lockReviewed;
	private Entry host;
	private final List<Entry> jars = new ArrayList<>();
	private volatile Process child;
	private FileChannel lockChannel;
	private FileLock instanceLock;

	public PlaytestLauncher(Settings settings) { this.s = Objects.requireNonNull(settings); }

	public static void main(String[] argv) throws Exception {
		List<String> args = new ArrayList<>(Arrays.asList(argv));
		if (args.isEmpty() || args.contains("--help")) { usage(); System.exit(args.isEmpty() ? 2 : 0); }
		String cmd = args.remove(0);
		Path repo = Paths.get(take(args, "--repo", ".")).toAbsolutePath().normalize();
		Settings st = Settings.production(repo, System.getenv());
		String pkg = take(args, "--package", null);
		if (pkg != null) st.packageDir = Paths.get(pkg).toAbsolutePath().normalize();
		String li = take(args, "--lane-inputs", null);
		if (li != null) st.laneInputs = Paths.get(li).toAbsolutePath().normalize();
		String prof = take(args, "--profile", null);
		if (prof != null) st.profile = Paths.get(prof).toAbsolutePath().normalize();
		st.scale = take(args, "--scale", "java");
		// The package folder may also be given on its own (what the wrapper scripts pass through).
		if (st.packageDir == null && args.size() == 1 && !args.get(0).startsWith("--")) st.packageDir = Paths.get(args.remove(0)).toAbsolutePath().normalize();
		if (!args.isEmpty()) { System.err.println("Unknown arguments: " + args); usage(); System.exit(2); }
		PlaytestLauncher l = new PlaytestLauncher(st);
		int rc;
		switch (cmd) {
			case "check": rc = l.check(true) ? 0 : 1; break;
			case "launch": rc = l.launch(); break;
			case "record-lock": rc = l.recordLock() ? 0 : 1; break;
			case "wipe": rc = l.wipe() ? 0 : 1; break;
			default: usage(); rc = 2;
		}
		System.exit(rc);
	}

	private static void usage() {
		System.err.println("usage: PlaytestLauncher check|launch|record-lock|wipe [--repo DIR] [--package DIR] [--lane-inputs ZIP] [--profile DIR]"
			+ " [--scale java|launcher-default|<factor>]");
	}

	private static String take(List<String> args, String name, String def) {
		int i = args.indexOf(name);
		if (i < 0) return def;
		if (i + 1 >= args.size()) throw new IllegalArgumentException(name + " needs a value");
		String v = args.get(i + 1);
		args.remove(i + 1);
		args.remove(i);
		return v;
	}

	public List<String> report() { return Collections.unmodifiableList(report); }
	public boolean failed() { return failed; }

	// ---------------------------------------------------------------- verification

	/** Runs every check. With {@code packageRequired}, a missing --package fails. Changes nothing on disk. */
	public boolean check(boolean packageRequired) {
		report.clear();
		failed = false;
		jars.clear();
		host = null;
		checkLatch();
		checkJdk();
		checkAccount();
		checkProcesses();
		checkRepo();
		checkClasspath();
		checkIsolationJar();
		checkPackage(packageRequired);
		checkLaneInputs();
		checkProfile();
		checkScale();
		for (String r : report) s.out.println(r);
		s.out.println(failed ? "RESULT: REFUSED (nothing was started)" : "RESULT: READY");
		return !failed;
	}

	private void pass(String what) { report.add("PASS  " + what); }
	private void fail(String what) { report.add("FAIL  " + what); failed = true; }
	private void info(String what) { report.add("INFO  " + what); }

	private void checkJdk() {
		String v = System.getProperty("java.version"), vendor = System.getProperty("java.vendor");
		if (JDK_VERSION.equals(v) && JDK_VENDOR.equals(vendor)) pass("JDK " + vendor + " " + v);
		else fail("JDK is " + vendor + " " + v + "; the playtest needs Temurin (" + JDK_VENDOR + ") " + JDK_VERSION);
		if (!Files.isExecutable(javaExe())) fail("no java executable at " + home(javaExe()));
	}

	private void checkRepo() {
		try {
			// git searches parent folders: make sure the repository is this checkout, not one that happens to contain it.
			Path top = Paths.get(git("rev-parse", "--show-toplevel").trim()).toRealPath();
			if (!top.equals(s.repo.toRealPath())) { fail("git unavailable or not a repository: " + home(s.repo) + " is not a checkout root"); return; }
			String status = git("status", "--porcelain");
			if (!status.isEmpty()) fail("checkout has uncommitted or untracked changes; commit or remove them first");
			else pass("checkout clean");
			commit = git("rev-parse", "HEAD").trim();
			String remotes = git("branch", "-r", "--contains", "HEAD");
			List<String> onGithub = new ArrayList<>();
			for (String line : remotes.split("\n")) {
				String ref = line.trim();
				if (ref.isEmpty() || ref.contains(" -> ")) continue;
				String remote = ref.substring(0, ref.indexOf('/') < 0 ? ref.length() : ref.indexOf('/'));
				String url = git("config", "--get", "remote." + remote + ".url").trim();
				if (s.allowedRemote.matcher(url).matches()) onGithub.add(ref);
			}
			if (onGithub.isEmpty()) fail("HEAD " + shortSha(commit) + " is not on a fetched feralenterprises GitHub remote branch (push it, then git fetch)");
			else pass("HEAD " + shortSha(commit) + " is on " + String.join(", ", onGithub));
		} catch (IOException e) {
			fail("git unavailable or not a repository: " + e.getMessage());
		}
	}

	private void checkClasspath() {
		Path cp = s.classpathFile;
		if (!Files.isRegularFile(cp)) { fail("no resolved classpath (" + home(cp) + "); run the prepare step"); return; }
		String builtFrom = null;
		try {
			for (String line : Files.readAllLines(cp, StandardCharsets.UTF_8)) {
				if (line.startsWith("# commit ")) builtFrom = line.substring(9).trim();
				if (line.startsWith("#") || line.trim().isEmpty()) continue;
				String[] p = line.split(" ", 3);
				if (p.length != 3) { fail("malformed classpath line: " + line); return; }
				Entry e = new Entry(p[0], p[1], Paths.get(p[2]));
				if ("host".equals(e.coordinate)) host = e; else jars.add(e);
			}
		} catch (IOException e) {
			fail("classpath unreadable: " + e.getMessage());
			return;
		}
		if (host == null) { fail("classpath has no host JAR"); return; }
		if (commit != null && !commit.equals(builtFrom)) fail("host JAR was built from " + shortSha(builtFrom) + ", not HEAD " + shortSha(commit) + "; run the prepare step again");
		else if (commit != null) pass("host JAR built from HEAD");
		hostSha = verifyFile(host, "host JAR");
		Map<String, String> lock = readLock();
		if (lock == null) return;
		boolean ok = true;
		Map<String, String> resolved = new TreeMap<>();
		for (Entry e : jars) resolved.put(e.coordinate, e.sha256);
		if (!resolved.keySet().equals(lock.keySet())) {
			List<String> extra = new ArrayList<>(resolved.keySet()); extra.removeAll(lock.keySet());
			List<String> missing = new ArrayList<>(lock.keySet()); missing.removeAll(resolved.keySet());
			fail("classpath differs from the lock: extra " + extra + ", missing " + missing);
			ok = false;
		}
		for (Entry e : jars) {
			String locked = lock.get(e.coordinate);
			if (locked != null && !locked.equals(e.sha256)) { fail(e.coordinate + " resolved with a different hash than the lock"); ok = false; }
			String actual = verifyFile(e, e.coordinate);
			if (actual == null) ok = false;
		}
		if (ok) pass(jars.size() + " RuneLite classpath JARs match the lock (SHA-256 recomputed now)");
		checkClientVersion();
	}

	/** Returns the recomputed hash, or null (and a FAIL) when the file is missing or differs. */
	private String verifyFile(Entry e, String label) {
		try {
			if (!Files.isRegularFile(e.path)) { fail(label + " missing: " + home(e.path)); return null; }
			String actual = sha256(e.path);
			if (!actual.equals(e.sha256)) { fail(label + " changed since it was resolved (" + home(e.path) + ")"); return null; }
			return actual;
		} catch (IOException x) {
			fail(label + " unreadable: " + x.getMessage());
			return null;
		}
	}

	private Map<String, String> readLock() {
		Path f = s.lockFile;
		if (!Files.isRegularFile(f)) { fail("no classpath lock (" + home(f) + "); run record-lock once, then have it independently compared"); return null; }
		Map<String, String> m = new TreeMap<>();
		try {
			byte[] bytes = Files.readAllBytes(f);
			lockDigest = sha256(bytes);
			String version = null;
			for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
				line = line.trim();
				if (line.startsWith("# reviewed:")) lockReviewed = line.substring(11).trim();
				if (line.startsWith("runelite.version=")) version = line.substring(17);
				if (line.startsWith("#") || line.isEmpty() || line.startsWith("runelite.version=")) continue;
				String[] p = line.split(" ");
				if (p.length < 2 || !p[0].matches("[0-9a-f]{64}")) { fail("malformed lock line: " + line); return null; }
				if (m.put(p[1], p[0]) != null) { fail("duplicate lock entry " + p[1]); return null; }
			}
			if (!RUNELITE_VERSION.equals(version)) { fail("lock is for RuneLite " + version + ", not " + RUNELITE_VERSION); return null; }
		} catch (IOException e) {
			fail("lock unreadable: " + e.getMessage());
			return null;
		}
		if ("yes".equals(lockReviewed)) pass("lock " + shortSha(lockDigest) + " independently reviewed");
		else fail("lock " + shortSha(lockDigest) + " is not marked independently reviewed; a launch needs the reviewed lock committed by Feral");
		return m;
	}

	private void checkClientVersion() {
		Entry client = null;
		for (Entry e : jars) if (e.coordinate.equals("net.runelite:client:" + RUNELITE_VERSION)) client = e;
		if (client == null) { fail("classpath has no net.runelite:client:" + RUNELITE_VERSION); return; }
		try (ZipFile z = new ZipFile(client.path.toFile())) {
			ZipEntry p = z.getEntry("net/runelite/client/runelite.properties");
			Properties props = new Properties();
			if (p != null) try (InputStream in = z.getInputStream(p)) { props.load(in); }
			String v = props.getProperty("runelite.version");
			if (RUNELITE_VERSION.equals(v)) pass("client JAR reports runelite.version=" + v);
			else fail("client JAR reports runelite.version=" + v + ", expected " + RUNELITE_VERSION);
		} catch (IOException e) {
			fail("client JAR unreadable: " + e.getMessage());
		}
	}

	private void checkPackage(boolean required) {
		if (s.packageDir == null) { if (required) fail("no --package given (the extracted Project-Raccoon-R02 folder)"); return; }
		Path root = s.packageDir;
		if (!Files.isRegularFile(root.resolve("SHA256SUMS.txt")) && Files.isDirectory(root.resolve("Project-Raccoon-R02"))) root = root.resolve("Project-Raccoon-R02");
		Path sums = root.resolve("SHA256SUMS.txt");
		try {
			if (!Files.isRegularFile(sums)) { fail("not an extracted R02 package: " + home(s.packageDir)); return; }
			String d = sha256(sums);
			if (PACKAGE_SUMS_SHA256.equals(d)) { pass("frozen package is the accepted R02 package (" + shortSha(d) + ")"); s.packageDir = root; }
			else fail("frozen package is not the accepted R02 package (SHA256SUMS.txt " + shortSha(d) + ")");
			if (root.toAbsolutePath().normalize().startsWith(s.repo)) fail("frozen package is inside the repository; keep it outside (it may hold personal data)");
		} catch (IOException e) {
			fail("package unreadable: " + e.getMessage());
		}
	}

	private void checkProfile() {
		Path p = s.profile.toAbsolutePath().normalize();
		Path real = s.realHome.toAbsolutePath().normalize();
		if (p.equals(real) || p.startsWith(real.resolve(".runelite")) || p.startsWith(s.repo) || real.startsWith(p)) {
			fail("profile " + home(p) + " is not an isolated folder (must not be your home, your real .runelite, the repository or a parent of them)");
			return;
		}
		if (Files.exists(p)) {
			if (!isOurs(p)) { fail("profile folder exists but was not made by this launcher: " + home(p)); return; }
			List<String> found = signInFiles(p);
			if (!found.isEmpty()) fail("profile holds saved sign-in files " + found + "; run wipe before the next playtest");
			else pass("isolated profile " + home(p) + " (no saved sign-in)");
		} else {
			pass("isolated profile " + home(p) + " (new)");
		}
	}

	private void checkScale() {
		if (scaleProperties() == null) fail("--scale must be java, launcher-default or a factor such as 1.5");
		else info("scaling mode " + s.scale + " " + scaleProperties());
	}

	/** JVM properties for the scaling mode (NATIVE-VERIFICATION-PLAN-R2 L-modes), or null when invalid. */
	List<String> scaleProperties() {
		if ("java".equals(s.scale)) return Collections.emptyList();
		if ("launcher-default".equals(s.scale)) return Collections.singletonList("-Dsun.java2d.dpiaware=false");
		if (s.scale.matches("[1-4](\\.[0-9]{1,2})?")) return Arrays.asList("-Dsun.java2d.dpiaware=true", "-Dsun.java2d.uiScale=" + s.scale);
		return null;
	}

	/** A run that changed a protected location latches the launcher until Solis has reviewed the run record. */
	Path latchFile() { return s.realHome.resolve(".raccoon-r02-playtest").resolve(LATCH); }

	private void checkLatch() {
		if (Files.exists(latchFile()))
			fail("a previous playtest changed a protected location (" + home(latchFile()) + "); send its run record to Solis. Only the reviewer removes this file");
	}

	/**
	 * The dedicated standard test account (FR-006 kit rule, applied to every launch here): the account name starts with
	 * RaccoonTest, it is not an administrator, it has no RuneLite folder of its own and (Windows) no installed RuneLite.
	 * The Windows account boundary keeps Credential Manager, AppData and HKCU away from the owner's own RuneLite.
	 */
	private void checkAccount() {
		String[] f = s.account == null ? null : s.account.get();
		if (f == null || f[0] == null || f[1] == null) { fail("could not read the operating-system account; nothing is started"); return; }
		boolean ok = true;
		if (!f[0].toLowerCase(java.util.Locale.ROOT).startsWith(TEST_ACCOUNT_PREFIX.toLowerCase(java.util.Locale.ROOT))) {
			fail("this is not the dedicated test account: sign in to Windows as a standard account named " + TEST_ACCOUNT_PREFIX + "...");
			ok = false;
		}
		if ("yes".equals(f[1])) { fail("this account is an administrator; the playtest needs a standard account"); ok = false; }
		if (Files.exists(s.realHome.resolve(".runelite"))) { fail("this account has its own RuneLite folder (" + home(s.realHome.resolve(".runelite")) + "); use a fresh test account"); ok = false; }
		String local = s.environment.get("LOCALAPPDATA");
		if (s.windows && (local == null || Files.exists(Paths.get(local, "RuneLite")))) { fail("RuneLite is installed in this account (or LOCALAPPDATA is unknown)"); ok = false; }
		if (ok) pass("dedicated standard test account " + f[0]);
	}

	static String[] accountFacts(Settings st) {
		if (st.windows) {
			String name = st.environment.get("USERNAME");
			String groups = st.command.apply(Arrays.asList("whoami.exe", "/groups"));
			return new String[] {name, groups == null ? null : groups.contains(ADMINISTRATORS_SID) ? "yes" : "no"};
		}
		String id = st.command.apply(Arrays.asList("id", "-u"));
		return new String[] {System.getProperty("user.name"), id == null ? null : "0".equals(id.trim()) ? "yes" : "no"};
	}

	private void checkProcesses() {
		List<String> running = new ArrayList<>();
		for (String n : s.processes.get()) {
			String b = n.toLowerCase(java.util.Locale.ROOT);
			if (b.equals("runelite") || b.equals("runelite.exe") || b.equals("jagexlauncher") || b.equals("jagexlauncher.exe")) running.add(n);
		}
		if (running.isEmpty()) pass("RuneLite and the Jagex Launcher are not running");
		else fail("close RuneLite and the Jagex Launcher first (running: " + running + ")");
	}

	static List<String> processNames() {
		List<String> out = new ArrayList<>();
		ProcessHandle.allProcesses().forEach(h -> h.info().command().ifPresent(c -> out.add(Paths.get(c).getFileName().toString())));
		return out;
	}

	private void checkIsolationJar() {
		if (s.isolationSha == null) { fail("run the launcher from its JAR (the preferences-isolation JAR identity is in its manifest)"); return; }
		try {
			if (!Files.isRegularFile(s.isolationJar)) { fail("preferences-isolation JAR missing; run the prepare step"); return; }
			if (!s.isolationSha.equals(sha256(s.isolationJar))) fail("preferences-isolation JAR changed since the launcher was built");
			else pass("preferences-isolation JAR " + shortSha(s.isolationSha));
		} catch (IOException e) {
			fail("preferences-isolation JAR unreadable: " + e.getMessage());
		}
	}

	private void checkLaneInputs() {
		if (s.laneInputs == null) { info("no --lane-inputs: lane panels are not loaded (every route shows its placeholder)"); return; }
		try {
			if (!Files.isRegularFile(s.laneInputs)) { fail("lane inputs ZIP not found: " + home(s.laneInputs)); return; }
			if (s.laneInputs.toAbsolutePath().normalize().startsWith(s.repo)) { fail("lane inputs ZIP is inside the repository; keep it outside (private)"); return; }
			String d = sha256(s.laneInputs);
			if (LANE_INPUTS_SHA256.equals(d)) pass("lane inputs ZIP is the accepted one (" + shortSha(d) + ")");
			else fail("lane inputs ZIP is not the accepted one (" + shortSha(d) + ")");
		} catch (IOException e) {
			fail("lane inputs ZIP unreadable: " + e.getMessage());
		}
	}

	/** Runs a command; null on any failure. The first line of the result is "EXIT n". */
	static String run(List<String> cmd) {
		try {
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (!p.waitFor(30, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
			return p.exitValue() == 0 ? out : "EXIT " + p.exitValue() + "\n" + out;
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	static String ownManifest(String attribute) {
		try {
			// Started as "java -jar raccoon-r02-launcher.jar": the class path is exactly that JAR.
			String cp = System.getProperty("java.class.path", "");
			if (cp.isEmpty() || cp.contains(File.pathSeparator) || !cp.endsWith(".jar")) return null;
			Path p = Paths.get(cp);
			if (!Files.isRegularFile(p)) return null;
			try (java.util.jar.JarFile j = new java.util.jar.JarFile(p.toFile())) {
				java.util.jar.Manifest m = j.getManifest();
				return m == null ? null : m.getMainAttributes().getValue(attribute);
			}
		} catch (Exception e) {
			return null;
		}
	}

	// ---------------------------------------------------------------- protected-location snapshot

	/** Protected locations outside the profile, by fingerprint. {@code incomplete} lists what could not be read. */
	static final class Snapshot {
		final Map<String, String> entries = new TreeMap<>();
		final List<String> incomplete = new ArrayList<>();

		List<String> changes(Snapshot after) {
			List<String> out = new ArrayList<>();
			for (String k : entries.keySet()) if (!entries.get(k).equals(after.entries.get(k))) out.add(k);
			for (String k : after.entries.keySet()) if (!entries.containsKey(k)) out.add(k);
			return out;
		}
	}

	private static final int TREE_LIMIT = 200_000;

	/**
	 * What the playtest must never change, as in the FR-006 kit: the account's top-level home entries (except the
	 * launcher's own folder), the real .runelite and jagexcache trees, the classic Jagex files, the shared Java
	 * preferences (Windows registry key, values included; elsewhere ~/.java/.userPrefs), and on Windows the presence of
	 * the RuneLite SessionStore credential (never read) and the top-level LOCALAPPDATA and APPDATA folder names.
	 */
	Snapshot snapshot() {
		Snapshot sn = new Snapshot();
		Path h = s.realHome;
		names(sn, "home", h, ".raccoon-r02-playtest");
		for (String d : new String[] {".runelite", "jagexcache"}) tree(sn, "home/" + d, h.resolve(d), false);
		for (String f : new String[] {"jagex_cl_oldschool_LIVE.dat", "random.dat", ".jagex_cache_32"}) tree(sn, "home/" + f, h.resolve(f), false);
		if (s.windows) {
			String reg = s.command.apply(Arrays.asList("reg.exe", "query", "HKCU\\Software\\JavaSoft\\Prefs", "/s"));
			if (reg == null) sn.incomplete.add("java-preferences");
			else if (reg.startsWith("EXIT ") && reg.toLowerCase(java.util.Locale.ROOT).contains("unable to find")) sn.entries.put("java-preferences", "absent");
			else if (reg.startsWith("EXIT ")) sn.incomplete.add("java-preferences");
			else sn.entries.put("java-preferences", sha256(reg.getBytes(StandardCharsets.UTF_8)));
			String keys = s.command.apply(Arrays.asList("cmdkey.exe", "/list"));
			if (keys == null || keys.startsWith("EXIT ")) sn.incomplete.add("credential-manager");
			else sn.entries.put("credential:RuneLite SessionStore", keys.contains("RuneLite SessionStore") ? "present" : "absent");
			for (String v : new String[] {"LOCALAPPDATA", "APPDATA"}) {
				String d = s.environment.get(v);
				if (d == null) sn.incomplete.add(v); else names(sn, v, Paths.get(d), null);
			}
		} else {
			tree(sn, "java-preferences", h.resolve(".java/.userPrefs"), true);
		}
		return sn;
	}

	private static void names(Snapshot sn, String label, Path dir, String skip) {
		try (java.util.stream.Stream<Path> st = Files.list(dir)) {
			st.forEach(p -> { String n = p.getFileName().toString(); if (!n.equals(skip)) sn.entries.put(label + ":" + n, "present"); });
		} catch (IOException e) {
			sn.incomplete.add(label);
		}
	}

	private static void tree(Snapshot sn, String label, Path root, boolean content) {
		if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) { sn.entries.put(label, "absent"); return; }
		int[] n = {0};
		try {
			Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
				@Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
					sn.entries.put(label + "/" + root.relativize(d).toString().replace('\\', '/'), "dir");
					return ++n[0] > TREE_LIMIT ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
				}
				@Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
					String v = a.size() + ":" + a.lastModifiedTime().toMillis() + (content && a.isRegularFile() ? ":" + sha256(f) : "");
					sn.entries.put(label + "/" + root.relativize(f).toString().replace('\\', '/'), v);
					return ++n[0] > TREE_LIMIT ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
				}
				@Override public FileVisitResult visitFileFailed(Path f, IOException e) {
					sn.incomplete.add(label + "/" + root.relativize(f));
					return FileVisitResult.CONTINUE;
				}
			});
			if (n[0] > TREE_LIMIT) sn.incomplete.add(label + " (too many entries)");
		} catch (IOException e) {
			sn.incomplete.add(label);
		}
	}

	static boolean isOurs(Path profile) {
		try {
			Path m = profile.resolve(MARKER);
			return Files.isRegularFile(m) && MARKER_CONTENT.equals(new String(Files.readAllBytes(m), StandardCharsets.UTF_8));
		} catch (IOException e) {
			return false;
		}
	}

	static List<String> signInFiles(Path profile) {
		List<String> out = new ArrayList<>();
		for (String f : SIGN_IN_FILES) if (Files.exists(profile.resolve(f))) out.add(f);
		// The developer client's Jagex sign-in helper keeps its WebView2 browser data (cookies) in java.io.tmpdir.
		try (java.util.stream.Stream<Path> st = Files.isDirectory(profile.resolve("tmp")) ? Files.list(profile.resolve("tmp")) : java.util.stream.Stream.empty()) {
			st.filter(x -> x.getFileName().toString().startsWith("runelite_jagexauth")).forEach(x -> out.add("tmp/" + x.getFileName()));
		} catch (IOException e) {
			out.add("tmp (unreadable)");
		}
		return out;
	}

	// ---------------------------------------------------------------- launch and teardown

	/** The child's environment: inherited, minus Jagex launcher session variables and injected JVM options. */
	Map<String, String> childEnvironment(List<String> removed) {
		Map<String, String> env = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : s.environment.entrySet()) {
			String k = e.getKey();
			String u = k.toUpperCase(java.util.Locale.ROOT);
			if (u.startsWith(STRIPPED_PREFIX) || STRIPPED_EXACT.contains(u)) { removed.add(k); continue; }
			env.put(k, e.getValue());
		}
		Collections.sort(removed);
		// Native helpers started by the client (the Jagex sign-in WebView2) use the TEMP variables, not java.io.tmpdir.
		for (String t : new String[] {"TEMP", "TMP", "TMPDIR"}) {
			env.keySet().removeIf(k -> k.equalsIgnoreCase(t));
			env.put(t, s.profile.resolve("tmp").toString());
		}
		return env;
	}

	List<String> childCommand(String runId) {
		List<String> c = new ArrayList<>();
		c.add(javaExe().toString());
		c.add("-ea");
		c.add("-XX:+DisableAttachMechanism");
		c.add("-Duser.home=" + s.profile);
		c.add("-Djava.io.tmpdir=" + s.profile.resolve("tmp"));
		c.add("-Djava.util.prefs.PreferencesFactory=" + ISOLATION_MAIN_FACTORY);
		c.add("-Draccoon.r02.prefsDir=" + s.profile.resolve("java-prefs"));
		c.add("-Draccoon.r02.profile=" + s.profile);
		c.add("-Draccoon.r02.package=" + s.packageDir);
		if (s.laneInputs != null) c.add("-Draccoon.r02.laneInputs=" + s.laneInputs);
		c.add("-Draccoon.r02.run=" + runId);
		c.add("-Draccoon.r02.commit=" + commit);
		c.add("-Draccoon.r02.host.sha256=" + hostSha);
		c.add("-Draccoon.r02.lock.sha256=" + lockDigest);
		c.addAll(scaleProperties());
		StringBuilder cp = new StringBuilder(s.isolationJar.toString()).append(File.pathSeparatorChar).append(host.path);
		for (Entry e : jars) cp.append(File.pathSeparatorChar).append(e.path);
		c.add("-cp");
		c.add(cp.toString());
		c.add(MAIN_CLASS);
		c.addAll(CLIENT_ARGS);
		return c;
	}

	/** Verifies, prepares the profile, starts the client and waits. Returns the process exit code (1 = refused). */
	public int launch() throws IOException, InterruptedException {
		if (!check(true)) return 1;
		Path profile = s.profile;
		Files.createDirectories(profile);
		if (!Files.exists(profile.resolve(MARKER))) Files.write(profile.resolve(MARKER), MARKER_CONTENT.getBytes(StandardCharsets.UTF_8));
		if (!isOurs(profile)) { s.out.println("RESULT: REFUSED (profile marker)"); return 1; }
		Files.createDirectories(profile.resolve("tmp"));
		Files.createDirectories(profile.resolve("java-prefs"));
		lockChannel = FileChannel.open(profile.resolve(".launcher.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		instanceLock = lockChannel.tryLock();
		if (instanceLock == null) { lockChannel.close(); s.out.println("RESULT: REFUSED (another playtest is already running in this profile)"); return 1; }
		Snapshot before = snapshot();
		if (!before.incomplete.isEmpty()) {
			releaseLock();
			s.out.println("RESULT: REFUSED (protected locations could not be read: " + before.incomplete + ")");
			return 1;
		}
		String runId = "run-" + ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path runDir = profile.resolve(".runelite").resolve("raccoon-r02-playtest").resolve(runId);
		Files.createDirectories(runDir);
		List<String> removed = new ArrayList<>();
		ProcessBuilder pb = new ProcessBuilder(childCommand(runId));
		pb.environment().clear();
		pb.environment().putAll(childEnvironment(removed));
		pb.directory(profile.toFile());
		pb.redirectErrorStream(true);
		pb.redirectOutput(runDir.resolve("client-console.log").toFile());
		List<String> rec = new ArrayList<>(report);
		rec.add("INFO  run " + runId);
		rec.add("INFO  environment removed from the client: " + (removed.isEmpty() ? "none" : String.join(", ", removed)));
		rec.add("INFO  client arguments " + CLIENT_ARGS);
		rec.add("INFO  lock reviewed: " + ("yes".equals(lockReviewed) ? "yes" : "no"));
		writeRecord(runDir, rec, null);
		Thread hook = new Thread(this::terminate, "raccoon-r02-teardown");
		Runtime.getRuntime().addShutdownHook(hook);
		s.out.println("Starting RuneLite " + RUNELITE_VERSION + " with the R02 playtest host (" + runId + "). Close the client window to finish.");
		int rc;
		try {
			child = pb.start();
			rc = child.waitFor();
		} finally {
			terminate();
			try { Runtime.getRuntime().removeShutdownHook(hook); } catch (IllegalStateException ignored) { }
			releaseLock();
		}
		Snapshot afterSnap = snapshot();
		List<String> changed = before.changes(afterSnap);
		List<String> after = signInFiles(profile);
		rec.add("INFO  client exit code " + rc);
		if (changed.isEmpty() && afterSnap.incomplete.isEmpty()) rec.add("PASS  protected locations outside the profile unchanged (" + before.entries.size() + " entries)");
		else {
			rec.add("FAIL  protected locations changed " + changed + (afterSnap.incomplete.isEmpty() ? "" : " or unreadable " + afterSnap.incomplete) + ": launcher latched");
			Files.createDirectories(latchFile().getParent());
			Files.write(latchFile(), ("Playtest " + runId + " changed or could not re-read protected locations outside its profile.\n"
				+ "Changed: " + changed + "\nUnreadable: " + afterSnap.incomplete + "\nSend " + home(runDir) + " to Solis. Only the reviewer removes this file.\n")
				.getBytes(StandardCharsets.UTF_8));
		}
		if (after.isEmpty()) rec.add("PASS  no sign-in files left in the profile");
		else rec.add("WARN  sign-in files now in the profile " + after + ": run wipe before sharing or reusing it");
		writeRecord(runDir, rec, after);
		s.out.println("Finished (exit " + rc + "). Run record: " + home(runDir));
		if (!after.isEmpty()) s.out.println("WARNING: the profile now holds sign-in files " + after + ". Run the wipe step.");
		if (Files.exists(latchFile())) s.out.println("STOPPED: something outside the playtest profile changed. Send the run record to Solis; no further launches.");
		return rc;
	}

	/** Stops the client and every process it started. Idempotent; used by the shutdown hook too. */
	public void terminate() {
		Process p = child;
		if (p == null) return;
		List<ProcessHandle> tree = new ArrayList<>();
		p.toHandle().descendants().forEach(tree::add);
		for (ProcessHandle h : tree) h.destroy();
		p.destroy();
		try {
			if (!p.waitFor(10, TimeUnit.SECONDS)) p.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			p.destroyForcibly();
		}
		for (ProcessHandle h : tree) if (h.isAlive()) h.destroyForcibly();
	}

	/** For tests: the running child, or null. */
	Process child() { return child; }

	private void releaseLock() {
		try {
			if (instanceLock != null) instanceLock.release();
			if (lockChannel != null) lockChannel.close();
		} catch (IOException ignored) {
			// the lock dies with the process anyway
		}
		instanceLock = null;
		lockChannel = null;
	}

	private void writeRecord(Path runDir, List<String> lines, List<String> after) throws IOException {
		StringBuilder b = new StringBuilder("R02 playtest launcher record\n");
		for (String l : lines) b.append(l).append('\n');
		Files.write(runDir.resolve("launcher.txt"), b.toString().getBytes(StandardCharsets.UTF_8));
	}

	// ---------------------------------------------------------------- lock and wipe

	/** Writes the lock from the resolved classpath, marked unreviewed. Refuses to overwrite an existing lock. */
	public boolean recordLock() throws IOException {
		if (Files.exists(s.lockFile)) { s.out.println("REFUSED: a lock already exists (" + home(s.lockFile) + "); it is never overwritten"); return false; }
		report.clear();
		failed = false;
		jars.clear();
		if (!Files.isRegularFile(s.classpathFile)) { s.out.println("REFUSED: no resolved classpath; run the prepare step"); return false; }
		for (String line : Files.readAllLines(s.classpathFile, StandardCharsets.UTF_8)) {
			if (line.startsWith("#") || line.trim().isEmpty()) continue;
			String[] p = line.split(" ", 3);
			if (p.length == 3 && !"host".equals(p[1])) {
				Entry e = new Entry(p[0], p[1], Paths.get(p[2]));
				if (verifyFile(e, e.coordinate) == null) { for (String r : report) s.out.println(r); s.out.println("REFUSED"); return false; }
				jars.add(e);
			}
		}
		jars.sort((a, b) -> a.coordinate.compareTo(b.coordinate));
		StringBuilder b = new StringBuilder("# Raccoon R02 playtest classpath lock v1 (RuneLite client runtime classpath, SHA-256)\n");
		b.append("# reviewed: no\n");
		b.append("# Set 'reviewed: yes' only after an independent second resolution produced identical lines.\n");
		b.append("runelite.version=").append(RUNELITE_VERSION).append('\n');
		for (Entry e : jars) b.append(e.sha256).append(' ').append(e.coordinate).append(' ').append(e.path.getFileName()).append('\n');
		Files.createDirectories(s.lockFile.toAbsolutePath().getParent());
		Files.write(s.lockFile, b.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
		s.out.println("Recorded " + jars.size() + " JARs in " + home(s.lockFile) + " (lock " + shortSha(sha256(s.lockFile)) + ", unreviewed)");
		return true;
	}

	/** Deletes the isolated profile, only if it carries the launcher's marker and is not in use. */
	public boolean wipe() throws IOException {
		Path p = s.profile.toAbsolutePath().normalize();
		if (!Files.exists(p)) { s.out.println("Nothing to wipe: " + home(p)); return true; }
		Path real = s.realHome.toAbsolutePath().normalize();
		if (p.equals(real) || p.startsWith(real.resolve(".runelite")) || real.startsWith(p) || p.startsWith(s.repo) || !isOurs(p)) {
			s.out.println("REFUSED: " + home(p) + " is not a profile made by this launcher; nothing deleted");
			return false;
		}
		try (FileChannel ch = FileChannel.open(p.resolve(".launcher.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
			FileLock l = ch.tryLock();
			if (l == null) { s.out.println("REFUSED: a playtest is running in this profile"); return false; }
			l.release();
		}
		List<String> unsafe = unsafeEntries(p);
		if (!unsafe.isEmpty()) {
			s.out.println("REFUSED: the profile contains links, junctions or other redirections " + unsafe + "; nothing deleted. Remove them by hand or ask Solis.");
			return false;
		}
		Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
			@Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException { Files.delete(f); return FileVisitResult.CONTINUE; }
			@Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
				if (e != null) throw e;
				Files.delete(d);
				return FileVisitResult.CONTINUE;
			}
		});
		s.out.println("Wiped " + home(p) + " (Windows Credential Manager entries are never touched; the latch, if any, stays)");
		return true;
	}

	/**
	 * Every entry that is not a plain file or folder at the path it appears to be: symbolic links, Windows junctions and
	 * other reparse points (their real path differs), or anything unreadable. A wipe deletes nothing if any is found,
	 * so it can never follow a redirection out of the profile.
	 */
	static List<String> unsafeEntries(Path profile) {
		List<String> out = new ArrayList<>();
		try {
			Path real = profile.toRealPath();
			Files.walkFileTree(profile, new SimpleFileVisitor<Path>() {
				private void judge(Path x, BasicFileAttributes a) {
					String rel = profile.relativize(x).toString();
					try {
						BasicFileAttributes own = Files.readAttributes(x, BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
						if (own.isSymbolicLink() || own.isOther()) { out.add(rel); return; }
						if (!x.equals(profile) && !x.toRealPath().equals(real.resolve(rel))) out.add(rel);
					} catch (IOException e) {
						out.add(rel + " (unreadable)");
					}
				}
				@Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
					int before = out.size();
					judge(d, a);
					return out.size() > before ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
				}
				@Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) { judge(f, a); return FileVisitResult.CONTINUE; }
				@Override public FileVisitResult visitFileFailed(Path f, IOException e) { out.add(profile.relativize(f) + " (unreadable)"); return FileVisitResult.CONTINUE; }
			});
		} catch (IOException e) {
			out.add("profile (unreadable)");
		}
		return out;
	}

	// ---------------------------------------------------------------- helpers

	Path javaExe() {
		Path bin = s.javaHome.resolve("bin");
		Path win = bin.resolve("java.exe");
		return Files.exists(win) ? win : bin.resolve("java");
	}

	private String git(String... args) throws IOException {
		List<String> c = new ArrayList<>();
		c.add(s.git);
		c.add("-C");
		c.add(s.repo.toString());
		c.addAll(Arrays.asList(args));
		ProcessBuilder pb = new ProcessBuilder(c).redirectErrorStream(true);
		pb.environment().remove("GIT_DIR");
		pb.environment().remove("GIT_WORK_TREE");
		Process p = pb.start();
		StringBuilder out = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) out.append(line).append('\n');
		}
		try {
			int rc = p.waitFor();
			if (rc != 0 && !(args.length > 0 && args[0].equals("config"))) throw new IOException("git " + String.join(" ", args) + " failed: " + out.toString().trim());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted");
		}
		return out.toString();
	}

	/** A path for display with the home folder shown as ~ (records stay free of the user name). */
	String home(Path p) {
		if (p == null) return "-";
		Path real = s.realHome.toAbsolutePath().normalize();
		Path a = p.toAbsolutePath().normalize();
		return a.startsWith(real) ? "~" + File.separator + real.relativize(a) : a.toString();
	}

	static String shortSha(String sha) { return sha == null ? "none" : sha.substring(0, Math.min(12, sha.length())); }

	public static String sha256(Path f) throws IOException {
		MessageDigest d = digest();
		try (InputStream in = Files.newInputStream(f)) {
			byte[] buf = new byte[1 << 16];
			int n;
			while ((n = in.read(buf)) > 0) d.update(buf, 0, n);
		}
		return hex(d.digest());
	}

	public static String sha256(byte[] b) { return hex(digest().digest(b)); }

	private static MessageDigest digest() {
		try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}

	private static String hex(byte[] b) {
		StringBuilder s = new StringBuilder();
		for (byte x : b) s.append(String.format("%02x", x));
		return s.toString();
	}
}
