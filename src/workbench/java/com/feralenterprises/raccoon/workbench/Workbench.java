package com.feralenterprises.raccoon.workbench;

import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.shell.RaccoonShell;
import com.feralenterprises.raccoon.ui.shell.ShellGeometry;
import com.feralenterprises.raccoon.ui.theme.DefaultFontSource;
import com.feralenterprises.raccoon.ui.theme.Skin;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/**
 * Runnable component workbench. Runs without RuneLite, the game or a login. Controls on the right are a developer
 * tool, not part of the framework. Usage: java -jar raccoon-ui-workbench.jar [--assets=DIR] [--width=225]
 * [--text=DEFAULT|LARGE] [--skin=THEMED|STRUCTURAL] [--page=ID] [--host=PX] [--rail=AUTO|SHOWN|HIDDEN]
 * [--scrollbar=AUTO|RESERVED|OVERLAY] [--capture=FILE.png]
 */
public final class Workbench {
	private Workbench() { }

	/** Asset folder from --assets=DIR, else ./review-assets. */
	public static Path assetsDirFor(String[] args) { return assetsDir(args); }

	static Path assetsDir(String[] args) {
		String a = arg(args, "--assets=", null);
		if (a != null) return Paths.get(a);
		for (String c : new String[]{"review-assets", "../review-assets"}) if (Files.isDirectory(Paths.get(c))) return Paths.get(c);
		return Paths.get("review-assets");
	}

	static String arg(String[] args, String key, String def) {
		for (String a : args) if (a.startsWith(key)) return a.substring(key.length());
		return def;
	}

	public static UiContext context(Path assets) {
		return new UiContext(new DefaultFontSource(), new FixtureAssetProvider(assets.resolve("art"), assets.resolve("item-fixtures")));
	}

	public static void main(String[] args) throws Exception {
		Path assets = assetsDir(args);
		SwingUtilities.invokeAndWait(() -> build(args, assets));
	}

	private static void build(String[] args, Path assets) {
		UiContext ctx = context(assets);
		ctx.setSkin(Skin.valueOf(arg(args, "--skin=", "THEMED")));
		ctx.setTextScale(TextScale.valueOf(arg(args, "--text=", "DEFAULT")));
		WorkbenchPages pages = new WorkbenchPages(ctx);
		RaccoonShell shell = pages.shell;
		shell.setContentWidth(Integer.parseInt(arg(args, "--width=", "225")));
		shell.setGeometrySpec(shell.geometrySpec()
			.withRail(com.feralenterprises.raccoon.ui.theme.Tokens.RAIL_WIDTH, ShellGeometry.RailMode.valueOf(arg(args, "--rail=", "AUTO")))
			.withScrollbar(ShellGeometry.ScrollbarMode.valueOf(arg(args, "--scrollbar=", "AUTO"))));
		String host = arg(args, "--host=", null);
		if (host != null) shell.setPreferredSize(new java.awt.Dimension(Integer.parseInt(host), 480));
		shell.navigator().navigate(arg(args, "--page=", WorkbenchPages.COMPONENTS));

		JFrame f = new JFrame("Project Raccoon UI-001 Workbench (prototype, fixtures only)");
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		BoundsOverlay overlay = new BoundsOverlay(shell);
		f.setGlassPane(overlay);
		JPanel root = new JPanel(new BorderLayout());
		root.add(shell, BorderLayout.WEST);
		root.add(controls(f, ctx, pages, overlay), BorderLayout.CENTER);
		f.setContentPane(root);
		f.setSize(shell.getPreferredSize().width + 280, 760);
		f.setLocation(20, 20);
		f.setVisible(true);

		String capture = arg(args, "--capture=", null);
		if (capture != null) {
			Timer t = new Timer(1500, e -> {
				try {
					captureScreen(shell, new File(capture));
					System.out.println("captured " + capture);
				} catch (Exception ex) {
					ex.printStackTrace();
					System.exit(2);
				}
				System.exit(0);
			});
			t.setRepeats(false);
			t.start();
		}
	}

	/** Real on-screen capture in device pixels (honours sun.java2d.uiScale). */
	static void captureScreen(JComponent c, File out) throws Exception {
		Point p = c.getLocationOnScreen();
		Rectangle r = new Rectangle(p.x, p.y, c.getWidth(), c.getHeight());
		MultiResolutionImage mri = new Robot().createMultiResolutionScreenCapture(r);
		List<Image> variants = mri.getResolutionVariants();
		Image best = variants.get(variants.size() - 1);
		BufferedImage img = new BufferedImage(best.getWidth(null), best.getHeight(null), BufferedImage.TYPE_INT_RGB);
		Graphics g = img.getGraphics();
		g.drawImage(best, 0, 0, null);
		g.dispose();
		out.getAbsoluteFile().getParentFile().mkdirs();
		ImageIO.write(img, "png", out);
		System.out.println("logical " + c.getWidth() + "x" + c.getHeight() + " device " + img.getWidth() + "x" + img.getHeight()
			+ " viewport " + ((RaccoonShell) c).scrollPane().getViewport().getExtentSize() + " | " + ((RaccoonShell) c).geometry());
	}

