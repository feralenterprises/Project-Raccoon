package com.feralenterprises.raccoon.core.bank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of reading one item container: present (with raw slots), missing, or unreadable. */
public final class ContainerRead
{
	public enum State
	{
		PRESENT,
		/** The client reports no such container: unreported, never "empty". */
		MISSING,
		/** The container exists but could not be read. */
		UNREADABLE
	}

	private static final ContainerRead MISSING = new ContainerRead(State.MISSING, Collections.emptyList());
	private static final ContainerRead UNREADABLE = new ContainerRead(State.UNREADABLE, Collections.emptyList());

	private final State state;
	private final List<RawSlot> slots;

	private ContainerRead(State state, List<RawSlot> slots)
	{
		this.state = state;
		this.slots = slots;
	}

	/** Raw slots in container order; a {@code null} element is a malformed slot. */
	public static ContainerRead present(List<RawSlot> slots)
	{
		if (slots == null)
		{
			return UNREADABLE;
		}
		return new ContainerRead(State.PRESENT, Collections.unmodifiableList(new ArrayList<>(slots)));
	}

	public static ContainerRead missing()
	{
		return MISSING;
	}

	public static ContainerRead unreadable()
	{
		return UNREADABLE;
	}

	public State state()
	{
		return state;
	}

	public List<RawSlot> slots()
	{
		return slots;
	}
}
