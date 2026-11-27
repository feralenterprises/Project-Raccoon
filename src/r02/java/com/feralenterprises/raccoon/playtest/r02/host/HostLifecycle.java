package com.feralenterprises.raccoon.playtest.r02.host;

import com.feralenterprises.raccoon.playtest.r02.assembly.LaneInputsArchive;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import com.feralenterprises.raccoon.ui.asset.AssetProvider;
import com.feralenterprises.raccoon.ui.theme.FontSource;
import java.awt.BorderLayout;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * The RuneLite-independent half of the native host plugin: profile guard, run record, background package load,
 * mounting into the two sidebar containers, the account-scope guard and teardown. The plugin only adds and removes
 * its navigation buttons and forwards GameState names. Teardown is safe at any point: {@link #stop} retires the load
 * (a load finishing later is discarded unmounted), disposes the session and empties both containers.
 */
public final class HostLifecycle {
	/** Launcher-set system properties (all host-owned; none comes from the game). */
	public static final String PACKAGE = "raccoon.r02.package", LANE_INPUTS = "raccoon.r02.laneInputs", RUN = "raccoon.r02.run", HOST_SHA = "raccoon.r02.host.sha256",
		LOCK_SHA = "raccoon.r02.lock.sha256", COMMIT = "raccoon.r02.commit";

	private final Properties props;
	private final FontSource fonts;
	private final AssetProvider nativeAssets;
	private final ClassLoader laneLoader;
	private final Container shellTarget;
	private final Container controlsTarget;
	private RunRecord record;
	private PlaytestSession session;
	private ClientPhase phase = ClientPhase.SUSPENDED;
	private int token;
	private boolean running;
	private String failure;
	private Map<String, Integer> lastTeardown;
	private String lastSize;
	private final java.awt.event.ComponentListener sizeRecorder = new java.awt.event.ComponentAdapter() {
		@Override public void componentResized(java.awt.event.ComponentEvent e) {
			String sz = shellTarget.getWidth() + "x" + shellTarget.getHeight();
			if (record != null && running && !sz.equals(lastSize)) { lastSize = sz; record.event("panel-size", sz + " scale=" + scale()); }
		}
	};

	public HostLifecycle(Properties props, FontSource fonts, AssetProvider nativeAssets, ClassLoader laneLoader,
		Container shellTarget, Container controlsTarget) {
		this.props = Objects.requireNonNull(props);
		this.fonts = Objects.requireNonNull(fonts);
		this.nativeAssets = nativeAssets;
		this.laneLoader = Objects.requireNonNull(laneLoader);
		this.shellTarget = Objects.requireNonNull(shellTarget);
		this.controlsTarget = Objects.requireNonNull(controlsTarget);
	}

	/**
	 * EDT. Verifies the profile (throws: the plugin must not start), opens the run record and loads the package in
	 * the background. {@code initialPhase} is read on the EDT when the session mounts.
	 */
	public void start(Path runeliteDir, Supplier<ClientPhase> initialPhase) throws IOException {
		requireEdt();
		if (running) throw new IllegalStateException("already started");
		Path profile = ProfileGuard.check(props.getProperty("user.home"), runeliteDir, props.getProperty(ProfileGuard.PROFILE_PROPERTY));
		String run = props.getProperty(RUN, "run-unnamed");
		if (!run.matches("[A-Za-z0-9-]{1,64}")) throw new IllegalStateException("bad run id");
		record = RunRecord.in(runeliteDir.resolve("raccoon-r02-playtest").resolve(run));
		record.event("start", "profile=" + profile.getFileName() + " commit=" + safe(COMMIT) + " host=" + safe(HOST_SHA) + " lock=" + safe(LOCK_SHA)
			+ " java=" + System.getProperty("java.version") + " dpiaware=" + System.getProperty("sun.java2d.dpiaware", "unset")
			+ " uiScale=" + System.getProperty("sun.java2d.uiScale", "unset"));
		running = true;
		shellTarget.addComponentListener(sizeRecorder);
		show(shellTarget, "Loading the frozen R02 package...");
		show(controlsTarget, "Loading...");
		String pkg = props.getProperty(PACKAGE);
		String laneInputs = props.getProperty(LANE_INPUTS);
		int mine = ++token;
		Thread t = new Thread(() -> {
			try {
				PlaytestSession.Package loaded = PlaytestSession.Package.load(pkg == null ? null : Paths.get(pkg));
				// Lane entries read private inputs: off the EDT, after the frozen package is verified.
				LaneDiscovery.Inspection found = LaneDiscovery.inspect(laneLoader);
				FixtureDispatcher fixtures = PlaytestSession.newDispatcher(loaded);
				// Lane C: the verified lane item-sprite pack fills frozen bank items the package has no sprite for.
				if (laneInputs != null) loaded.assets.withLaneSprites(LaneInputsArchive.open(Paths.get(laneInputs)));
				List<RouteRegistration> lanes = LaneDiscovery.load(laneLoader, laneInputs == null ? null : Paths.get(laneInputs), loaded.root, fixtures);
				record.event("lanes", "present=" + found.entries.keySet() + " absent=" + found.absent
					+ (laneInputs == null ? " not-called=no-lane-inputs" : " routes=" + lanes.size()));
				SwingUtilities.invokeLater(() -> mount(mine, loaded, fixtures, lanes, initialPhase));
			} catch (IOException | RuntimeException e) {
				String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
				SwingUtilities.invokeLater(() -> fail(mine, msg));
			}
		}, "raccoon-r02-package-load");
		t.setDaemon(true);
		t.start();
	}

	/** Any thread: the client's GameState name. */
	public void gameState(String name) {
		ClientPhase p = ClientPhase.ofGameState(name);
		if (SwingUtilities.isEventDispatchThread()) applyPhase(p);
		else SwingUtilities.invokeLater(() -> applyPhase(p));
	}

	/** EDT. Safe before, during or after the load; idempotent. Returns the teardown report (null if never mounted). */
	public Map<String, Integer> stop() {
		requireEdt();
		if (!running) return lastTeardown;
		running = false;
		token++;
		shellTarget.removeComponentListener(sizeRecorder);
		if (session != null) {
			session.dispose();
			lastTeardown = session.teardownReport();
			record.event("stopped", PlaytestSession.clean(lastTeardown) ? "teardown clean" : "teardown NOT clean");
			session = null;
		} else {
			record.event("stopped", "before mount");
		}
		empty(shellTarget);
		empty(controlsTarget);
		return lastTeardown;
	}

	public PlaytestSession session() { return session; }
	public RunRecord record() { return record; }
	public String failure() { return failure; }
	public boolean running() { return running; }

	private void mount(int mine, PlaytestSession.Package loaded, FixtureDispatcher fixtures, List<RouteRegistration> lanes,
		Supplier<ClientPhase> initialPhase) {
		if (mine != token || !running) { if (record != null) record.event("discarded", "late package load after stop"); return; }
		PlaytestSession s = new PlaytestSession(loaded, fixtures, fonts, nativeAssets, lanes, record);
		PlaytestControls c = new PlaytestControls(s);
		session = s;
		place(shellTarget, s.shell());
		place(controlsTarget, c);
		phase = initialPhase.get();
		s.start(phase);
	}

	private void fail(int mine, String msg) {
		if (mine != token || !running) return;
		failure = msg;
		record.event("refused", "package: " + msg.replaceAll("\\S*[/\\\\]\\S*", "<path>").replaceAll("[^A-Za-z0-9 _.,:;=+()#<>-]", "_"));
		show(shellTarget, "<html>The R02 playtest could not start.<br>" + escape(msg) + "</html>");
		show(controlsTarget, "Not started");
	}

	private void applyPhase(ClientPhase p) {
		phase = p;
		if (session != null && running) session.clientPhase(p);
	}

	/** The device scale of the panel's screen (1.0 when unknown or headless). */
	private String scale() {
		java.awt.GraphicsConfiguration gc = shellTarget.getGraphicsConfiguration();
		return gc == null ? "unknown" : String.valueOf(gc.getDefaultTransform().getScaleX());
	}

	private static void place(Container target, JComponent c) {
		target.removeAll();
		target.setLayout(new BorderLayout());
		target.add(c, BorderLayout.CENTER);
		target.revalidate();
		target.repaint();
	}

	private static void show(Container target, String text) {
		JLabel l = new JLabel(text, SwingConstants.CENTER);
		l.setForeground(java.awt.Color.LIGHT_GRAY);
		JComponent box = new javax.swing.JPanel(new BorderLayout());
		box.setOpaque(false);
		box.add(l, BorderLayout.NORTH);
		place(target, box);
	}

	private static void empty(Container target) {
		target.removeAll();
		target.revalidate();
		target.repaint();
	}

	private String safe(String key) {
		String v = props.getProperty(key, "unset");
		return v.matches("[A-Za-z0-9.-]{1,80}") ? v : "invalid";
	}

	private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

	private static void requireEdt() {
		if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("EDT only");
	}
}
