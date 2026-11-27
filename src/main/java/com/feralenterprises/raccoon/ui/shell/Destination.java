package com.feralenterprises.raccoon.ui.shell;

import java.util.Objects;

/** A navigation destination. The shell holds many; panels never define their own navigation. */
public final class Destination {
	public enum Group { WORKBENCH, PRODUCT, UTILITY }

	private final String id, label, monogram;
	private final Group group;
	private final boolean deferred;
	private final boolean accountData;
	private final boolean railListed;

	public Destination(String id, String label, String monogram, Group group, boolean deferred) {
		this(id, label, monogram, group, deferred, group == Group.PRODUCT);
	}

	/**
	 * @param accountData true if the page shows account-derived values: it is replaced by the shared notice while
	 *                    the account is not publishable, and its cached state is cleared on scope change.
	 */
	public Destination(String id, String label, String monogram, Group group, boolean deferred, boolean accountData) {
		this(id, label, monogram, group, deferred, accountData, true);
	}

	/**
	 * @param railListed false keeps a destination registered and navigable while leaving it off the rail (owner
	 *                   decision, roadmap #1: the Tools icon is removed from the rail; its functionality is not deleted)
	 */
	public Destination(String id, String label, String monogram, Group group, boolean deferred, boolean accountData, boolean railListed) {
		this.id = Objects.requireNonNull(id);
		this.label = Objects.requireNonNull(label);
		this.monogram = Objects.requireNonNull(monogram);
		this.group = Objects.requireNonNull(group);
		this.deferred = deferred;
		this.accountData = accountData;
		this.railListed = railListed;
	}

	public String id() { return id; }
	public String label() { return label; }
	public String monogram() { return monogram; }
	public Group group() { return group; }
	public boolean deferred() { return deferred; }
	public boolean accountData() { return accountData; }
	/** Utility destinations (Settings) are reached from the header control only, never duplicated in the rail. */
	public boolean inRail() { return !deferred && railListed && group != Group.UTILITY; }
}
