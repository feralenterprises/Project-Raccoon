package com.feralenterprises.raccoon.playtest.r02.fixture;

import java.time.Instant;
import java.util.Objects;

/**
 * One immutable observation of a value. The factories enforce the invariants: only VERIFIED, VERIFIED_ZERO and STALE
 * carry a value; STALE always carries the time it was observed; VERIFIED_ZERO holds a numeric zero.
 */
public final class Evidence<T> {
	private final EvidenceStatus status;
	private final T value;
	private final Instant observedAt;
	private final String source;

	private Evidence(EvidenceStatus status, T value, Instant observedAt, String source) {
		this.status = status;
		this.value = value;
		this.observedAt = observedAt;
		this.source = source;
	}

	public static <T> Evidence<T> verified(T value, Instant at, String source) {
		return new Evidence<>(EvidenceStatus.VERIFIED, Objects.requireNonNull(value), at, Objects.requireNonNull(source));
	}

	/** A verified count; zero becomes VERIFIED_ZERO so it is never mistaken for "no evidence". */
	public static Evidence<Integer> count(int n, Instant at, String source) {
		if (n < 0) throw new IllegalArgumentException("negative count");
		return new Evidence<>(n == 0 ? EvidenceStatus.VERIFIED_ZERO : EvidenceStatus.VERIFIED, n, at, Objects.requireNonNull(source));
	}

	public static Evidence<Long> quantity(long n, Instant at, String source) {
		if (n < 0) throw new IllegalArgumentException("negative quantity");
		return new Evidence<>(n == 0 ? EvidenceStatus.VERIFIED_ZERO : EvidenceStatus.VERIFIED, n, at, Objects.requireNonNull(source));
	}

	public static <T> Evidence<T> absent(Instant at, String source) {
		return new Evidence<>(EvidenceStatus.VERIFIED_ABSENT, null, at, Objects.requireNonNull(source));
	}

	public static <T> Evidence<T> stale(T value, Instant observedAt, String source) {
		return new Evidence<>(EvidenceStatus.STALE, Objects.requireNonNull(value), Objects.requireNonNull(observedAt), Objects.requireNonNull(source));
	}

	public static <T> Evidence<T> unknown() { return new Evidence<>(EvidenceStatus.UNKNOWN, null, null, null); }
	public static <T> Evidence<T> unavailable() { return new Evidence<>(EvidenceStatus.UNAVAILABLE, null, null, null); }
	public static <T> Evidence<T> notApplicable() { return new Evidence<>(EvidenceStatus.NOT_APPLICABLE, null, null, null); }

	public EvidenceStatus status() { return status; }
	public boolean hasValue() { return status.hasValue(); }
	/** The value; throws when {@link #hasValue()} is false so no caller can turn missing evidence into a default. */
	public T value() {
		if (!hasValue()) throw new IllegalStateException("no value: " + status);
		return value;
	}
	/** Observation time, or null for UNKNOWN / UNAVAILABLE / NOT_APPLICABLE. */
	public Instant observedAt() { return observedAt; }
	/** Short provenance label ("Demo A bank, 26 Nov 18:15"), or null when nothing was observed. */
	public String source() { return source; }

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Evidence)) return false;
		Evidence<?> e = (Evidence<?>) o;
		return status == e.status && Objects.equals(value, e.value) && Objects.equals(observedAt, e.observedAt) && Objects.equals(source, e.source);
	}

	@Override
	public int hashCode() { return Objects.hash(status, value, observedAt, source); }

	@Override
	public String toString() { return hasValue() ? status + "(" + value + ")" : status.name(); }
}
