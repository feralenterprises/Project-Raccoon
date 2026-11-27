package com.feralenterprises.raccoon.playtest.r02.panel.settings;

import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.Appearance;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.component.Tone;
import com.feralenterprises.raccoon.ui.layout.ColumnsPanel;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.TextScale;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

final class SettingsPanel extends StackPanel implements LocalBack {
	private enum Reset {
		PREFERENCES("Reset demo preferences", "Restore appearance defaults. Your demo observations will stay as they are."),
		OBSERVATIONS("Reset demo observations", "Reset the simulator observations for this review. Your appearance preferences will stay as they are.");
		final String label, detail;
		Reset(String label, String detail) { this.label = label; this.detail = detail; }
	}

	private final RouteContext context;
	private final UiContext ui;
	private final AccountScope openedScope;
	private final Consumer<Appearance> updateAppearance;
	private final StackPanel page = new StackPanel(5);
	private final StackPanel confirmation = new StackPanel(0);
	private final StackPanel body = new StackPanel(5);
	private final RButton interfaceTab, trackingTab, resetPreferences, resetObservations;
	private final TextBlock notice;
	private final RDropdown<String> size, surface;
	private final RDropdown<Appearance.Accent> accent;
	private final RDropdown<Appearance.Weight> weight;
	private final Card interfacePage, combat, account, privacy, experimental;
	private boolean tracking;
	private Reset pending;
	private Runnable backChanged = () -> { };

