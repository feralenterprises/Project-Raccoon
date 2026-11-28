package com.feralenterprises.raccoon.playtest.r02.panel.capture;

import com.feralenterprises.raccoon.playtest.r02.panel.bank.RetainedBankRegression;
import java.util.Arrays;
import java.util.function.BiConsumer;

/** Account Capture half of {@link RetainedBankRegression}: the Bank row keeps the last-complete time. */
public final class CaptureRetainedChecks {
	private CaptureRetainedChecks() { }

	public static void run(BiConsumer<String, Boolean> check) {
		CaptureInputs in = CaptureInputs.synthetic(Arrays.asList(new CaptureInputs.Source("bank", "Bank", "Open your bank.", null),
			new CaptureInputs.Source("skills", "Skills", "Log in.", null)));

		CaptureModel.Row base = bank(CaptureModel.of(RetainedBankRegression.current(), in));
		check.accept("capture baseline: complete capture is verified with its time", base.state == CaptureModel.State.VERIFIED
			&& RetainedBankRegression.COMPLETE_AT.equals(base.at));

		CaptureModel m = CaptureModel.of(RetainedBankRegression.retained(), in);
		CaptureModel.Row r = bank(m);
		check.accept("capture retained: bank row stays Incomplete, never Verified", r.state == CaptureModel.State.INCOMPLETE);
		check.accept("capture retained: last-complete timestamp preserved", RetainedBankRegression.COMPLETE_AT.equals(r.at)
			&& r.lastComplete().contains("18:15") && !r.lastComplete().contains("unavailable"));
		check.accept("capture retained: counted as saved evidence, not verified", m.saved == 1 && m.verified == 0);

		CaptureModel.Row u = bank(CaptureModel.of(RetainedBankRegression.incompleteOnly(), in));
		check.accept("capture incomplete without earlier capture: no last-complete time", u.state == CaptureModel.State.INCOMPLETE && u.at == null
			&& u.lastComplete().equals("Last complete: unavailable"));
	}

	private static CaptureModel.Row bank(CaptureModel m) {
		return m.rows.stream().filter(x -> x.source.id.equals("bank")).findFirst().orElseThrow(IllegalStateException::new);
	}
}
