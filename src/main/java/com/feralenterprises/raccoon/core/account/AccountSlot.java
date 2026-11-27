package com.feralenterprises.raccoon.core.account;

import java.util.Optional;

/**
 * Account data held centrally by the lifecycle. Reads are masked unless publishable; writes are
 * accepted only from the current scope. Each call is atomic with respect to lifecycle changes.
 */
public interface AccountSlot<T>
{
	String name();

	/** Value for the current scope, or empty when masked, retired, or not yet written this scope. */
	Optional<T> get();

	/**
	 * Value only if {@code reader} is still the current scope; empty otherwise. Use this when the
	 * value must belong to a scope the caller already holds (for example a snapshot's scope).
	 */
	Optional<T> get(AccountScope reader);

	/** Stores {@code value} if {@code writer} is current; returns false (and drops it) otherwise. */
	boolean set(AccountScope writer, T value);
}
