package net.runelite.client.game;
public class ItemManager {
	public final java.util.Map<Integer, String> names = new java.util.HashMap<>();
	public net.runelite.api.ItemComposition getItemComposition(int itemId) { String n = names.get(itemId); return n == null ? null : () -> n; }
	public net.runelite.client.util.AsyncBufferedImage getImage(int itemId, int quantity, boolean stackable) { return null; }
}
