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
	private final ContainerObservation retained;

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
		this.retained = null;
	}

	private ContainerObservation(ContainerObservation incomplete, ContainerObservation retained) {
		this.container = incomplete.container;
		this.state = incomplete.state;
		this.observedAt = incomplete.observedAt;
		this.source = incomplete.source;
		this.items = incomplete.items;
		this.retained = retained;
	}

	/**
	 * Lane C (frozen {@code incompleteRetained}): an INCOMPLETE observation may carry the earlier complete capture it
	 * did not replace. The retained capture must be HISTORICAL (shown as saved, never as current), of the same container
	 * and not newer. {@link #quantity} is unchanged: the incomplete observation still claims nothing it did not see.
	 */
	public ContainerObservation withRetained(ContainerObservation earlier) {
		if (state != ContainerState.INCOMPLETE) throw new IllegalArgumentException("only an incomplete observation retains an earlier capture");
		Objects.requireNonNull(earlier);
		if (earlier.state != ContainerState.HISTORICAL) throw new IllegalArgumentException("the retained capture must be historical");
		if (earlier.container != container) throw new IllegalArgumentException("the retained capture must be the same container");
		if (earlier.observedAt.isAfter(observedAt)) throw new IllegalArgumentException("the retained capture cannot be newer");
		if (earlier.retained != null) throw new IllegalArgumentException("a retained capture retains nothing");
		return new ContainerObservation(this, earlier);
	}

	/** The earlier complete capture an INCOMPLETE observation retains (HISTORICAL), or null. */
	public ContainerObservation retained() { return retained; }

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
