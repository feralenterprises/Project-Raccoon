package com.feralenterprises.raccoon.core.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

/**
 * P1 account isolation (issue #1): scopes are namespaced by lifecycle instance. A scope from
 * another AccountLifecycle never compares equal and never carries authority here, whatever the
 * identity or counters.
 */
public class ScopeNamespaceTest
{
	private static final IdentityReading ONE = IdentityReading.of(111_111_111L);
	private static final IdentityReading TWO = IdentityReading.of(222_222_222L);
	private static final WorldContext WORLD = WorldContext.supported(Membership.UNKNOWN);

	private AccountLifecycle a;
	private AccountLifecycle b;
	private AccountSlot<Integer> slotA;
	private AccountSlot<Integer> slotB;

	private static AccountLifecycle active(IdentityReading id)
	{
		AccountLifecycle lc = new AccountLifecycle();
		lc.start(0);
		lc.onClientPhase(ClientPhase.LOGGED_IN, 0);
		lc.onGameTick(id, WORLD, 0);
		return lc;
	}

	@Before
	public void setUp()
	{
		a = new AccountLifecycle();
		b = new AccountLifecycle();
	}

	private void activate(IdentityReading idA, IdentityReading idB)
	{
		slotA = a.loginSlot("totals");
		slotB = b.loginSlot("totals");
		for (AccountLifecycle lc : new AccountLifecycle[]{a, b})
		{
			lc.start(0);
			lc.onClientPhase(ClientPhase.LOGGED_IN, 0);
		}
		a.onGameTick(idA, WORLD, 0);
		b.onGameTick(idB, WORLD, 0);
	}

	private AccountScope scopeOf(AccountLifecycle lc)
	{
		return lc.snapshot().scope().orElseThrow(AssertionError::new);
	}

	private void assertForeign(AccountScope foreign, AccountLifecycle here, AccountSlot<Integer> slot)
	{
		AccountScope own = scopeOf(here);
		assertNotEquals(own, foreign);
		assertFalse(own.sameLogin(foreign));
		assertFalse(own.sameEpoch(foreign));
		assertFalse(here.isCurrent(foreign));
		assertFalse("foreign write rejected", slot.set(foreign, 99));
		assertEquals(Optional.empty(), slot.get(foreign));
		AtomicInteger ran = new AtomicInteger();
		here.fenced(foreign, ran::incrementAndGet).run();
		assertEquals(0, ran.get());
		ConsumerScopeGate gate = new ConsumerScopeGate(here);
		gate.onSnapshot(here.snapshot());
		assertFalse(gate.accepts(foreign));
		assertTrue("own scope still works", gate.accepts(own));
	}

	@Test
	public void distinctInstancesWithSameIdentityNeverShareScopes()
	{
		activate(ONE, ONE);
		assertForeign(scopeOf(a), b, slotB);
		assertForeign(scopeOf(b), a, slotA);
		assertFalse(b.confirms(scopeOf(a), ONE));
	}

	@Test
	public void distinctInstancesWithDifferentIdentityNeverShareScopes()
	{
		activate(ONE, TWO);
		assertForeign(scopeOf(a), b, slotB);
		assertFalse(b.confirms(scopeOf(a), ONE));
		assertFalse(b.confirms(scopeOf(a), TWO));
	}

	@Test
	public void foreignWriteNeverBecomesReadable()
	{
		activate(ONE, ONE);
		slotB.set(scopeOf(a), 42);
		assertEquals(Optional.empty(), slotB.get());
		assertEquals(Optional.empty(), slotB.get(scopeOf(b)));
	}

	@Test
	public void restartAndRebindKeepIsolation()
	{
		activate(ONE, ONE);
		AccountScope before = scopeOf(a);
		a.stop();
		a.start(1);
		a.onClientPhase(ClientPhase.LOGGED_IN, 1);
		a.onGameTick(ONE, WORLD, 1);
		AccountScope after = scopeOf(a);
		assertFalse(a.isCurrent(before));
		assertFalse(after.sameLogin(before));
		assertForeign(after, b, slotB);
		// a fresh instance created later (rebind) is foreign too
		AccountLifecycle c = active(ONE);
		assertFalse(c.isCurrent(after));
		assertNotEquals(scopeOf(c), after);
	}

	@Test
	public void sameInstanceContinuityIsPreserved()
	{
		activate(ONE, ONE);
		AccountScope before = scopeOf(a);
		slotA.set(before, 5);
		a.onClientPhase(ClientPhase.HOPPING, 1);
		a.onClientPhase(ClientPhase.LOGGED_IN, 1);
		a.onGameTick(ONE, WORLD, 1);
		assertTrue(scopeOf(a).sameLogin(before));
		assertEquals(Optional.of(5), slotA.get());
	}
}
