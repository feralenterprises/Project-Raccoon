package com.feralenterprises.raccoon.core.account;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The single owner of account identity, login token, observation epoch and account-scoped data
 * (SR-1). Pure Java: driven by an adapter (RuneLite) or by the simulator through the same inputs.
 *
 * <p>Inputs ({@link #start}, {@link #stop}, {@link #onClientPhase}, {@link #onGameTick},
 * {@link #poll}) are expected on one thread (the client thread); they are synchronized so a stop
 * racing a tick stays consistent. Snapshots are immutable and may be read from any thread.
 * Listeners are notified after the lock is released, in publication order.
 *
 * <p>Account hashes are held privately to compare identities and are never exposed.
 */
public final class AccountLifecycle implements AccountLifecycleView, AccountDataRegistry
{
	private final Object lock = new Object();
	private final List<AccountLifecycleListener> listeners = new CopyOnWriteArrayList<>();
	private final Map<String, Slot<?>> loginSlots = new LinkedHashMap<>();
	private final Map<String, Slot<?>> epochSlots = new LinkedHashMap<>();

	/** Per-run opaque ordinals; cleared on stop. */
	private final Map<Long, Integer> accountIds = new HashMap<>();

	private volatile AccountSnapshot snapshot =
		new AccountSnapshot(0, AccountState.UNAUTHENTICATED, null, Membership.UNKNOWN, RetireReason.NONE);

	// guarded by lock
	private boolean running;
	private AccountState state = AccountState.UNAUTHENTICATED;
	private ClientPhase phase = ClientPhase.UNKNOWN;
	private long sessionHash;
	private int sessionAccountId;
	private long sessionLoginToken;
	private AccountScope currentScope;
	private Membership membership = Membership.UNKNOWN;
	private RetireReason lastRetireReason = RetireReason.NONE;
	private long suspendedAtNanos;
	private boolean awaitingAuthentication;
	private int authenticationTicks;
	private long revision;
	private long epochCounter;
	private long loginCounter;
	private final List<AccountSnapshot> pending = new ArrayList<>();

	// ------------------------------------------------------------------ inputs

	/** Plugin enabled. Nothing is known: identity must be read fresh (A02). */
	public void start(long nowNanos)
	{
		synchronized (lock)
		{
			running = true;
			phase = ClientPhase.UNKNOWN;
			clearSession();
			state = AccountState.UNAUTHENTICATED;
			lastRetireReason = RetireReason.NONE;
			publish();
		}
		flush();
	}

	/** Plugin disabled: retire, clear everything, fence all outstanding work. */
	public void stop()
	{
		synchronized (lock)
		{
			if (!running)
			{
				return;
			}
			retire(RetireReason.PLUGIN_STOPPED);
			running = false;
			accountIds.clear();
		}
		flush();
	}

	public void onClientPhase(ClientPhase newPhase, long nowNanos)
	{
		Objects.requireNonNull(newPhase);
		synchronized (lock)
		{
			if (!running)
			{
				return;
			}
			expireIfDue(nowNanos);
			phase = newPhase;
			switch (state)
			{
				case ACTIVE:
					onPhaseWhileActive(newPhase, nowNanos);
					break;
				case SUSPENDED_CONTINUITY:
					onPhaseWhileSuspended(newPhase);
					break;
				default:
					// UNAUTHENTICATED / RETIRED: activation needs a fresh identity on a game tick
					break;
			}
		}
		flush();
	}

	/**
	 * One game tick while logged in, with a fresh identity read and the current world policy.
	 * Ticks outside LOGGED_IN are ignored.
	 */
	public void onGameTick(IdentityReading identity, WorldContext world, long nowNanos)
	{
		Objects.requireNonNull(identity);
		Objects.requireNonNull(world);
		synchronized (lock)
		{
			if (!running)
			{
				return;
			}
			expireIfDue(nowNanos);
			if (phase != ClientPhase.LOGGED_IN)
			{
				return;
			}
			switch (state)
			{
				case ACTIVE:
					tickWhileActive(identity, world);
					break;
				case SUSPENDED_CONTINUITY:
					tickWhileSuspended(identity, world);
					break;
				default:
					if (identity.isVerified() && world.isSupported())
					{
						activateNewLogin(identity, world);
					}
					break;
			}
		}
		flush();
	}

	/** Lets time-based limits expire promptly without other events. */
	public void poll(long nowNanos)
	{
		synchronized (lock)
		{
			if (running)
			{
				expireIfDue(nowNanos);
			}
		}
		flush();
	}

	// ------------------------------------------------------------------ transitions

	private void onPhaseWhileActive(ClientPhase p, long nowNanos)
	{
		switch (p)
		{
			case LOGGED_IN:
			case LOADING:
				// LOADING alone is not a hop; identity keeps being verified every tick
				break;
			case HOPPING:
			case CONNECTION_LOST:
				suspend(nowNanos);
				break;
			case LOGIN_SCREEN:
				retire(RetireReason.LOGOUT);
				break;
			case LOGIN_SCREEN_AUTHENTICATOR:
				retire(RetireReason.AUTHENTICATOR);
				break;
			default:
				retire(RetireReason.UNRECOGNIZED_CONTINUITY);
				break;
		}
	}

	private void onPhaseWhileSuspended(ClientPhase p)
	{
		switch (p)
		{
			case HOPPING:
			case CONNECTION_LOST:
			case LOADING:
			case LOGGING_IN:
				// still inside the witnessed transition; limits keep running, never extended
				break;
			case LOGGED_IN:
				awaitingAuthentication = true;
				break;
			case LOGIN_SCREEN:
				retire(RetireReason.LOGOUT);
				break;
			case LOGIN_SCREEN_AUTHENTICATOR:
				retire(RetireReason.AUTHENTICATOR);
				break;
			default:
				retire(RetireReason.UNRECOGNIZED_CONTINUITY);
				break;
		}
	}

	private void tickWhileActive(IdentityReading identity, WorldContext world)
	{
		if (!world.isSupported())
		{
			retire(RetireReason.UNSUPPORTED_WORLD);
			return;
		}
		if (!identity.isVerified())
		{
			// outside a witnessed transition, identity loss retires the active identity
			retire(RetireReason.IDENTITY_LOST);
			return;
		}
		if (identity.hash() != sessionHash)
		{
			// missed logout: never let the prior account's view survive
			retire(RetireReason.DIFFERENT_ACCOUNT);
			activateNewLogin(identity, world);
			return;
		}
		if (membership != world.membership())
		{
			membership = world.membership();
			publish();
		}
	}

	private void tickWhileSuspended(IdentityReading identity, WorldContext world)
	{
		awaitingAuthentication = true;
		authenticationTicks++;
		if (!identity.isVerified())
		{
			if (authenticationTicks >= ContinuityLimits.AUTHENTICATION_TICK_LIMIT)
			{
				retire(RetireReason.AUTHENTICATION_TIMEOUT);
			}
			return;
		}
		if (identity.hash() != sessionHash)
		{
			retire(RetireReason.DIFFERENT_ACCOUNT);
			if (world.isSupported())
			{
				activateNewLogin(identity, world);
			}
			return;
		}
		if (!world.isSupported())
		{
			retire(RetireReason.UNSUPPORTED_WORLD);
			return;
		}
		restoreLogin(world);
	}

	private void expireIfDue(long nowNanos)
	{
		if (state == AccountState.SUSPENDED_CONTINUITY
			&& nowNanos - suspendedAtNanos >= ContinuityLimits.SUSPENSION_LIMIT_NANOS)
		{
			retire(RetireReason.CONTINUITY_TIMEOUT);
		}
	}

	private void activateNewLogin(IdentityReading identity, WorldContext world)
	{
		clearSession();
		sessionHash = identity.hash();
		Integer id = accountIds.get(sessionHash);
		if (id == null)
		{
			id = accountIds.size() + 1;
			accountIds.put(sessionHash, id);
		}
		sessionAccountId = id;
		sessionLoginToken = ++loginCounter;
		openEpoch(world);
	}

	private void restoreLogin(WorldContext world)
	{
		// same hash, uninterrupted suspension, supported world: keep the login, new epoch
		openEpoch(world);
	}

	private void openEpoch(WorldContext world)
	{
		clear(epochSlots);
		currentScope = new AccountScope(sessionAccountId, sessionLoginToken, ++epochCounter);
		membership = world.membership();
		state = AccountState.ACTIVE;
		awaitingAuthentication = false;
		authenticationTicks = 0;
		publish();
	}

	private void suspend(long nowNanos)
	{
		currentScope = null;
		clear(epochSlots);
		membership = Membership.UNKNOWN;
		state = AccountState.SUSPENDED_CONTINUITY;
		suspendedAtNanos = nowNanos;
		awaitingAuthentication = false;
		authenticationTicks = 0;
		publish();
	}

	private void retire(RetireReason reason)
	{
		clearSession();
		state = AccountState.RETIRED;
		lastRetireReason = reason;
		publish();
	}

	private void clearSession()
	{
		sessionHash = 0;
		sessionAccountId = 0;
		sessionLoginToken = 0;
		currentScope = null;
		membership = Membership.UNKNOWN;
		awaitingAuthentication = false;
		authenticationTicks = 0;
		clear(loginSlots);
		clear(epochSlots);
	}

	private static void clear(Map<String, Slot<?>> slots)
	{
		for (Slot<?> s : slots.values())
		{
			s.clear();
		}
	}

	// ------------------------------------------------------------------ publication

	private void publish()
	{
		AccountSnapshot s = new AccountSnapshot(++revision, state, currentScope, membership, lastRetireReason);
		snapshot = s;
		pending.add(s);
	}

	private void flush()
	{
		List<AccountSnapshot> out;
		synchronized (lock)
		{
			if (pending.isEmpty())
			{
				return;
			}
			out = new ArrayList<>(pending);
			pending.clear();
		}
		RuntimeException first = null;
		for (AccountSnapshot s : out)
		{
			for (AccountLifecycleListener l : listeners)
			{
				try
				{
					l.onAccountSnapshot(s);
				}
				catch (RuntimeException e)
				{
					if (first == null)
					{
						first = e;
					}
				}
			}
		}
		if (first != null)
		{
			throw first;
		}
	}

	// ------------------------------------------------------------------ view

	@Override
	public AccountSnapshot snapshot()
	{
		return snapshot;
	}

	@Override
	public Subscription subscribe(AccountLifecycleListener listener)
	{
		Objects.requireNonNull(listener);
		listeners.add(listener);
		listener.onAccountSnapshot(snapshot);
		return () -> listeners.remove(listener);
	}

	@Override
	public boolean isCurrent(AccountScope scope)
	{
		synchronized (lock)
		{
			return running && state == AccountState.ACTIVE && scope != null && scope.equals(currentScope);
		}
	}

	@Override
	public Runnable fenced(AccountScope scope, Runnable work)
	{
		Objects.requireNonNull(work);
		return () ->
		{
			if (isCurrent(scope))
			{
				work.run();
			}
		};
	}

	/** Current state, for diagnostics; same as {@code snapshot().state()}. */
	public AccountState state()
	{
		return snapshot.state();
	}

	// ------------------------------------------------------------------ data

	@Override
	public <T> AccountSlot<T> loginSlot(String name)
	{
		return register(loginSlots, name);
	}

	@Override
	public <T> AccountSlot<T> epochSlot(String name)
	{
		return register(epochSlots, name);
	}

	private <T> AccountSlot<T> register(Map<String, Slot<?>> slots, String name)
	{
		Objects.requireNonNull(name);
		synchronized (lock)
		{
			if (loginSlots.containsKey(name) || epochSlots.containsKey(name))
			{
				throw new IllegalArgumentException("account slot already registered: " + name);
			}
			Slot<T> s = new Slot<>(name);
			slots.put(name, s);
			return s;
		}
	}

	private final class Slot<T> implements AccountSlot<T>
	{
		private final String name;
		private T value;
		private boolean present;

		Slot(String name)
		{
			this.name = name;
		}

		@Override
		public String name()
		{
			return name;
		}

		@Override
		public Optional<T> get()
		{
			synchronized (lock)
			{
				if (!running || state != AccountState.ACTIVE || !present)
				{
					return Optional.empty();
				}
				return Optional.ofNullable(value);
			}
		}

		@Override
		public boolean set(AccountScope writer, T v)
		{
			synchronized (lock)
			{
				if (!isCurrent(writer))
				{
					return false;
				}
				value = v;
				present = true;
				return true;
			}
		}

		void clear()
		{
			value = null;
			present = false;
		}

		@Override
		public String toString()
		{
			return "AccountSlot[" + name + "]";
		}
	}
}
