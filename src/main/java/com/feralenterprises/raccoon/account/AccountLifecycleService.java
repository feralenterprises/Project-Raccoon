package com.feralenterprises.raccoon.account;

import com.feralenterprises.raccoon.core.account.AccountDataRegistry;
import com.feralenterprises.raccoon.core.account.AccountLifecycle;
import com.feralenterprises.raccoon.core.account.AccountLifecycleView;
import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.AccountState;
import com.feralenterprises.raccoon.core.account.ClientPhase;
import com.feralenterprises.raccoon.core.account.IdentityReading;
import com.feralenterprises.raccoon.core.account.WorldContext;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.WorldType;

/**
 * RuneLite adapter for the single account lifecycle. It only translates client facts into
 * lifecycle inputs; every decision is made by {@link AccountLifecycle}. Future features inject
 * this service and use {@link #view()} and {@link #data()}; they never drive transitions.
 */
@Singleton
public class AccountLifecycleService
{
	private final AccountLifecycle lifecycle = new AccountLifecycle();
	private final Supplier<String> gameStateName;
	private final LongSupplier accountHash;
	private final Supplier<Set<String>> worldTypeNames;
	private final LongSupplier nanoTime;

	@Inject
	AccountLifecycleService(Client client)
	{
		this(() -> client.getGameState().name(), client::getAccountHash, () -> names(client.getWorldType()), System::nanoTime);
	}

	/** Seam for tests and the simulator: plain suppliers instead of a RuneLite client. */
	public AccountLifecycleService(Supplier<String> gameStateName, LongSupplier accountHash,
		Supplier<Set<String>> worldTypeNames, LongSupplier nanoTime)
	{
		this.gameStateName = gameStateName;
		this.accountHash = accountHash;
		this.worldTypeNames = worldTypeNames;
		this.nanoTime = nanoTime;
	}

	public AccountLifecycleView view()
	{
		return lifecycle;
	}

	public AccountDataRegistry data()
	{
		return lifecycle;
	}

	/**
	 * Scope for an event happening now (for example a chat message). The live game state, world
	 * and identity are read once and given to the central lifecycle
	 * ({@link AccountLifecycle#onEventObservation}), which ends or masks authority at once if they
	 * contradict it; the scope is returned only if it is still current, the live state is
	 * LOGGED_IN, the world supported, and the lifecycle confirms the identity read for it.
	 * Client thread.
	 */
	public Optional<AccountScope> scopeForEventNow()
	{
		ClientPhase observed = ClientPhase.fromName(readGameState());
		IdentityReading identity = readIdentity();
		WorldContext world = readWorld();
		lifecycle.onEventObservation(observed, identity, world, nanoTime.getAsLong());
		Optional<AccountScope> scope = lifecycle.snapshot().scope();
		if (!scope.isPresent()
			|| observed != ClientPhase.LOGGED_IN
			|| !world.isSupported()
			|| !lifecycle.confirms(scope.get(), identity))
		{
			return Optional.empty();
		}
		return scope;
	}

	public void start()
	{
		lifecycle.start(nanoTime.getAsLong());
	}

	public void stop()
	{
		lifecycle.stop();
	}

	public void onGameState(String name)
	{
		lifecycle.onClientPhase(ClientPhase.fromName(name), nanoTime.getAsLong());
	}

	/**
	 * Each tick first reconciles the lifecycle's phase with the client's actual game state (an
	 * unreadable state counts as UNKNOWN), so a missed or stale event can never leave the
	 * lifecycle believing it is LOGGED_IN; then the identity and world are read fresh.
	 */
	public void onGameTick()
	{
		ClientPhase actual = ClientPhase.fromName(readGameState());
		if (actual != lifecycle.phase())
		{
			lifecycle.onClientPhase(actual, nanoTime.getAsLong());
		}
		lifecycle.onGameTick(readIdentity(), readWorld(), nanoTime.getAsLong());
	}

	public void onClientTick()
	{
		if (lifecycle.state() == AccountState.SUSPENDED_CONTINUITY)
		{
			lifecycle.poll(nanoTime.getAsLong());
		}
	}

	private String readGameState()
	{
		try
		{
			return gameStateName.get();
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	IdentityReading readIdentity()
	{
		try
		{
			return IdentityReading.of(accountHash.getAsLong());
		}
		catch (RuntimeException e)
		{
			return IdentityReading.failed();
		}
	}

	WorldContext readWorld()
	{
		try
		{
			return WorldPolicy.evaluate(worldTypeNames.get());
		}
		catch (RuntimeException e)
		{
			return WorldContext.unsupported();
		}
	}

	static Set<String> names(Set<WorldType> types)
	{
		if (types == null)
		{
			return null;
		}
		Set<String> out = new LinkedHashSet<>();
		for (WorldType t : types)
		{
			out.add(t.name());
		}
		return out;
	}
}
