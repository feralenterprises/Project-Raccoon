package com.feralenterprises.raccoon.core.account;

import static com.feralenterprises.raccoon.core.account.LifecycleHarness.A;
import static com.feralenterprises.raccoon.core.account.LifecycleHarness.B;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.Test;

/** Models a single consumer thread (for example the Swing EDT) fed through an ordered queue. */
public class ConsumerScopeGateTest
{
	private final LifecycleHarness h = new LifecycleHarness();
	private final Deque<Runnable> consumerQueue = new ArrayDeque<>();
	private final ConsumerScopeGate gate = new ConsumerScopeGate();
	private final List<String> rendered = new ArrayList<>();

	private void drain()
	{
		while (!consumerQueue.isEmpty())
		{
			consumerQueue.poll().run();
		}
	}

	private void postValue(AccountScope scope, String value)
	{
		consumerQueue.add(() ->
		{
			if (gate.accepts(scope))
			{
				rendered.add(value);
			}
		});
	}

	@Test
	public void valueProducedBeforeRetirementIsRejectedOnceRetirementIsSeen()
	{
		h.lifecycle.subscribe(s -> consumerQueue.add(() ->
		{
			if (gate.onSnapshot(s) && !s.isPublishable())
			{
				rendered.clear();
			}
		}));
		h.loggedInAs(A);
		AccountScope a = h.scope();
		drain();
		h.tick(B); // missed logout: A retired, B active
		postValue(a, "A totals"); // produced late for A
		drain();
		assertTrue(rendered.isEmpty());
		assertFalse(gate.accepts(a));
		assertTrue(gate.accepts(h.scope()));
	}

	@Test
	public void valueAppliedBeforeRetirementIsClearedByIt()
	{
		h.lifecycle.subscribe(s -> consumerQueue.add(() ->
		{
			if (gate.onSnapshot(s) && !s.isPublishable())
			{
				rendered.clear();
			}
		}));
		h.loggedInAs(A);
		postValue(h.scope(), "A totals");
		h.phase(ClientPhase.HOPPING);
		drain();
		assertTrue(rendered.isEmpty());
	}

	@Test
	public void outOfOrderSnapshotsNeverRegressTheGate()
	{
		h.loggedInAs(A);
		AccountSnapshot active = h.snap();
		h.phase(ClientPhase.LOGIN_SCREEN);
		AccountSnapshot retired = h.snap();
		assertTrue(gate.onSnapshot(retired));
		assertFalse(gate.onSnapshot(active));
		assertEquals(retired, gate.latest().get());
		assertFalse(gate.accepts(active.scope().get()));
	}
}
