package net.runelite.client.ui;
public class ClientToolbar {
	public final java.util.List<NavigationButton> buttons = new java.util.ArrayList<>();
	public void addNavigation(NavigationButton button) { buttons.add(button); }
	public void removeNavigation(final NavigationButton button) { buttons.remove(button); }
}
