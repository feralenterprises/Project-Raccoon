package com.feralenterprises.raccoon.ui.model;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Panel-local UI state (selected tab, expanded sections) keyed by an opaque account scope. When the scope changes
 * and the continuity predicate says the data does not carry over, the state is discarded (FR-002 ui-integration
 * rule 2). The integration layer passes {@code AccountScope::sameEpoch} so a region change (new scope, same epoch)
 * keeps UI state while a hop, reconnect or new login resets it. The scope type is Object, so this framework has no
 * compile dependency on the foundation.
 */
public final class ScopedState<T> {
	private final Supplier<T> initial;
	private final BiPredicate<Object, Object> continuity;
	private Object scope;
	private T value;

	/** Discards on any change of scope identity. */
	public ScopedState(Supplier<T> initial) { this(initial, Objects::equals); }

	public ScopedState(Supplier<T> initial, BiPredicate<Object, Object> continuity) {
		this.initial = Objects.requireNonNull(initial);
		this.continuity = Objects.requireNonNull(continuity);
	}

	public T get(Object currentScope) {
		if (value == null || scope == null || currentScope == null || !continuity.test(scope, currentScope)) value = initial.get();
		scope = currentScope;
		return value;
	}

	public void set(Object currentScope, T v) { scope = currentScope; value = v; }

	public void clear() { scope = null; value = null; }
}
