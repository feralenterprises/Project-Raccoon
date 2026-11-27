package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.util.Objects;

/**
 * The shared appearance model (frozen R02 {@code settings} defaults: size, accent, weight, surface). Preferences are
 * not account observations: a preference reset never touches observations, and the reverse.
 */
public final class Appearance {
	public enum Size { DEFAULT, LARGE }
	public enum Accent { BRONZE, CRIMSON, VERDIGRIS, SAPPHIRE }
	public enum Weight { REGULAR, BOLD }
	public enum Surface { STONE, TIMBER, CANVAS }

	public static final Appearance DEFAULTS = new Appearance(Size.DEFAULT, Accent.BRONZE, Weight.REGULAR, Surface.STONE);

	private final Size size;
	private final Accent accent;
	private final Weight weight;
	private final Surface surface;

	public Appearance(Size size, Accent accent, Weight weight, Surface surface) {
		this.size = Objects.requireNonNull(size);
		this.accent = Objects.requireNonNull(accent);
		this.weight = Objects.requireNonNull(weight);
		this.surface = Objects.requireNonNull(surface);
	}

	public Size size() { return size; }
	public Accent accent() { return accent; }
	public Weight weight() { return weight; }
	public Surface surface() { return surface; }
	public Appearance withSize(Size s) { return new Appearance(s, accent, weight, surface); }
	public Appearance withAccent(Accent a) { return new Appearance(size, a, weight, surface); }
	public Appearance withWeight(Weight w) { return new Appearance(size, accent, w, surface); }
	public Appearance withSurface(Surface s) { return new Appearance(size, accent, weight, s); }

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Appearance)) return false;
		Appearance a = (Appearance) o;
		return size == a.size && accent == a.accent && weight == a.weight && surface == a.surface;
	}

	@Override
	public int hashCode() { return Objects.hash(size, accent, weight, surface); }
}
