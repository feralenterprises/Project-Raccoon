/*
 * Project Raccoon foundation verifier (FR-000).
 *
 * Offline, dependency-free, Java 11. Run as a single source file:
 *   java tools/verify/FoundationVerifier.java source   <repoRoot>
 *   java tools/verify/FoundationVerifier.java jdk      <repoRoot>
 *   java tools/verify/FoundationVerifier.java tests    <junitXmlDir>
 *   java tools/verify/FoundationVerifier.java artifact <repoRoot> <jar>
 *   java tools/verify/FoundationVerifier.java packaged <repoRoot> <jar> <packagerLog> <commit>
 *   java tools/verify/FoundationVerifier.java selftest <repoRoot>
 *
 * Local results here are not the official Plugin Hub packager gate (FR-001).
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class FoundationVerifier
{
	// ---------------------------------------------------------------- rules

	static final String OWN = "com/feralenterprises/raccoon/";
	static final String CORE = OWN + "core/";
	/** Entry the official packager injects into the post-processed JAR. */
	static final String PACKAGER_STUB = "runelite_plugin.json";

	/** External classes the FR-000 artifact may reference. Widening this is a reviewed change. */
	static final List<String> ALLOWED_EXTERNAL_PREFIXES = Arrays.asList(
		"java/lang/",
		"java/util/",
		"java/time/",
		"javax/inject/",
		"com/google/inject/",
		"org/slf4j/"
	);
	static final Set<String> ALLOWED_EXTERNAL_EXACT = new LinkedHashSet<>(Arrays.asList(
		"net/runelite/client/plugins/Plugin",
		"net/runelite/client/plugins/PluginDescriptor",
		// FR-002 account lifecycle adapter: client phase, account hash, world type, ticks
		"net/runelite/api/Client",
		"com/jagex/oldscape/pub/OAuthApi", // declares Client.getAccountHash()
		"net/runelite/api/GameState",
		"net/runelite/api/WorldType",
		"net/runelite/api/events/GameStateChanged",
		"net/runelite/api/events/GameTick",
		"net/runelite/api/events/ClientTick",
		"net/runelite/client/eventbus/Subscribe",
		// FR-003 read-only bank observation: containers, item definitions, bank/PIN widget visibility
		"net/runelite/api/ItemContainer",
		"net/runelite/api/Item",
		"net/runelite/api/ItemComposition",
		"net/runelite/api/widgets/Widget",
		// constant holders: values are inlined, but javac records the class names
		"net/runelite/api/gameval/InventoryID",
		"net/runelite/api/gameval/VarbitID",
		"net/runelite/api/gameval/ItemID",
		"net/runelite/api/gameval/InterfaceID",
		"net/runelite/api/gameval/InterfaceID$Bankmain",
		"net/runelite/api/gameval/InterfaceID$BankpinKeypad"
	));
	/** Core (pure domain) may reference only these, plus itself. */
	static final List<String> CORE_ALLOWED_PREFIXES = Arrays.asList("java/lang/", "java/util/", "java/time/");

	/** Denied even when an allowed prefix matches; ordered prefix -> category. */
	static final Map<String, String> DENIED_PREFIXES = new LinkedHashMap<>();
	static
	{
		DENIED_PREFIXES.put("java/lang/reflect/", "reflection");
		DENIED_PREFIXES.put("java/lang/ClassLoader", "dynamic class loading");
		DENIED_PREFIXES.put("java/lang/ProcessBuilder", "external process");
		DENIED_PREFIXES.put("java/lang/ProcessHandle", "external process");
		DENIED_PREFIXES.put("java/lang/Process", "external process");
		DENIED_PREFIXES.put("java/lang/Runtime", "runtime/process control");
		DENIED_PREFIXES.put("java/lang/instrument/", "instrumentation");
		DENIED_PREFIXES.put("java/net/", "networking");
		DENIED_PREFIXES.put("okhttp3/", "networking");
		DENIED_PREFIXES.put("javax/net/", "networking");
		DENIED_PREFIXES.put("java/io/", "file/serialization I/O");
		DENIED_PREFIXES.put("java/nio/", "file/persistence I/O");
		DENIED_PREFIXES.put("java/sql/", "persistence");
		DENIED_PREFIXES.put("javax/swing/", "UI");
		DENIED_PREFIXES.put("java/awt/", "UI");
		DENIED_PREFIXES.put("net/runelite/client/ui/", "UI");
		DENIED_PREFIXES.put("net/runelite/client/config/", "persistent config storage");
		DENIED_PREFIXES.put("net/runelite/api/", "game API outside the reviewed allowlist");
		DENIED_PREFIXES.put("sun/", "internal JDK API");
		DENIED_PREFIXES.put("jdk/internal/", "internal JDK API");
		DENIED_PREFIXES.put("com/sun/", "internal JDK API / JNA");
	}
	static final Set<String> ALLOWED_INVOKE = new LinkedHashSet<>(Arrays.asList(
		"java/lang/invoke/StringConcatFactory",
		"java/lang/invoke/LambdaMetafactory",
		"java/lang/invoke/MethodHandles$Lookup",
		"java/lang/invoke/MethodHandles",
		"java/lang/invoke/MethodHandle",
		"java/lang/invoke/MethodType",
		"java/lang/invoke/CallSite"
	));
	static final Map<String, Set<String>> DENIED_METHODS = new LinkedHashMap<>();
	static
	{
		DENIED_METHODS.put("java/lang/Class", new LinkedHashSet<>(Arrays.asList(
			"forName", "newInstance", "getMethod", "getMethods", "getDeclaredMethod", "getDeclaredMethods",
			"getField", "getFields", "getDeclaredField", "getDeclaredFields",
			"getConstructor", "getConstructors", "getDeclaredConstructor", "getDeclaredConstructors")));
		DENIED_METHODS.put("java/lang/System", new LinkedHashSet<>(Arrays.asList("load", "loadLibrary", "exit")));
		DENIED_METHODS.put("java/lang/Thread", new LinkedHashSet<>(Collections.singletonList("sleep")));
	}

	static final Pattern ASSET_EXT = Pattern.compile("(?i).*\\.(png|jpe?g|gif|bmp|ico|webp|svg|wav|ogg|mp3|flac|ttf|otf|woff2?|jar|zip|class|so|dll|dylib|jnilib)$");
	static final List<Pattern> SECRET_PATTERNS = Arrays.asList(
		Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
		Pattern.compile("ghp_[A-Za-z0-9]{36}"),
		Pattern.compile("github_pat_[A-Za-z0-9_]{40,}"),
		Pattern.compile("AKIA[0-9A-Z]{16}"),
		Pattern.compile("xox[abprs]-[A-Za-z0-9-]{10,}"));
	static final List<String> REQUIRED_RECORDS = Arrays.asList(
		"docs/architecture.md", "docs/feature-parity.md", "docs/migration-provenance.md",
		"docs/release-evidence.md", "docs/governance.md", "docs/engineering-requirements.md", "docs/account-lifecycle.md", "docs/ui-integration.md", "docs/bank-observation.md", "docs/kill-count.md", "release/pins.properties", "LICENSE");

	// ---------------------------------------------------------------- main

	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			usage();
		}
		List<String> v;
		switch (args[0])
		{
			case "source":
				v = checkSource(Paths.get(args[1]));
				break;
			case "jdk":
				v = checkJdk(loadPins(Paths.get(args[1])));
				break;
			case "tests":
				v = checkTests(Paths.get(args[1]));
				break;
			case "artifact":
				if (args.length < 3)
				{
					usage();
				}
				Properties pins = loadPins(Paths.get(args[1]));
				v = checkArtifact(Paths.get(args[2]), Long.parseLong(pins.getProperty("artifact.maxBytes")),
					Integer.parseInt(pins.getProperty("bytecode.maxMajor")), true, false);
				break;
			case "packaged":
				if (args.length < 5)
				{
					usage();
				}
				v = checkPackaged(loadPins(Paths.get(args[1])), Paths.get(args[2]), Paths.get(args[3]), args[4]);
				break;
			case "selftest":
				v = selfTest(Paths.get(args[1]));
				break;
			default:
				usage();
				return;
		}
		finish(args[0], v);
	}

	static void usage()
	{
		System.err.println("usage: FoundationVerifier source|jdk|selftest <repoRoot> | tests <xmlDir> | artifact <repoRoot> <jar> | packaged <repoRoot> <jar> <log> <commit>");
		System.exit(2);
	}

	static void finish(String mode, List<String> violations)
	{
		if (violations.isEmpty())
		{
			System.out.println("[" + mode + "] PASS");
			return;
		}
		for (String s : violations)
		{
			System.out.println("[" + mode + "] FAIL " + s);
			if (System.getenv("GITHUB_ACTIONS") != null)
			{
				System.out.println("::error title=" + mode + "::" + s);
			}
		}
		System.out.println("[" + mode + "] FAIL (" + violations.size() + " violation(s))");
		System.exit(1);
	}

	static boolean quiet;

	static void evidence(String key, String value)
	{
		if (quiet)
		{
			return;
		}
		System.out.println("EVIDENCE " + key + "=" + value);
		if (System.getenv("GITHUB_ACTIONS") != null)
		{
			System.out.println("::notice title=evidence " + key + "::" + value);
		}
		String file = System.getenv("FOUNDATION_EVIDENCE");
		if (file != null)
		{
			try (Writer w = Files.newBufferedWriter(Paths.get(file), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND))
			{
				w.write(key + "=" + value + "\n");
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	static Properties loadPins(Path root) throws IOException
	{
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(root.resolve("release/pins.properties")))
		{
			p.load(in);
		}
		return p;
	}

	// ---------------------------------------------------------------- jdk

	static List<String> checkJdk(Properties pins)
	{
		List<String> v = new ArrayList<>();
		String want = pins.getProperty("jdk.version");
		String have = Runtime.version().toString();
		String vendor = System.getProperty("java.vendor", "") + " / " + System.getProperty("java.vm.vendor", "");
		evidence("jdk.runtime", have + " (" + vendor + ")");
		if (!have.startsWith(want))
		{
			v.add("development JDK is " + have + ", pinned " + want);
		}
		return v;
	}

	// ---------------------------------------------------------------- tests

	static List<String> checkTests(Path dir) throws IOException
	{
		List<String> v = new ArrayList<>();
		if (!Files.isDirectory(dir))
		{
			v.add("no JUnit results at " + dir);
			return v;
		}
		int tests = 0, failures = 0, errors = 0, skipped = 0, suites = 0;
		Pattern suite = Pattern.compile("<testsuite\\b([^>]*)>");
		List<Path> xmls;
		try (Stream<Path> s = Files.list(dir))
		{
			xmls = s.filter(p -> p.toString().endsWith(".xml")).sorted().collect(Collectors.toList());
		}
		for (Path x : xmls)
		{
			Matcher m = suite.matcher(new String(Files.readAllBytes(x), StandardCharsets.UTF_8));
			while (m.find())
			{
				suites++;
				tests += attr(m.group(1), "tests");
				failures += attr(m.group(1), "failures");
				errors += attr(m.group(1), "errors");
				skipped += attr(m.group(1), "skipped");
			}
		}
		evidence("tests", "suites=" + suites + " tests=" + tests + " failures=" + failures
			+ " errors=" + errors + " skipped=" + skipped);
		if (tests == 0)
		{
			v.add("zero tests executed");
		}
		if (failures + errors > 0)
		{
			v.add(failures + " failure(s), " + errors + " error(s)");
		}
		return v;
	}

	static int attr(String attrs, String name)
	{
		Matcher m = Pattern.compile("\\b" + name + "=\"(\\d+)\"").matcher(attrs);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	// ---------------------------------------------------------------- source

	static List<String> checkSource(Path root) throws IOException
	{
		List<String> v = new ArrayList<>();
		Properties pins = loadPins(root);
		List<String> files = listFiles(root);

		// one root project, one wrapper
		for (String f : files)
		{
			String name = f.substring(f.lastIndexOf('/') + 1);
			if (name.matches("(settings|build)\\.gradle(\\.kts)?") && f.contains("/"))
			{
				v.add("nested Gradle project file: " + f);
			}
			if (name.matches("gradlew(\\.bat)?") && f.contains("/"))
			{
				v.add("extra Gradle wrapper script: " + f);
			}
			if (name.equals("gradle-wrapper.jar") && !f.equals("gradle/wrapper/gradle-wrapper.jar"))
			{
				v.add("extra Gradle wrapper jar: " + f);
			}
			if (name.endsWith(".gradle.kts"))
			{
				v.add("Kotlin DSL not used by template: " + f);
			}
		}
		for (String req : Arrays.asList("build.gradle", "settings.gradle", "gradlew", "gradlew.bat",
			"gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties", "runelite-plugin.properties"))
		{
			if (!files.contains(req))
			{
				v.add("missing " + req);
			}
		}
		for (String req : REQUIRED_RECORDS)
		{
			if (!files.contains(req))
			{
				v.add("missing record " + req);
			}
		}
		if (!v.isEmpty())
		{
			return v;
		}

		// pin shape
		for (String k : new TreeSet<>(pins.stringPropertyNames()))
		{
			if (k.endsWith(".commit") && !pins.getProperty(k).matches("[0-9a-f]{40}"))
			{
				v.add("pins: " + k + " must be a full 40-hex commit SHA");
			}
			if (k.endsWith("Sha256") && !pins.getProperty(k).matches("[0-9a-f]{64}"))
			{
				v.add("pins: " + k + " must be a 64-hex SHA-256");
			}
		}

		// wrapper pins
		Properties wp = new Properties();
		try (InputStream in = Files.newInputStream(root.resolve("gradle/wrapper/gradle-wrapper.properties")))
		{
			wp.load(in);
		}
		expect(v, "wrapper distributionUrl", pins.getProperty("gradle.distributionUrl"), wp.getProperty("distributionUrl"));
		expect(v, "wrapper distributionSha256Sum", pins.getProperty("gradle.distributionSha256"), wp.getProperty("distributionSha256Sum"));
		expect(v, "gradle-wrapper.jar sha256", pins.getProperty("gradle.wrapperJarSha256"),
			sha256(Files.readAllBytes(root.resolve("gradle/wrapper/gradle-wrapper.jar"))));

		// build script
		String build = read(root, "build.gradle");
		String settings = read(root, "settings.gradle");
		for (String gf : Arrays.asList("build.gradle", "settings.gradle"))
		{
			String text = read(root, gf);
			Matcher dyn = Pattern.compile("latest\\.(release|integration|milestone)|SNAPSHOT|['\"][^'\"\\s]*:[^'\"\\s]*\\+['\"]").matcher(text);
			while (dyn.find())
			{
				v.add(gf + ": floating version '" + dyn.group() + "'");
			}
			Matcher sp = Pattern.compile("(?m)^ +\\S").matcher(text);
			if (sp.find())
			{
				v.add(gf + ": indent with tabs (Plugin Hub convention)");
			}
		}
		if (!build.contains("options.release.set(11)"))
		{
			v.add("build.gradle: options.release.set(11) missing");
		}
		expectDef(v, build, "runeLiteVersion", pins.getProperty("runelite.client.version"));
		expectDef(v, build, "lombokVersion", pins.getProperty("lombok.version"));
		expectDef(v, build, "junitVersion", pins.getProperty("junit.version"));
		Matcher runtimeDep = Pattern.compile("(?m)^\\s*(implementation|api|runtimeOnly|compile|runtime)\\b").matcher(build);
		while (runtimeDep.find())
		{
			v.add("build.gradle: runtime dependency configuration '" + runtimeDep.group(1) + "' (no extra runtime dependencies)");
		}
		if (!settings.trim().equals("rootProject.name = 'project-raccoon'"))
		{
			v.add("settings.gradle: must contain only rootProject.name = 'project-raccoon'");
		}

		// plugin properties
		Properties rp = new Properties();
		try (InputStream in = Files.newInputStream(root.resolve("runelite-plugin.properties")))
		{
			rp.load(in);
		}
		expect(v, "runelite-plugin.properties build", "standard", rp.getProperty("build"));
		String plugins = rp.getProperty("plugins", "");
		if (plugins.isEmpty() || plugins.contains(","))
		{
			v.add("runelite-plugin.properties: exactly one plugin entry required, got '" + plugins + "'");
		}
		else if (!files.contains("src/main/java/" + plugins.replace('.', '/') + ".java"))
		{
			v.add("runelite-plugin.properties: plugin class source not found for " + plugins);
		}
		else if (!plugins.replace('.', '/').startsWith(OWN))
		{
			v.add("runelite-plugin.properties: plugin outside " + OWN);
		}
		for (String[] kv : new String[][]{{"displayName", "Example"}, {"author", "Nobody"}, {"description", "An example greeter plugin"}})
		{
			String val = rp.getProperty(kv[0], "");
			if (val.isEmpty() || val.equals(kv[1]))
			{
				v.add("runelite-plugin.properties: " + kv[0] + " unset or template default");
			}
		}

		// content rules
		for (String f : files)
		{
			if (f.startsWith("src/main/resources/"))
			{
				v.add("unapproved production resource: " + f);
			}
			if (f.contains("META-INF/services/"))
			{
				v.add("service descriptor not allowed: " + f);
			}
			if (f.equals("icon.png"))
			{
				v.add("icon.png is an unapproved asset");
			}
			if (ASSET_EXT.matcher(f).matches() && !f.equals("gradle/wrapper/gradle-wrapper.jar"))
			{
				v.add("binary/asset file not allowed: " + f);
			}
			if (f.toLowerCase().contains("r03") || f.toLowerCase().contains("handoff"))
			{
				v.add("governing handoff material must stay out of public source: " + f);
			}
			if (f.startsWith("src/") && !f.startsWith("src/main/java/" + OWN) && !f.startsWith("src/test/java/" + OWN)
				&& !f.equals("src/test/resources/logback-test.xml"))
			{
				v.add("source outside " + OWN + ": " + f);
			}
		}
		for (String f : files)
		{
			byte[] data = Files.readAllBytes(root.resolve(f));
			if (ASSET_EXT.matcher(f).matches())
			{
				continue;
			}
			String text = new String(data, StandardCharsets.UTF_8);
			if (f.startsWith("src/") && (text.contains("com.example") || text.contains("ExamplePlugin")))
			{
				v.add("template leftover in " + f);
			}
			for (Pattern p : SECRET_PATTERNS)
			{
				if (p.matcher(text).find())
				{
					v.add("possible secret (" + p.pattern() + ") in " + f);
				}
			}
		}

		// CI pins
		boolean sawJava = false;
		for (String f : files)
		{
			if (!f.startsWith(".github/workflows/"))
			{
				continue;
			}
			String wf = read(root, f);
			Matcher uses = Pattern.compile("(?m)^\\s*-?\\s*uses:\\s*(\\S+)").matcher(wf);
			while (uses.find())
			{
				if (!uses.group(1).matches("[\\w.-]+/[\\w./-]+@[0-9a-f]{40}"))
				{
					v.add(f + ": action not pinned to a full commit SHA: " + uses.group(1));
				}
			}
			Matcher jv = Pattern.compile("(?m)^\\s*java-version:\\s*['\"]?([^'\"\\s]+)").matcher(wf);
			while (jv.find())
			{
				sawJava = true;
				expect(v, f + " java-version", pins.getProperty("jdk.version"), jv.group(1));
			}
			Matcher dist = Pattern.compile("(?m)^\\s*distribution:\\s*['\"]?([^'\"\\s]+)").matcher(wf);
			while (dist.find())
			{
				expect(v, f + " distribution", pins.getProperty("jdk.distribution"), dist.group(1));
			}
		}
		if (!sawJava)
		{
			v.add("no CI workflow pins java-version");
		}
		evidence("source.files", String.valueOf(files.size()));
		return v;
	}

	static void expect(List<String> v, String what, String want, String have)
	{
		if (want == null || !want.equals(have))
		{
			v.add(what + ": expected '" + want + "', found '" + have + "'");
		}
	}

	static void expectDef(List<String> v, String build, String name, String want)
	{
		Matcher m = Pattern.compile("def\\s+" + name + "\\s*=\\s*'([^']*)'").matcher(build);
		expect(v, "build.gradle " + name, want, m.find() ? m.group(1) : null);
	}

	static String read(Path root, String rel) throws IOException
	{
		return new String(Files.readAllBytes(root.resolve(rel)), StandardCharsets.UTF_8);
	}

	static List<String> listFiles(Path root) throws IOException
	{
		List<String> out = new ArrayList<>();
		Files.walkFileTree(root, new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
			{
				String n = dir.getFileName() == null ? "" : dir.getFileName().toString();
				if (!dir.equals(root) && (n.equals(".git") || n.equals("build") || n.equals(".gradle") || n.equals(".idea") || n.equals("out")))
				{
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
			{
				out.add(root.relativize(file).toString().replace('\\', '/'));
				return FileVisitResult.CONTINUE;
			}
		});
		Collections.sort(out);
		return out;
	}

	// ---------------------------------------------------------------- artifact

	static List<String> checkPackaged(Properties pins, Path jar, Path log, String commit) throws IOException
	{
		List<String> v = new ArrayList<>();
		if (!commit.matches("[0-9a-f]{40}"))
		{
			v.add("commit must be a full 40-hex SHA: " + commit);
			return v;
		}
		v.addAll(checkArtifact(jar, Long.parseLong(pins.getProperty("artifact.maxBytes")),
			Integer.parseInt(pins.getProperty("bytecode.maxMajor")), true, true));
		if (!Files.isRegularFile(jar) || !Files.isRegularFile(log))
		{
			v.add("packager output missing (jar or log)");
			return v;
		}
		byte[] bytes = Files.readAllBytes(jar);
		String b64;
		try
		{
			b64 = java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		String logText = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
		String expectLine = "built jar with hash " + b64 + " from commit " + commit;
		if (!logText.contains(expectLine))
		{
			v.add("packager log does not record this JAR for commit " + commit + " (expected '" + expectLine + "')");
		}
		if (logText.contains("package failed"))
		{
			v.add("packager log reports 'package failed'");
		}
		String stub = readEntry(bytes, PACKAGER_STUB);
		if (stub != null && !stub.contains("\"" + commit.substring(0, 8) + "\""))
		{
			v.add(PACKAGER_STUB + " does not carry version " + commit.substring(0, 8));
		}
		evidence("packaged.commit", commit);
		evidence("packaged.hashB64", b64);
		return v;
	}

	static String readEntry(byte[] jar, String name) throws IOException
	{
		try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jar)))
		{
			for (ZipEntry e; (e = zin.getNextEntry()) != null; )
			{
				if (e.getName().equals(name))
				{
					return new String(readAll(zin), StandardCharsets.UTF_8);
				}
			}
		}
		return null;
	}

	static List<String> checkArtifact(Path jar, long maxBytes, int maxMajor, boolean report, boolean packaged) throws IOException
	{
		List<String> v = new ArrayList<>();
		if (!Files.isRegularFile(jar))
		{
			v.add("artifact missing: " + jar);
			return v;
		}
		byte[] jarBytes = Files.readAllBytes(jar);
		int entries = 0, classes = 0, highestMajor = 0, stubs = 0;
		Set<String> externalRefs = new TreeSet<>();
		try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes)))
		{
			ZipEntry e;
			while ((e = zin.getNextEntry()) != null)
			{
				entries++;
				String n = e.getName();
				if (e.isDirectory())
				{
					if (!(n.equals("META-INF/") || OWN.startsWith(n) || n.startsWith(OWN)))
					{
						v.add("unexpected directory entry: " + n);
					}
					continue;
				}
				if (n.equals("META-INF/MANIFEST.MF"))
				{
					continue;
				}
				if (packaged && n.equals(PACKAGER_STUB))
				{
					stubs++;
					continue;
				}
				if (n.endsWith("module-info.class"))
				{
					v.add("module-info not allowed: " + n);
					continue;
				}
				if (!n.startsWith(OWN) || !n.endsWith(".class"))
				{
					v.add("forbidden package content (only " + OWN + "**.class allowed): " + n);
					continue;
				}
				classes++;
				byte[] data = readAll(zin);
				ClassInfo ci;
				try
				{
					ci = ClassInfo.parse(data);
				}
				catch (IOException | RuntimeException ex)
				{
					v.add("unparseable class " + n + ": " + ex);
					continue;
				}
				highestMajor = Math.max(highestMajor, ci.major);
				if (ci.major > maxMajor)
				{
					v.add("bytecode major " + ci.major + " > " + maxMajor + " (Java 11): " + n);
				}
				checkReferences(n, ci, v, externalRefs);
			}
		}
		if (jarBytes.length > maxBytes)
		{
			v.add("artifact is " + jarBytes.length + " bytes, limit " + maxBytes);
		}
		if (classes == 0)
		{
			v.add("artifact contains no classes");
		}
		if (packaged && stubs != 1)
		{
			v.add("post-processed JAR must contain exactly one " + PACKAGER_STUB + ", found " + stubs);
		}
		if (report)
		{
			String k = packaged ? "packaged" : "artifact";
			evidence(k + ".path", jar.toString());
			evidence(k + ".bytes", jarBytes.length + " (limit " + maxBytes + ", " + String.format("%.2f", jarBytes.length / 1024.0) + " KiB)");
			evidence(k + ".sha256", sha256(jarBytes));
			evidence(k + ".entries", "entries=" + entries + " classes=" + classes + " highestMajor=" + highestMajor);
			evidence(k + ".externalRefs", String.join(",", externalRefs));
		}
		return v;
	}

	static void checkReferences(String entry, ClassInfo ci, List<String> v, Set<String> externalRefs)
	{
		boolean isCore = entry.startsWith(CORE);
		for (String ref : ci.referencedClasses)
		{
			if (ref.startsWith(OWN))
			{
				if (isCore && !ref.startsWith(CORE))
				{
					v.add("dependency direction: core " + entry + " -> " + ref);
				}
				continue;
			}
			externalRefs.add(ref);
			if (!isCore && ALLOWED_EXTERNAL_EXACT.contains(ref))
			{
				continue;
			}
			String denied = deniedCategory(ref);
			if (denied != null)
			{
				v.add("forbidden " + denied + " reference " + ref + " in " + entry);
				continue;
			}
			if (isCore)
			{
				if (CORE_ALLOWED_PREFIXES.stream().noneMatch(ref::startsWith))
				{
					v.add("dependency direction: core " + entry + " -> external " + ref);
				}
				continue;
			}
			if (!ALLOWED_EXTERNAL_EXACT.contains(ref) && ALLOWED_EXTERNAL_PREFIXES.stream().noneMatch(ref::startsWith))
			{
				v.add("reference outside the allowed surface " + ref + " in " + entry);
			}
		}
		for (String[] m : ci.methodRefs)
		{
			Set<String> names = DENIED_METHODS.get(m[0]);
			if (names != null && names.contains(m[1]))
			{
				v.add("forbidden call " + m[0] + "." + m[1] + " in " + entry);
			}
		}
	}

	static String deniedCategory(String ref)
	{
		if (ref.startsWith("java/lang/invoke/"))
		{
			return ALLOWED_INVOKE.contains(ref) ? null : "method-handle/reflection";
		}
		for (Map.Entry<String, String> d : DENIED_PREFIXES.entrySet())
		{
			if (ref.equals(d.getKey()) || (ref.startsWith(d.getKey()) && (d.getKey().endsWith("/") || ref.charAt(d.getKey().length()) == '$')))
			{
				return d.getValue();
			}
		}
		return null;
	}

	static final class ClassInfo
	{
		int major;
		final Set<String> referencedClasses = new TreeSet<>();
		final List<String[]> methodRefs = new ArrayList<>();

		static ClassInfo parse(byte[] data) throws IOException
		{
			ClassInfo ci = new ClassInfo();
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
			if (in.readInt() != 0xCAFEBABE)
			{
				throw new IOException("bad magic");
			}
			in.readUnsignedShort();
			ci.major = in.readUnsignedShort();
			int count = in.readUnsignedShort();
			int[] tag = new int[count];
			String[] utf = new String[count];
			int[] a = new int[count];
			int[] b = new int[count];
			for (int i = 1; i < count; i++)
			{
				tag[i] = in.readUnsignedByte();
				switch (tag[i])
				{
					case 1: utf[i] = in.readUTF(); break;
					case 3: case 4: in.readInt(); break;
					case 5: case 6: in.readLong(); i++; break;
					case 7: case 8: case 16: case 19: case 20: a[i] = in.readUnsignedShort(); break;
					case 9: case 10: case 11: case 12: case 17: case 18:
						a[i] = in.readUnsignedShort();
						b[i] = in.readUnsignedShort();
						break;
					case 15: in.readUnsignedByte(); a[i] = in.readUnsignedShort(); break;
					default: throw new IOException("unknown constant tag " + tag[i]);
				}
			}
			Pattern desc = Pattern.compile("L([A-Za-z_$][\\w$]*(?:/[\\w$]+)*);");
			for (int i = 1; i < count; i++)
			{
				if (tag[i] == 7)
				{
					String n = utf[a[i]];
					while (n.startsWith("["))
					{
						n = n.substring(1);
					}
					if (n.startsWith("L") && n.endsWith(";"))
					{
						n = n.substring(1, n.length() - 1);
					}
					if (n.length() > 1 || !"BCDFIJSZ".contains(n))
					{
						ci.referencedClasses.add(n);
					}
				}
				else if (tag[i] == 1 && utf[i].indexOf(';') >= 0)
				{
					Matcher m = desc.matcher(utf[i]);
					while (m.find())
					{
						ci.referencedClasses.add(m.group(1));
					}
				}
				else if (tag[i] == 10 || tag[i] == 11)
				{
					String owner = utf[a[a[i]]];
					String name = utf[a[b[i]]];
					ci.methodRefs.add(new String[]{owner, name});
				}
			}
			return ci;
		}
	}

	static byte[] readAll(InputStream in) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int r;
		while ((r = in.read(buf)) > 0)
		{
			out.write(buf, 0, r);
		}
		return out.toByteArray();
	}

	static String sha256(byte[] data)
	{
		try
		{
			StringBuilder sb = new StringBuilder();
			for (byte x : MessageDigest.getInstance("SHA-256").digest(data))
			{
				sb.append(String.format("%02x", x));
			}
			return sb.toString();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	// ---------------------------------------------------------------- self-test

	static int selfCases;
	static List<String> selfFailures;

	static List<String> selfTest(Path root) throws Exception
	{
		selfCases = 0;
		selfFailures = new ArrayList<>();
		Path tmp = Files.createTempDirectory("raccoon-verify-selftest");
		Path src = tmp.resolve("src");
		Path out = tmp.resolve("classes");
		Files.createDirectories(out);

		Map<String, String> fx = new LinkedHashMap<>();
		fx.put("net/runelite/client/plugins/Plugin.java",
			"package net.runelite.client.plugins; public abstract class Plugin { protected void startUp() throws Exception {} protected void shutDown() throws Exception {} }");
		fx.put("net/runelite/client/plugins/PluginDescriptor.java",
			"package net.runelite.client.plugins; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface PluginDescriptor { String name(); String description() default \"\"; }");
		fx.put("net/runelite/client/plugins/xptracker/XpTrackerPlugin.java",
			"package net.runelite.client.plugins.xptracker; public class XpTrackerPlugin { public static int x() { return 1; } }");
		fx.put("com/feralenterprises/raccoon/GoodPlugin.java",
			"package com.feralenterprises.raccoon; import net.runelite.client.plugins.*; import com.feralenterprises.raccoon.core.Pure;"
				+ " @PluginDescriptor(name = \"Fixture\") public class GoodPlugin extends Plugin { protected void startUp() { String s = \"n=\" + Pure.size(); java.util.List<Runnable> l = new java.util.ArrayList<>(); l.add(() -> {}); } }");
		fx.put("com/feralenterprises/raccoon/core/Pure.java",
			"package com.feralenterprises.raccoon.core; public final class Pure { public static int size() { return java.util.Collections.emptyList().size(); } }");
		fx.put("com/feralenterprises/raccoon/core/CoreToHost.java",
			"package com.feralenterprises.raccoon.core; public final class CoreToHost { Object p = new com.feralenterprises.raccoon.GoodPlugin(); }");
		fx.put("com/feralenterprises/raccoon/core/CoreToRuneLite.java",
			"package com.feralenterprises.raccoon.core; public final class CoreToRuneLite { net.runelite.client.plugins.Plugin p; }");
		fx.put("com/feralenterprises/raccoon/bad/Net.java",
			"package com.feralenterprises.raccoon.bad; public class Net { Object s() throws Exception { return new java.net.Socket(); } }");
		fx.put("com/feralenterprises/raccoon/bad/Reflect.java",
			"package com.feralenterprises.raccoon.bad; public class Reflect { Object r() throws Exception { return Class.forName(\"x\"); } }");
		fx.put("com/feralenterprises/raccoon/bad/ReflectApi.java",
			"package com.feralenterprises.raccoon.bad; public class ReflectApi { java.lang.reflect.Method m; }");
		fx.put("com/feralenterprises/raccoon/bad/Proc.java",
			"package com.feralenterprises.raccoon.bad; public class Proc { Object p() { return new ProcessBuilder(\"x\"); } }");
		fx.put("com/feralenterprises/raccoon/bad/Ui.java",
			"package com.feralenterprises.raccoon.bad; public class Ui { Object u() { return new javax.swing.JPanel(); } }");
		fx.put("com/feralenterprises/raccoon/bad/Disk.java",
			"package com.feralenterprises.raccoon.bad; public class Disk { Object f() { return new java.io.File(\"x\"); } }");
		fx.put("com/feralenterprises/raccoon/bad/Sleep.java",
			"package com.feralenterprises.raccoon.bad; public class Sleep { void s() throws Exception { Thread.sleep(1); } }");
		fx.put("com/feralenterprises/raccoon/bad/HostImport.java",
			"package com.feralenterprises.raccoon.bad; public class HostImport { int x() { return net.runelite.client.plugins.xptracker.XpTrackerPlugin.x(); } }");
		fx.put("org/thirdparty/Shaded.java", "package org.thirdparty; public class Shaded {}");
		fx.put("net/runelite/api/Client.java", "package net.runelite.api; public interface Client { long getAccountHash(); }");
		fx.put("net/runelite/api/NPC.java", "package net.runelite.api; public interface NPC { }");
		fx.put("com/feralenterprises/raccoon/account/Adapter.java",
			"package com.feralenterprises.raccoon.account; public class Adapter { long h(net.runelite.api.Client c) { return c.getAccountHash(); } }");
		fx.put("com/feralenterprises/raccoon/bad/Observer.java",
			"package com.feralenterprises.raccoon.bad; public class Observer { net.runelite.api.NPC npc; }");
		fx.put("com/feralenterprises/raccoon/core/CoreToClient.java",
			"package com.feralenterprises.raccoon.core; public final class CoreToClient { net.runelite.api.Client c; }");

		List<String> paths = new ArrayList<>();
		for (Map.Entry<String, String> e : fx.entrySet())
		{
			Path p = src.resolve(e.getKey());
			Files.createDirectories(p.getParent());
			Files.write(p, e.getValue().getBytes(StandardCharsets.UTF_8));
			paths.add(p.toString());
		}
		JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
		if (javac == null)
		{
			return Collections.singletonList("selftest needs a JDK (no system Java compiler)");
		}
		List<String> argv = new ArrayList<>(Arrays.asList("--release", "11", "-d", out.toString()));
		argv.addAll(paths);
		if (javac.run(null, null, null, argv.toArray(new String[0])) != 0)
		{
			return Collections.singletonList("selftest fixture compilation failed");
		}

		String good = "com/feralenterprises/raccoon/GoodPlugin.class";
		String pure = "com/feralenterprises/raccoon/core/Pure.class";
		long max = 262144;

		artifactCase("good artifact passes", out, tmp, max, null, Collections.emptyMap(), good, pure);
		artifactCase("Java 17 bytecode rejected", out, tmp, max, "bytecode major 61",
			Collections.singletonMap(good, patchMajor(Files.readAllBytes(out.resolve(good)), 61)), good, pure);
		artifactCase("oversize artifact rejected", out, tmp, 100, "limit 100", Collections.emptyMap(), good, pure);
		artifactCase("core -> host rejected", out, tmp, max, "dependency direction: core", Collections.emptyMap(), good, pure,
			"com/feralenterprises/raccoon/core/CoreToHost.class");
		artifactCase("core -> RuneLite rejected", out, tmp, max, "dependency direction: core", Collections.emptyMap(), good, pure,
			"com/feralenterprises/raccoon/core/CoreToRuneLite.class");
		artifactCase("networking rejected", out, tmp, max, "forbidden networking", Collections.emptyMap(), good, "com/feralenterprises/raccoon/bad/Net.class");
		artifactCase("Class.forName rejected", out, tmp, max, "forbidden call java/lang/Class.forName", Collections.emptyMap(), good, "com/feralenterprises/raccoon/bad/Reflect.class");
		artifactCase("reflection API rejected", out, tmp, max, "forbidden reflection", Collections.emptyMap(), good, "com/feralenterprises/raccoon/bad/ReflectApi.class");
		artifactCase("process rejected", out, tmp, max, "forbidden external process", Collections.emptyMap(), good, "com/feralenterprises/raccoon/bad/Proc.class");
		artifactCase("UI rejected", out, tmp, max, "forbidden UI", Collections.emptyMap(), good, "com/feralenterprises/raccoon/bad/Ui.class");
		artifactCase("file I/O rejected", out, tmp, max, "forbidden file", Collections.emptyMap(), good, "com/feralenterprises/raccoon/bad/Disk.class");
		artifactCase("Thread.sleep rejected", out, tmp, max, "forbidden call java/lang/Thread.sleep", Collections.emptyMap(), good, "com/feralenterprises/raccoon/bad/Sleep.class");
		artifactCase("broad host import rejected", out, tmp, max, "outside the allowed surface", Collections.emptyMap(), good, "com/feralenterprises/raccoon/bad/HostImport.class");
		artifactCase("lifecycle adapter RuneLite types allowed", out, tmp, max, null, Collections.emptyMap(), good,
			"com/feralenterprises/raccoon/account/Adapter.class");
		artifactCase("unlisted game API (NPC) still rejected", out, tmp, max, "forbidden game API outside the reviewed allowlist", Collections.emptyMap(), good,
			"com/feralenterprises/raccoon/bad/Observer.class");
		artifactCase("core -> allowed RuneLite type still rejected", out, tmp, max, "net/runelite/api/Client in com/feralenterprises/raccoon/core/CoreToClient.class", Collections.emptyMap(), good,
			"com/feralenterprises/raccoon/core/CoreToClient.class");
		artifactCase("shaded third-party class rejected", out, tmp, max, "forbidden package content", Collections.emptyMap(), good, "org/thirdparty/Shaded.class");
		artifactCase("service descriptor rejected", out, tmp, max, "forbidden package content",
			Collections.singletonMap("META-INF/services/net.runelite.client.plugins.Plugin", "x".getBytes(StandardCharsets.UTF_8)), good);
		artifactCase("native library rejected", out, tmp, max, "forbidden package content",
			Collections.singletonMap(OWN + "native.so", new byte[]{0}), good);
		artifactCase("image asset rejected", out, tmp, max, "forbidden package content",
			Collections.singletonMap(OWN + "icon.png", new byte[]{0}), good);

		byte[] stubJson = "{\"version\":\"0123abcd\"}".getBytes(StandardCharsets.UTF_8);
		artifactCase("post-processed JAR with packager stub passes", true, out, tmp, max, null,
			Collections.singletonMap(PACKAGER_STUB, stubJson), good, pure);
		artifactCase("post-processed JAR without stub rejected", true, out, tmp, max, "exactly one " + PACKAGER_STUB,
			Collections.emptyMap(), good, pure);
		artifactCase("development JAR with packager stub rejected", false, out, tmp, max, "forbidden package content",
			Collections.singletonMap(PACKAGER_STUB, stubJson), good, pure);

		packagedLogCase(root, tmp, out, good, pure, stubJson);

		// source-mode mutations on a copy of the real repository
		sourceCase(root, tmp, "repository source passes", null, null, null);
		sourceCase(root, tmp, "floating RuneLite version rejected", "build.gradle", s -> s.replace("'1.13.8'", "'latest.release'"), "floating version");
		sourceCase(root, tmp, "release 11 removed rejected", "build.gradle", s -> s.replace("options.release.set(11)", "options.release.set(17)"), "options.release.set(11) missing");
		sourceCase(root, tmp, "runtime dependency rejected", "build.gradle", s -> s.replace("dependencies {", "dependencies {\n\timplementation 'com.example:x:1'"), "runtime dependency");
		sourceCase(root, tmp, "space indentation rejected", "build.gradle", s -> s.replace("\tid 'java'", "    id 'java'"), "indent with tabs");
		sourceCase(root, tmp, "build=gradle rejected", "runelite-plugin.properties", s -> s.replace("build=standard", "build=gradle"), "build");
		sourceCase(root, tmp, "unpinned action rejected", ".github/workflows/foundation.yml", s -> s.replaceFirst("uses: actions/checkout@[0-9a-f]{40}", "uses: actions/checkout@v4"), "not pinned");
		sourceCase(root, tmp, "short commit pin rejected", "release/pins.properties", s -> s.replaceFirst("template.commit=([0-9a-f]{39})[0-9a-f]", "template.commit=$1"), "full 40-hex commit");
		sourceCase(root, tmp, "nested project rejected", "sub/settings.gradle", s -> "rootProject.name = 'x'\n", "nested Gradle project");
		sourceCase(root, tmp, "service descriptor source rejected", "src/main/resources/META-INF/services/net.runelite.client.plugins.Plugin", s -> "x\n", "service descriptor");
		sourceCase(root, tmp, "unapproved icon rejected", "icon.png", s -> "x", "unapproved asset");
		sourceCase(root, tmp, "private key rejected", "docs/leak.md", s -> "-----BEGIN RSA " + "PRIVATE KEY-----\n", "possible secret");
		sourceCase(root, tmp, "handoff file rejected", "docs/R03-plan.md", s -> "x\n", "handoff");

		evidence("selftest.cases", selfCases + " run, " + (selfCases - selfFailures.size()) + " passed");
		return selfFailures;
	}

	interface Edit
	{
		String apply(String s);
	}

	static void artifactCase(String name, Path classes, Path tmp, long max, String expectFail,
		Map<String, byte[]> extra, String... entries) throws IOException
	{
		artifactCase(name, false, classes, tmp, max, expectFail, extra, entries);
	}

	static void artifactCase(String name, boolean packaged, Path classes, Path tmp, long max, String expectFail,
		Map<String, byte[]> extra, String... entries) throws IOException
	{
		selfCases++;
		Path jar = tmp.resolve("case-" + selfCases + ".jar");
		Manifest mf = new Manifest();
		mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		try (OutputStream os = Files.newOutputStream(jar); JarOutputStream jos = new JarOutputStream(os, mf))
		{
			Set<String> written = new LinkedHashSet<>();
			for (String e : entries)
			{
				byte[] data = extra.containsKey(e) ? extra.get(e) : Files.readAllBytes(classes.resolve(e));
				jos.putNextEntry(new JarEntry(e));
				jos.write(data);
				written.add(e);
			}
			for (Map.Entry<String, byte[]> e : extra.entrySet())
			{
				if (!written.contains(e.getKey()))
				{
					jos.putNextEntry(new JarEntry(e.getKey()));
					jos.write(e.getValue());
				}
			}
		}
		judge(name, checkArtifact(jar, max, 55, false, packaged), expectFail);
	}

	static void sourceCase(Path root, Path tmp, String name, String file, Edit edit, String expectFail) throws IOException
	{
		selfCases++;
		Path copy = tmp.resolve("repo-" + selfCases);
		for (String f : listFiles(root))
		{
			Path dst = copy.resolve(f);
			Files.createDirectories(dst.getParent());
			Files.copy(root.resolve(f), dst);
		}
		if (file != null)
		{
			Path target = copy.resolve(file);
			Files.createDirectories(target.getParent());
			String before = Files.exists(target) ? new String(Files.readAllBytes(target), StandardCharsets.UTF_8) : "";
			String after = edit.apply(before);
			if (after.equals(before))
			{
				selfFailures.add("selftest '" + name + "': mutation did not change " + file);
				return;
			}
			Files.write(target, after.getBytes(StandardCharsets.UTF_8));
		}
		quiet = true;
		try
		{
			judge(name, checkSource(copy), expectFail);
		}
		finally
		{
			quiet = false;
		}
	}

	static void packagedLogCase(Path root, Path tmp, Path classes, String good, String pure, byte[] stubJson) throws IOException
	{
		String commit = "0123abcd" + "0".repeat(32);
		Path jar = tmp.resolve("packaged.jar");
		try (OutputStream os = Files.newOutputStream(jar); JarOutputStream jos = new JarOutputStream(os))
		{
			for (String e : new String[]{good, pure})
			{
				jos.putNextEntry(new JarEntry(e));
				jos.write(Files.readAllBytes(classes.resolve(e)));
			}
			jos.putNextEntry(new JarEntry(PACKAGER_STUB));
			jos.write(stubJson);
		}
		String b64;
		try
		{
			b64 = java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		Properties pins = loadPins(root);
		Path log = tmp.resolve("packager.log");
		quiet = true;
		try
		{
			Files.write(log, ("built jar with hash " + b64 + " from commit " + commit + "\n").getBytes(StandardCharsets.UTF_8));
			selfCases++;
			judge("packager log matches retained JAR and commit", checkPackaged(pins, jar, log, commit), null);
			selfCases++;
			judge("packager log for another commit rejected", checkPackaged(pins, jar, log, "f".repeat(40)), "does not record this JAR");
			Files.write(log, ("built jar with hash AAAA from commit " + commit + "\n").getBytes(StandardCharsets.UTF_8));
			selfCases++;
			judge("packager log with different JAR hash rejected", checkPackaged(pins, jar, log, commit), "does not record this JAR");
			Files.write(log, ("built jar with hash " + b64 + " from commit " + commit + "\npackage failed\n").getBytes(StandardCharsets.UTF_8));
			selfCases++;
			judge("packager failure in log rejected", checkPackaged(pins, jar, log, commit), "package failed");
		}
		finally
		{
			quiet = false;
		}
	}

	static void judge(String name, List<String> violations, String expectFail)
	{
		boolean ok;
		if (expectFail == null)
		{
			ok = violations.isEmpty();
		}
		else
		{
			ok = violations.stream().anyMatch(s -> s.contains(expectFail));
		}
		System.out.println("  selftest " + (ok ? "ok  " : "BAD ") + name + (violations.isEmpty() ? "" : " -> " + violations.get(0)));
		if (!ok)
		{
			selfFailures.add("selftest '" + name + "' expected " + (expectFail == null ? "pass" : "failure containing '" + expectFail + "'") + ", got " + violations);
		}
	}

	static byte[] patchMajor(byte[] cls, int major)
	{
		byte[] c = cls.clone();
		c[6] = (byte) (major >> 8);
		c[7] = (byte) major;
		return c;
	}
}
