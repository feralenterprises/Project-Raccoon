package com.feralenterprises.raccoon.ui.test;

import com.feralenterprises.raccoon.ui.component.SegmentedSelector;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.workbench.Workbench;
import com.feralenterprises.raccoon.workbench.WorkbenchPages;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Real-display interaction tests: a visible window, OS-level mouse clicks and key presses through java.awt.Robot,
 * real focus traversal. Run under a display (xvfb-run on Linux, or a normal Windows desktop).
 */
public final class DisplayTests {
	private static final List<String> failures = new ArrayList<>();
	private static int passed;
	private static Robot robot;

	public static void main(String[] args) throws Exception {
		robot = new Robot();
		robot.setAutoDelay(40);
		AtomicReference<WorkbenchPages> ref = new AtomicReference<>();
		AtomicReference<JFrame> frame = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			WorkbenchPages p = new WorkbenchPages(Workbench.context(Paths.get("review-assets")));
			JFrame f = new JFrame("DisplayTests");
			f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout());
			root.add(p.shell, java.awt.BorderLayout.WEST);
			f.setContentPane(root);
			f.setSize(p.shell.shellWidth() + 16, 760);
			f.setLocation(10, 10);
			f.setVisible(true);
			p.shell.navigator().navigate(WorkbenchPages.COMPONENTS);
			ref.set(p);
			frame.set(f);
		});
		settle();
		WorkbenchPages p = ref.get();

		click(p.shell.rail().button(WorkbenchPages.GEAR), null);
		check("click rail W2 opens Gear", on(() -> WorkbenchPages.GEAR.equals(p.shell.navigator().current())));
		click(p.shell.header().back(), null);
		check("click header Back returns to Components", on(() -> WorkbenchPages.COMPONENTS.equals(p.shell.navigator().current())));

		SegmentedSelector sel = p.styleSelector;
		Rectangle c1 = on(() -> sel.cell(1, sel.getWidth()));
		click(sel, c1);
		check("click selector option 2", on(() -> sel.selected() == 1));
		check("click gives selector keyboard focus", on(sel::isFocusOwner));
		type(KeyEvent.VK_RIGHT);
		check("keyboard Right moves selection", on(() -> sel.selected() == 2));
		type(KeyEvent.VK_HOME);
		check("keyboard Home selects first", on(() -> sel.selected() == 0));

		JComponent summary = p.details.summary();
		on(() -> { p.shell.scrollPane().getViewport().scrollRectToVisible(SwingUtilities.convertRectangle(summary.getParent(), summary.getBounds(), p.shell.scrollPane().getViewport().getView())); return null; });
		settle();
		click(summary, null);
		check("click summary expands details", on(p.details::expanded));
		type(KeyEvent.VK_ENTER);
		check("keyboard Enter on focused summary collapses", on(() -> !p.details.expanded()));

		int before = on(() -> p.progress.fillWidth());
		on(() -> { p.shell.scrollPane().getViewport().scrollRectToVisible(SwingUtilities.convertRectangle(p.advance.getParent(), p.advance.getBounds(), p.shell.scrollPane().getViewport().getView())); return null; });
		settle();
		click(p.advance, null);
		check("click Advance grows the live progress fill", on(() -> p.progress.fillWidth() > before));

		on(() -> { p.ctx.setTextScale(TextScale.LARGE); return null; });
		settle();
		List<String> issues = on(() -> com.feralenterprises.raccoon.workbench.LayoutAudit.audit(p.shell.currentHost()));
		issues.forEach(i -> System.out.println("  audit: " + i));
		System.out.println("  viewport width after Large: " + on(() -> p.shell.scrollPane().getViewport().getWidth()));
		check("Large text applied live without horizontal overflow or clipping", on(() -> p.shell.scrollPane().getViewport().getWidth() == 225)
			&& issues.isEmpty());

		SwingUtilities.invokeAndWait(() -> frame.get().dispose());

		// Narrow host (unwrapped PluginPanel width): rail moves into the header menu; real popup and click.
		AtomicReference<WorkbenchPages> narrow = new AtomicReference<>();
		AtomicReference<JFrame> nf = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			WorkbenchPages q = new WorkbenchPages(Workbench.context(Paths.get("review-assets")));
			q.shell.setPreferredSize(new java.awt.Dimension(225, 600));
			JFrame f = new JFrame("DisplayTests narrow");
			javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout());
			root.add(q.shell, java.awt.BorderLayout.WEST);
			f.setContentPane(root);
			f.setSize(420, 640);
			f.setLocation(400, 10);
			f.setVisible(true);
			q.shell.navigator().navigate(WorkbenchPages.COMPONENTS);
			narrow.set(q);
			nf.set(f);
		});
		settle();
		WorkbenchPages q = narrow.get();
		check("narrow host: rail hidden, menu shown, viewport 225", on(() -> q.shell.header().menu().isShowing()
			&& !q.shell.getComponent(0).isVisible() && q.shell.scrollPane().getViewport().getWidth() == 225));
		click(q.shell.header().menu(), null);
		javax.swing.JPopupMenu popup = on(() -> {
			for (java.awt.Window w : java.awt.Window.getWindows()) {
				javax.swing.JPopupMenu m = findPopup(w);
				if (m != null && m.isShowing()) return m;
			}
			return null;
		});
		check("narrow host: real click opens the destination menu", popup != null);
		if (popup != null) {
			javax.swing.JMenuItem home = (javax.swing.JMenuItem) on(() -> popup.getComponent(3));
			click(home, null);
			check("narrow host: clicking a menu item navigates", on(() -> "home".equals(q.shell.navigator().current())));
		}
		SwingUtilities.invokeAndWait(() -> { q.shell.dispose(); nf.get().dispose(); });

		// Short window: the rail overflows; a real mouse wheel over it scrolls even though no bar is visible.
		AtomicReference<WorkbenchPages> shortRef = new AtomicReference<>();
		AtomicReference<JFrame> sf = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			WorkbenchPages r = new WorkbenchPages(Workbench.context(Paths.get("review-assets")));
			JFrame f = new JFrame("DisplayTests short");
			javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout());
			root.add(r.shell, java.awt.BorderLayout.WEST);
			f.setContentPane(root);
			f.setSize(400, 320);
			f.setLocation(10, 700);
			f.setVisible(true);
			r.shell.navigator().navigate(WorkbenchPages.COMPONENTS);
			shortRef.set(r);
			sf.set(f);
		});
		settle();
		WorkbenchPages r = shortRef.get();
		check("short host: rail scrollbar has zero width and rail touches content",
			on(() -> r.shell.railScrollPane().getVerticalScrollBar().getWidth() == 0 && r.shell.columnX() == r.shell.railScrollPane().getWidth()));
		java.awt.Point railCentre = on(() -> {
			java.awt.Point o = r.shell.railScrollPane().getLocationOnScreen();
			return new java.awt.Point(o.x + r.shell.railScrollPane().getWidth() / 2, o.y + r.shell.railScrollPane().getHeight() / 2);
		});
		robot.mouseMove(railCentre.x, railCentre.y);
		settle();
		robot.mouseWheel(4);
		settle();
		check("short host: real mouse wheel scrolls the rail", on(() -> r.shell.railScrollPane().getViewport().getViewPosition().y > 0));
		SwingUtilities.invokeAndWait(() -> { r.shell.dispose(); sf.get().dispose(); });
		System.out.println("DisplayTests: " + passed + " passed, " + failures.size() + " failed");
		failures.forEach(f -> System.out.println("  FAIL " + f));
		System.exit(failures.isEmpty() ? 0 : 1);
	}

	interface Call<T> { T get() throws Exception; }

	static javax.swing.JPopupMenu findPopup(java.awt.Container c) {
		for (java.awt.Component k : c.getComponents()) {
			if (k instanceof javax.swing.JPopupMenu) return (javax.swing.JPopupMenu) k;
			if (k instanceof java.awt.Container) { javax.swing.JPopupMenu m = findPopup((java.awt.Container) k); if (m != null) return m; }
		}
		return null;
	}

	static <T> T on(Call<T> c) {
		AtomicReference<T> r = new AtomicReference<>();
		try {
			SwingUtilities.invokeAndWait(() -> { try { r.set(c.get()); } catch (Exception e) { throw new RuntimeException(e); } });
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return r.get();
	}

	static void settle() throws Exception {
		robot.waitForIdle();
		Thread.sleep(250);
		robot.waitForIdle();
	}

	static void click(JComponent c, Rectangle area) throws Exception {
		Point p = on(() -> {
			Point s = c.getLocationOnScreen();
			Rectangle r = area != null ? area : new Rectangle(0, 0, c.getWidth(), c.getHeight());
			return new Point(s.x + r.x + r.width / 2, s.y + r.y + r.height / 2);
		});
		robot.mouseMove(p.x, p.y);
		robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
		robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
		settle();
	}

	static void type(int key) throws Exception {
		robot.keyPress(key);
		robot.keyRelease(key);
		settle();
	}

	static void check(String name, boolean ok) {
		if (ok) passed++; else failures.add(name);
	}
}
