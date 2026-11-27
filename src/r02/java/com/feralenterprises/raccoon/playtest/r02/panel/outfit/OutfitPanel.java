package com.feralenterprises.raccoon.playtest.r02.panel.outfit;

import com.feralenterprises.raccoon.playtest.r02.fixture.Container;
import com.feralenterprises.raccoon.playtest.r02.fixture.ContainerState;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.panel.outfit.CombatCatalog.Encounter;
import com.feralenterprises.raccoon.playtest.r02.panel.outfit.CombatCatalog.Kit;
import com.feralenterprises.raccoon.playtest.r02.panel.outfit.CombatCatalog.Slot;
import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.ItemSlots;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.SearchField;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.layout.ColumnsPanel;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteAware;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import static com.feralenterprises.raccoon.playtest.r02.panel.outfit.CombatUi.*;

final class OutfitPanel extends StackPanel implements LocalBack, RouteAware {
	private static final List<String> STYLES = List.of("melee", "ranged", "magic");
	private static final String[] SLOTS = {"Head", "Cape", "Neck", "Ammo", "Weapon", "Body", "Shield", "Legs", "Gloves", "Boots", "Ring"};
	private static final int[][] DOLL = {{1,0},{0,1},{1,1},{2,1},{0,2},{1,2},{2,2},{1,3},{0,4},{1,4},{2,4}};

	private enum Source {
		WIKI("Wiki reference · example"), BANK("Best in Bank"), CUSTOM("Custom");
		final String label;
		Source(String label) { this.label = label; }
	}

	private final RouteContext context;
	private final CombatCatalog catalog;
	private final UiContext ui;
	private final StackPanel hero = new StackPanel(0);
	private final StackPanel body = new StackPanel(5);
	private final StackPanel detail = new StackPanel(0);
	private final RDropdown<Encounter> picker;
	private final SearchField search;
	private final JComponent noMatches;
	private final Map<String, String> styles = new HashMap<>();
	private final Set<String> copiedKits = new HashSet<>();
	private Encounter selected;
	private TextBlock bankTitle;
	private Source source = Source.WIKI;
	private boolean showDifferences, notesOpen = true, dropsOpen;
	private JComponent slotOpener;
	private Point slotScroll;
	private Runnable onBackChanged = () -> { };

