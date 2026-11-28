package com.feralenterprises.raccoon.playtest.r02.panel.bank;

import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.fixture.TestFixtures;
import com.feralenterprises.raccoon.playtest.r02.panel.capture.CaptureRetainedChecks;
import com.feralenterprises.raccoon.playtest.r02.panel.xpstash.XpRetainedChecks;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Permanent regression for the retained-bank evidence defect (Feral integrated Swing verification, corroborated by
 * Solis): an INCOMPLETE bank observation that follows a complete capture carries that capture as retained HISTORICAL
 * evidence ({@code ContainerObservation.withRetained}). Bank Memory must keep the last complete rows as historical
 * (never current), Account Capture must keep the last-complete time, XP Stash must use the retained capture as a
 * saved (not current) supply source, partial quantities never enter totals, and an incomplete observation with no
 * earlier complete capture stays unknown. Synthetic quantities only; no private inputs.
 *
 * <p>Run: {@code java -Djava.awt.headless=true -cp build/classes/java/main:build/classes/java/r02:build/classes/java/r02test
 * com.feralenterprises.raccoon.playtest.r02.panel.bank.RetainedBankRegression}. Exit code 1 on any failure.
 */
public final class RetainedBankRegression {
	/** Synthetic complete capture time and the later incomplete observation time. */
	public static final Instant COMPLETE_AT = Instant.parse("2026-11-26T18:15:00Z"), INCOMPLETE_AT = Instant.parse("2026-11-27T09:30:00Z");
	/** Synthetic complete bank: coins 1,000; shark 12; logs 85. */
	public static final Map<Integer, Long> COMPLETE_ITEMS = items(995, 1_000, 385, 12, 1511, 85);
	/** Synthetic partial observation: a different coin count and an item never in the complete capture. */
	public static final Map<Integer, Long> PARTIAL_ITEMS = items(995, 7, 4151, 1);

	private static int failed, passed;

	public static void main(String[] args) {
		BiConsumer<String, Boolean> check = (name, ok) -> {
			if (ok) passed++; else failed++;
			System.out.println((ok ? "PASS " : "FAIL ") + name);
		};
		bank(check);
		CaptureRetainedChecks.run(check);
		XpRetainedChecks.run(check);
		System.out.println(passed + " passed, " + failed + " failed");
		if (failed > 0) System.exit(1);
	}

	public static Map<Integer, Long> items(long... kv) {
		Map<Integer, Long> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) m.put((int) kv[i], kv[i + 1]);
		return m;
	}

	/** Complete capture, then an incomplete observation that retains it (the defect scenario). */
	public static FixtureSnapshot retained() {
		ContainerObservation complete = new ContainerObservation(Container.BANK, ContainerState.HISTORICAL, COMPLETE_AT, "synthetic complete capture", COMPLETE_ITEMS);
		ContainerObservation partial = new ContainerObservation(Container.BANK, ContainerState.INCOMPLETE, INCOMPLETE_AT, "synthetic incomplete observation", PARTIAL_ITEMS);
		return snapshot(partial.withRetained(complete), 2);
	}

	/** An incomplete observation with no earlier complete capture. */
	public static FixtureSnapshot incompleteOnly() {
		return snapshot(new ContainerObservation(Container.BANK, ContainerState.INCOMPLETE, INCOMPLETE_AT, "synthetic incomplete observation", PARTIAL_ITEMS), 1);
	}

	/** The complete capture while the bank is still open (baseline). */
	public static FixtureSnapshot current() {
		return snapshot(new ContainerObservation(Container.BANK, ContainerState.CURRENT, COMPLETE_AT, "synthetic complete capture", COMPLETE_ITEMS), 1);
	}

	static FixtureSnapshot snapshot(ContainerObservation bank, long revision) {
		return FixtureSnapshot.builder(AccountScope.of("A", 1)).generation(1).revision(revision).container(bank).build();
	}

	static BankInputs inputs() {
		Map<Integer, BankInputs.CatalogItem> c = new HashMap<>();
		c.put(995, BankInputs.item(995, "Coins", "Other", 1L));
		c.put(385, BankInputs.item(385, "Shark", "Supplies", 1_000L));
		c.put(1511, BankInputs.item(1511, "Logs", "Materials", 50L));
		c.put(4151, BankInputs.item(4151, "Abyssal whip", "Weapons", 1_500_000L));
		Map<String, String[]> st = new HashMap<>();
		st.put("populated", new String[] {"Verified · bank open", "Complete observation. Counts reflect the observed bank."});
		st.put("historical", new String[] {"Historical · bank closed", "Last complete capture retained. Changes since then are unknown."});
		st.put("awaiting", new String[] {"Awaiting verification", "Open your in-game bank to capture it automatically. No complete observation yet."});
		st.put("incomplete", new String[] {"Incomplete observation", "This capture cannot establish ownership. Open your bank again for a complete observation."});
		st.put("empty", new String[] {"Verified empty · bank open", "Complete observation confirmed an empty bank."});
		st.put("logout", new String[] {"Unavailable · logged out", "Log in and open your bank. No account bank data is exposed."});
		return BankInputs.synthetic(c, st);
	}

	static void bank(BiConsumer<String, Boolean> check) {
		Registry reg = TestFixtures.registry();
		BankInputs in = inputs();

		BankModel base = BankModel.of(current(), reg, in);
		check.accept("bank baseline: complete current capture shows 3 verified rows", base.current && base.items != null && base.items.size() == 3);

		BankModel m = BankModel.of(retained(), reg, in);
		Map<Integer, Long> rows = m.items == null ? null : m.items.stream().collect(Collectors.toMap(r -> r.id, r -> r.quantity));
		check.accept("bank retained: last complete rows are kept", COMPLETE_ITEMS.equals(rows));
		check.accept("bank retained: rows are historical, never current ownership", !m.current && m.quantityLabel.equals("Last seen")
			&& m.countLabel.equals("Last seen types") && m.accountNote.endsWith("Last verified only"));
		check.accept("bank retained: last-complete time is the complete capture's", m.observed.startsWith("Last verified: ") && m.observed.contains("18:15")
			&& !m.observed.contains("09:30"));
		check.accept("bank retained: state stays incomplete with the discarded-capture note", m.state == BankModel.State.INCOMPLETE
			&& m.statusLabel.equals("Incomplete observation")
			&& m.statusNote.equals("Incomplete capture discarded. Showing the earlier complete snapshot only; present ownership is unknown."));
		check.accept("bank retained: partial quantities never enter rows or totals", rows != null && rows.get(995) == 1_000L && !rows.containsKey(4151)
			&& m.valueText.equals(BankModel.gp(1_000 + 12 * 1_000 + 85 * 50)));
		check.accept("bank retained: rows shown, not withheld or claimed empty", m.message(m.items == null ? 0 : m.items.size()) == null && m.controlsEnabled()
			&& m.resultCount(3).equals("3 item types"));

		BankModel u = BankModel.of(incompleteOnly(), reg, in);
		check.accept("bank incomplete without earlier capture: unknown, nothing shown", u.items == null && u.observed.equals("Last verified: unavailable")
			&& u.countText.equals("Unknown") && u.valueText.equals("Unknown") && !u.current
			&& "No complete snapshot".equals(u.message(0)[0]) && u.statusNote.equals("This capture cannot establish ownership. Open your bank again for a complete observation."));
	}

	static List<Integer> ids(BankModel m) { return m.items.stream().map(r -> r.id).collect(Collectors.toList()); }
}
