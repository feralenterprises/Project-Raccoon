package com.feralenterprises.raccoon.ui.lifecycle;

/**
 * A component that can display account-derived values. {@link #clearAccountData()} replaces every held value with
 * an UNAVAILABLE model so no account data stays referenced (FR-002 ui-integration rule 1). Called on the EDT.
 */
public interface AccountBound {
	void clearAccountData();
}
