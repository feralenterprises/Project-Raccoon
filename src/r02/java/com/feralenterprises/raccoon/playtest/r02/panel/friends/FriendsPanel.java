package com.feralenterprises.raccoon.playtest.r02.panel.friends;

import com.feralenterprises.raccoon.playtest.r02.fixture.AccountScope;
import com.feralenterprises.raccoon.playtest.r02.fixture.Evidence;
import com.feralenterprises.raccoon.playtest.r02.fixture.EvidenceStatus;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.Ownership;
import com.feralenterprises.raccoon.playtest.r02.panel.friends.FriendsCatalog.EncounterLink;
import com.feralenterprises.raccoon.playtest.r02.panel.friends.FriendsCatalog.Method;
import com.feralenterprises.raccoon.playtest.r02.panel.friends.FriendsCatalog.Pet;
import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteContext;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import com.feralenterprises.raccoon.ui.component.Card;
import com.feralenterprises.raccoon.ui.component.Expandable;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.component.SearchField;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.component.Tone;
import com.feralenterprises.raccoon.ui.layout.ColumnsPanel;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.layout.StackLayout;
import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.shell.LocalBack;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

final class FriendsPanel extends StackPanel implements LocalBack {
	private enum View { HOME, HUNT, ALBUM, WISHLIST, ARRIVALS, COMPANIONS, PET, METHODS, HELP }

	private enum Status {
		ALL("All statuses"), OBTAINED("Obtained"), ABSENT("Not yet obtained"), UNKNOWN("Unknown"), UNMAPPED("Not mapped");
		final String label;
		Status(String label) { this.label = label; }
	}

	private final RouteContext context;
	private final FriendsCatalog catalog;
	private final UiContext ui;
	private final AccountScope scope;
	private final Set<String> wishlist = new LinkedHashSet<>();
	private final Deque<Location> history = new ArrayDeque<>();
	private final StackPanel body = new StackPanel(5);
	private final RButton homeTab, huntTab, albumTab;
	private View view = View.HOME;
	private String petKey, hunt, query = "", category = "";
	private Status filter = Status.ALL;
	private StackPanel totals, results;
	private Runnable changed = () -> { };

