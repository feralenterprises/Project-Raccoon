package com.feralenterprises.raccoon.playtest.r02.runelite;

import com.feralenterprises.raccoon.playtest.r02.host.HostLifecycle;
import com.feralenterprises.raccoon.playtest.r02.host.NameAudit;
import com.feralenterprises.raccoon.playtest.r02.host.PlaytestSession;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;

/**
 * DEV-ONLY R02 native playtest host. Shows the R02 shell with simulated (frozen A/B) data in a real RuneLite sidebar,
 * plus a second tab of playtest controls. It reads exactly one thing from the game client: the GameState, to hide
 * every simulated value while an account may be signed in. It reads item names and images from the game cache
 * (ItemManager / SpriteManager), never account state. It starts only inside a launcher-made isolated profile.
 * Never part of the framework, the foundation build or a Plugin Hub package.
 */
@PluginDescriptor(name = "Raccoon R02 Playtest (dev)", description = "R02 interface with simulated data; no account data",
	developerPlugin = true, enabledByDefault = true)
public class R02PlaytestPlugin extends Plugin {
	@Inject private ClientToolbar clientToolbar;
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ItemManager itemManager;
	@Inject private SpriteManager spriteManager;

	private HostLifecycle host;
	private NavigationButton shellButton;
	private NavigationButton controlsButton;
	private volatile boolean audited;

	@Override
	protected void startUp() throws Exception {
		// RuneLite 1.13.8 starts plugins on the EDT (PluginManager.startPlugins -> invokeAndWait).
		HostPanel shellPanel = new HostPanel();
		HostPanel controlsPanel = new HostPanel();
		host = new HostLifecycle(System.getProperties(), new RuneLiteFonts(), new NativeAssets(spriteManager, itemManager),
			getClass().getClassLoader(), shellPanel, controlsPanel);
		host.start(RuneLite.RUNELITE_DIR.toPath(), () -> com.feralenterprises.raccoon.playtest.r02.host.ClientPhase.ofGameState(client.getGameState().name()));
		shellButton = NavigationButton.builder().tooltip("Raccoon R02 playtest").icon(icon(0xb39a62)).priority(98).panel(shellPanel).build();
		controlsButton = NavigationButton.builder().tooltip("R02 playtest controls").icon(icon(0x6f8f9a)).priority(99).panel(controlsPanel).build();
		clientToolbar.addNavigation(shellButton);
		clientToolbar.addNavigation(controlsButton);
		maybeAudit(client.getGameState());
	}

	@Override
	protected void shutDown() throws Exception {
		Runnable stop = () -> {
			if (shellButton != null) clientToolbar.removeNavigation(shellButton);
			if (controlsButton != null) clientToolbar.removeNavigation(controlsButton);
			if (host != null) host.stop();
			shellButton = null;
			controlsButton = null;
			host = null;
		};
		if (SwingUtilities.isEventDispatchThread()) stop.run();
		else SwingUtilities.invokeAndWait(stop);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e) {
		HostLifecycle h = host;
		if (h != null) h.gameState(e.getGameState().name());
		maybeAudit(e.getGameState());
	}

	/** Native item naming check, once per run, when the game cache is loaded (login screen). Client thread. */
	private void maybeAudit(GameState state) {
		if (audited || state != GameState.LOGIN_SCREEN) return;
		audited = true;
		clientThread.invokeLater(() -> {
			HostLifecycle h = host;
			PlaytestSession s = h == null ? null : h.session();
			if (s == null) { audited = false; return; }
			NameAudit.Result r = NameAudit.run(s.data().registry(), id -> {
				ItemComposition c = itemManager.getItemComposition(id);
				return c == null ? null : c.getName();
			});
			SwingUtilities.invokeLater(() -> {
				try {
					h.record().file("native-name-audit.csv", r.csv);
					h.record().event("name-audit", "checked=" + r.checked + " mismatches=" + r.mismatches + " unresolved=" + r.unresolved);
				} catch (java.io.IOException ex) {
					h.record().event("name-audit", "not written");
				}
			});
		});
	}

	/** Unwrapped sidebar panel; as wide as its content asks (never narrower than RuneLite's 225 px). */
	static final class HostPanel extends PluginPanel {
		HostPanel() { super(false); setBackground(new Color(0x1e1b16)); }

		@Override public Dimension getPreferredSize() {
			Dimension d = super.getPreferredSize();
			int w = getComponentCount() == 0 ? PANEL_WIDTH : Math.max(PANEL_WIDTH, getComponent(0).getPreferredSize().width);
			return new Dimension(w, d.height);
		}
	}

	private static BufferedImage icon(int rgb) {
		BufferedImage i = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = i.createGraphics();
		g.setColor(new Color(rgb));
		g.fillOval(1, 1, 14, 14);
		g.dispose();
		return i;
	}

	/** For the stub lifecycle test. */
	HostLifecycle host() { return host; }
}
