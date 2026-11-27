package com.feralenterprises.raccoon.playtest.r02.panel.outfit;

import com.feralenterprises.raccoon.playtest.r02.shared.R02Assets;
import com.feralenterprises.raccoon.ui.component.RButton;
import com.feralenterprises.raccoon.ui.component.TextBlock;
import com.feralenterprises.raccoon.ui.component.Tone;
import com.feralenterprises.raccoon.ui.layout.HeightForWidth;
import com.feralenterprises.raccoon.ui.layout.StackLayout;
import com.feralenterprises.raccoon.ui.paint.Paints;
import com.feralenterprises.raccoon.ui.theme.FontRole;
import com.feralenterprises.raccoon.ui.theme.UiContext;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** Small presentation helpers shared only by the two Combat lane panels. */
public final class CombatUi {
	private CombatUi() { }

	public static TextBlock text(UiContext ui, String s) { return new TextBlock(ui, s, FontRole.BODY, Tone.TEXT); }
	public static TextBlock note(UiContext ui, String s) { return new TextBlock(ui, s, FontRole.SMALL, Tone.MUTED); }
	public static TextBlock title(UiContext ui, String s) { return new TextBlock(ui, s, FontRole.TITLE, Tone.TEXT); }

	public static RButton button(UiContext ui, String label, String name, Runnable action) {
		RButton b = new RButton(ui, label);
		b.setName(name);
		b.addActionListener(event -> action.run());
		b.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "activate");
		b.getActionMap().put("activate", new AbstractAction() {
			@Override public void actionPerformed(java.awt.event.ActionEvent e) { if (b.isEnabled()) b.doClick(); }
		});
		return b;
	}

	/** A source link is an explicit user action. Only the pinned catalog's HTTPS OSRS Wiki targets are offered. */
	public static void addSource(JComponent parent, UiContext ui, String label, String url) {
		if (url.isEmpty()) return;
		URI uri;
		try { uri = URI.create(url); }
		catch (IllegalArgumentException e) { return; }
		if (!"https".equals(uri.getScheme()) || !"oldschool.runescape.wiki".equals(uri.getHost())) return;
		TextBlock status = note(ui, "");
		status.setVisible(false);
		RButton link = button(ui, label + " ↗", "wiki-source", () -> {
			try {
				if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
					throw new IOException("browser unavailable");
				Desktop.getDesktop().browse(uri);
			} catch (IOException | UnsupportedOperationException | SecurityException e) {
				status.setText(url);
				status.setVisible(true);
				parent.revalidate();
			}
		});
		link.setToolTipText(url);
		parent.add(link);
		parent.add(status);
	}

	public static JLabel picture(UiContext ui, BufferedImage image, String description, int width, int height) {
		JLabel label = new JLabel(image == null ? "·" : "", SwingConstants.CENTER);
		label.getAccessibleContext().setAccessibleName(image == null ? R02Assets.fallbackLabel(description) : description);
		label.setToolTipText(description);
		label.setForeground(ui.palette().muted);
		if (image != null) label.setIcon(icon(image, width, height));
		label.setPreferredSize(new Dimension(width, height));
		return label;
	}

	public static Icon icon(BufferedImage image, int width, int height) {
		double scale = Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
		int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
		return new ImageIcon(image.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH));
	}

	public static BufferedImage styleIcon(R02Assets assets, String style) {
		return assets.skillIcon("melee".equals(style) ? "attack" : style);
	}

	public static void cover(Graphics2D graphics, BufferedImage image, int width, int height) {
		double scale = Math.max(width / (double) image.getWidth(), height / (double) image.getHeight());
		int w = (int) Math.ceil(image.getWidth() * scale), h = (int) Math.ceil(image.getHeight() * scale);
		Graphics2D copy = (Graphics2D) graphics.create(2, 2, Math.max(0, width - 4), Math.max(0, height - 4));
		Paints.image(copy, image, (width - w) / 2 - 2, (height - h) / 2 - 2, w, h, 1f);
		copy.dispose();
	}

	public static Color outfitSurface(String environment) {
		if (environment.contains("frozen") || environment.contains("glacial") || environment.contains("tidal"))
			return new Color(30, 59, 79, 222);
		if (environment.contains("swamp") || environment.contains("moss") || environment.contains("grove"))
			return new Color(42, 56, 40, 222);
		return new Color(63, 44, 31, 220);
	}

	/** A fixed image well and wrapping text column, avoiding clipped labels beside portraits and sprite cells. */
	public static final class PictureRow extends JPanel implements HeightForWidth {
		private final JComponent image, body;
		private final int imageWidth, imageHeight, gap;

		public PictureRow(JComponent image, JComponent body, int imageWidth, int imageHeight, int gap) {
			super(null);
			this.image = image;
			this.body = body;
			this.imageWidth = imageWidth;
			this.imageHeight = imageHeight;
			this.gap = gap;
			setOpaque(false);
			add(image);
			add(body);
		}

		@Override public int heightForWidth(int width) {
			return Math.max(imageHeight, StackLayout.heightFor(body, Math.max(1, width - imageWidth - gap)));
		}

		@Override public Dimension getPreferredSize() {
			int width = getWidth() > 0 ? getWidth() : 185;
			return new Dimension(width, heightForWidth(width));
		}

		@Override public void doLayout() {
			int w = Math.max(1, getWidth() - imageWidth - gap);
			int h = StackLayout.heightFor(body, w);
			image.setBounds(0, Math.max(0, (getHeight() - imageHeight) / 2), imageWidth, imageHeight);
			body.setBounds(imageWidth + gap, Math.max(0, (getHeight() - h) / 2), w, h);
		}
	}

	/** A folio entry is one focusable button with a portrait and readable, wrapping detail. */
	public static final class FolioButton extends JButton implements HeightForWidth {
		private final UiContext ui;
		private final PictureRow content;

		public FolioButton(UiContext ui, String name, String accessible, PictureRow content, Runnable action) {
			this.ui = ui;
			this.content = content;
			setName(name);
			getAccessibleContext().setAccessibleName(accessible);
			setToolTipText(accessible);
			setLayout(null);
			setContentAreaFilled(false);
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
			add(content);
			addActionListener(event -> action.run());
			getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "activate");
			getActionMap().put("activate", new AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent e) { doClick(); }
			});
		}

		@Override public int heightForWidth(int width) { return content.heightForWidth(Math.max(1, width - 12)) + 10; }
		@Override public Dimension getPreferredSize() {
			int width = getWidth() > 0 ? getWidth() : 190;
			return new Dimension(width, heightForWidth(width));
		}
		@Override public void doLayout() {
			content.setBounds(6, 5, Math.max(1, getWidth() - 12), Math.max(1, getHeight() - 10));
		}
		@Override protected void paintComponent(Graphics g) {
			Graphics2D g2 = Paints.prepare(g);
			g2.setColor(getModel().isArmed() ? ui.palette().trough
				: getModel().isRollover() ? ui.palette().panelRaised : ui.palette().panel);
			g2.fillRect(0, 0, getWidth(), getHeight());
			g2.setColor(isFocusOwner() ? ui.palette().focus : ui.palette().accentDim);
			g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
			g2.drawLine(2, getHeight() - 3, getWidth() - 3, getHeight() - 3);
			g2.dispose();
		}
	}

	public static JViewport viewport(JComponent component) {
		return (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, component);
	}

	public static Point position(JComponent component) {
		JViewport viewport = viewport(component);
		return viewport == null ? null : viewport.getViewPosition();
	}

	public static void restore(JComponent owner, JComponent focus, Point position) {
		SwingUtilities.invokeLater(() -> {
			if (focus != null && !SwingUtilities.isDescendingFrom(focus, owner)) return;
			JViewport viewport = viewport(owner);
			if (viewport != null && position != null) {
				viewport.validate();
				viewport.setViewPosition(position);
			}
			if (focus != null) focus.requestFocusInWindow();
		});
	}

	public static String quantity(long value) { return String.format(Locale.ROOT, "%,d", value); }
}
