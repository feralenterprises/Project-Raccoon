package com.feralenterprises.raccoon.playtest.r02.host;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios.ArchiveState;
import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios.BankState;
import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenScenarios.Selection;
import com.feralenterprises.raccoon.ui.component.RDropdown;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * DEV-ONLY playtest controls, mounted in their own sidebar tab so the R02 shell keeps its real sidebar width. They
 * replace the frozen page's simulator bar: simulated account, bank, archive, quest data, route step and activity.
 * Every control is disabled while the account-scope guard is suspended.
 */
public final class PlaytestControls extends JPanel {
	private static final List<String> ACCOUNTS = Arrays.asList("A", "B", "-");

	private final PlaytestSession session;
	private final JLabel banner = new JLabel();
	private final JLabel status = new JLabel();
	private final RDropdown<String> account;
	private final RDropdown<BankState> bank;
	private final RDropdown<ArchiveState> archive;
	private final RDropdown<Boolean> quests;
	private final RDropdown<String> route;
	private final RDropdown<String> activity;

	public PlaytestControls(PlaytestSession session) {
		this.session = session;
		UiContext ui = session.ui();
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ui.palette().background);
		JLabel title = label(ui, "R02 playtest: simulated data", FontRole.TITLE);
		add(title);
		add(Box.createVerticalStrut(4));
		banner.setFont(ui.font(FontRole.SMALL));
		banner.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(banner);
		add(Box.createVerticalStrut(8));
		account = add(ui, "Simulated account", new RDropdown<>(ui, "Simulated account", ACCOUNTS,
			a -> "A".equals(a) ? "Demo A" : "B".equals(a) ? "Demo B" : "Signed out", a -> session.selectAccount("-".equals(a) ? null : a)));
		bank = add(ui, "Bank", new RDropdown<>(ui, "Bank state", Arrays.asList(BankState.values()), PlaytestControls::title,
			b -> session.vary("bank=" + b, s -> s.withBank(b))));
		archive = add(ui, "Archive", new RDropdown<>(ui, "Archive state", Arrays.asList(ArchiveState.values()), PlaytestControls::title,
			a -> session.vary("archive=" + a, s -> s.withArchive(a))));
		quests = add(ui, "Quest data", new RDropdown<>(ui, "Quest data", Arrays.asList(Boolean.TRUE, Boolean.FALSE), q -> q ? "Example data" : "No quest data",
			q -> session.vary("questData=" + q, s -> s.withQuestData(q))));
		route = add(ui, "Pathfinder step", new RDropdown<>(ui, "Pathfinder route step", session.routeOptions(), r -> r,
			r -> session.vary("route=" + r, s -> s.withRoute(r))));
		activity = add(ui, "Current activity", new RDropdown<>(ui, "Current activity", Arrays.asList("-"), a -> a,
			a -> session.vary("activity=" + a, s -> s.withActivity(a))));
		add(Box.createVerticalStrut(8));
		status.setFont(ui.font(FontRole.SMALL));
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(status);
		add(Box.createVerticalGlue());
		session.addStateListener(this::refresh);
		refresh();
	}

	/** Mirrors the session: current selection, options for the account, enabled state, banner and status. */
	public void refresh() {
		boolean on = !session.suspended() && !session.disposed();
		Selection s = session.selection();
		if (s != null) {
			account.restore(s.account == null ? "-" : s.account);
			bank.restore(s.bank);
			archive.restore(s.archive);
			quests.restore(s.questData);
			route.restore(s.route);
			activity.setOptions(session.activityOptions());
			if (s.activity != null) activity.restore(s.activity);
		}
		boolean signedIn = s != null && s.account != null;
		account.setEnabled(on);
		for (RDropdown<?> d : Arrays.asList(bank, archive, quests, route, activity)) d.setEnabled(on && signedIn);
		banner.setText(wrap(on ? "Frozen R02 package. No game or account data is read or shown." : "Hidden while a game account may be signed in."));
		banner.setForeground(on ? session.ui().palette().muted : new Color(0xd08a3a));
		status.setText(wrap(session.status()));
		status.setForeground(session.ui().palette().text);
	}

	private <T> RDropdown<T> add(UiContext ui, String caption, RDropdown<T> d) {
		add(label(ui, caption, FontRole.SMALL));
		d.setAlignmentX(Component.LEFT_ALIGNMENT);
		d.setMaximumSize(new Dimension(Integer.MAX_VALUE, d.getPreferredSize().height));
		add(d);
		add(Box.createVerticalStrut(6));
		return d;
	}

	private static JLabel label(UiContext ui, String text, FontRole role) {
		JLabel l = new JLabel(text);
		l.setFont(ui.font(role));
		l.setForeground(ui.palette().text);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	/** Wrapped label text that fits the 225 px sidebar (labels do not wrap on their own). */
	private static String wrap(String text) {
		return "<html><div style='width:150px'>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>";
	}

	private static String title(Enum<?> e) {
		String n = e.name().toLowerCase();
		return Character.toUpperCase(n.charAt(0)) + n.substring(1);
	}
}
