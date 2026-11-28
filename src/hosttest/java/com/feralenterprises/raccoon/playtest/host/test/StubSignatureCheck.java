package com.feralenterprises.raccoon.playtest.host.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Proves the host plugin's signature doubles (playtest/host-plugin/typecheck-stubs) and the safety facts the host
 * relies on match the pinned RuneLite source: each file's git blob id (content identity at runelite-parent-1.13.8,
 * 8397ec71) and each signature or behaviour line. Arg: a runelite checkout (sparse is fine).
 */
public final class StubSignatureCheck {
	private static final String C = "runelite-client/src/main/java/net/runelite/client/", A = "runelite-api/src/main/java/net/runelite/api/";

	private static final String[][] TABLE = {
		{C + "RuneLite.java", "002c27764104df63c988d6207139d037463364ee",
			"public static final File RUNELITE_DIR = new File(System.getProperty(\"user.home\"), \".runelite\");",
			"public static void main(String[] args) throws Exception",
			"parser.accepts(\"developer-mode\", \"Enable developer tools\");",
			"parser.accepts(\"disable-telemetry\", \"Disable telemetry\");",
			"final boolean developerMode = options.has(\"developer-mode\") && RuneLiteProperties.getLauncherVersion() == null;",
			"System.setProperty(\"jagex.userhome\", RUNELITE_DIR.getAbsolutePath());"},
		{C + "externalplugins/ExternalPluginManager.java", "099d669a71f7bbdb9e61e66e287492131619a635",
			"public static void loadBuiltin(Class<? extends Plugin>... plugins)"},
		{C + "plugins/Plugin.java", "1e17cbb4c9f2d2f19cba7ed06d0242ede952e5c7",
			"public abstract class Plugin implements Module", "protected void startUp() throws Exception", "protected void shutDown() throws Exception"},
		{C + "plugins/PluginDescriptor.java", "8a9d755ad10c32ef247a9f617665cfed74ff325f",
			"String name();", "String description() default \"\";", "boolean enabledByDefault() default true;", "boolean developerPlugin() default false;"},
		// Fact: plugins start on the EDT (startPlugins wraps startPlugin in invokeAndWait), so startUp must not invokeAndWait.
		{C + "plugins/PluginManager.java", "d7ee2a375aab56aacc093819b3c223d0667a2a30",
			"public void startPlugins()", "SwingUtilities.invokeAndWait(() ->", "plugin.startUp();", "eventBus.register(plugin);"},
		{C + "eventbus/Subscribe.java", "dae4eb4597e2008c11bfeebba35ee97d7ec753c3", "public @interface Subscribe"},
		{C + "ui/PluginPanel.java", "b6997171427803825dc30f2fcbf76bcffab7e97a",
			"public abstract class PluginPanel extends JPanel implements Activatable", "public static final int PANEL_WIDTH = 225;", "protected PluginPanel(boolean wrap)"},
		{C + "ui/NavigationButton.java", "f5cacf1a28886ecd0f7d9a1fa019ada61ce6f7b8",
			"@Builder", "private final BufferedImage icon;", "private final String tooltip = \"\";", "private final PluginPanel panel;", "private final int priority;"},
		{C + "ui/ClientToolbar.java", "9ca1ab2380fe61080ccf19cd5f13615ee1218105",
			"public void addNavigation(NavigationButton button)", "public void removeNavigation(final NavigationButton button)"},
		{C + "ui/FontManager.java", "eb78b816ba091adf6b3845a6c0343c3a66d9176b",
			"private static final Font runescapeFont;", "private static final Font runescapeSmallFont;", "private static final Font runescapeBoldFont;"},
		{C + "util/AsyncBufferedImage.java", "c805fee45dd033d8ded1fd41ef8cd2e50a877f18",
			"public class AsyncBufferedImage extends BufferedImage", "public synchronized void onLoaded(Runnable r)"},
		{C + "game/SpriteManager.java", "5920682dd795a34a445e5d31ed7b5738a16dd2d2",
			"public void getSpriteAsync(int archive, int file, Consumer<BufferedImage> user)"},
		{C + "game/ItemManager.java", "6b28e4b038da8ee0bff676a3e0a68939f30b624a",
			"public ItemComposition getItemComposition(int itemId)", "public AsyncBufferedImage getImage(int itemId, int quantity, boolean stackable)"},
		{C + "callback/ClientThread.java", "29f88cb0c92fe2a3a00f1c35c77def0a11c7f70a", "public void invokeLater(Runnable r)"},
		// Fact: saved Jagex accounts live in RUNELITE_DIR (so in the isolated profile), loaded by the developer Jagex Auth plugin.
		{C + "rs/auth/SessionStore.java", "c65082d265ed74948ff8adc3175394cda8c789cb",
			"private static final Path SESSIONS_FILE = RuneLite.RUNELITE_DIR.toPath().resolve(\"jagex-accounts.dat\");"},
		// Fact (ordinary-account launch): SessionStore loads nothing and touches no credential when the profile has no
		// jagex-accounts.dat (the launcher refuses a profile that has one); the key is read or created only on Windows,
		// through WindowsCredentialStore, which reads before it creates and has no delete call (the blob pins the file).
		{C + "rs/auth/SessionStore.java", "c65082d265ed74948ff8adc3175394cda8c789cb",
			"if (!Files.exists(SESSIONS_FILE))", "return WindowsCredentialStore.getOrCreateKey();"},
		{C + "rs/auth/WindowsCredentialStore.java", "08d50462494d352766ee14616cdeff1f5836cad4",
			"private static final String TARGET = \"RuneLite SessionStore\";",
			"if (Advapi32.INSTANCE.CredReadW(new WString(TARGET), CRED_TYPE_GENERIC, 0, credentialRef))",
			"boolean CredReadW(WString target, int type, int flags, PointerByReference credential);",
			"boolean CredWriteW(Credential credential, int flags);",
			"void CredFree(Pointer credential);"},
		{C + "plugins/jagexauth/JagexAuthPlugin.java", "7e9ee7c6e375cbef595dc945b4dfd05793591edf", "developerPlugin = true,", "jagexAuthService.loadFromStore()"},
		{A + "ItemComposition.java", "517c3949b6d4cdd995cef42af407042bf68a251e", "String getName();"},
		{A + "GameState.java", "526c89f06e936d1f5a563486e3724d60fcee5a88",
			"UNKNOWN(-1),", "STARTING(0),", "LOGIN_SCREEN(10),", "LOGIN_SCREEN_AUTHENTICATOR(11),", "LOGGING_IN(20),", "LOADING(25),", "LOGGED_IN(30),",
			"CONNECTION_LOST(40),", "HOPPING(45);"},
		{A + "Client.java", "ea4b666781d07d5974ec639708504c405c47ce6b", "GameState getGameState();"},
		{A + "events/GameStateChanged.java", "f62ee5547ee669c2017e9124bbbad2fef48c96d9", "private GameState gameState;"},
	};

	public static void main(String[] args) throws Exception {
		Path root = Paths.get(args[0]);
		Check t = new Check("HostStubCheck");
		for (String[] row : TABLE) {
			Path f = root.resolve(row[0]);
			if (!Files.isRegularFile(f)) { t.that("present " + row[0], false); continue; }
			byte[] raw = Files.readAllBytes(f);
			String text = new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n");
			t.that("blob " + row[0], row[1].equals(blob(text.getBytes(StandardCharsets.UTF_8))));
			for (int i = 2; i < row.length; i++)
				t.that(row[0] + ": " + row[i], Pattern.compile("(?m)^\\s*" + Pattern.quote(row[i])).matcher(text).find());
		}
		System.exit(t.finish());
	}

	private static String blob(byte[] content) throws Exception {
		MessageDigest d = MessageDigest.getInstance("SHA-1");
		d.update(("blob " + content.length + "\0").getBytes(StandardCharsets.UTF_8));
		d.update(content);
		StringBuilder s = new StringBuilder();
		for (byte b : d.digest()) s.append(String.format("%02x", b));
		return s.toString();
	}
}
