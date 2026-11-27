package com.feralenterprises.raccoon.playtest.r02.shared;

import com.feralenterprises.raccoon.playtest.r02.fixture.Appearance;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureAction;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureClock;
import com.feralenterprises.raccoon.playtest.r02.fixture.FixtureSnapshot;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import com.feralenterprises.raccoon.playtest.r02.fixture.Subscription;
import com.feralenterprises.raccoon.ui.shell.RouteRequest;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.util.function.Consumer;

/**
 * Everything a route page may use; the narrowest interface the dispatch allows. One context per page instance: its
 * subscriptions close automatically when the shell discards the page (scope change), so a page never sees another
 * scope's data. Cross-panel links go through {@link #navigate} with route ids and stable payload ids only.
 */
public interface RouteContext {
	RouteId route();
	UiContext ui();

	/** The current immutable snapshot; never null while the page exists. */
	FixtureSnapshot snapshot();
	Registry registry();
	R02Assets assets();
	FixtureClock clock();
	Appearance appearance();

	/** Newer revisions of the same scope, on the EDT. Closed automatically with the page. */
	Subscription onSnapshot(Consumer<FixtureSnapshot> listener);
	/** Appearance changes, on the EDT. Closed automatically with the page. */
	Subscription onAppearance(Consumer<Appearance> listener);

	void navigate(RouteId route);
	void navigate(RouteId route, RouteRequest.Param param, String id);
	void dispatch(FixtureAction action);

	/**
	 * Changes the shared appearance (Settings). Every page receives it through {@link #onAppearance}. Added for Lane E,
	 * whose Settings page previously needed the whole dispatcher. Implementations that cannot change it refuse.
	 */
	default void setAppearance(Appearance appearance) { throw new UnsupportedOperationException("appearance is read-only here"); }
}
