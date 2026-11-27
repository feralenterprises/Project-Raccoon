package com.feralenterprises.raccoon.ui.shell;

import com.feralenterprises.raccoon.ui.theme.Tokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decides how the shell uses whatever width the host (RuneLite sidebar) gives it. Pending native measurements,
 * nothing here is fixed: rail width, rail mode, scrollbar mode and the minimum content width are all inputs.
 *
 * <p>AUTO preference order: rail + reserved scrollbar + chrome; rail + overlay scrollbar; rail without chrome;
 * then the rail moves into a header menu (reserved, overlay, no chrome). The first arrangement that leaves at least
 * {@code minContent} for content wins; extra width goes to content. Pinned RuneLite source shows a PluginPanel
 * reports 225 (unwrapped) or 242 (wrapped) unless it overrides getPreferredSize, so all three cases are tested.
 */
public final class ShellGeometry {
	public enum RailMode { AUTO, SHOWN, HIDDEN }
	public enum ScrollbarMode { AUTO, RESERVED, OVERLAY }

	/** Immutable layout policy. */
	public static final class Spec {
		public final int railWidth, chrome, scrollbar, minContent;
		public final RailMode railMode;
		public final ScrollbarMode scrollbarMode;

		public Spec(int railWidth, RailMode railMode, ScrollbarMode scrollbarMode, int chrome, int scrollbar, int minContent) {
			if (railWidth < 0 || chrome < 0 || scrollbar < 0 || minContent < 1) throw new IllegalArgumentException("negative geometry");
			this.railWidth = railWidth; this.railMode = Objects.requireNonNull(railMode);
			this.scrollbarMode = Objects.requireNonNull(scrollbarMode);
			this.chrome = chrome; this.scrollbar = scrollbar; this.minContent = minContent;
		}

		public static Spec defaults() {
			return new Spec(Tokens.RAIL_WIDTH, RailMode.AUTO, ScrollbarMode.AUTO, Tokens.CHROME, Tokens.SCROLLBAR, Tokens.CONTENT_WIDTH);
		}

		public Spec withRail(int width, RailMode mode) { return new Spec(width, mode, scrollbarMode, chrome, scrollbar, minContent); }
		public Spec withScrollbar(ScrollbarMode mode) { return new Spec(railWidth, railMode, mode, chrome, scrollbar, minContent); }

		/** Width that shows everything (rail, reserved bar, outer chrome) at {@code content} px. */
		public int fullWidth(int content) { return railWidth + chrome + content + scrollbar; }
	}

	/** One resolved arrangement. */
	public static final class Layout {
		public final boolean railVisible, scrollbarReserved;
		/** {@code chrome} is the outer chrome; {@code chromeLeft} is 0 while the rail is shown (no rail/content separator). */
		public final int hostWidth, railWidth, chrome, chromeLeft, scrollbarWidth, contentWidth;

		Layout(int hostWidth, boolean railVisible, int railWidth, int chrome, boolean reserved, int scrollbarWidth) {
			this.hostWidth = hostWidth; this.railVisible = railVisible; this.railWidth = railVisible ? railWidth : 0;
			this.chrome = chrome; this.chromeLeft = railVisible ? 0 : chrome;
			this.scrollbarReserved = reserved; this.scrollbarWidth = scrollbarWidth;
			this.contentWidth = Math.max(1, hostWidth - this.railWidth - chromeLeft - chrome - (reserved ? scrollbarWidth : 0));
		}

		@Override
		public String toString() {
			return "host " + hostWidth + ": rail " + (railVisible ? railWidth : "menu") + ", chrome " + chromeLeft + "/" + chrome + ", content "
				+ contentWidth + ", scrollbar " + (scrollbarReserved ? "reserved " + scrollbarWidth : "overlay");
		}
	}

	private ShellGeometry() { }

	public static Layout compute(Spec s, int hostWidth) {
		List<Layout> candidates = new ArrayList<>();
		boolean[] rails = s.railMode == RailMode.SHOWN ? new boolean[]{true} : s.railMode == RailMode.HIDDEN ? new boolean[]{false} : new boolean[]{true, false};
		for (boolean rail : rails) {
			if (s.scrollbarMode != ScrollbarMode.OVERLAY) candidates.add(new Layout(hostWidth, rail, s.railWidth, s.chrome, true, s.scrollbar));
			if (s.scrollbarMode != ScrollbarMode.RESERVED) {
				candidates.add(new Layout(hostWidth, rail, s.railWidth, s.chrome, false, s.scrollbar));
				candidates.add(new Layout(hostWidth, rail, s.railWidth, 0, false, s.scrollbar));
			} else {
				candidates.add(new Layout(hostWidth, rail, s.railWidth, 0, true, s.scrollbar));
			}
		}
		for (Layout l : candidates) if (l.contentWidth >= s.minContent) return l;
		return candidates.get(candidates.size() - 1);
	}
}
