package com.feralenterprises.raccoon.playtest.r02.panel.chronicle;

import com.feralenterprises.raccoon.playtest.r02.fixture.ArchiveRecord;
import com.feralenterprises.raccoon.playtest.r02.fixture.EncounterDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.EvidenceStatus;
import com.feralenterprises.raccoon.playtest.r02.fixture.QuestState;
import com.feralenterprises.raccoon.playtest.r02.panel.outfit.CombatCatalog;
import com.feralenterprises.raccoon.playtest.r02.panel.outfit.CombatCatalog.Encounter;
import com.feralenterprises.raccoon.playtest.r02.panel.outfit.CombatCatalog.Reward;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.SearchField;
import com.feralenterprises.raccoon.ui.component.SegmentedSelector;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteAware;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JPanel;

import static com.feralenterprises.raccoon.playtest.r02.panel.outfit.CombatUi.*;

final class ChroniclePanel extends StackPanel implements LocalBack, RouteAware {
	private static final List<String> STYLES = List.of("all", "melee", "ranged", "magic");
	private static final DateTimeFormatter UTC_DATE = DateTimeFormatter.ofPattern("dd MMM · HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);
	private static final String KINGS = "dagannoth-kings";

	private enum Sort {
		EASY("Easy → Hard"), HARD("Hard → Easy"), AZ("Name A–Z"), ZA("Name Z–A"), APPROACHES("Approaches");
		final String label;
		Sort(String label) { this.label = label; }
	}

	private enum Category {
		ALL("All bosses", ""), REGULAR("Regular", "Regular"), WILD("Wilderness", "Wilderness"),
		MOONS("Moons of Peril", "Moons"), DEMI("Demi-bosses", "Demi");
		final String label, registry;
		Category(String label, String registry) { this.label = label; this.registry = registry; }
		boolean accepts(String category) { return this == ALL || (this == WILD ? category.startsWith(registry) : registry.equals(category)); }
	}

	private enum Tab { OVERVIEW, DROPS, TRIPS }

	private final RouteContext context;
	private final CombatCatalog catalog;
	private final UiContext ui;
	private final StackPanel index = new StackPanel(5);
	private final StackPanel details = new StackPanel(5);
	private final StackPanel totals = new StackPanel(3);
	private final StackPanel rows = new StackPanel(4);
	private final StackPanel detailBody = new StackPanel(5);
	private final SearchField search;
	private final Map<String, JComponent> openers = new LinkedHashMap<>();
	private Category category = Category.ALL;
	private Sort sort = Sort.EASY;
	private String approach = "all", query = "";
	private Encounter selected;
	private Tab tab = Tab.OVERVIEW;
	private Point indexScroll;
	private Runnable onBackChanged = () -> { };

	ChroniclePanel(RouteContext context, CombatCatalog catalog) {
		super(5);
		this.context = context;
		this.catalog = catalog;
		ui = context.ui();
		setName("boss-chronicle");
		Card ledger = ledgerHeader();
		ledger.add(totals);
		search = new SearchField(ui, "Search bosses…", s -> { query = s; renderRows(); });
		search.setName("chronicle-search");
		search.getAccessibleContext().setAccessibleName("Search bosses");
		ledger.add(search);
		JPanel selects = new JPanel(new GridLayout(1, 2, 4, 0));
		selects.setOpaque(false);
		RDropdown<Category> groups = new RDropdown<>(ui, "Boss category", List.of(Category.values()), v -> v.label, v -> {
			category = v;
			renderRows();
		});
		RDropdown<Sort> sorting = new RDropdown<>(ui, "Sort bosses", List.of(Sort.values()), v -> v.label, v -> {
			sort = v;
			renderRows();
		});
		groups.setName("chronicle-category");
		sorting.setName("chronicle-sort");
		selects.add(groups);
		selects.add(sorting);
		ledger.add(selects);
		SegmentedSelector styleFilter = new SegmentedSelector(ui, "All", "Melee", "Ranged", "Magic");
		styleFilter.setName("chronicle-approach");
		styleFilter.getAccessibleContext().setAccessibleName("Documented approaches");
		styleFilter.addSelectionListener(i -> { approach = STYLES.get(i); renderRows(); });
		ledger.add(styleFilter);
		index.add(ledger);
		index.add(rows);
		add(index);
		add(details);
		details.setVisible(false);
		context.onSnapshot(s -> {
			if (selected == null) renderIndex();
			else renderDetailBody();
		});
		renderIndex();
	}

	private Card ledgerHeader() {
		BufferedImage wood = context.assets().art("PR-032");
		return new Card(ui, null) {
			@Override protected void paintComponent(Graphics g) {
				if (wood == null || !ChroniclePanel.this.ui.themed()) {
					super.paintComponent(g);
					return;
				}
				Graphics2D copy = Paints.prepare(g);
				cover(copy, wood, getWidth(), getHeight());
				copy.setColor(Paints.alpha(ChroniclePanel.this.ui.palette().panel, 190));
				copy.fillRect(2, 2, getWidth() - 4, getHeight() - 4);
				copy.dispose();
			}
		};
	}

	private String name(Encounter e) {
		return context.registry().encounter(e.key).map(EncounterDef::name).orElse(e.name);
	}

	private String group(Encounter e) {
		return context.registry().encounter(e.key).map(EncounterDef::category).orElse(e.category);
	}

	private Evidence<Integer> kc(Encounter e) {
		if (KINGS.equals(e.key)) return Evidence.unknown();
		EncounterDef definition = context.registry().encounter(e.key).orElse(null);
		String key = definition == null ? e.key : definition.kcKey();
		return key == null ? Evidence.notApplicable() : context.snapshot().officialKc(key);
	}

	private static boolean current(Evidence<?> evidence) {
		return evidence.status() == EvidenceStatus.VERIFIED || evidence.status() == EvidenceStatus.VERIFIED_ZERO;
	}

	private static String kcValue(Evidence<Integer> e) {
		if (e.status() == EvidenceStatus.NOT_APPLICABLE) return "Not applicable";
		if (!e.hasValue()) return e.status() == EvidenceStatus.UNAVAILABLE ? "Unavailable" : "Unknown";
		return quantity(e.value()) + (e.status() == EvidenceStatus.STALE ? " · saved observation" : "");
	}

	private String kcLabel(Encounter encounter) {
		if (KINGS.equals(encounter.key)) return "Individual KC · Rex, Prime, Supreme";
		Evidence<Integer> value = kc(encounter);
		return value.hasValue() ? quantity(value.value()) + " KC" + (current(value) ? "" : " · saved")
			: "KC " + kcValue(value).toLowerCase(Locale.ROOT);
	}

	private String accessQuest(Encounter e) {
		if ("brutus".equals(e.key)) return "The Ides of Milk";
		if ("vorkath".equals(e.key)) return "Dragon Slayer II";
		return "";
	}

	private boolean locked(Encounter e) {
		String quest = accessQuest(e);
		Evidence<QuestState> evidence = context.snapshot().quest(quest);
		return !quest.isEmpty() && current(evidence) && evidence.value() != QuestState.COMPLETE;
	}

	private String access(Encounter e) {
		String quest = accessQuest(e);
		if (locked(e)) return "Locked · " + quest + " unmet";
		Evidence<QuestState> evidence = context.snapshot().quest(quest);
		if (!quest.isEmpty() && current(evidence)) return quest + " complete · other access unverified";
		return "Access unverified";
	}

	private void renderIndex() {
		int done = 0, zero = 0;
		for (Encounter e : catalog.encounters()) {
			Evidence<Integer> kc = kc(e);
			if (current(kc)) {
				if (kc.value() == 0) zero++;
				else done++;
			}
		}
		int total = catalog.encounters().size();
		int unknown = total - done - zero;
		totals.removeAll();
		boolean noCounts = unknown == total;
		totals.add(title(ui, noCounts ? "Boss KC unavailable" : done + " / " + total + " bosses defeated"));
		totals.add(new LedgerProgress(ui, done, zero, total));
		totals.add(note(ui, noCounts ? "No kill counts observed" : zero + " bosses at 0 KC · " + unknown + " with unknown KC"));
		renderRows();
	}

	private Comparator<Encounter> order() {
		Comparator<Encounter> alphabet = Comparator.comparing(this::name, String.CASE_INSENSITIVE_ORDER);
		switch (sort) {
			case AZ: return alphabet;
			case ZA: return alphabet.reversed();
			case APPROACHES: return Comparator.<Encounter>comparingInt(e -> -e.approaches.size()).thenComparing(alphabet);
			default: return Comparator.<Encounter, Boolean>comparing(e -> e.rank == 0)
				.thenComparingInt(e -> sort == Sort.HARD ? -e.rank : e.rank).thenComparing(alphabet);
		}
	}

	private void renderRows() {
		rows.removeAll();
		openers.clear();
		List<Encounter> matches = catalog.encounters().stream().filter(e -> category.accepts(group(e)) && e.matches(query)
			&& (approach.equals("all") || e.approaches.contains(approach))).sorted(order()).collect(Collectors.toList());
		if (matches.size() < catalog.encounters().size() && !matches.isEmpty())
			rows.add(note(ui, matches.size() + " of " + catalog.encounters().size() + " bosses"));
		if (matches.isEmpty()) rows.add(note(ui, "No matching bosses."));
		for (Encounter e : matches) {
			StackPanel text = new StackPanel(2);
			text.add(title(ui, name(e)));
			text.add(note(ui, kcLabel(e)));
			text.add(note(ui, e.difficulty + (locked(e) ? " · Locked" : "")));
			if (!e.approaches.isEmpty()) text.add(icons(e.approaches));
			FolioButton entry = new FolioButton(ui, "chronicle-boss-" + e.key,
				name(e) + ", " + kcLabel(e) + ", " + access(e) + ", " + e.difficulty, portraitRow(e, text, 47, 49),
				() -> open(e));
			rows.add(entry);
			openers.put(e.key, entry);
		}
		revalidate();
		repaint();
	}

	private JPanel icons(List<String> styles) {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 3, 0));
		row.setOpaque(false);
		for (String style : styles) row.add(picture(ui, styleIcon(context.assets(), style), CombatCatalog.styleName(style), 13, 13));
		return row;
	}

	private PictureRow portraitRow(Encounter e, JComponent body, int width, int height) {
		return new PictureRow(picture(ui, context.assets().portrait(e.key), name(e), width, height), body, width, height, 6);
	}

	private void open(Encounter e) {
		if (selected == null) indexScroll = position(this);
		selected = e;
		tab = Tab.OVERVIEW;
		index.setVisible(false);
		details.removeAll();
		SegmentedSelector tabs = new SegmentedSelector(ui, "Overview", "Drops", "Trips");
		tabs.setName("chronicle-tabs");
		tabs.getAccessibleContext().setAccessibleName("Encounter detail tabs");
		tabs.addSelectionListener(i -> { tab = Tab.values()[i]; renderDetailBody(); });
		// Keep tab and header controls in place while the observed account values are refreshed.
		details.add(detailBody);
		details.setVisible(true);
		renderDetailBody();
		details.add(tabs, 1);
		onBackChanged.run();
		restore(this, tabs, new Point());
	}

	private List<ArchiveRecord> records(Encounter e) {
		return context.snapshot().archive().stream().filter(r -> e.key.equals(r.encounterKey())).collect(Collectors.toList());
	}

	private boolean archiveKnown() {
		Evidence<Boolean> available = context.snapshot().archiveAvailable();
		return available.hasValue() && Boolean.TRUE.equals(available.value());
	}

	private String archiveStatus() {
		if (!context.snapshot().scope().signedIn()) return "Logged out";
		if (!archiveKnown()) return "Archive unavailable";
		return context.snapshot().archive().isEmpty() ? "Verified empty archive" : "Saved observations";
	}

	private void renderDetailBody() {
		Encounter e = selected;
		detailBody.removeAll();
		// The hero belongs before the stable tabs; only the body changes on a tab switch.
		StackPanel names = new StackPanel(2);
		names.add(title(ui, name(e)));
		names.add(note(ui, group(e) + " · " + e.difficulty));
		names.add(text(ui, kcLabel(e)));
		names.add(note(ui, access(e)));
		if (!e.approaches.isEmpty()) names.add(icons(e.approaches));
		Card hero = new Card(ui, null).addAll(portraitRow(e, names, 53, 64), note(ui, archiveStatus()));
		if (details.getComponentCount() > 0 && details.getComponent(0) != detailBody) details.remove(0);
		details.add(hero, 0);
		List<ArchiveRecord> records = records(e);
		switch (tab) {
			case OVERVIEW: overview(e, records); break;
			case DROPS: drops(e, records); break;
			case TRIPS: trips(e, records); break;
			default: throw new AssertionError(tab);
		}
		if (locked(e)) {
			String milestone = "brutus".equals(e.key) ? "the-ides-of-milk" : "dragon-slayer-ii";
			detailBody.add(button(ui, "Plan unmet access requirement", "chronicle-plan",
				() -> context.navigate(RouteId.PATHFINDER, RouteRequest.Param.MILESTONE, milestone)));
		}
		detailBody.add(button(ui, "Open in Outfitter", "chronicle-outfit",
			() -> context.navigate(RouteId.OUTFIT, RouteRequest.Param.ENCOUNTER, e.key)));
		detailBody.add(note(ui, "Reference catalog · offline review"));
		revalidate();
		repaint();
	}

	private void overview(Encounter e, List<ArchiveRecord> records) {
		Card own = new Card(ui, "Your record");
		if (KINGS.equals(e.key)) {
			for (String king : List.of("rex", "prime", "supreme")) {
				String exactKey = "dagannoth-" + king;
				String key = context.registry().encounter(exactKey).map(EncounterDef::kcKey).orElse(exactKey);
				own.add(text(ui, CombatCatalog.styleName(king) + " KC: " + kcValue(context.snapshot().officialKc(key))));
			}
		} else {
			own.add(text(ui, "Official KC: " + kcValue(kc(e))));
		}
		String kind = "barrows".equals(e.key) ? "chests" : "kills";
		String count = archiveKnown() && !records.isEmpty() && records.stream().allMatch(r -> kind.equalsIgnoreCase(r.unitLabel()))
			? quantity(records.stream().mapToLong(ArchiveRecord::units).sum()) : "Unknown";
		own.add(text(ui, "Recorded trip " + kind + ": " + count));
		own.add(note(ui, "Best time: Unknown · Average time: Unknown"));
		own.add(note(ui, "Official KC is a separate account observation. Saved trip counts do not establish lifetime KC."));
		detailBody.add(own);
		Card encounter = new Card(ui, "Encounter");
		encounter.add(text(ui, "Difficulty: " + e.difficulty));
		encounter.add(text(ui, "Documented approaches: " + styles(e.approaches)));
		encounter.add(text(ui, "Archived loadouts: " + (e.kits.isEmpty() ? "None archived" : styles(List.copyOf(e.kits.keySet())))));
		if (!e.strategy.isEmpty()) encounter.add(note(ui, e.strategy));
		if (!e.strategyQuality.isEmpty()) encounter.add(note(ui, e.strategyQuality));
		addSource(encounter, ui, "Strategy source", e.strategySource);
		encounter.add(text(ui, access(e)));
		if (!e.accessReference.isEmpty()) encounter.add(note(ui, e.accessReference));
		addSource(encounter, ui, "OSRS Wiki reference", e.wiki);
		detailBody.add(encounter);
	}

	private static String styles(List<String> styles) {
		return styles.isEmpty() ? "Unknown" : styles.stream().map(CombatCatalog::styleName).collect(Collectors.joining(", "));
	}

	private void drops(Encounter e, List<ArchiveRecord> records) {
		Card reference = new Card(ui, "Notable drop reference");
		if (e.rewards.isEmpty()) reference.add(note(ui, "No sourced drop list in this preview."));
		for (Reward reward : e.rewards) {
			StackPanel name = new StackPanel(2);
			name.add(title(ui, reward.name));
			name.add(note(ui, (reward.unit.isEmpty() ? "Encounter reference" : reward.unit) + " · "
				+ (reward.condition.isEmpty() ? "Eligibility unknown" : reward.condition)));
			name.add(note(ui, reward.note.isEmpty() ? "Rate not established in this preview" : reward.note));
			addSource(name, ui, "Reward source", reward.source.isEmpty() ? e.rewardSource : reward.source);
			BufferedImage sprite = reward.id == null ? null : catalog.itemImage(context.assets(), reward.id);
			PictureRow row = new PictureRow(picture(ui, sprite, reward.name, 25, 27), name, 25, 27, 5);
			row.setName("chronicle-reward");
			reference.add(row);
		}
		reference.add(note(ui, "Archived reward reference. Current rates and collection status are unverified."));
		detailBody.add(reference);
		if (!e.rewardGaps.isEmpty()) {
			Card gaps = new Card(ui, "Conditional rewards / evidence gaps");
			for (String gap : e.rewardGaps) gaps.add(note(ui, gap));
			detailBody.add(gaps);
		}
		Card observed = new Card(ui, "Observed archived loot");
		if (!archiveKnown()) observed.add(note(ui, "Incomplete or unavailable observations. Quantities unknown."));
		else if (records.stream().allMatch(r -> r.loot().isEmpty()))
			observed.add(note(ui, "No matching loot entries in the available archive."));
		else {
			for (ArchiveRecord record : records) {
				record.loot().forEach((id, count) -> {
					String name = context.registry().itemName(id);
					observed.add(new PictureRow(picture(ui, catalog.itemImage(context.assets(), id), name, 25, 27),
						text(ui, name + " × " + quantity(count)), 25, 27, 5));
				});
			}
		}
		detailBody.add(observed);
	}

	private void trips(Encounter e, List<ArchiveRecord> records) {
		Card trips = new Card(ui, "Recorded sessions");
		if (!archiveKnown()) trips.add(note(ui, "Archive unavailable."));
		else if (records.isEmpty()) trips.add(note(ui, "No matching saved sessions in the available archive."));
		else for (ArchiveRecord record : records) {
			String metric = "barrows".equals(e.key) && !"chests".equalsIgnoreCase(record.unitLabel())
				? "Count unavailable" : quantity(record.units()) + " " + record.unitLabel();
			StackPanel body = new StackPanel(2);
			body.add(title(ui, name(e)));
			body.add(note(ui, UTC_DATE.format(record.started()) + " · " + metric));
			trips.add(new FolioButton(ui, "chronicle-trip-" + record.id(), name(e) + " saved session · " + metric,
				portraitRow(e, body, 35, 34), () -> context.navigate(RouteId.FILES, RouteRequest.Param.SESSION, record.id())));
		}
		detailBody.add(trips);
		detailBody.add(new Card(ui, "Supply records").addAll(note(ui, "Open a recorded trip for observed supplies.")));
	}

	@Override public boolean hasLocalBack() { return selected != null; }

	@Override public void localBack() {
		if (selected == null) return;
		String key = selected.key;
		selected = null;
		details.removeAll();
		details.setVisible(false);
		index.setVisible(true);
		renderIndex();
		onBackChanged.run();
		restore(this, openers.getOrDefault(key, search), indexScroll);
	}

	@Override public void setLocalBackListener(Runnable listener) { onBackChanged = listener; }

	@Override public void routeShown(RouteRequest request, boolean returning) {
		if (returning || request.param() != RouteRequest.Param.ENCOUNTER) return;
		Encounter e = catalog.encounter(request.paramId());
		if (e != null) open(e);
	}

	private static final class LedgerProgress extends JComponent implements HeightForWidth {
		private final UiContext ui;
		private final int done, zero, total;

		LedgerProgress(UiContext ui, int done, int zero, int total) {
			this.ui = ui;
			this.done = done;
			this.zero = zero;
			this.total = total;
		}

		@Override public Dimension getPreferredSize() { return new Dimension(1, 7); }
		@Override public int heightForWidth(int width) { return 7; }

		@Override protected void paintComponent(Graphics g) {
			Graphics2D copy = Paints.prepare(g);
			copy.setColor(ui.palette().trough);
			copy.fillRect(0, 0, getWidth(), getHeight());
			int verified = getWidth() * done / total;
			copy.setColor(ui.palette().accent);
			copy.fillRect(0, 0, verified, getHeight());
			copy.setColor(ui.palette().accentDim);
			copy.fillRect(verified, 0, getWidth() * zero / total, getHeight());
			copy.setColor(ui.palette().border);
			copy.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
			copy.dispose();
		}
	}
}