	OutfitPanel(RouteContext context, CombatCatalog catalog) {
		super(5);
		this.context = context;
		this.catalog = catalog;
		ui = context.ui();
		selected = catalog.encounter("vorkath");
		setName("outfitter");
		add(hero);
		Card encounter = card("Encounter");
		search = new SearchField(ui, "Find a boss…", this::filter);
		search.setName("outfit-search");
		search.getAccessibleContext().setAccessibleName("Find a boss");
		picker = new RDropdown<>(ui, "Encounter", catalog.encounters(), this::name, this::select);
		picker.setName("outfit-encounter");
		picker.restore(selected);
		search.addActionListener(event -> {
			if (picker.getItemCount() == 0) return;
			Encounter first = picker.getItemAt(0);
			select(first == selected && picker.getItemCount() > 1 ? picker.getItemAt(1) : first);
		});
		noMatches = note(ui, "No matching bosses.");
		noMatches.setVisible(false);
		encounter.addAll(search, picker, noMatches);
		add(encounter);
		add(detail);
		detail.setVisible(false);
		add(body);
		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "closeSlot");
		getActionMap().put("closeSlot", new AbstractAction() {
			@Override public void actionPerformed(java.awt.event.ActionEvent e) { if (hasLocalBack()) localBack(); }
		});
		context.onSnapshot(s -> { if (bankTitle != null) bankTitle.setText(bankStatus()); });
		renderHero();
		refreshBody();
	}

	private String name(Encounter e) {
		return context.registry().encounter(e.key).map(d -> d.name()).orElse(e.name);
	}

	private Card card(String title) {
		return card(title, false);
	}

	private Card card(String title, boolean scene) {
		return new Card(ui, title) {
			@Override protected void paintComponent(Graphics g) {
				if (!OutfitPanel.this.ui.themed()) {
					super.paintComponent(g);
					return;
				}
				Graphics2D copy = Paints.prepare(g);
				BufferedImage texture = catalog.art(selected.environment, scene);
				if (texture != null) cover(copy, texture, getWidth(), getHeight());
				copy.setColor(Paints.alpha(outfitSurface(selected.environment), texture == null ? 222 : scene ? 147 : 192));
				copy.fillRect(2, 2, getWidth() - 4, getHeight() - 4);
				copy.dispose();
			}
		};
	}

	private void filter(String query) {
		List<Encounter> matches = catalog.encounters().stream().filter(e -> e.matches(query)).collect(Collectors.toList());
		picker.setOptions(matches);
		picker.restore(selected);
		noMatches.setVisible(matches.isEmpty());
		revalidate();
	}

	private void select(Encounter encounter) {
		selected = encounter;
		search.setText("");
		filter("");
		showDifferences = false;
		renderHero();
		refreshBody();
		restore(this, picker, new Point());
	}

	private void renderHero() {
		hero.removeAll();
		StackPanel heading = new StackPanel(2);
		heading.add(title(ui, name(selected)));
		if (!selected.area.isEmpty()) heading.add(note(ui, selected.area));
		hero.add(card(null, true).addAll(new PictureRow(picture(ui, context.assets().portrait(selected.key), name(selected), 74, 66),
			heading, 74, 66, 7)));
		hero.revalidate();
		hero.repaint();
	}

	private String style() { return styles.getOrDefault(selected.key, selected.defaultStyle); }
	private String kitKey() { return selected.key + "|" + style(); }

	private Kit shownKit() {
		if (source == Source.WIKI || source == Source.CUSTOM && copiedKits.contains(kitKey())) return selected.kits.get(style());
		return null;
	}

	private void refreshBody() {
		if (hasLocalBack()) closeSlot(false);
		body.removeAll();
		bankTitle = null;
		Kit example = selected.kits.get(style());
		Kit kit = shownKit();
		Card prep = card("Prepare your kit");
		RDropdown<Source> sources = new RDropdown<>(ui, "Gear source", List.of(Source.values()), s -> s.label, s -> {
			source = s;
			showDifferences = false;
			refreshBody();
			restore(this, bodyControl("outfit-source"), null);
		});
		sources.setName("outfit-source");
		sources.restore(source);
		prep.addAll(note(ui, "Gear source"), sources, styleButtons());
		switch (source) {
			case WIKI:
				prep.add(title(ui, example == null ? "No verified loadout fixture" : example.label));
				prep.add(example == null ? note(ui, "No equipment or supply plan recovered.") : preparationCheck(example));
				break;
			case BANK:
				bankTitle = title(ui, bankStatus());
				prep.add(bankTitle);
				prep.add(note(ui, "An approved planner result is required before recommending owned gear."));
				break;
			case CUSTOM:
				prep.add(title(ui, kit == null ? "No saved custom setup" : "Custom · session demo"));
				prep.add(note(ui, kit == null ? "No setup captured here." : "Explicit copy of archived example; no account evidence."));
				if (example != null) prep.add(button(ui, kit == null ? "Copy example as demo setup" : "Clear demo setup", "outfit-custom", () -> {
					if (kit == null) copiedKits.add(kitKey());
					else copiedKits.remove(kitKey());
					refreshBody();
					restore(this, bodyControl("outfit-custom"), null);
				}));
				break;
			default: throw new AssertionError(source);
		}
		if (source == Source.WIKI && kit != null) addSwitches(prep, kit);
		body.add(prep);

		Card equipment = card("Equipment", true);
		equipment.add(note(ui, kit == null ? "Unknown" : CombatCatalog.styleName(style())));
		equipment.add(grid(kit == null ? Collections.nCopies(11, Slot.UNKNOWN) : kit.gear, true));
		equipment.add(note(ui, "? Unknown · blank = known empty · sprite = supplied"));
		body.add(equipment);

		Card inventory = card("Inventory");
		long supplied = kit == null ? 0 : kit.inventory.stream().filter(s -> s.state == Slot.State.SUPPLIED).count();
		inventory.add(note(ui, kit == null ? "Unknown" : supplied + " / 28"));
		inventory.add(grid(kit == null ? Collections.nCopies(28, Slot.UNKNOWN) : kit.inventory, false));
		body.add(inventory);
		body.add(prepNotes(example, kit));
		body.add(rareDrops());
		revalidate();
		repaint();
	}

	private String bankStatus() {
		ContainerState bank = context.snapshot().container(Container.BANK).state();
		return bank == ContainerState.UNAVAILABLE ? "Bank snapshot unavailable" : "No account-aware plan available";
	}

	private JComponent preparationCheck(Kit example) {
		if (example.checkIcon.startsWith("i") && example.checkIcon.matches("i[0-9]+\\.png")) {
			int id = Integer.parseInt(example.checkIcon.substring(1, example.checkIcon.length() - 4));
			return new PictureRow(picture(ui, catalog.itemImage(context.assets(), id), context.registry().itemName(id), 17, 17),
				note(ui, example.check), 17, 17, 5);
		}
		if (example.checkIcon.startsWith("s-") && example.checkIcon.endsWith(".png")) {
			String skill = example.checkIcon.substring(2, example.checkIcon.length() - 4);
			return new PictureRow(picture(ui, context.assets().skillIcon(skill), skill, 17, 17),
				note(ui, example.check), 17, 17, 5);
		}
		return note(ui, example.check);
	}

	private ColumnsPanel styleButtons() {
		ColumnsPanel row = new ColumnsPanel(3, 3);
		for (String s : STYLES) {
			RButton button = button(ui, CombatCatalog.styleName(s), "outfit-style-" + s, () -> chooseStyle(s));
			button.setSelected(s.equals(style()));
			button.setEnabled(selected.kits.containsKey(s));
			if (!button.isEnabled()) button.setToolTipText("No archived fixture for this style");
			row.add(button);
		}
		return row;
	}

	private void chooseStyle(String style) {
		styles.put(selected.key, style);
		showDifferences = false;
		refreshBody();
		restore(this, bodyControl("outfit-style-" + style), null);
	}

	private void addSwitches(Card card, Kit kit) {
		boolean comparison = "compare-only".equals(selected.switchMode);
		List<String> targets = comparison
			? selected.kits.keySet().stream().filter(s -> !s.equals(style())).collect(Collectors.toList())
			: style().equals(selected.switchPrimary) ? selected.switchTargets : List.of();
		if (!targets.isEmpty()) {
			card.add(title(ui, comparison ? "Multi-style comparison" : "Hybrid switch preview"));
			card.add(note(ui, comparison ? "Separate archived kits. A combined 28-slot trip is not verified."
				: "Only matching items in the displayed inventory are marked packed. Switch coverage is incomplete."));
			for (String target : targets) {
				card.add(button(ui, "View " + CombatCatalog.styleName(target) + " kit", "outfit-compare-" + target, () -> chooseStyle(target)));
				if (showDifferences) {
					Kit other = selected.kits.get(target);
					for (int i = 0; i < SLOTS.length; i++) {
						Slot item = other.gear.get(i);
						if (item.state != Slot.State.SUPPLIED || item.id == kit.gear.get(i).id) continue;
						String status = comparison ? "compare only" : kit.packed(item.id) ? "in example inventory" : "not in example inventory";
						card.add(itemLine(item.id, SLOTS[i] + ": " + context.registry().itemName(item.id) + " · " + status));
					}
				}
			}
			card.add(button(ui, (showDifferences ? "Hide" : "Show") + " switch differences", "outfit-switch", () -> {
				showDifferences = !showDifferences;
				refreshBody();
				restore(this, bodyControl("outfit-switch"), null);
			}));
		}
		if (!selected.switchSupplies.isEmpty()) {
			card.add(title(ui, "Archived switch supplies"));
			for (CombatCatalog.SwitchSupplies supplies : selected.switchSupplies) {
				String items = supplies.itemIds.stream().map(id -> context.registry().itemName(id) + (kit.packed(id) ? " (packed)" : " (not packed)"))
					.collect(Collectors.joining(" · "));
				card.add(note(ui, supplies.role + ": " + items));
			}
			card.add(note(ui, "Profile evidence only; live readiness unknown."));
		}
	}

	private JComponent itemLine(int id, String text) {
		String name = context.registry().itemName(id);
		return new PictureRow(picture(ui, catalog.itemImage(context.assets(), id), name, 24, 24), note(ui, text), 24, 24, 5);
	}

	private JComponent prepNotes(Kit example, Kit kit) {
		StackPanel details = new StackPanel(4);
		if (source == Source.WIKI && example != null) {
			details.add(text(ui, example.note));
			details.add(note(ui, example.evidence));
			if (!example.issues.isEmpty()) details.add(note(ui, "Evidence conflicts: " + String.join("; ", example.issues)));
		} else if (source == Source.WIKI) {
			details.add(note(ui, "No encounter-specific loadout was recovered. Gear and supplies remain unknown."));
		} else if (source == Source.BANK) {
			details.add(note(ui, "Preparation requires a selected Best in Bank plan and verified account state."));
		} else {
			details.add(note(ui, kit == null ? "Preparation is unknown without a custom loadout."
				: "This demo setup is session-only. Check requirements, supplies and route manually."));
		}
		addSource(details, ui, "Wiki strategy", source == Source.WIKI && example != null ? example.source : selected.strategySource);
		Expandable section = new Expandable(ui, "Preparation notes", details);
		section.setExpanded(notesOpen);
		section.addExpansionListener(open -> notesOpen = open);
		return card(null).addAll(section);
	}

	private JComponent rareDrops() {
		StackPanel details = new StackPanel(4);
		details.add(note(ui, "Archived notable-drop index. Live collection status and current rates are unverified."));
		for (CombatCatalog.Reward reward : selected.rewards) {
			String label = reward.name + (reward.condition.isEmpty() ? "" : " · " + reward.condition);
			details.add(reward.id == null ? text(ui, label) : itemLine(reward.id, label));
		}
		for (String gap : selected.rewardGaps) details.add(note(ui, "Not modelled: " + gap));
		addSource(details, ui, "Wiki drops", selected.rewardSource);
		Expandable section = new Expandable(ui, "Rare drops", details);
		section.setExpanded(dropsOpen);
		section.addExpansionListener(open -> dropsOpen = open);
		return card(null).addAll(section);
	}

	private JPanel grid(List<Slot> slots, boolean equipment) {
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);
		grid.setName(equipment ? "outfit-paperdoll" : "outfit-inventory");
		grid.getAccessibleContext().setAccessibleName(equipment ? "11 equipment slots" : "28 inventory slots");
		for (int index = 0; index < slots.size(); index++) {
			String label = equipment ? SLOTS[index] : "Inventory " + (index + 1);
			Slot slot = slots.get(index);
			RButton control = slotButton(label, slot);
			control.setName((equipment ? "gear-" : "inventory-") + index);
			GridBagConstraints c = new GridBagConstraints();
			c.gridx = equipment ? DOLL[index][0] : index % 4;
			c.gridy = equipment ? DOLL[index][1] : index / 4;
			c.insets = new Insets(2, 3, 2, 3);
			grid.add(control, c);
		}
		return grid;
	}

	private String slotStatus(Slot slot) {
		switch (slot.state) {
			case EMPTY: return "Confirmed empty";
			case UNKNOWN: return "Unknown · no source evidence";
			default: return context.registry().itemName(slot.id) + (slot.quantity == 1 ? "" : " × " + quantity(slot.quantity));
		}
	}

	private RButton slotButton(String label, Slot slot) {
		final BufferedImage sprite = slot.state == Slot.State.SUPPLIED ? catalog.itemImage(context.assets(), slot.id) : null;
		final Icon image = sprite == null ? null : icon(sprite, 34, 30);
		RButton control = new RButton(ui, "") {
			@Override public int heightForWidth(int width) { return 32; }
			@Override protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				Graphics2D copy = Paints.prepare(g);
				if (image != null) image.paintIcon(this, copy, (getWidth() - image.getIconWidth()) / 2, (getHeight() - image.getIconHeight()) / 2);
				else if (slot.state != Slot.State.EMPTY) {
					String label = slot.state == Slot.State.UNKNOWN ? "?" : Integer.toString(slot.id);
					copy.setFont(OutfitPanel.this.ui.font(FontRole.QUANTITY));
					copy.setColor(OutfitPanel.this.ui.palette().muted);
					copy.drawString(label, (getWidth() - copy.getFontMetrics().stringWidth(label)) / 2, (getHeight() + copy.getFontMetrics().getAscent()) / 2);
				}
				if (slot.quantity > 1) {
					copy.setFont(OutfitPanel.this.ui.font(FontRole.QUANTITY));
					copy.setColor(ItemSlots.quantityColor(slot.quantity));
					copy.drawString(ItemSlots.quantityText(slot.quantity), 3, copy.getFontMetrics().getAscent() + 1);
				}
				copy.dispose();
			}
		};
		control.setPreferredSize(new Dimension(36, 32));
		control.setToolTipText(label + ": " + slotStatus(slot));
		control.getAccessibleContext().setAccessibleName(control.getToolTipText());
		control.addActionListener(event -> showSlot(control, label, slot));
		control.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "slot");
		control.getActionMap().put("slot", new AbstractAction() {
			@Override public void actionPerformed(java.awt.event.ActionEvent e) { control.doClick(); }
		});
		return control;
	}

	private void showSlot(JComponent opener, String label, Slot slot) {
		slotScroll = position(this);
		slotOpener = opener;
		detail.removeAll();
		Card card = card("Slot details");
		card.add(title(ui, label));
		card.add(note(ui, slotStatus(slot)));
		if (slot.state == Slot.State.SUPPLIED) {
			card.add(itemLine(slot.id, "Item ID " + slot.id + " · Quantity " + quantity(slot.quantity)));
			card.add(note(ui, "Supplied by the selected setup. Account ownership is a separate observation."));
		}
		RButton close = button(ui, "Close", "outfit-slot-close", this::localBack);
		card.add(close);
		detail.add(card);
		detail.setVisible(true);
		revalidate();
		onBackChanged.run();
		SwingUtilities.invokeLater(() -> {
			if (slotOpener != opener || viewport(this) == null) return;
			detail.scrollRectToVisible(new java.awt.Rectangle(0, 0, detail.getWidth(), detail.getHeight()));
			close.requestFocusInWindow();
		});
	}

	private void closeSlot(boolean restoreFocus) {
		JComponent opener = slotOpener;
		Point scroll = slotScroll;
		slotOpener = null;
		slotScroll = null;
		detail.removeAll();
		detail.setVisible(false);
		revalidate();
		repaint();
		onBackChanged.run();
		if (restoreFocus) restore(this, opener, scroll);
	}

	private JComponent bodyControl(String name) {
		return find(body, name);
	}

	private static JComponent find(JComponent parent, String name) {
		for (java.awt.Component child : parent.getComponents()) {
			if (name.equals(child.getName())) return (JComponent) child;
			if (child instanceof JComponent) {
				JComponent found = find((JComponent) child, name);
				if (found != null) return found;
			}
		}
		return null;
	}

	@Override public boolean hasLocalBack() { return slotOpener != null; }
	@Override public void localBack() { if (hasLocalBack()) closeSlot(true); }
	@Override public void setLocalBackListener(Runnable listener) { onBackChanged = listener; }

	@Override public void routeShown(RouteRequest request, boolean returning) {
		if (returning || request.param() != RouteRequest.Param.ENCOUNTER) return;
		Encounter target = catalog.encounter(request.paramId());
		if (target == null) return;
		source = Source.WIKI;
		select(target);
	}
}
