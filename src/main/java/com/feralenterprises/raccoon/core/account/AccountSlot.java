package com.feralenterprises.raccoon.core.account;

import java.util.Optional;

/**
 * Account data held centrally by the lifecycle. Reads are masked unless ACTIVE; writes are
 * accepted only from the current scope. Use on the client thread.
 */
public interface AccountSlot<T>
{
	String name();

	/** Value for the current scope, or empty when masked, retired, or not yet written this scope. */
	Optional<T> get();

	/** Stores {@code value} if {@code writer} is current; returns false (and drops it) otherwise. */
	boolean set(AccountScope writer, T value);
}
