package com.feralenterprises.raccoon.ui.integration;

import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.workbench.Offscreen;
import com.feralenterprises.raccoon.workbench.Workbench;
import com.feralenterprises.raccoon.workbench.WorkbenchPages;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Workbench wired to the real FR-002 AccountLifecycle (foundation 9e625d2) through {@link LifecycleBinding}. Buttons
 * drive a simulated client thread (login, region change, hop, connection loss, logout) so the owner can watch the
 * shared notice, clearing and scope continuity. Fixture data only; no RuneLite, no real account.
 * Usage: java -jar raccoon-ui-integration-workbench.jar [--assets=DIR] [--script=OUT_DIR]
 */
public final class IntegrationWorkbench {
	private final ExecutorService clientThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "simulated-client-thread"));
	private final SimulatedClient client = new SimulatedClient();
	private WorkbenchPages pages;
	private LifecycleBinding binding;
	private JLabel status;

	public static void main(String[] args) throws Exception {
		Path assets = Workbench.assetsDirFor(args);
		String script = null;
		for (String a : args) if (a.startsWith("--script=")) script = a.substring(9);
		IntegrationWorkbench w = new IntegrationWorkbench();
		if (script != null) { w.script(assets, new File(script)); System.exit(0); }
		SwingUtilities.invokeAndWait(() -> w.window(assets));
	}

	private void build(Path assets) {
		pages = new WorkbenchPages(Workbench.context(assets), true);
		pages.shell.navigator().navigate(WorkbenchPages.GEAR);
		binding = new LifecycleBinding(client.lifecycle(), pages.shell);
		pages.shell.addScopeListener(scope -> binding.apply((AccountScope) scope, pages::deliverFixture));
	}

	private void window(Path assets) {
		build(assets);
		JFrame f = new JFrame("Project Raccoon UI workbench + FR-002 lifecycle (fixtures only)");
		f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		f.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override public void windowClosed(java.awt.event.WindowEvent e) {
				binding.dispose();
				pages.shell.dispose();
				clientThread.shutdownNow();
			}
		});
		JPanel controls = new JPanel(new GridLayout(0, 1, 4, 4));
		controls.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		controls.add(new JLabel("<html><b>Simulated client (FR-002 9e625d2)</b><br>Developer tool; synthetic identities.</html>"));
		action(controls, "Start: login screen", client::start);
		action(controls, "Log in: fixture account A", () -> client.login(SimulatedClient.FIXTURE_A));
		action(controls, "Log in: fixture account B", () -> client.login(SimulatedClient.FIXTURE_B));
		action(controls, "Region change (LOADING)", client::regionChange);
		action(controls, "World hop", client::hop);
		action(controls, "Connection lost", client::connectionLost);
		action(controls, "Reconnect", client::reconnect);
		action(controls, "Log out", client::logout);
		JButton dispose = new JButton("Dispose binding (unsubscribe)");
		dispose.addActionListener(e -> { binding.dispose(); refresh(); });
		controls.add(dispose);
		status = new JLabel(" ");
		controls.add(status);
		JPanel root = new JPanel(new BorderLayout());
		root.add(pages.shell, BorderLayout.WEST);
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.add(controls, BorderLayout.NORTH);
		root.add(wrap, BorderLayout.CENTER);
		f.setContentPane(root);
		f.setSize(pages.shell.shellWidth() + 300, 760);
		f.setVisible(true);
		refresh();
	}

	private void action(JPanel p, String label, Runnable onClient) {
		JButton b = new JButton(label);
		b.addActionListener(e -> clientThread.submit(() -> { onClient.run(); SwingUtilities.invokeLater(this::refresh); }));
		p.add(b);
	}

	private void refresh() {
		if (status == null) return;
		RaccoonShell s = pages.shell;
		status.setText("<html>Lifecycle: " + client.lifecycle().state() + "<br>Shell: " + (s.presence() == null ? "showing account data" : s.presence())
			+ "<br>Value clears " + s.accountClears() + ", page discards " + s.pageDiscards() + "<br>Applied " + binding.applied()
			+ ", rejected " + binding.rejected() + (binding.isDisposed() ? "<br><b>binding disposed</b>" : "") + "</html>");
	}

	/** Scripted run: drives each lifecycle step and writes an offscreen capture of the shell after it. */
	private void script(Path assets, File out) throws Exception {
		SwingUtilities.invokeAndWait(() -> build(assets));
		String[] names = {"01-before-start", "02-login-screen", "03-logged-in-A", "04-region-change-loading", "05-region-change-done",
			"06-hop-suspended", "07-hop-new-epoch", "08-logged-out", "09-logged-in-B"};
		Runnable[] steps = {() -> { }, client::start, () -> client.login(SimulatedClient.FIXTURE_A),
			() -> client.phase(com.feralenterprises.raccoon.core.account.ClientPhase.LOADING),
			() -> { client.phase(com.feralenterprises.raccoon.core.account.ClientPhase.LOGGED_IN); client.tick(); },
			() -> client.phase(com.feralenterprises.raccoon.core.account.ClientPhase.HOPPING),
			() -> { client.phase(com.feralenterprises.raccoon.core.account.ClientPhase.LOADING);
				client.phase(com.feralenterprises.raccoon.core.account.ClientPhase.LOGGED_IN); client.tick(); },
			client::logout, () -> client.login(SimulatedClient.FIXTURE_B)};
		StringBuilder log = new StringBuilder("step,lifecycle_state,shell_presence,value_clears,page_discards,applied,rejected\n");
		for (int i = 0; i < steps.length; i++) {
			clientThread.submit(steps[i]).get(5, TimeUnit.SECONDS);
			for (int k = 0; k < 3; k++) SwingUtilities.invokeAndWait(() -> { });
			int n = i;
			SwingUtilities.invokeAndWait(() -> {
				Offscreen.size(pages.shell, 640);
				try {
					Offscreen.write(Offscreen.paint(pages.shell, 1.0), new File(out, names[n] + ".png"));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				RaccoonShell s = pages.shell;
				log.append(names[n]).append(',').append(client.lifecycle().state()).append(',')
					.append(s.presence() == null ? "ACCOUNT_DATA" : s.presence()).append(',').append(s.accountClears()).append(',')
					.append(s.pageDiscards()).append(',').append(binding.applied()).append(',').append(binding.rejected()).append('\n');
			});
		}
		java.nio.file.Files.write(new File(out, "lifecycle-steps.csv").toPath(), log.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		SwingUtilities.invokeAndWait(() -> { binding.dispose(); pages.shell.dispose(); });
		clientThread.shutdownNow();
		System.out.print(log);
	}
}
