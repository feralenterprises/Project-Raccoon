package com.feralenterprises.raccoon.core.account;

/** Handle returned by {@link AccountLifecycleView#subscribe}. Cancelling is idempotent. */
public interface Subscription extends AutoCloseable
{
	void cancel();

	@Override
	default void close()
	{
		cancel();
	}
}
