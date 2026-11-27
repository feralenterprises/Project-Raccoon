package com.feralenterprises.raccoon.ui.shell;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Navigation state: current destination and Back history. UI-only; contains no feature logic. */
public final class Navigator {
	public static final int HISTORY_LIMIT = 50;

	private final Map<String, Destination> destinations = new LinkedHashMap<>();
	private final Deque<RouteRequest> history = new ArrayDeque<>();
	private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
	private RouteRequest current;
	private boolean returning;

	public Navigator(List<Destination> all) {
		for (Destination d : all) {
			if (destinations.put(d.id(), d) != null) throw new IllegalArgumentException("duplicate destination " + d.id());
		}
	}

	public List<Destination> destinations() { return Collections.unmodifiableList(new ArrayList<>(destinations.values())); }
	public Destination destination(String id) { return destinations.get(id); }
	public String current() { return current == null ? null : current.route(); }
	/** The full current request, including any typed payload; null before the first navigation. */
	public RouteRequest currentRequest() { return current; }
	/** True while listeners are being told about a Back step (and until the next navigation). */
	public boolean returning() { return returning; }
	public boolean canGoBack() { return !history.isEmpty(); }
	public int historyDepth() { return history.size(); }
	public void addListener(Consumer<String> l) { listeners.add(l); }
	public void removeListener(Consumer<String> l) { listeners.remove(l); }
	public int listenerCount() { return listeners.size(); }

	/** Bare navigation, as from the rail: re-selecting the current route is a no-op and adds no history. */
	public boolean navigate(String id) {
		if (current != null && id.equals(current.route())) {
			if (destinations.get(id) == null) throw new IllegalArgumentException("unknown destination " + id);
			return false;
		}
		return navigate(RouteRequest.of(id));
	}

	/**
	 * Typed navigation. A request equal to the current one does nothing; the same route with another payload is a new
	 * history entry, so Back returns to the previous payload's page state.
	 */
	public boolean navigate(RouteRequest request) {
		String id = request.route();
		Destination d = destinations.get(id);
		if (d == null) throw new IllegalArgumentException("unknown destination " + id);
		if (d.deferred()) return false;
		if (request.equals(current)) return false;
		if (current != null) {
			history.push(current);
			while (history.size() > HISTORY_LIMIT) history.removeLast();
		}
		current = request;
		returning = false;
		listeners.forEach(l -> l.accept(id));
		return true;
	}

	public boolean back() {
		if (history.isEmpty()) return false;
		current = history.pop();
		returning = true;
		String c = current.route();
		listeners.forEach(l -> l.accept(c));
		return true;
	}

	/**
	 * Drops all history and shows {@code start} (R02: a scope change returns to Home with an empty history, so Back
	 * can never reach a page or payload of the previous account). Listeners are notified when the route changes.
	 */
	public void resetHistory(RouteRequest start) {
		Destination d = destinations.get(start.route());
		if (d == null) throw new IllegalArgumentException("unknown destination " + start.route());
		history.clear();
		boolean changed = !start.equals(current);
		current = start;
		returning = false;
		if (changed) listeners.forEach(l -> l.accept(start.route()));
	}
}
