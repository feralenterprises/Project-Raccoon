package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One immutable container observation: state, time and item quantities by exact item id. */
public final class ContainerObservation {
	private final Container container;
	private final ContainerState state;
	private final Instant observedAt;
	private final String source;
	private final Map<Integer, Long> items;

	public ContainerObservation(Container container, ContainerState state, Instant observedAt, String source, Map<Integer, Long> items) {
		this.container = Objects.requireNonNull(container);
		this.state = Objects.requireNonNull(state);
		if (state == ContainerState.UNAVAILABLE) {
			if (!items.isEmpty()) throw new IllegalArgumentException("an unavailable container holds no items");
		} else {
			Objects.requireNonNull(observedAt, "observation time");
			Objects.requireNonNull(source, "source");
		}
		Map<Integer, Long> copy = new LinkedHashMap<>();
		items.forEach((id, n) -> {
			if (n == null || n <= 0) throw new IllegalArgumentException("item " + id + " needs a positive quantity");
			copy.put(id, n);
		});
		this.observedAt = observedAt;
		this.source = source;
		this.items = Collections.unmodifiableMap(copy);
	}

	public static ContainerObservation unavailable(Container c) {
		return new ContainerObservation(c, ContainerState.UNAVAILABLE, null, null, Collections.emptyMap());
	}

	public Container container() { return container; }
	public ContainerState state() { return state; }
	public Instant observedAt() { return observedAt; }
	public String source() { return source; }
	/** Observed item quantities (positive) in observation order; empty for verified empty or unavailable. */
	public Map<Integer, Long> items() { return items; }
	/** True for a CURRENT observation with no items: reads as zero and no rows, never as unknown. */
	public boolean verifiedEmpty() { return state == ContainerState.CURRENT && items.isEmpty(); }

	/** The quantity of one item, with the state's meaning applied (see {@link ContainerState}). */
	public Evidence<Long> quantity(int itemId) {
		Long n = items.get(itemId);
		switch (state) {
			case CURRENT: return n != null ? Evidence.quantity(n, observedAt, source) : Evidence.absent(observedAt, source);
			case HISTORICAL: return n != null ? Evidence.stale(n, observedAt, source) : Evidence.unknown();
			case INCOMPLETE: return n != null ? Evidence.quantity(n, observedAt, source) : Evidence.unknown();
			default: return Evidence.unavailable();
		}
	}
}
