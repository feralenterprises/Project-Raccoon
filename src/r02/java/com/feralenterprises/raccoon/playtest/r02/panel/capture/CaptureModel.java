package com.feralenterprises.raccoon.playtest.r02.panel.capture;

import com.feralenterprises.raccoon.playtest.r02.fixture.CaptureState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerObservation;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.EvidenceStatus;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Account Capture's view of one snapshot (frozen R02 {@code captureRender}). The Bank row follows the scope's shared
 * bank observation, exactly as Bank Memory does (so the two can never disagree); every other source follows the
 * shared capture evidence for its id. Stale evidence is "Saved", partial is "Incomplete" (never a new verified
 * result), missing evidence is "Pending", signed out exposes nothing.
 */
final class CaptureModel {
	enum State {
		VERIFIED("Verified"), HISTORICAL("Saved"), WAITING("Pending"), INCOMPLETE("Incomplete"), UNAVAILABLE("Unavailable");

		final String label;

		State(String label) { this.label = label; }
	}

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm 'UTC'", Locale.UK).withZone(ZoneOffset.UTC);

	/** One source row. */
	static final class Row {
		final CaptureInputs.Source source;
		final State state;
		final Instant at;
		final boolean emptyBank;

		Row(CaptureInputs.Source source, State state, Instant at, boolean emptyBank) {
			this.source = source;
			this.state = state;
			this.at = at;
			this.emptyBank = emptyBank;
		}

		String lastComplete() { return at == null ? "Last complete: unavailable" : "Last complete: " + TIME.format(at); }
	}

	final String account, title, intro, count, summary, identity, nextTitle, nextCopy, nextButton, nextId, meterText;
	final int verified, saved, covered;
	final List<Row> rows;

	private CaptureModel(FixtureSnapshot s, CaptureInputs in) {
		account = s.scope().signedIn() ? s.scope().account() : null;
		List<Row> r = new ArrayList<>();
		for (CaptureInputs.Source src : in.sources) r.add(row(src, s, account != null));
		rows = Collections.unmodifiableList(r);
		verified = (int) rows.stream().filter(x -> x.state == State.VERIFIED).count();
		saved = (int) rows.stream().filter(x -> x.state == State.HISTORICAL || (x.state == State.INCOMPLETE && x.at != null)).count();
		covered = verified + saved;
		int total = rows.size();
		title = account == null ? "Account unavailable" : verified == total ? "Capture complete" : covered == total ? "Saved observations" : "Capture your account";
		intro = account == null ? "Log in to begin capture." : verified == total ? "All ten sample sources observed."
			: covered == total ? "Saved evidence may be out of date." : "Play normally; supported observations are automatic.";
		count = account == null ? "—" : covered + " / " + total;
		meterText = account == null ? "Unavailable while logged out" : covered + " of " + total + " sources have complete evidence";
		summary = account == null ? "No account observations are exposed." : verified + " verified now · " + saved + " saved · " + (total - covered) + " pending";
		identity = account == null ? "Offline" : "Demo " + account;

		Row bank = rows.stream().filter(x -> x.source.id.equals("bank")).findFirst().orElse(null);
		Row next = null;
		if (account != null) {
			if (bank != null && bank.state != State.VERIFIED && bank.state != State.HISTORICAL) next = bank;
			else next = rows.stream().filter(x -> x.state == State.WAITING || x.state == State.INCOMPLETE).findFirst()
				.orElse(rows.stream().filter(x -> x.state == State.HISTORICAL).findFirst().orElse(null));
		}
		nextId = next == null ? null : next.source.id;
		if (account == null) {
			nextTitle = "Log in to your account";
			nextCopy = "Capture starts only after account identity is confirmed.";
			nextButton = null;
		} else if (next == null) {
			nextTitle = "You’re ready";
			nextCopy = "No additional action is needed. Observations continue as you play.";
			nextButton = "View Bank Memory ›";
		} else if (next.source.id.equals("bank")) {
			nextTitle = next.state == State.INCOMPLETE ? "Complete bank capture" : "Open your bank";
			nextCopy = next.state == State.HISTORICAL ? "Your last capture is saved. Open the bank again if you want current evidence."
				: "Visit any accessible bank. A complete unlocked bank is observed automatically.";
			nextButton = "Show bank guidance ›";
		} else {
			nextTitle = "Observe " + next.source.name.toLowerCase(Locale.ROOT);
			nextCopy = next.source.help;
			nextButton = "Show guidance ›";
		}
	}

	static CaptureModel of(FixtureSnapshot s, CaptureInputs in) { return new CaptureModel(s, in); }

	private static Row row(CaptureInputs.Source src, FixtureSnapshot s, boolean signedIn) {
		if (!signedIn) return new Row(src, State.UNAVAILABLE, null, false);
		if (src.id.equals("bank")) {
			ContainerObservation b = s.container(Container.BANK);
			ContainerState cs = b == null ? ContainerState.UNAVAILABLE : b.state();
			switch (cs) {
				case CURRENT: return new Row(src, State.VERIFIED, b.observedAt(), b.items().isEmpty());
				case HISTORICAL: return new Row(src, State.HISTORICAL, b.observedAt(), false);
				case INCOMPLETE: return new Row(src, State.INCOMPLETE, null, false);
				default: return new Row(src, State.WAITING, null, false);
			}
		}
		Evidence<CaptureState> e = s.capture(src.id);
		if (!e.hasValue()) return new Row(src, e.status() == EvidenceStatus.UNAVAILABLE ? State.UNAVAILABLE : State.WAITING, null, false);
		switch (e.value()) {
			case VERIFIED:
				return e.status() == EvidenceStatus.STALE ? new Row(src, State.HISTORICAL, e.observedAt(), false)
					: new Row(src, State.VERIFIED, e.observedAt(), false);
			case PARTIAL: return new Row(src, State.INCOMPLETE, e.status() == EvidenceStatus.STALE ? e.observedAt() : null, false);
			case UNAVAILABLE: return new Row(src, State.UNAVAILABLE, null, false);
			default: return new Row(src, State.WAITING, null, false);
		}
	}

	/** Frozen guidance copy for the next step. */
	String guidance() {
		if (nextId == null) return null;
		if (nextId.equals("bank")) return "Open the in-game world map and look for a bank icon, or ask an NPC for directions. "
			+ "This offline prototype cannot locate your player or open the RuneLite map.";
		Row r = rows.stream().filter(x -> x.source.id.equals(nextId)).findFirst().orElse(null);
		return r.source.help + " Adapter availability must be confirmed in native testing. This preview does not simulate a player "
			+ "interaction from this button.";
	}
}
