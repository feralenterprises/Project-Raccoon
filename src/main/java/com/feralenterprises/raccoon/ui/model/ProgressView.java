package com.feralenterprises.raccoon.ui.model;

import java.util.Objects;

/** PROVISIONAL immutable read model for a progress bar. Values are supplied by the data layer; the UI only draws. */
public final class ProgressView {
	private final String label;
	private final long current;
	private final long target;
	private final DataState state;
	private final Evidence evidence;

	public ProgressView(String label, long current, long target, DataState state, Evidence evidence) {
		this.label = Objects.requireNonNull(label);
		this.current = current;
		this.target = target;
		this.state = Objects.requireNonNull(state);
		this.evidence = evidence == null ? Evidence.NONE : evidence;
	}

	public String label() { return label; }
	public long current() { return current; }
	public long target() { return target; }
	public DataState state() { return state; }
	public Evidence evidence() { return evidence; }

	/** Fraction in [0,1]; 0 when the state shows no data or the target is not positive. */
	public double fraction() {
		if (!state.showsData() || target <= 0) return 0d;
		return Math.max(0d, Math.min(1d, (double) current / (double) target));
	}
}
