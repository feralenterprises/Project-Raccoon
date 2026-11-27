package net.runelite.client.callback;
public class ClientThread {
	public final java.util.List<Runnable> queue = new java.util.ArrayList<>();
	public void invokeLater(Runnable r) { queue.add(r); }
}
