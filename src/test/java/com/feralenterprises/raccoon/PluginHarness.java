package com.feralenterprises.raccoon;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import com.feralenterprises.raccoon.bank.BankObservationService;
import com.feralenterprises.raccoon.kc.BrutusKcService;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.WorldType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;

/**
 * Deterministic simulation of the plugin's production wiring: Guice injection of the plugin and
 * its singleton services from a recording {@link Client} proxy, and the real RuneLite
 * {@link EventBus}, registered and unregistered in the order RuneLite's plugin manager uses.
 * Every client call is recorded; any call outside {@link #READS} is a failure. Not native
 * RuneLite: genuine client behaviour is FR-006.
 */
final class PluginHarness
{
	/** The only client calls the plugin may make (all reads). */
	static final Set<String> READS = new HashSet<>(Arrays.asList(
		"getGameState", "getAccountHash", "getWorldType", "getTickCount", "getWidget",
		"getItemContainer", "getItemDefinition", "getVarbitValue"));

	final Map<String, Integer> calls = new LinkedHashMap<>();
	final List<String> unexpected = Collections.synchronizedList(new ArrayList<>());
	final List<Throwable> subscriberErrors = Collections.synchronizedList(new ArrayList<>());

	GameState gameState = GameState.LOGIN_SCREEN;
	long accountHash = -1L;
	Set<WorldType> world = EnumSet.of(WorldType.MEMBERS);
	int tick = 100;
	boolean bankOpen;
	Item[] bank = new Item[0];
	Item[] inventory = new Item[0];
	Item[] equipment = new Item[0];

	final Client client;
	final Injector injector;
	final RaccoonPlugin plugin;
	final EventBus eventBus;
	final AccountLifecycleService accounts;
	final BankObservationService bankService;
	final BrutusKcService kc;
	private boolean enabled;

