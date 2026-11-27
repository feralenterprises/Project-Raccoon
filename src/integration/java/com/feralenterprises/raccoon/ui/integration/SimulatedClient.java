package com.feralenterprises.raccoon.ui.integration;

import com.feralenterprises.raccoon.core.account.AccountLifecycle;
import com.feralenterprises.raccoon.core.account.ClientPhase;
import com.feralenterprises.raccoon.core.account.IdentityReading;
import com.feralenterprises.raccoon.core.account.Membership;
import com.feralenterprises.raccoon.core.account.WorldContext;

/**
 * Drives the real FR-002 {@link AccountLifecycle} with a synthetic monotonic clock, as the foundation's
 * LifecycleHarness does. Identity values are synthetic numbers, not real account hashes. Call from one thread
 * (the simulated client thread).
 */
public final class SimulatedClient {
	public static final long FIXTURE_A = 1001L, FIXTURE_B = 2002L;
	private static final WorldContext WORLD = WorldContext.supported(Membership.UNKNOWN);
	private final AccountLifecycle lifecycle = new AccountLifecycle();
	private long now = 1_000_000_000L;
	private long identity = FIXTURE_A;

	public AccountLifecycle lifecycle() { return lifecycle; }

	private void advance() { now += 600_000_000L; }

	public void start() { lifecycle.start(now); phase(ClientPhase.LOGIN_SCREEN); }
	public void phase(ClientPhase p) { lifecycle.onClientPhase(p, now); }
	public void tick() { advance(); lifecycle.onGameTick(IdentityReading.of(identity), WORLD, now); }

	public void login(long id) {
		identity = id;
		phase(ClientPhase.LOGGING_IN);
		phase(ClientPhase.LOGGED_IN);
		tick();
	}

	public void regionChange() { phase(ClientPhase.LOADING); phase(ClientPhase.LOGGED_IN); tick(); }
	public void hop() { phase(ClientPhase.HOPPING); phase(ClientPhase.LOADING); phase(ClientPhase.LOGGED_IN); tick(); }
	public void connectionLost() { phase(ClientPhase.CONNECTION_LOST); }
	public void reconnect() { phase(ClientPhase.LOGGED_IN); tick(); }
	public void logout() { phase(ClientPhase.LOGIN_SCREEN); }
}
