package com.feralenterprises.raccoon.ui.model;

/** PROVISIONAL. Where a value came from and how old it is, pre-formatted by the data layer (UI does no clock math). */
public final class Evidence {
	public static final Evidence NONE = new Evidence("", "");

	private final String source;
	private final String age;

	public Evidence(String source, String age) {
		this.source = source == null ? "" : source;
		this.age = age == null ? "" : age;
	}

	public String source() { return source; }
	public String age() { return age; }
	public boolean isEmpty() { return source.isEmpty() && age.isEmpty(); }

	public String line() {
		if (source.isEmpty()) return age;
		if (age.isEmpty()) return source;
		return source + " · " + age;
	}
}