	private static JComponent controls(JFrame f, UiContext ctx, WorkbenchPages pages, BoundsOverlay overlay) {
		JPanel p = new JPanel(new GridLayout(0, 1, 4, 4));
		p.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		p.add(new JLabel("<html><b>Workbench controls</b><br>Developer tool, not part of the framework.</html>"));
		JComboBox<Integer> width = new JComboBox<>(new Integer[]{225, 263, 280});
		width.setSelectedItem(pages.shell.contentWidth());
		JComboBox<String> host = new JComboBox<>(new String[]{"Auto", "225", "242", "263", "268", "275", "306", "330"});
		host.setEditable(true);
		if (pages.shell.isPreferredSizeSet()) host.setSelectedItem(Integer.toString(pages.shell.getPreferredSize().width));
		JLabel geo = new JLabel(" ");
		Runnable relayout = () -> {
			String h = (String) host.getSelectedItem();
			pages.shell.setPreferredSize("Auto".equals(h) ? null : new java.awt.Dimension(Integer.parseInt(h), 480));
			f.setSize(pages.shell.getPreferredSize().width + 280, f.getHeight());
			f.validate();
			SwingUtilities.invokeLater(() -> geo.setText("<html>" + pages.shell.geometry() + "</html>"));
		};
		width.addActionListener(e -> { pages.shell.setContentWidth((Integer) width.getSelectedItem()); relayout.run(); });
		p.add(labelled("Content width (px)", width));
		host.addActionListener(e -> relayout.run());
		p.add(labelled("Host width (simulated sidebar)", host));
		JComboBox<ShellGeometry.RailMode> rail = new JComboBox<>(ShellGeometry.RailMode.values());
		rail.setSelectedItem(pages.shell.geometrySpec().railMode);
		rail.addActionListener(e -> {
			pages.shell.setGeometrySpec(pages.shell.geometrySpec().withRail(pages.shell.geometrySpec().railWidth, (ShellGeometry.RailMode) rail.getSelectedItem()));
			relayout.run();
		});
		p.add(labelled("Rail", rail));
		JComboBox<ShellGeometry.ScrollbarMode> sb = new JComboBox<>(ShellGeometry.ScrollbarMode.values());
		sb.setSelectedItem(pages.shell.geometrySpec().scrollbarMode);
		sb.addActionListener(e -> {
			pages.shell.setGeometrySpec(pages.shell.geometrySpec().withScrollbar((ShellGeometry.ScrollbarMode) sb.getSelectedItem()));
			relayout.run();
		});
		p.add(labelled("Scrollbar", sb));
		p.add(geo);
		SwingUtilities.invokeLater(relayout);
		JComboBox<TextScale> text = new JComboBox<>(TextScale.values());
		text.setSelectedItem(ctx.textScale());
		text.addActionListener(e -> ctx.setTextScale((TextScale) text.getSelectedItem()));
		p.add(labelled("Text", text));
		JComboBox<Skin> skin = new JComboBox<>(Skin.values());
		skin.setSelectedItem(ctx.skin());
		skin.addActionListener(e -> ctx.setSkin((Skin) skin.getSelectedItem()));
		p.add(labelled("Mode", skin));
		JComboBox<DataState> state = new JComboBox<>(DataState.values());
		state.addActionListener(e -> pages.setGearState((DataState) state.getSelectedItem()));
		p.add(labelled("Gear page state", state));
		JButton tick = new JButton("Simulate read-model update");
		tick.addActionListener(e -> pages.tick());
		p.add(tick);
		JCheckBox bounds = new JCheckBox("Show component bounds");
		bounds.addActionListener(e -> overlay.setVisible(bounds.isSelected()));
		p.add(bounds);
		JButton shot = new JButton("Save screenshot (./workbench-shots)");
		shot.addActionListener(e -> {
			try {
				File out = new File("workbench-shots/shot-" + System.currentTimeMillis() + ".png");
				captureScreen(pages.shell, out);
				shot.setText("Saved " + out.getName());
			} catch (Exception ex) {
				shot.setText("Capture failed: " + ex.getMessage());
			}
		});
		p.add(shot);
		p.add(new JLabel("<html>Rail " + Tokens.RAIL_WIDTH + " px is a proposal; geometry adapts to the host width.</html>"));
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.add(p, BorderLayout.NORTH);
		return wrap;
	}

	private static JComponent labelled(String label, JComponent c) {
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.add(new JLabel(label), BorderLayout.WEST);
		row.add(c, BorderLayout.CENTER);
		return row;
	}

	/** Glass-pane overlay outlining every framework component with its class name. */
	static final class BoundsOverlay extends JComponent {
		private final Container target;

		BoundsOverlay(Container target) { this.target = target; }

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setFont(g2.getFont().deriveFont(9f));
			paintTree(g2, target);
			g2.dispose();
		}

		private void paintTree(Graphics2D g, Container c) {
			for (Component k : c.getComponents()) {
				if (!k.isShowing()) continue;
				if (k.getClass().getName().startsWith("com.feralenterprises")) {
					Rectangle r = SwingUtilities.convertRectangle(c, k.getBounds(), this);
					g.setColor(new Color(255, 0, 160, 170));
					g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
					g.drawString(k.getClass().getSimpleName(), r.x + 2, r.y + 9);
				}
				if (k instanceof Container) paintTree(g, (Container) k);
			}
		}
	}
}
