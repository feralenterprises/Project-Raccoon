package com.feralenterprises.raccoon.playtest.r02.host;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureDispatcher;
import com.feralenterprises.raccoon.playtest.r02.shared.Lane;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteRegistration;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the lanes' panels through each lane's single entry class ({@link Lane#entryClass()}):
 * {@code public static List<RouteRegistration> registrations(Path laneInputsZip, Path frozenReview)}. Called off the EDT.
 * A lane whose entry class is absent leaves its routes on the framework placeholder. A lane that is present but
 * malformed, throws, or registers a route it does not own fails the host start, so a broken lane is never silently
 * replaced by a placeholder. Without the private lane inputs no lane is called (all placeholders, recorded).
 */
public final class LaneDiscovery {
	/** SHA-256 of the accepted private Lane Inputs ZIP (Lanes A and C pin the same value). */
	public static final String LANE_INPUTS_SHA256 = "1f6a821d37705b7c7acdca9d598c2c5e7f74f7462536c9bf0f9039086545cece";

	private LaneDiscovery() { }

	/** What was found, before any lane is called. */
	public static final class Inspection {
		public final Map<Lane, Method> entries;
		public final Set<Lane> absent;

		Inspection(Map<Lane, Method> entries, Set<Lane> absent) {
			this.entries = Collections.unmodifiableMap(entries);
			this.absent = Collections.unmodifiableSet(absent);
		}
	}

	/** Finds and checks every lane entry signature without calling it. Throws for a present but malformed entry. */
	public static Inspection inspect(ClassLoader loader) {
		Map<Lane, Method> entries = new EnumMap<>(Lane.class);
		Set<Lane> absent = EnumSet.noneOf(Lane.class);
		for (Lane lane : Lane.values()) {
			Class<?> c;
			try {
				c = Class.forName(lane.entryClass(), false, loader);
			} catch (ClassNotFoundException e) {
				absent.add(lane);
				continue;
			}
			Method m = entry(c, Path.class, Path.class);
			// Compatibility: Lane E's entry also takes the dispatcher (for Settings' appearance, now RouteContext.setAppearance).
			if (m == null) m = entry(c, Path.class, Path.class, FixtureDispatcher.class);
			if (m == null) throw new IllegalStateException(c.getName() + " has no public static registrations(Path laneInputsZip, Path frozenReview)");
			if (!Modifier.isStatic(m.getModifiers()) || !List.class.isAssignableFrom(m.getReturnType()))
				throw new IllegalStateException(c.getName() + ".registrations must be public static and return a List");
			entries.put(lane, m);
		}
		return new Inspection(entries, absent);
	}

	private static Method entry(Class<?> c, Class<?>... params) {
		try {
			return c.getMethod("registrations", params);
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	/**
	 * Calls every present lane entry (off the EDT) and validates the result. {@code laneInputs} null: nothing is called.
	 * @throws IOException when the lane inputs are not the accepted ZIP or a lane cannot read them
	 */
	public static List<RouteRegistration> load(ClassLoader loader, Path laneInputs, Path frozenReview, FixtureDispatcher fixtures) throws IOException {
		return load(loader, laneInputs, frozenReview, fixtures, LANE_INPUTS_SHA256);
	}

	/** As {@link #load(ClassLoader, Path, Path, FixtureDispatcher)} against an explicit accepted digest (tests use stand-in inputs). */
	public static List<RouteRegistration> load(ClassLoader loader, Path laneInputs, Path frozenReview, FixtureDispatcher fixtures,
		String acceptedSha256) throws IOException {
		Inspection found = inspect(loader);
		if (laneInputs == null || found.entries.isEmpty()) return Collections.emptyList();
		if (!Files.isRegularFile(laneInputs)) throw new IOException("lane inputs ZIP not found");
		String d = FrozenPackageAssets.sha256(Files.readAllBytes(laneInputs));
		if (!acceptedSha256.equals(d)) throw new IOException("lane inputs ZIP is not the accepted one (" + d.substring(0, 12) + ")");
		List<RouteRegistration> out = new ArrayList<>();
		Set<RouteId> seen = EnumSet.noneOf(RouteId.class);
		for (Map.Entry<Lane, Method> e : found.entries.entrySet()) {
			Lane lane = e.getKey();
			Object result;
			try {
				Method m = e.getValue();
				result = m.getParameterCount() == 3 ? m.invoke(null, laneInputs, frozenReview, fixtures) : m.invoke(null, laneInputs, frozenReview);
			} catch (InvocationTargetException x) {
				Throwable cause = x.getCause();
				if (cause instanceof IOException) throw new IOException(lane + " lane could not load its inputs: " + cause.getMessage(), cause);
				throw new IllegalStateException(lane + " lane entry failed", cause);
			} catch (IllegalAccessException x) {
				throw new IllegalStateException(lane + " lane entry is not accessible", x);
			}
			if (!(result instanceof List)) throw new IllegalStateException(lane + " lane returned no registration list");
			for (Object o : (List<?>) result) {
				if (!(o instanceof RouteRegistration)) throw new IllegalStateException(lane + " lane returned a non-registration");
				RouteRegistration r = (RouteRegistration) o;
				if (!lane.routes().contains(r.route())) throw new IllegalStateException(lane + " lane registered " + r.route().id() + ", which it does not own");
				if (!lane.name().equals(r.owner())) throw new IllegalStateException(lane + " lane registered " + r.route().id() + " under owner " + r.owner());
				if (!seen.add(r.route())) throw new IllegalStateException(r.route().id() + " registered twice");
				out.add(r);
			}
		}
		return Collections.unmodifiableList(out);
	}
}
