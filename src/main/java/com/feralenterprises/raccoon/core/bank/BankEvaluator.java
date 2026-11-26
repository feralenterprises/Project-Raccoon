package com.feralenterprises.raccoon.core.bank;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Pure validation of one {@link BankFrame} (structure, quantities, witness, carried items). */
final class BankEvaluator
{
	private BankEvaluator()
	{
	}

	static final class Evaluation
	{
		final BankReason failure;
		final Map<Integer, Integer> contents;
		final CarriedEvidence inventory;
		final CarriedEvidence equipment;
		final TabWitness witness;

		private Evaluation(BankReason failure, Map<Integer, Integer> contents, CarriedEvidence inventory,
			CarriedEvidence equipment, TabWitness witness)
		{
			this.failure = failure;
			this.contents = contents;
			this.inventory = inventory;
			this.equipment = equipment;
			this.witness = witness;
		}

		boolean valid()
		{
			return failure == null;
		}

		static Evaluation invalid(BankReason reason)
		{
			return new Evaluation(reason, null, null, null, null);
		}
	}

	private static final class Scan
	{
		BankReason failure;
		final Map<Integer, Integer> totals = new TreeMap<>();
		boolean excludedNoted;
		int rawSlots;
	}

	static Evaluation evaluate(BankFrame frame)
	{
		switch (frame.bank().state())
		{
			case MISSING:
				return Evaluation.invalid(BankReason.MISSING_CONTAINER);
			case UNREADABLE:
				return Evaluation.invalid(BankReason.READ_FAILURE);
			default:
				break;
		}
		Scan bank = scan(frame.bank());
		if (bank.failure != null)
		{
			return Evaluation.invalid(bank.failure);
		}

		TabWitness witness = frame.witness();
		if (witness.isKnown())
		{
			int total;
			try
			{
				total = witness.total();
			}
			catch (ArithmeticException e)
			{
				return Evaluation.invalid(BankReason.OVERFLOW);
			}
			if (total > bank.rawSlots)
			{
				return Evaluation.invalid(BankReason.WITNESS_EXCEEDS_SLOTS);
			}
		}

		CarriedEvidence[] carried = new CarriedEvidence[2];
		ContainerRead[] reads = {frame.inventory(), frame.equipment()};
		for (int i = 0; i < 2; i++)
		{
			switch (reads[i].state())
			{
				case MISSING:
					carried[i] = CarriedEvidence.unreported();
					break;
				case UNREADABLE:
					return Evaluation.invalid(BankReason.CARRIED_UNREADABLE);
				default:
					Scan s = scan(reads[i]);
					if (s.failure != null)
					{
						return Evaluation.invalid(s.failure);
					}
					carried[i] = CarriedEvidence.reported(s.totals, s.excludedNoted);
					break;
			}
		}

		// combined bank + carried quantities must also fit
		Set<Integer> ids = new TreeSet<>(bank.totals.keySet());
		for (CarriedEvidence c : carried)
		{
			c.items().ifPresent(m -> ids.addAll(m.keySet()));
		}
		for (int id : ids)
		{
			try
			{
				Math.addExact(Math.addExact(bank.totals.getOrDefault(id, 0), carried[0].quantity(id)), carried[1].quantity(id));
			}
			catch (ArithmeticException e)
			{
				return Evaluation.invalid(BankReason.OVERFLOW);
			}
		}
		return new Evaluation(null, bank.totals, carried[0], carried[1], witness);
	}

	private static Scan scan(ContainerRead read)
	{
		Scan s = new Scan();
		s.rawSlots = read.slots().size();
		for (RawSlot slot : read.slots())
		{
			if (slot == null)
			{
				s.failure = BankReason.MALFORMED_SLOT;
				return s;
			}
			int id = slot.itemId();
			int qty = slot.quantity();
			if (id == -1)
			{
				if (qty != 0)
				{
					s.failure = BankReason.MALFORMED_QUANTITY;
					return s;
				}
				continue;
			}
			if (id < -1)
			{
				s.failure = BankReason.MALFORMED_SLOT;
				return s;
			}
			switch (slot.kind())
			{
				case UNREADABLE:
					s.failure = BankReason.UNREADABLE_DEFINITION;
					return s;
				case PLACEHOLDER:
				case FILLER:
					if (qty < 0)
					{
						s.failure = BankReason.MALFORMED_QUANTITY;
						return s;
					}
					break;
				case NOTED:
					if (qty < 1)
					{
						s.failure = BankReason.MALFORMED_QUANTITY;
						return s;
					}
					s.excludedNoted = true;
					break;
				default:
					if (qty < 1)
					{
						s.failure = BankReason.MALFORMED_QUANTITY;
						return s;
					}
					try
					{
						s.totals.put(id, Math.addExact(s.totals.getOrDefault(id, 0), qty));
					}
					catch (ArithmeticException e)
					{
						s.failure = BankReason.OVERFLOW;
						return s;
					}
					break;
			}
		}
		return s;
	}
}
