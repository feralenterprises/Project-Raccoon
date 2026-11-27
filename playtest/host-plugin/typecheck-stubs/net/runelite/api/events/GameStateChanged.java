package net.runelite.api.events;
public class GameStateChanged {
	private net.runelite.api.GameState gameState;
	public net.runelite.api.GameState getGameState() { return gameState; }
	public void setGameState(net.runelite.api.GameState s) { gameState = s; }
}
