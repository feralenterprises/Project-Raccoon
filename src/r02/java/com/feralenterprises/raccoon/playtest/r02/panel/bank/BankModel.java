package com.feralenterprises.raccoon.playtest.r02.panel.bank;

import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.ItemDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bank Memory's view of one snapshot (frozen R02 {@code bankRender}). The shared bank observation of this scope is the
 * only authority: {@code CURRENT} is a verified open bank (empty when it has no items), {@code HISTORICAL} the last
 * complete capture (changes since unknown), {@code INCOMPLETE} withholds every record (no ownership claim),
 * {@code UNAVAILABLE} awaits a capture, signed out exposes nothing. Item identity is the exact item ID; the frozen
 * catalog supplies only name, category and an illustrative price for that same ID.
 */
final class BankModel {
	enum State { POPULATED, EMPTY, HISTORICAL, AWAITING, INCOMPLETE, LOGOUT }

	static final String[] CATEGORIES = {"All", "Materials", "Gear", "Supplies", "Other"};
	enum Sort {
		ID("Item ID ↑"), NAME("Name A–Z"), QUANTITY("Quantity ↓"), VALUE("Est. value ↓");

		final String label;

		Sort(String label) { this.label = label; }
	}

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm 'UTC'", Locale.UK).withZone(ZoneOffset.UTC);

	/** One observed item type. */
	static final class Row {
		final int id;
		final String name, category;
		final long quantity;
		final Long unitPrice;

		Row(int id, String name, String category, long quantity, Long unitPrice) {
			this.id = id;
			this.name = name;
			this.category = category;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
		}

		Long stackValue() { return unitPrice == null ? null : unitPrice * quantity; }
	}

	final State state;
	final String account, statusLabel, statusNote, observed, countText, countLabel, valueText, valueLabel, valueNote, accountNote,
		quantityLabel;
	/** Null when no complete observation may be shown (awaiting, incomplete, signed out). */
	final List<Row> items;
	final boolean current;

	private BankModel(FixtureSnapshot s, Registry registry, BankInputs in) {
		account = s.scope().signedIn() ? s.scope().account() : null;
		ContainerObservation bank = account == null ? null : s.container(Container.BANK);
		ContainerState cs = bank == null ? ContainerState.UNAVAILABLE : bank.state();
		if (account == null) state = State.LOGOUT;
		else if (cs == ContainerState.CURRENT) state = bank.items().isEmpty() ? State.EMPTY : State.POPULATED;
		else if (cs == ContainerState.HISTORICAL) state = State.HISTORICAL;
		else if (cs == ContainerState.INCOMPLETE) state = State.INCOMPLETE;
		else state = State.AWAITING;
		current = state == State.POPULATED || state == State.EMPTY;
		String[] info = in.states.get(state.name().toLowerCase(Locale.ROOT));
		statusLabel = info[0];
		statusNote = info[1];
		boolean shown = current || state == State.HISTORICAL;
		observed = shown ? "Last verified: " + TIME.format(bank.observedAt()) : "Last verified: unavailable";
		if (shown) {
			List<Row> rows = new ArrayList<>();
			for (Map.Entry<Integer, Long> e : bank.items().entrySet()) rows.add(row(e.getKey(), e.getValue(), registry, in));
			rows.sort(Comparator.comparingInt(r -> r.id));
			items = Collections.unmodifiableList(rows);
		} else items = null;
		countText = items == null ? "Unknown" : fmt(items.size());
		countLabel = current ? "Verified item types" : items != null ? "Last seen types" : "Item types";
		if (items == null) {
			valueText = "Unknown";
			valueLabel = "Value estimate";
			valueNote = "No valuation without a complete snapshot.";
		} else {
			long value = 0;
			int priced = 0;
			for (Row r : items) if (r.unitPrice != null) { value += r.stackValue(); priced++; }
			int unpriced = items.size() - priced;
			valueText = priced > 0 || items.isEmpty() ? gp(value) : "Unknown";
			valueLabel = unpriced > 0 ? "Partial estimate" : "Value estimate";
			valueNote = (unpriced > 0 ? priced + "/" + items.size() + " types priced · " : "") + "Illustrative prices, not live GE values.";
		}
		accountNote = account == null ? "No active account · Offline demo"
			: "Demo account " + account + " · " + (current ? "Current observation" : items != null ? "Last verified only" : "No verified snapshot");
		quantityLabel = current ? "Quantity" : "Last seen";
	}

