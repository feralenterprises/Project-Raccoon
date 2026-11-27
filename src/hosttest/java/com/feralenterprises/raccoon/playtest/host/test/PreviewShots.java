package com.feralenterprises.raccoon.playtest.host.test;

import com.feralenterprises.raccoon.playtest.r02.host.ClientPhase;
import com.feralenterprises.raccoon.playtest.r02.host.PlaytestControls;
import com.feralenterprises.raccoon.playtest.r02.host.PlaytestSession;
import com.feralenterprises.raccoon.playtest.r02.host.RunRecord;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * DEV preview (needs a display, e.g. xvfb-run): the two sidebar tabs the native host mounts, at RuneLite's sidebar
 * height, with the frozen package and framework default fonts (the native client uses RuneLite's fonts and the real
 * sidebar). Not a native measurement. Args: output folder, extracted frozen package.
 */
public final class PreviewShots {
	public static void main(String[] args) throws Exception {
		Path out = Files.createDirectories(Paths.get(args[0]));
		PlaytestSession.Package pkg = PlaytestSession.Package.load(Paths.get(args[1]));
		AtomicReference<PlaytestSession> ref = new AtomicReference<>();
		AtomicReference<JFrame> frame = new AtomicReference<>();
		AtomicReference<JPanel> sidebar = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			PlaytestSession s = new PlaytestSession(pkg, new DefaultFontSource(), null, Collections.emptyList(), RunRecord.memory());
			s.start(ClientPhase.OPEN);
			JPanel side = new JPanel(new BorderLayout());
			side.add(s.shell(), BorderLayout.CENTER);
			int w = Math.max(225, s.shell().getPreferredSize().width);
			side.setPreferredSize(new Dimension(w, 503));
			JFrame f = new JFrame("R02 host preview");
			f.getContentPane().setLayout(new BorderLayout());
			f.getContentPane().add(side, BorderLayout.CENTER);
			f.pack();
			f.setVisible(true);
			ref.set(s);
			frame.set(f);
			sidebar.set(side);
		});
		PlaytestSession s = ref.get();
		shot(sidebar.get(), out.resolve("host-demoA-home.png"));
		SwingUtilities.invokeAndWait(() -> s.shell().navigator().navigate(RouteId.BANK.id()));
		shot(sidebar.get(), out.resolve("host-demoA-bank-placeholder.png"));
		SwingUtilities.invokeAndWait(() -> s.selectAccount("B"));
		shot(sidebar.get(), out.resolve("host-demoB-home-after-switch.png"));
		SwingUtilities.invokeAndWait(() -> s.clientPhase(ClientPhase.SUSPENDED));
		shot(sidebar.get(), out.resolve("host-suspended-while-signed-in.png"));
		SwingUtilities.invokeAndWait(() -> {
			s.clientPhase(ClientPhase.OPEN);
			JPanel c = new JPanel(new BorderLayout());
			c.add(new PlaytestControls(s), BorderLayout.CENTER);
			c.setPreferredSize(new Dimension(225, 503));
			frame.get().getContentPane().removeAll();
			frame.get().getContentPane().add(c, BorderLayout.CENTER);
			frame.get().pack();
			sidebar.set(c);
		});
		shot(sidebar.get(), out.resolve("host-controls-tab.png"));
		SwingUtilities.invokeAndWait(() -> { s.dispose(); frame.get().dispose(); });
		System.out.println("PreviewShots: teardown " + s.teardownReport() + " -> " + (PlaytestSession.clean(s.teardownReport()) ? "clean" : "NOT clean"));
		System.exit(0);
	}

	private static void shot(JComponent c, Path file) throws Exception {
		Thread.sleep(300);
		AtomicReference<BufferedImage> img = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			c.validate();
			BufferedImage i = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = i.createGraphics();
			c.printAll(g);
			g.dispose();
			img.set(i);
		});
		ImageIO.write(img.get(), "png", file.toFile());
		System.out.println("PreviewShots: " + file.getFileName() + " " + img.get().getWidth() + "x" + img.get().getHeight());
	}
}
