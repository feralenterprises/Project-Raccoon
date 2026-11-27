package com.feralenterprises.raccoon.ui.lifecycle;

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Fences asynchronous image callbacks (e.g. RuneLite AsyncBufferedImage.onLoaded). A callback captures the
 * generation current when it was issued; after {@link #invalidate()} (new model, cleared data) or
 * {@link #dispose()}, older callbacks do nothing. Callbacks may arrive on any thread; the repaint runs on the EDT.
 */
public final class RepaintFence implements Disposable {
	private final JComponent target;
	private final AtomicInteger generation = new AtomicInteger();
	private final AtomicInteger ignored = new AtomicInteger();
	private volatile boolean disposed;

	public RepaintFence(JComponent target) { this.target = target; }

	public Runnable callback() {
		int issued = generation.get();
		return () -> {
			if (disposed || issued != generation.get()) { ignored.incrementAndGet(); return; }
			SwingUtilities.invokeLater(() -> {
				if (disposed || issued != generation.get()) { ignored.incrementAndGet(); return; }
				target.repaint();
			});
		};
	}

	public void invalidate() { generation.incrementAndGet(); }

	@Override
	public void dispose() { disposed = true; generation.incrementAndGet(); }

	public boolean isDisposed() { return disposed; }

	/** Callbacks dropped because they were stale or arrived after disposal (for tests and evidence). */
	public int ignoredCallbacks() { return ignored.get(); }
}
