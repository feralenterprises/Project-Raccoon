package com.feralenterprises.raccoon.ui.component;

import com.feralenterprises.raccoon.ui.layout.StackPanel;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.Tokens;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search box plus rows, filtered by case-insensitive substring. Generic presentation filtering only. */
public class FilterList extends StackPanel {
	private final List<TextBlock> rows = new ArrayList<>();
	private final TextBlock none;
	private final SearchField field;

	public FilterList(UiContext ctx, String placeholder, List<String> items) {
		super(Tokens.GAP_SMALL);
		field = new SearchField(ctx, placeholder, this::filter);
		add(field);
		for (String s : items) {
			TextBlock row = new TextBlock(ctx, s, FontRole.BODY, Tone.TEXT);
			rows.add(row);
			add(row);
		}
		none = new TextBlock(ctx, "No matches", FontRole.SMALL, Tone.MUTED);
		none.setVisible(false);
		add(none);
	}

	public SearchField field() { return field; }

	public int visibleCount() {
		int n = 0;
		for (TextBlock r : rows) if (r.isVisible()) n++;
		return n;
	}

	public boolean showsNoMatches() { return none.isVisible(); }

	public void filter(String query) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		for (TextBlock r : rows) r.setVisible(q.isEmpty() || r.text().toLowerCase(Locale.ROOT).contains(q));
		none.setVisible(visibleCount() == 0);
		revalidate();
		repaint();
	}
}