	static BankModel of(FixtureSnapshot s, Registry registry, BankInputs in) { return new BankModel(s, registry, in); }

	/** Exact identity: the observed ID names the row; the catalog and registry only describe that same ID. */
	static Row row(int id, long quantity, Registry registry, BankInputs in) {
		BankInputs.CatalogItem c = in.catalog.get(id);
		ItemDef d = registry.item(id).orElse(null);
		String name = c != null ? c.name : d != null ? d.name() : "Item " + id;
		String cat = c != null ? c.category : d != null && d.category() != null ? d.category() : "Other";
		return new Row(id, name, group(cat), quantity, c == null ? null : c.unitPrice);
	}

	/** Frozen {@code bankCategory}. */
	static String group(String category) {
		if (category.equals("Equipment") || category.equals("Weapons") || category.equals("Gear")) return "Gear";
		if (category.equals("Materials") || category.equals("Resources")) return "Materials";
		if (category.equals("Supplies")) return "Supplies";
		return "Other";
	}

	boolean controlsEnabled() { return items != null && !items.isEmpty(); }

	List<Row> rows(String category, String query, Sort sort) {
		if (items == null) return Collections.emptyList();
		String term = query.trim().toLowerCase(Locale.ROOT);
		Comparator<Row> byId = Comparator.comparingInt(r -> r.id);
		Comparator<Row> order;
		switch (sort) {
			case NAME: order = Comparator.comparing((Row r) -> r.name).thenComparing(byId); break;
			case QUANTITY: order = Comparator.comparingLong((Row r) -> -r.quantity).thenComparing(byId); break;
			case VALUE: order = Comparator.comparingLong((Row r) -> r.stackValue() == null ? 1 : -r.stackValue()).thenComparing(byId); break;
			default: order = byId;
		}
		return items.stream().filter(r -> (category.equals("All") || r.category.equals(category))
			&& (term.isEmpty() || r.name.toLowerCase(Locale.ROOT).contains(term) || String.valueOf(r.id).equals(term)))
			.sorted(order).collect(Collectors.toList());
	}

	String resultCount(int shown) {
		if (items == null) return "Unavailable";
		return shown == items.size() ? shown + " item types" : shown + " of " + items.size() + " types";
	}

	/** Frozen empty-result message {title, body}; null when rows exist. */
	String[] message(int shown) {
		if (items == null) {
			if (state == State.INCOMPLETE) return new String[] {"No complete snapshot", "Partial records withheld. No ownership claims can be made."};
			if (state == State.LOGOUT) return new String[] {"Account unavailable", "Bank contents are hidden while logged out."};
			return new String[] {"No bank captured", "Items appear after a successful automatic observation."};
		}
		if (shown > 0) return null;
		if (!items.isEmpty()) return new String[] {"No matching items", "Try a different name, ID or category."};
		return current ? new String[] {"Bank is empty", "Zero item types verified."}
			: new String[] {"Last verified bank was empty", "No items at the recorded observation; current contents unknown."};
	}

	static String fmt(long n) { return String.format(Locale.US, "%,d", n); }

	static String gp(long n) {
		if (n >= 1_000_000) return String.format(Locale.US, "%.2fm gp", n / 1e6);
		if (n >= 1_000) return String.format(Locale.US, "%.1fk gp", n / 1e3);
		return fmt(n) + " gp";
	}
}