	PluginHarness()
	{
		client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, this::client);
		injector = Guice.createInjector(binder -> binder.bind(Client.class).toInstance(client));
		plugin = injector.getInstance(RaccoonPlugin.class);
		accounts = injector.getInstance(AccountLifecycleService.class);
		bankService = injector.getInstance(BankObservationService.class);
		kc = injector.getInstance(BrutusKcService.class);
		eventBus = new EventBus(subscriberErrors::add);
	}

	/** RuneLite PluginManager order: startUp, then register with the event bus. */
	void enable() throws Exception
	{
		plugin.startUp();
		eventBus.register(plugin);
		enabled = true;
	}

	/** RuneLite PluginManager order: unregister from the event bus, then shutDown. */
	void disable() throws Exception
	{
		eventBus.unregister(plugin);
		plugin.shutDown();
		enabled = false;
	}

	boolean isEnabled()
	{
		return enabled;
	}

	synchronized int calls(String name)
	{
		return calls.getOrDefault(name, 0);
	}

	void logIn(long hash)
	{
		accountHash = hash;
		gameState = GameState.LOGGED_IN;
		eventBus.post(gameStateChanged(GameState.LOGGED_IN));
		gameTick();
	}

	void gameTick()
	{
		tick++;
		eventBus.post(newEvent(GameTick.class));
	}

	static GameStateChanged gameStateChanged(GameState state)
	{
		GameStateChanged e = new GameStateChanged();
		e.setGameState(state);
		return e;
	}

	static ChatMessage chat(ChatMessageType type, String message)
	{
		ChatMessage e = new ChatMessage();
		e.setType(type);
		e.setMessage(message);
		return e;
	}

	static ChatMessage brutus(int count)
	{
		return chat(ChatMessageType.GAMEMESSAGE, "Your Brutus kill count is: <col=ff0000>" + count + "</col>.");
	}

	static ClientTick clientTick()
	{
		return newEvent(ClientTick.class);
	}

	/** Event classes differ in constructor visibility across client versions. */
	static <T> T newEvent(Class<T> type)
	{
		try
		{
			Constructor<T> c = type.getDeclaredConstructor();
			c.setAccessible(true);
			return c.newInstance();
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError("cannot create " + type.getName(), e);
		}
	}

	static Item[] items(int count, int firstId)
	{
		Item[] out = new Item[count];
		for (int i = 0; i < count; i++)
		{
			out[i] = new Item(firstId + 2 * i, 1 + i % 50);
		}
		return out;
	}

	// ------------------------------------------------------------------ recording client

	private synchronized void record(String name)
	{
		calls.merge(name, 1, Integer::sum);
		if (!READS.contains(name))
		{
			unexpected.add(name);
		}
	}

	private Object client(Object proxy, Method m, Object[] args)
	{
		if (m.getDeclaringClass() == Object.class)
		{
			return objectMethod(proxy, m, args);
		}
		record(m.getName());
		switch (m.getName())
		{
			case "getGameState":
				return gameState;
			case "getAccountHash":
				return accountHash;
			case "getWorldType":
				return world.isEmpty() ? EnumSet.noneOf(WorldType.class) : EnumSet.copyOf(world);
			case "getTickCount":
				return tick;
			case "getVarbitValue":
				return 0;
			case "getWidget":
				return widget(args);
			case "getItemContainer":
				return container(args);
			case "getItemDefinition":
				return definition((Integer) args[0]);
			default:
				return defaultValue(m.getReturnType());
		}
	}

	private Object widget(Object[] args)
	{
		if (!bankOpen || args.length != 1 || !(args[0] instanceof Integer))
		{
			return null;
		}
		int id = (Integer) args[0];
		if (id != InterfaceID.Bankmain.UNIVERSE && id != InterfaceID.Bankmain.ITEMS)
		{
			return null; // no PIN keypad
		}
		return Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
			(p, m, a) -> m.getDeclaringClass() == Object.class ? objectMethod(p, m, a)
				: "isHidden".equals(m.getName()) ? Boolean.FALSE : defaultValue(m.getReturnType()));
	}

	private Object container(Object[] args)
	{
		if (args.length != 1 || !(args[0] instanceof Integer))
		{
			return null;
		}
		int id = (Integer) args[0];
		Item[] items = id == InventoryID.BANK ? (bankOpen ? bank : null)
			: id == InventoryID.INV ? inventory : id == InventoryID.WORN ? equipment : null;
		if (items == null)
		{
			return null;
		}
		return Proxy.newProxyInstance(ItemContainer.class.getClassLoader(), new Class<?>[]{ItemContainer.class},
			(p, m, a) -> m.getDeclaringClass() == Object.class ? objectMethod(p, m, a)
				: "getItems".equals(m.getName()) ? items.clone() : defaultValue(m.getReturnType()));
	}

	private Object definition(int itemId)
	{
		InvocationHandler h = (p, m, a) ->
		{
			if (m.getDeclaringClass() == Object.class)
			{
				return objectMethod(p, m, a);
			}
			switch (m.getName())
			{
				case "getId":
					return itemId;
				case "getNote":
				case "getPlaceholderTemplateId":
					return -1;
				default:
					return defaultValue(m.getReturnType());
			}
		};
		return Proxy.newProxyInstance(ItemComposition.class.getClassLoader(), new Class<?>[]{ItemComposition.class}, h);
	}

	private static Object objectMethod(Object proxy, Method m, Object[] args)
	{
		switch (m.getName())
		{
			case "equals":
				return proxy == args[0];
			case "hashCode":
				return System.identityHashCode(proxy);
			default:
				return "proxy";
		}
	}

	static Object defaultValue(Class<?> t)
	{
		if (!t.isPrimitive() || t == void.class)
		{
			return null;
		}
		if (t == boolean.class)
		{
			return false;
		}
		if (t == long.class)
		{
			return 0L;
		}
		if (t == double.class)
		{
			return 0d;
		}
		if (t == float.class)
		{
			return 0f;
		}
		if (t == char.class)
		{
			return (char) 0;
		}
		if (t == byte.class)
		{
			return (byte) 0;
		}
		if (t == short.class)
		{
			return (short) 0;
		}
		return 0;
	}
}
