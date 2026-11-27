package com.feralenterprises.raccoon.playtest.r02.panel.capture;

import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.ProgressBar;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.component.Tone;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.model.DataState;
import com.feralenterprises.raccoon.ui.model.Evidence;
import com.feralenterprises.raccoon.ui.model.ProgressView;
import com.feralenterprises.raccoon.ui.lifecycle.Disposable;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;

/**
 * R02 Account Capture (frozen {@code #capture}): coverage summary, next step with guidance, the ten account
 * observations (expandable, with last-complete time) and How capture works. Functional build on shared components.
 * Guidance and open rows are page state, reset on a scope change and kept across same-scope revisions.
 */
final class CapturePanel extends StackPanel implements Disposable {
	static final String HOW = "Capture is read-only and automatic: Project Raccoon observes supported game signals while you play. "
		+ "Nothing is requested from you and nothing is written to the game. Each source records when it was last complete; "
		+ "incomplete or unobserved sources never become verified results.";

	private final RouteContext context;
	private final UiContext uiContext;
	private final CaptureInputs in;
	private final Runnable textScaleChanged = this::render;
	private final Set<String> open = new HashSet<>();
	private boolean guideOpen;
	private CaptureModel model;

	CapturePanel(RouteContext context, CaptureInputs in) {
		super(7);
		this.context = context;
		this.uiContext = context.ui();
		this.in = in;
		setName("capture");
		setOpaque(false);
		setBorder(new EmptyBorder(0, 0, 6, 0));
		model = CaptureModel.of(context.snapshot(), in);
		render();
		context.onSnapshot(this::onSnapshot);
		uiContext.addChangeListener(textScaleChanged);
	}

	@Override public void dispose() { uiContext.removeChangeListener(textScaleChanged); }

	CaptureModel model() { return model; }

	private void onSnapshot(FixtureSnapshot s) {
		String before = model.nextId;
		model = CaptureModel.of(s, in);
		if (before == null || !before.equals(model.nextId)) guideOpen = false;
		render();
	}

	private void render() {
		removeAll();
		Card intro = new Card(uiContext, model.title);
		intro.setName("capture-intro");
		ProgressBar meter = new ProgressBar(uiContext, new ProgressView("Capture coverage", model.account == null ? 0 : model.covered,
			model.rows.size(), model.account == null ? DataState.UNAVAILABLE : DataState.ACTIVE, Evidence.NONE), ProgressBar.Variant.PROGRESS);
		meter.setName("capture-meter");
		meter.getAccessibleContext().setAccessibleName("Capture coverage");
		meter.getAccessibleContext().setAccessibleDescription(model.meterText);
		intro.addAll(text("capture-intro-copy", FontRole.SMALL, Tone.TEXT, model.intro),
			text("capture-count", FontRole.VALUE, Tone.ACCENT, "Capture coverage " + model.count), meter,
			text("capture-summary", FontRole.SMALL, Tone.MUTED, model.summary));
		add(intro);

		Card next = new Card(uiContext, "Next step");
		next.setName("capture-next");
		next.addAll(text("capture-next-title", FontRole.BODY, Tone.ACCENT, model.nextTitle), text("capture-next-copy", FontRole.SMALL, Tone.TEXT, model.nextCopy));
		if (model.nextButton != null) {
			RButton b = new RButton(uiContext, model.nextButton);
			b.setName("capture-guide");
			b.addActionListener(e -> {
				if (model.nextId == null) context.navigate(RouteId.BANK);
				else { guideOpen = !guideOpen; render(); }
			});
			next.addAll(b);
		}
		if (guideOpen && model.nextId != null) {
			next.addAll(text("capture-guidance", FontRole.SMALL, Tone.TEXT, model.guidance()));
			if (model.nextId.equals("bank")) next.addAll(bankLink("capture-guidance-bank"));
		}
		add(next);

		Card rows = new Card(uiContext, "Account observations · " + model.identity);
		rows.setName("capture-rows");
		rows.addAll(text("capture-legend", FontRole.SMALL, Tone.MUTED, "Verified · Saved · Pending"));
		for (CaptureModel.Row r : model.rows) rows.addAll(row(r));
		add(rows);
		add(new Expandable(uiContext, "How capture works", text("capture-how", FontRole.SMALL, Tone.TEXT, HOW)));
		add(text("capture-footer", FontRole.SMALL, Tone.MUTED, "Read-only observations · Offline preview"));
		revalidate();
		repaint();
	}

	private JComponent row(CaptureModel.Row r) {
		StackPanel detail = new StackPanel(3);
		detail.setOpaque(false);
		detail.add(text("capture-help-" + r.source.id, FontRole.SMALL, Tone.TEXT, r.source.help));
		detail.add(text("capture-at-" + r.source.id, FontRole.SMALL, Tone.MUTED, r.lastComplete()));
		if (r.state == CaptureModel.State.INCOMPLETE)
			detail.add(text("capture-incomplete-" + r.source.id, FontRole.SMALL, Tone.TEXT, "Incomplete evidence cannot establish a new verified result."));
		if (r.emptyBank) detail.add(text("capture-empty-bank", FontRole.SMALL, Tone.TEXT, "Complete observation verified an empty bank."));
		if (r.source.id.equals("bank")) detail.add(bankLink("capture-row-bank-link"));
		Expandable e = new Expandable(uiContext, r.source.name + " · " + r.state.label, detail);
		e.setName("capture-row-" + r.source.id);
		e.getAccessibleContext().setAccessibleName(r.source.name + ", " + r.state.label);
		e.setExpanded(open.contains(r.source.id));
		e.addExpansionListener(x -> { if (x) open.add(r.source.id); else open.remove(r.source.id); });
		return e;
	}

	private JComponent bankLink(String name) {
		RButton b = new RButton(uiContext, "View Bank Memory ›");
		b.setName(name);
		b.addActionListener(e -> context.navigate(RouteId.BANK));
		return b;
	}

	private TextBlock text(String name, FontRole role, Tone tone, String t) {
		TextBlock b = new TextBlock(uiContext, t, role, tone);
		b.setName(name);
		return b;
	}
}
