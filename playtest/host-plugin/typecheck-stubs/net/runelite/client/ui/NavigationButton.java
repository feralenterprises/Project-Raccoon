package net.runelite.client.ui;
public class NavigationButton {
	private final java.awt.image.BufferedImage icon; private final String tooltip; private final PluginPanel panel; private final int priority;
	NavigationButton(java.awt.image.BufferedImage icon, String tooltip, PluginPanel panel, int priority) { this.icon = icon; this.tooltip = tooltip; this.panel = panel; this.priority = priority; }
	public java.awt.image.BufferedImage getIcon() { return icon; }
	public String getTooltip() { return tooltip; }
	public PluginPanel getPanel() { return panel; }
	public int getPriority() { return priority; }
	public static Builder builder() { return new Builder(); }
	public static class Builder {
		private java.awt.image.BufferedImage icon; private String tooltip = ""; private PluginPanel panel; private int priority;
		public Builder icon(java.awt.image.BufferedImage i) { icon = i; return this; }
		public Builder tooltip(String t) { tooltip = t; return this; }
		public Builder panel(PluginPanel p) { panel = p; return this; }
		public Builder priority(int p) { priority = p; return this; }
		public NavigationButton build() { return new NavigationButton(icon, tooltip, panel, priority); }
	}
}