	FriendsPanel(RouteContext context, FriendsCatalog catalog) {
		super(5);
		this.context = context;
		this.catalog = catalog;
		ui = context.ui();
		scope = context.snapshot().scope();
		setName("r02-friends");

		ColumnsPanel tabs = new ColumnsPanel(3, 4);
		homeTab = button("friends-home", "Home", () -> open(View.HOME));
		huntTab = button("friends-hunt", "Hunt", () -> open(View.HUNT));
		albumTab = button("friends-album", "Album", () -> open(View.ALBUM));
		tabs.add(homeTab);
		tabs.add(huntTab);
		tabs.add(albumTab);
		add(tabs);
		add(body);
		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "friendsBack");
		getActionMap().put("friendsBack", new AbstractAction() {
			@Override public void actionPerformed(ActionEvent event) { localBack(); }
		});
		context.onSnapshot(this::observationsChanged);
		render();
	}

	private void observationsChanged(FixtureSnapshot snapshot) {
		if (!scope.equals(snapshot.scope())) return;
		if (view == View.ALBUM) {
			updateTotals(totals);
			updateResults();
		} else if (view == View.HOME || view == View.PET || view == View.WISHLIST) {
			refresh();
		}
	}

	private Evidence<Ownership> evidence(String key) {
		return currentScope() && scope.signedIn() ? context.snapshot().ownership(key) : Evidence.unknown();
	}

	private static Status status(Evidence<Ownership> evidence) {
		switch (evidence.status()) {
			case VERIFIED: return evidence.value() == Ownership.OBTAINED ? Status.OBTAINED : Status.ABSENT;
			case VERIFIED_ABSENT: return Status.ABSENT;
			case NOT_APPLICABLE: return Status.UNMAPPED;
			default: return Status.UNKNOWN;
		}
	}

	private static String statusLabel(Evidence<Ownership> evidence) {
		if (evidence.status() == EvidenceStatus.STALE)
			return evidence.value() == Ownership.OBTAINED ? "Last seen obtained" : "Last seen not obtained";
		if (evidence.status() == EvidenceStatus.UNAVAILABLE) return "Unknown · observation unavailable";
		return status(evidence).label;
	}

	private boolean currentScope() { return scope.equals(context.snapshot().scope()); }

	private void render() {
		body.removeAll();
		totals = null;
		results = null;
		View primary = primary(view) ? view : view == View.METHODS ? View.HUNT : history.stream()
			.map(entry -> entry.view).filter(FriendsPanel::primary).findFirst().orElse(View.HOME);
		homeTab.setSelected(primary == View.HOME);
		huntTab.setSelected(primary == View.HUNT);
		albumTab.setSelected(primary == View.ALBUM);
		switch (view) {
			case HOME: home(); break;
			case ALBUM: album(); break;
			case WISHLIST: wishlist(); break;
			case HUNT: hunt(); break;
			case ARRIVALS: arrivals(); break;
			case COMPANIONS: companions(); break;
			case PET: pet(catalog.pet(petKey)); break;
			case METHODS: methodsPage(catalog.pet(petKey)); break;
			case HELP: help(); break;
			default: throw new AssertionError(view);
		}
		body.add(note("Offline preview · pins are temporary · no live account collection"));
		revalidate();
		repaint();
	}

	private static boolean primary(View view) { return view == View.HOME || view == View.HUNT || view == View.ALBUM; }

	private void home() {
		StackPanel counts = new StackPanel(2);
		int seen = updateTotals(counts);
		body.add(new Card(ui, null).addAll(portrait(picture("The sanctuary", context.assets().art("SIG-02"), 62, 58),
			"The sanctuary", seen == 0 ? "Collection observation pending" : "Partial collection observed", 62, 58)));

		Card album = new Card(ui, null);
		album.add(button("friends-family-album", "Family album", () -> open(View.ALBUM)));
		ColumnsPanel portraits = new ColumnsPanel(3, 3);
		for (String key : List.of("baby_mole", "beaver", "heron")) portraits.add(sprite(key, 34));
		album.addAll(portraits, counts);
		body.add(album);

		Card task = new Card(ui, "Your hunt");
		if (hunt == null) task.add(shortcut("friends-find-hunt", "Find a pet to hunt", "Browse the collection", "rocky", View.ALBUM));
		else task.add(shortcut("friends-active-hunt", catalog.pet(hunt).name, "Active hunt", hunt, View.HUNT));
		body.add(task);

		Card shortcuts = new Card(ui, "Sanctuary journal");
		shortcuts.add(shortcut("friends-wishlist", "Wishlist", wishlist.isEmpty() ? "No saved pets" : wishlist.size() + " saved", "giant_squirrel", View.WISHLIST));
		shortcuts.add(shortcut("friends-arrivals", "New arrivals", "Acquisition history unavailable", "chompy_chick", View.ARRIVALS));
		shortcuts.add(shortcut("friends-companions", "Cats & dogs", "Companion observations", "cat", View.COMPANIONS));
		body.add(shortcuts);
	}

	private JComponent shortcut(String id, String title, String line, String key, View destination) {
		StackPanel text = new StackPanel(1);
		text.add(button(id, title, () -> open(destination)));
		text.add(note(line));
		return new ImageRow(sprite(key, 35), text, 35, 35);
	}

	private int updateTotals(StackPanel counts) {
		int obtained = 0, absent = 0, unknown = 0, unmapped = 0;
		for (Pet pet : catalog.roster) {
			switch (status(evidence(pet.key))) {
				case OBTAINED: obtained++; break;
				case ABSENT: absent++; break;
				case UNMAPPED: unmapped++; break;
				default: unknown++;
			}
		}
		int observed = obtained + absent;
		counts.removeAll();
		ColumnsPanel row = new ColumnsPanel(3, 3);
		row.add(countCell(observed == 0 ? "—" : Integer.toString(obtained), "Obtained"));
		row.add(countCell(observed == 0 ? "—" : Integer.toString(absent), "Not yet"));
		row.add(countCell(Integer.toString(unknown), "Unknown"));
		counts.add(row);
		if (unmapped > 0) counts.add(note(unmapped + " not mapped"));
		counts.add(note(observed + " of " + catalog.roster.size() + " individual ownership states observed"));
		counts.revalidate();
		counts.repaint();
		return observed;
	}

	private JComponent countCell(String number, String label) {
		StackPanel cell = new StackPanel(1) {
			@Override public Dimension getPreferredSize() { return new Dimension(48, heightForWidth(48)); }
		};
		cell.add(title(number));
		cell.add(note(label));
		return cell;
	}

	private void album() {
		body.add(title("Pet album"));
		body.add(new Card(ui, null).addAll(portrait(sprite("baby_chinchompa", 35), "Meet the little ones",
			"All " + catalog.roster.size() + " pets · find your next friend", 35, 35)));
		totals = new StackPanel(3);
		body.add(new Card(ui, "Your collection").addAll(totals));
		updateTotals(totals);

		SearchField search = new SearchField(ui, "Find a pet…", value -> { query = value; updateResults(); });
		search.setName("friends-search");
		search.getAccessibleContext().setAccessibleName("Search pets");
		search.setText(query);
		body.add(search);
		List<String> categories = new ArrayList<>();
		categories.add("");
		categories.addAll(catalog.categories.keySet());
		RDropdown<String> group = new RDropdown<>(ui, "Pet category", categories, key -> catalog.categories.getOrDefault(key, "All pets"),
			value -> { category = value; updateResults(); });
		group.setName("friends-category");
		group.restore(category);
		RDropdown<Status> statuses = new RDropdown<>(ui, "Collection status", List.of(Status.values()), value -> value.label,
			value -> { filter = value; updateResults(); });
		statuses.setName("friends-status-filter");
		statuses.restore(filter);
		ColumnsPanel filters = new ColumnsPanel(2, 4);
		filters.add(group);
		filters.add(statuses);
		body.add(filters);
		body.add(button("friends-album-wishlist", "Wishlist (" + wishlist.size() + ")", () -> open(View.WISHLIST)));
		results = new StackPanel(4);
		body.add(results);
		updateResults();
		body.add(note("Unknown means no current collection observation for that pet."));
		body.add(button("friends-capture", "Account Capture", () -> context.navigate(RouteId.CAPTURE)));
	}

	private void updateResults() {
		if (results == null) return;
		String text = query.trim().toLowerCase(Locale.ROOT);
		List<Pet> matches = new ArrayList<>();
		for (Pet pet : catalog.roster) {
			if (pet.name.toLowerCase(Locale.ROOT).contains(text) && (category.isEmpty() || pet.category.equals(category))
				&& (filter == Status.ALL || filter == status(evidence(pet.key)))) matches.add(pet);
		}
		results.removeAll();
		results.add(note(matches.size() + (matches.size() == 1 ? " pet" : " pets")));
		if (matches.isEmpty()) results.add(note("No friends match those filters. Try a different search."));
		else results.add(grid(matches));
		results.revalidate();
		results.repaint();
	}

	private JComponent grid(List<Pet> pets) {
		ColumnsPanel grid = new ColumnsPanel(2, 4);
		for (Pet pet : pets) {
			Card tile = new Card(ui, null) {
				@Override public Dimension getPreferredSize() { return new Dimension(89, heightForWidth(89)); }
			};
			RButton open = button("friends-open-" + pet.key, pet.name, () -> open(View.PET, pet.key));
			open.getAccessibleContext().setAccessibleDescription(pet.type + " · " + statusLabel(evidence(pet.key)));
			tile.addAll(sprite(pet.key, 35), open, note(pet.type), note(statusLabel(evidence(pet.key))));
			if (pet.key.equals(hunt)) tile.add(note("Active hunt"));
			RButton pin = button("friends-pin-" + pet.key, wishlist.contains(pet.key) ? "★ Remove" : "☆ Save",
				() -> togglePin(pet, "friends-pin-" + pet.key));
			pin.getAccessibleContext().setAccessibleName(wishlist.contains(pet.key) ? "Remove " + pet.name + " from wishlist" : "Save " + pet.name + " to wishlist");
			pin.setEnabled(scope.signedIn());
			tile.add(pin);
			grid.add(tile);
		}
		return grid;
	}

	private void wishlist() {
		body.add(title("Wishlist"));
		body.add(new Card(ui, null).addAll(portrait(sprite("rocky", 35), "Future friends", "Keep your favorite pets close.", 35, 35)));
		if (wishlist.isEmpty()) body.add(new Card(ui, null).addAll(note("Your wishlist is empty.")));
		else {
			List<Pet> pinned = new ArrayList<>();
			for (String key : wishlist) pinned.add(catalog.pet(key));
			body.add(grid(pinned));
		}
		body.add(button("friends-wishlist-album", "Browse the pet album", () -> open(View.ALBUM)));
	}

	private void arrivals() {
		body.add(title("New arrivals"));
		body.add(new Card(ui, null).addAll(portrait(sprite("chompy_chick", 35), "Room for new friends",
			"No acquisition observations are available.", 35, 35)));
		body.add(button("friends-arrivals-album", "Visit the pet album", () -> open(View.ALBUM)));
	}

	private void hunt() {
		body.add(title(hunt == null ? "Hunt" : "Active hunt"));
		if (hunt == null) {
			Card empty = new Card(ui, "No active hunt yet");
			empty.addAll(sprite("rocky", 52), note("Pick a pet and make it your active hunt."),
				button("friends-explore-pets", "Explore pets", () -> open(View.ALBUM)),
				button("friends-hunt-wishlist", "Open wishlist", () -> open(View.WISHLIST)));
			body.add(empty);
			return;
		}
		Pet pet = catalog.pet(hunt);
		body.add(profile(pet));
		body.add(huntEvidence());
		body.add(methods(pet));
		body.add(button("friends-manage-hunt", "Manage hunt", () -> open(View.PET, pet.key)));
	}

	private void pet(Pet pet) {
		body.add(title("Meet your friend"));
		body.add(profile(pet));
		Evidence<Ownership> result = evidence(pet.key);
		Card collection = new Card(ui, "Collection status");
		collection.add(title(statusLabel(result)));
		if (result.source() != null) collection.add(note(result.source()));
		else collection.add(note(status(result) == Status.UNMAPPED ? "Individual row unavailable" : "No individual collection observation"));
		if (result.status() == EvidenceStatus.STALE)
			collection.add(note("Last observed " + result.observedAt()));
		RButton pin = button("friends-detail-pin", wishlist.contains(pet.key) ? "★ Remove from wishlist" : "☆ Save to wishlist",
			() -> togglePin(pet, "friends-detail-pin"));
		pin.setEnabled(scope.signedIn());
		collection.add(pin);
		if (pet.key.equals(hunt)) {
			collection.add(button("friends-stop", "Stop active hunt", () -> { hunt = null; refresh("friends-start"); }));
			collection.add(button("friends-view-active", "View active hunt", () -> open(View.HUNT)));
		} else if (status(result) != Status.OBTAINED) {
			RButton start = button("friends-start", "Start hunting for this pet", () -> {
				if (scope.signedIn() && status(evidence(pet.key)) != Status.OBTAINED) { hunt = pet.key; refresh("friends-stop"); }
			});
			start.setEnabled(scope.signedIn());
			collection.add(start);
		}
		body.add(collection);
		body.add(huntEvidence());
		body.add(button("friends-methods", "See hunting methods", () -> open(View.METHODS, pet.key)));
	}

	private JComponent profile(Pet pet) {
		return new Card(ui, null).addAll(portrait(sprite(pet.key, 54), pet.name,
			pet.type + " pet" + (pet.key.equals(hunt) ? " · Active hunt" : ""), 54, 54));
	}

	private Card huntEvidence() {
		return new Card(ui, "Hunt evidence").addAll(note("Observed actions · Unavailable"),
			note("Estimated opportunities · Unavailable"), note("Verified eligible rolls · Unavailable"),
			button("friends-help", "About hunt evidence", () -> open(View.HELP)));
	}

	private void help() {
		body.add(new Card(ui, "Hunt evidence").addAll(
			note("Observed actions, estimated opportunities and verified eligible rolls are separate evidence. This account fixture contains no verified roll observations."),
			button("friends-close-help", "Close", this::localBack)));
	}

	private void methodsPage(Pet pet) {
		body.add(title("Hunting methods"));
		body.add(profile(pet));
		body.add(methods(pet));
	}

	private Card methods(Pet pet) {
		Card where = new Card(ui, "Where to look");
		where.add(note("Source: " + pet.source));
		if (pet.methods == null) {
			for (EncounterLink link : pet.links) {
				StackPanel details = new StackPanel(3);
				details.add(note(link.condition.isEmpty() ? "Roll conditions unavailable." : link.condition));
				details.add(button("friends-chronicle-" + link.key, "Open Boss Chronicle",
					() -> context.navigate(RouteId.CHRONICLE, RouteRequest.Param.ENCOUNTER, link.key)));
				Expandable e = new Expandable(ui, link.name, details);
				e.setName("friends-source-" + link.key);
				where.add(e);
			}
		} else {
			for (Method option : pet.methods.options) {
				StackPanel details = new StackPanel(3);
				details.add(note("Where / how · " + option.place));
				details.add(note("Base drop rate · 1 / " + number(option.base)));
				if (option.listed != null) details.add(note("Archived example per eligible roll · 1 / " + number(option.listed)));
				details.add(note("Account access · Unconfirmed"));
				details.add(note("Verified rolls / hour · Pending"));
				details.add(note("Pet chance / hour · Unknown"));
				addWiki(details, pet.methods.wiki, pet.name);
				Expandable e = new Expandable(ui, option.name + " · " + option.requirement, details);
				e.setName("friends-method-" + option.name);
				where.add(e);
			}
		}
		where.add(note("Eligible opportunities, verified roll history and account odds are unavailable."));
		return where;
	}

	private void companions() {
		body.add(title("Companions"));
		body.add(new Card(ui, null).addAll(portrait(sprite("cat", 35), "Everyday adventure buddies",
			"Cats, dogs and their rune trades", 35, 35)));
		body.add(companion("cat", "Cats", "Death runes", "Civilian", "West Ardougne", "Eligible adult cat",
			"Ardougne Easy Diary", "Civilian"));
		body.add(companion("dog", "Dogs", "Blood runes", "Mysterious Stranger", "Ver Sinhaza",
			"Adult dog · A Ruff Situation", "Morytania Medium Diary", "Mysterious_Stranger_(dog)"));
		body.add(note("Acquisition, growth and trade tracking await native validation."));
	}

	private Card companion(String key, String name, String runes, String trader, String place,
		String requirement, String diary, String wiki) {
		Card card = new Card(ui, null).addAll(portrait(sprite(key, 43), name, runes + " trade", 43, 43));
		for (String row : List.of("Acquisitions witnessed", "Raised to adult", "Confirmed trade-ins", runes + " observed"))
			card.add(note(row + " · Pending"));
		card.add(note("Current ownership · Unknown"));
		StackPanel trade = new StackPanel(3);
		String resource = runes.toLowerCase(Locale.ROOT);
		trade.add(note("Trader · " + trader + " · " + place));
		trade.add(note("Requires · " + requirement));
		trade.add(note("Standard trade · 100 " + resource));
		trade.add(note(diary + " · 200 " + resource));
		addWiki(trade, wiki, trader);
		Expandable e = new Expandable(ui, "Trade details", trade);
		e.setName("friends-trade-" + key);
		card.add(e);
		return card;
	}

	private void addWiki(StackPanel container, String slug, String subject) {
		URI link = URI.create("https://oldschool.runescape.wiki/w/" + slug);
		TextBlock fallback = note(link.toString());
		fallback.setVisible(false);
		RButton source = button("friends-wiki", "OSRS Wiki · " + subject, () -> {
			try {
				if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
					throw new IOException("No browser handler");
				Desktop.getDesktop().browse(link);
			} catch (IOException | SecurityException | UnsupportedOperationException ex) {
				fallback.setVisible(true);
				container.revalidate();
			}
		});
		source.setToolTipText(link.toString());
		container.add(source);
		container.add(fallback);
	}

	private void togglePin(Pet pet, String focus) {
		if (!scope.signedIn()) return;
		if (!wishlist.remove(pet.key)) wishlist.add(pet.key);
		refresh(focus);
	}

	private static String number(int value) { return NumberFormat.getIntegerInstance(Locale.US).format(value); }
	private TextBlock title(String text) { return new TextBlock(ui, text, FontRole.TITLE, Tone.TEXT); }
	private TextBlock note(String text) { return new TextBlock(ui, text, FontRole.SMALL, Tone.MUTED); }

	private RButton button(String id, String label, Runnable action) {
		RButton button = new RButton(ui, label);
		button.setName(id);
		button.addActionListener(event -> { if (currentScope()) action.run(); });
		button.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "activate");
		button.getActionMap().put("activate", new AbstractAction() {
			@Override public void actionPerformed(ActionEvent event) { button.doClick(0); }
		});
		return button;
	}

	private JLabel sprite(String key, int size) {
		Pet pet = catalog.pet(key);
		return picture(pet == null ? key : pet.name, catalog.image(context.assets(), key), size, size);
	}

	private JLabel picture(String label, BufferedImage image, int width, int height) {
		JLabel picture = new JLabel(image == null ? "?" : "", SwingConstants.CENTER);
		picture.getAccessibleContext().setAccessibleName(image == null ? R02Assets.fallbackLabel(label) + " (image unavailable)" : label);
		picture.setToolTipText(label);
		picture.setForeground(ui.palette().muted);
		picture.setPreferredSize(new Dimension(width, height));
		if (image != null) {
			double factor = Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
			picture.setIcon(new ImageIcon(image.getScaledInstance(Math.max(1, (int) (image.getWidth() * factor)),
				Math.max(1, (int) (image.getHeight() * factor)), java.awt.Image.SCALE_SMOOTH)));
		}
		return picture;
	}

	private JComponent portrait(JLabel image, String title, String note, int width, int height) {
		StackPanel text = new StackPanel(2);
		text.add(title(title));
		text.add(note(note));
		return new ImageRow(image, text, width, height);
	}

	private void open(View target) { open(target, null); }
	private void open(View target, String key) {
		if (target == view && Objects.equals(key, petKey)) return;
		history.push(new Location(view, petKey, position(), focusedName()));
		view = target;
		petKey = key;
		render();
		changed.run();
		restore(new Point(), target == View.HELP ? "friends-close-help" : null);
	}

	private void refresh() { refresh(focusedName()); }
	private void refresh(String focus) {
		Point position = position();
		render();
		restore(position, focus);
	}

	@Override public boolean hasLocalBack() { return !history.isEmpty(); }
	@Override public void localBack() {
		if (!currentScope() || history.isEmpty()) return;
		Location previous = history.pop();
		view = previous.view;
		petKey = previous.petKey;
		render();
		changed.run();
		restore(previous.scroll, previous.focus);
	}

	@Override public void setLocalBackListener(Runnable listener) { changed = listener; }

	private JViewport viewport() { return (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this); }
	private Point position() {
		JViewport viewport = viewport();
		return viewport == null ? new Point() : viewport.getViewPosition();
	}

	private String focusedName() {
		Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		return focus != null && SwingUtilities.isDescendingFrom(focus, this) ? focus.getName() : null;
	}

	private void restore(Point scroll, String focus) {
		View expected = view;
		String key = petKey;
		SwingUtilities.invokeLater(() -> {
			if (!currentScope() || view != expected || !Objects.equals(petKey, key)) return;
			JViewport viewport = viewport();
			if (viewport != null) {
				viewport.validate();
				viewport.setViewPosition(scroll);
			}
			JComponent control = focus == null ? null : find(this, focus);
			if (control != null) control.requestFocusInWindow();
		});
	}

	private static JComponent find(Container root, String name) {
		for (Component child : root.getComponents()) if (child instanceof JComponent && child.isVisible()) {
			if (name.equals(child.getName())) return (JComponent) child;
			JComponent nested = find((JComponent) child, name);
			if (nested != null) return nested;
		}
		return null;
	}

	private static final class Location {
		final View view;
		final String petKey, focus;
		final Point scroll;
		Location(View view, String petKey, Point scroll, String focus) {
			this.view = view;
			this.petKey = petKey;
			this.scroll = scroll;
			this.focus = focus;
		}
	}

	private static final class ImageRow extends JPanel implements HeightForWidth {
		private final JComponent image, text;
		private final int width, height;

		ImageRow(JComponent image, JComponent text, int width, int height) {
			super(null);
			this.image = image;
			this.text = text;
			this.width = width;
			this.height = height;
			setOpaque(false);
			add(image);
			add(text);
		}

		@Override public int heightForWidth(int available) {
			return Math.max(height, StackLayout.heightFor(text, Math.max(1, available - width - 5)));
		}

		@Override public void doLayout() {
			image.setBounds(0, Math.max(0, (getHeight() - height) / 2), width, height);
			text.setBounds(width + 5, 0, Math.max(1, getWidth() - width - 5), getHeight());
		}
	}
}
