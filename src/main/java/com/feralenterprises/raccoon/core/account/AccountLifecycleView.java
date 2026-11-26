package com.feralenterprises.raccoon.core.account;

/**
 * Read side of the single account lifecycle. Panels, the simulator and observers depend on this
 * interface; none of them can drive transitions.
 */
public interface AccountLifecycleView
{
	/** Latest snapshot; never null; safe from any thread. */
	AccountSnapshot snapshot();

	/** Listener receives the current snapshot immediately, then every change. */
	Subscription subscribe(AccountLifecycleListener listener);

	/** True only while ACTIVE and {@code scope} is the current epoch's scope. */
	boolean isCurrent(AccountScope scope);

	/**
	 * Wraps work produced under {@code scope}. When run later, it executes only if the scope is
	 * still current; delayed callbacks after stop, account switch or epoch change are discarded.
	 */
	Runnable fenced(AccountScope scope, Runnable work);

	/**
	 * True only if {@code scope} is current and {@code reading} is a verified identity of that
	 * scope's account. Lets observers check identity read during a capture against the central
	 * authority; the account hash itself is never exposed.
	 */
	boolean confirms(AccountScope scope, IdentityReading reading);
}
