package com.feralenterprises.raccoon.core.account;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The single owner of account identity, login token, observation epoch and account-scoped data
 * (SR-1). Pure Java: driven by an adapter (RuneLite) or by the simulator through the same inputs.
 *
 * <p>Publication rule: only {@code ACTIVE} with a <em>fresh</em> verified identity publishes.
 * Freshness is lost on every client phase change and regained only by a {@code LOGGED_IN} game
 * tick that verifies the same account hash on a supported world.
 *
 * <p>Inputs ({@link #start}, {@link #stop}, {@link #onClientPhase}, {@link #onGameTick},
 * {@link #poll}) are expected on the client thread; they are synchronized so a stop racing a tick
 * stays consistent. Snapshots are immutable and may be read from any thread. Listener delivery is
 * serialized: every listener sees snapshots in strictly increasing revision order, never
 * concurrently, and never after its subscription is cancelled. Inputs made from inside a listener
 * are delivered after the current snapshot has reached every listener.
 *
 * <p>Account hashes are held privately to compare identities and are never exposed.
 */
public final class AccountLifecycle implements AccountLifecycleView, AccountDataRegistry
{
	private final Object lock = new Object();
	private final Object deliveryLock = new Object();
	private final CopyOnWriteArrayList<Registration> registrations = new CopyOnWriteArrayList<>();
	private final Map<String, Slot<?>> loginSlots = new LinkedHashMap<>();
	private final Map<String, Slot<?>> epochSlots = new LinkedHashMap<>();

	/** Per-run opaque ordinals; cleared on stop. */
	private final Map<Long, Integer> accountIds = new HashMap<>();

	private volatile AccountSnapshot snapshot =
		new AccountSnapshot(0, AccountState.UNAUTHENTICATED, false, null, Membership.UNKNOWN, RetireReason.NONE);

	// guarded by lock
	private boolean running;
	private AccountState state = AccountState.UNAUTHENTICATED;
	private ClientPhase phase = ClientPhase.UNKNOWN;
	private boolean identityFresh;
	private long sessionHash;
	private int sessionAccountId;
	private long sessionLoginToken;
	private AccountScope currentScope;
	private Membership membership = Membership.UNKNOWN;
	private RetireReason lastRetireReason = RetireReason.NONE;
	private long suspendedAtNanos;
	private int authenticationTicks;
	private long revision;
	private long epochCounter;
	private long loginCounter;
	private final Queue<AccountSnapshot> pending = new ArrayDeque<>();

	// guarded by deliveryLock
	private boolean delivering;

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
		deliver();
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
		deliver();
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
					// UNAUTHENTICATED / RETIRED: activation needs a fresh identity on a LOGGED_IN tick
					break;
			}
		}
		deliver();
	}

	/**
	 * One game tick, with a fresh identity read and the current world policy. Every tick is
	 * checked against the current login whatever the phase; only ticks in {@code LOGGED_IN} can
	 * activate, restore, or re-establish freshness.
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
			switch (state)
			{
				case ACTIVE:
					tickWhileActive(identity, world);
					break;
				case SUSPENDED_CONTINUITY:
					tickWhileSuspended(identity, world);
					break;
				default:
					if (phase == ClientPhase.LOGGED_IN && identity.isVerified() && world.isSupported())
					{
						activateNewLogin(identity, world);
					}
					break;
			}
		}
		deliver();
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
		deliver();
	}

	// ------------------------------------------------------------------ transitions

	private void onPhaseWhileActive(ClientPhase p, long nowNanos)
	{
		switch (p)
		{
			case LOGGED_IN:
			case LOADING:
				// Same login and epoch, but identity must be re-verified on a LOGGED_IN tick before
				// anything is published again. LOADING never establishes hop continuity.
				loseFreshness();
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
				// LOGGING_IN without a witnessed hop/connection loss, STARTING, UNKNOWN
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
			case LOGGED_IN:
				// still inside the witnessed transition; limits keep running, never extended
				break;
			case LOGGING_IN:
				// RuneLite 1.13.8: "a player logging in"; the client resets session state here
				// (see docs/account-lifecycle.md). Continuity cannot be established: retire.
				retire(RetireReason.CONTINUITY_INTERRUPTED);
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
			if (phase == ClientPhase.LOGGED_IN)
			{
				activateNewLogin(identity, world);
			}
			return;
		}
		if (phase != ClientPhase.LOGGED_IN)
		{
			return;
		}
		if (!identityFresh || membership != world.membership())
		{
			identityFresh = true;
			membership = world.membership();
			publish();
		}
	}

	private void tickWhileSuspended(IdentityReading identity, WorldContext world)
	{
		if (!world.isSupported())
		{
			// whatever the identity reading: never keep or later restore this login
			retire(RetireReason.UNSUPPORTED_WORLD);
			return;
		}
		if (phase != ClientPhase.LOGGED_IN)
		{
			return;
		}
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
			activateNewLogin(identity, world);
			return;
		}
		// same hash, uninterrupted suspension, supported world, within limits: keep the login
		openEpoch(world);
	}

	private void expireIfDue(long nowNanos)
	{
		if (state != AccountState.SUSPENDED_CONTINUITY)
		{
			return;
		}
		long elapsed = nowNanos - suspendedAtNanos;
		// a clock that runs backwards cannot prove the limit has not passed
		if (elapsed < 0 || elapsed >= ContinuityLimits.SUSPENSION_LIMIT_NANOS)
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

	private void openEpoch(WorldContext world)
	{
		clear(epochSlots);
		currentScope = new AccountScope(sessionAccountId, sessionLoginToken, ++epochCounter);
		membership = world.membership();
		state = AccountState.ACTIVE;
		identityFresh = true;
		authenticationTicks = 0;
		publish();
	}

	private void loseFreshness()
	{
		if (identityFresh)
		{
			identityFresh = false;
			membership = Membership.UNKNOWN;
			publish();
		}
	}

	private void suspend(long nowNanos)
	{
		currentScope = null;
		identityFresh = false;
		clear(epochSlots);
		membership = Membership.UNKNOWN;
		state = AccountState.SUSPENDED_CONTINUITY;
		suspendedAtNanos = nowNanos;
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
		identityFresh = false;
		sessionHash = 0;
		sessionAccountId = 0;
		sessionLoginToken = 0;
		currentScope = null;
		membership = Membership.UNKNOWN;
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

	/** Caller holds {@code lock}. */
	private boolean publishableLocked()
	{
		return running && state == AccountState.ACTIVE && identityFresh && currentScope != null;
	}

	// ------------------------------------------------------------------ publication

	private void publish()
	{
		AccountSnapshot s = new AccountSnapshot(++revision, state, identityFresh, currentScope, membership, lastRetireReason);
		snapshot = s;
		pending.add(s);
	}

	/**
	 * Delivers queued snapshots. One deliverer at a time (other threads wait); a re-entrant call
	 * from a listener returns at once and its snapshots are delivered by the outer loop, after the
	 * current snapshot reached every listener.
	 */
	private void deliver()
	{
		synchronized (deliveryLock)
		{
			if (delivering)
			{
				return;
			}
			delivering = true;
			RuntimeException first = null;
			try
			{
				while (true)
				{
					AccountSnapshot s;
					synchronized (lock)
					{
						s = pending.poll();
					}
					if (s == null)
					{
						break;
					}
					for (Registration r : registrations)
					{
						try
						{
							r.offer(s);
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
			}
			finally
			{
				delivering = false;
			}
			if (first != null)
			{
				throw first;
			}
		}
	}

	private final class Registration implements Subscription
	{
		private final AccountLifecycleListener listener;
		private volatile boolean cancelled;
		private long lastRevision = -1;

		Registration(AccountLifecycleListener listener)
		{
			this.listener = listener;
		}

		/** Called with deliveryLock held. */
		void offer(AccountSnapshot s)
		{
			if (cancelled || s.revision() <= lastRevision)
			{
				return;
			}
			lastRevision = s.revision();
			listener.onAccountSnapshot(s);
		}

		@Override
		public void cancel()
		{
			cancelled = true;
			registrations.remove(this);
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
		Registration r = new Registration(listener);
		synchronized (deliveryLock)
		{
			registrations.add(r);
			if (!delivering)
			{
				// deliver what is queued first, so the new listener starts from the latest state
				deliver();
			}
			r.offer(snapshot);
		}
		return r;
	}

	@Override
	public boolean isCurrent(AccountScope scope)
	{
		synchronized (lock)
		{
			return scope != null && publishableLocked() && scope.equals(currentScope);
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

	/** Last client phase seen by the lifecycle (adapter use: phase reconciliation). */
	public ClientPhase phase()
	{
		synchronized (lock)
		{
			return phase;
		}
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
				return publishableLocked() && present ? Optional.ofNullable(value) : Optional.empty();
			}
		}

		@Override
		public Optional<T> get(AccountScope reader)
		{
			synchronized (lock)
			{
				return isCurrent(reader) && present ? Optional.ofNullable(value) : Optional.empty();
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
