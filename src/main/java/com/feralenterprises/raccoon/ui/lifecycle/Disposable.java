package com.feralenterprises.raccoon.ui.lifecycle;

/**
 * Explicit teardown. After {@code dispose()} an object holds no listener registrations, ignores late callbacks and
 * retains no account-derived values. Idempotent. Called on the EDT.
 */
public interface Disposable {
	void dispose();
}