	SettingsPanel(RouteContext context, Consumer<Appearance> updateAppearance) {
		super(0);
		this.context = context;
		this.updateAppearance = updateAppearance;
		ui = context.ui();
		openedScope = context.snapshot().scope();
		setName("r02-settings");
		add(page);
		add(confirmation);
		confirmation.setVisible(false);

		ColumnsPanel tabs = new ColumnsPanel(2, 4);
		interfaceTab = button("settings-interface", "Interface", () -> showTab(false));
		trackingTab = button("settings-tracking", "Combat & Tracking", () -> showTab(true));
		tabs.add(interfaceTab);
		tabs.add(trackingTab);
		page.add(tabs);
		page.add(body);

		interfacePage = new Card(ui, "Appearance");
		size = new RDropdown<>(ui, "Text size", List.of("Compact", "Default", "Large", "Extra large"),
			v -> isSizeAvailable(v) ? v : v + " (unavailable)", this::changeSize);
		accent = new RDropdown<>(ui, "Accent", List.of(Appearance.Accent.values()), SettingsPanel::label,
			v -> apply(context.appearance().withAccent(v)));
		weight = new RDropdown<>(ui, "Text weight", List.of(Appearance.Weight.values()), SettingsPanel::label,
			v -> apply(context.appearance().withWeight(v)));
		surface = new RDropdown<>(ui, "Surface", List.of("Stone", "Bronze"),
			v -> v.equals("Bronze") ? "Bronze (unavailable)" : v, this::changeSurface);
		field(interfacePage, "size", "Text size", size);
		field(interfacePage, "accent", "Accent", accent);
		field(interfacePage, "weight", "Text weight", weight);
		field(interfacePage, "surface", "Surface", surface);
		notice = note("Accent and weight are shared preview settings. This framework renders the selected text size.");
		notice.setName("settings-appearance-status");
		interfacePage.add(notice);

		combat = new Card(ui, "Combat");
		combat.add(button("settings-outfit", "Open Outfitter", () -> navigate(RouteId.OUTFIT)));
		StackPanel bestInBank = new StackPanel(3);
		bestInBank.add(note("Native controls unavailable here."));
		for (String option : List.of("Assume thrall", "Special attacks during kill", "Wilderness bosses")) bestInBank.add(note(option));
		Expandable unavailable = new Expandable(ui, "Best in Bank settings", bestInBank);
		unavailable.setName("settings-best-in-bank");
		combat.add(unavailable);

		account = new Card(ui, "Account & data").addAll(
			button("settings-capture", "Account Capture", () -> navigate(RouteId.CAPTURE)),
			button("settings-bank", "Bank Memory", () -> navigate(RouteId.BANK)),
			note("All observations are simulated. Changes stay in this review."));
		privacy = new Card(ui, "Privacy").addAll(note("This offline review reads no credentials and has no account connection."));
		experimental = new Card(ui, "Experimental features").addAll(
			note("Bandit’s Bounties is an offline preview. Public availability and GIM are deferred."));

		resetPreferences = button("settings-reset-preferences", Reset.PREFERENCES.label, () -> confirm(Reset.PREFERENCES));
		resetObservations = button("settings-reset-observations", Reset.OBSERVATIONS.label, () -> confirm(Reset.OBSERVATIONS));
		page.add(new Card(ui, "Reset").addAll(resetPreferences, resetObservations));

		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "cancelReset");
		getActionMap().put("cancelReset", new AbstractAction() {
			@Override public void actionPerformed(ActionEvent e) { localBack(); }
		});
		context.onAppearance(this::refreshAppearance);
		refreshAppearance(context.appearance());
		showTab(false);
	}

	private static String label(Enum<?> option) {
		String name = option.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private TextBlock note(String copy) { return new TextBlock(ui, copy, FontRole.SMALL, Tone.MUTED); }
	private RButton button(String id, String label, Runnable action) {
		RButton button = new RButton(ui, label);
		button.setName(id);
		button.addActionListener(e -> action.run());
		button.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "activate");
		button.getActionMap().put("activate", new AbstractAction() {
			@Override public void actionPerformed(ActionEvent e) { button.doClick(0); }
		});
		return button;
	}

	private void field(Card card, String id, String name, JComponent component) {
		StackPanel field = new StackPanel(1);
		component.setName("settings-" + id);
		field.add(note(name));
		field.add(component);
		card.add(field);
	}

	private void showTab(boolean showTracking) {
		if (tracking == showTracking && body.getComponentCount() > 0) return;
		tracking = showTracking;
		interfaceTab.setSelected(!tracking);
		trackingTab.setSelected(tracking);
		body.removeAll();
		if (tracking) {
			body.add(combat);
			body.add(account);
			body.add(privacy);
			body.add(experimental);
		} else {
			body.add(interfacePage);
			body.add(new Card(ui, "Navigation").addAll(button("settings-tools", "Tools", () -> navigate(RouteId.TOOLS))));
		}
		revalidate();
		repaint();
	}

	private void refreshAppearance(Appearance value) {
		size.restore(label(value.size()));
		accent.restore(value.accent());
		weight.restore(value.weight());
		String current = label(value.surface());
		surface.setOptions(value.surface() == Appearance.Surface.STONE ? List.of("Stone", "Bronze")
			: List.of("Stone", "Bronze", current));
		surface.restore(current);
		ui.setTextScale(value.size() == Appearance.Size.LARGE ? TextScale.LARGE : TextScale.DEFAULT);
	}

	private static boolean isSizeAvailable(String value) { return value.equals("Default") || value.equals("Large"); }
	private void changeSize(String value) {
		if (isSizeAvailable(value)) {
			apply(context.appearance().withSize(Appearance.Size.valueOf(value.toUpperCase(Locale.ROOT))));
		} else {
			size.restore(label(context.appearance().size()));
			notice.setText(value + " text is unavailable in this preview.");
		}
	}

	private void changeSurface(String value) {
		if (value.equals("Stone")) {
			apply(context.appearance().withSurface(Appearance.Surface.STONE));
		} else if (value.equals("Bronze")) {
			surface.restore(label(context.appearance().surface()));
			notice.setText("The Bronze surface is unavailable in this preview.");
		}
	}

	private boolean currentScope() { return openedScope.equals(context.snapshot().scope()); }
	private void apply(Appearance value) { if (currentScope()) updateAppearance.accept(value); }
	private void navigate(RouteId route) { if (currentScope()) context.navigate(route); }

	private void confirm(Reset reset) {
		if (!currentScope() || pending != null) return;
		pending = reset;
		RButton cancel = button("settings-cancel", "Cancel", this::localBack);
		RButton submit = button("settings-confirm", "Reset", this::applyReset);
		ColumnsPanel actions = new ColumnsPanel(2, 4);
		actions.add(cancel);
		actions.add(submit);
		confirmation.add(new Card(ui, "Reset demo?").addAll(note(reset.detail), actions));
		page.setVisible(false);
		confirmation.setVisible(true);
		backChanged.run();
		revalidate();
		repaint();
		SwingUtilities.invokeLater(cancel::requestFocusInWindow);
	}

	private void applyReset() {
		Reset reset = pending;
		if (reset == null || !currentScope()) return;
		localBack();
		if (reset == Reset.PREFERENCES) {
			apply(Appearance.DEFAULTS);
			notice.setText("Demo preferences restored.");
			showTab(false);
		} else {
			context.dispatch(FixtureAction.of(FixtureAction.Kind.RESET_OBSERVATIONS));
		}
	}

	@Override public boolean hasLocalBack() { return pending != null; }
	@Override public void localBack() {
		Reset closing = pending;
		if (closing == null) return;
		pending = null;
		confirmation.removeAll();
		confirmation.setVisible(false);
		page.setVisible(true);
		backChanged.run();
		revalidate();
		repaint();
		SwingUtilities.invokeLater((closing == Reset.PREFERENCES ? resetPreferences : resetObservations)::requestFocusInWindow);
	}

	@Override public void setLocalBackListener(Runnable listener) { backChanged = listener; }
}
