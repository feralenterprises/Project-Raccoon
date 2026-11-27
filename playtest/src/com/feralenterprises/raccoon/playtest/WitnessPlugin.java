package com.feralenterprises.raccoon.playtest;

import com.feralenterprises.raccoon.RaccoonPlugin;
import com.feralenterprises.raccoon.account.AccountLifecycleService;
import com.feralenterprises.raccoon.bank.BankObservationService;
import com.feralenterprises.raccoon.bank.NativeBankProbe;
import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.account.AccountSnapshot;
import com.feralenterprises.raccoon.core.account.Subscription;
import com.feralenterprises.raccoon.core.bank.BankSnapshot;
import com.feralenterprises.raccoon.core.kc.BrutusKcView;
import com.feralenterprises.raccoon.kc.BrutusKcService;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

/**
 * FR-006 playtest witness. Loaded only by {@link PlaytestClient}; never part of the production
 * artifact. It never drives a production input and never manufactures game events:
 * <ul>
 * <li>R2: once the genuine client reaches the login screen, runs the adapter suites on the
 * actual client thread against synthetic fixtures (a proxied client and private service
 * instances; the genuine client and the production instances are not touched).</li>
 * <li>R3: observes the production services' read-only views, brackets genuine events with
 * highest/lowest-priority subscribers for timing, and probes genuine bank captures read-only.</li>
 * </ul>
 */
@PluginDescriptor(
	name = "Raccoon Playtest Witness",
	description = "FR-006 verification witness (playtest only)"
)
public class WitnessPlugin extends Plugin
{
	private static final Pattern BRUTUS = Pattern.compile("^Your Brutus kill count is: ?([0-9][0-9,]*)\\.?$");
	private static final Pattern COLOR = Pattern.compile("</?col(?:=[0-9a-fA-F]{6})?>");

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private AccountLifecycleService accounts;

	@Inject
	private BankObservationService bank;

	@Inject
	private BrutusKcService kc;

	private String step;
	private Receipt receipt;
	private Subscription lifecycleSubscription;
	private Subscription bankSubscription;
	private NativeBankProbe probe;
	private boolean r2Scheduled;
	private long tickStart;
	private long chatStart;
	private long stateStart;
	private long kcRevisionBefore;
	private OptionalLong kcTotalBefore = OptionalLong.empty();

	@Override
	protected void startUp() throws Exception
	{
		step = System.getProperty("raccoon.playtest.mode", "R3");
		receipt = Receipt.open(step);
		Environment.record(receipt, step);
		receipt.event("witness.start", "step", step, "thread", Thread.currentThread().getName());
		recordSharedInstances();
		if ("R3".equals(step))
		{
			probe = new NativeBankProbe(client);
			lifecycleSubscription = accounts.view().subscribe(this::onAccount);
			bankSubscription = bank.view().subscribe(this::onBank);
		}
		else if ("R2".equals(step))
		{
			maybeScheduleR2(client.getGameState());
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (lifecycleSubscription != null)
		{
			lifecycleSubscription.cancel();
		}
		if (bankSubscription != null)
		{
			bankSubscription.cancel();
		}
		receipt.event("witness.stop", "thread", Thread.currentThread().getName());
		receipt.close();
	}

	/** The witness must observe the same singletons the production plugin uses, or R3 is BLOCKED. */
	private void recordSharedInstances()
	{
		RaccoonPlugin production = null;
		for (Plugin p : pluginManager.getPlugins())
		{
			if (p instanceof RaccoonPlugin)
			{
				production = (RaccoonPlugin) p;
			}
		}
		boolean shared = false;
		String detail;
		if (production == null)
		{
			detail = "production plugin not loaded";
		}
		else
		{
			try
			{
				shared = field(production, "accountLifecycle") == accounts
					&& field(production, "bankObservation") == bank
					&& field(production, "brutusKc") == kc;
				detail = shared ? "same instances" : "different instances";
			}
			catch (ReflectiveOperationException e)
			{
				detail = "unreadable: " + e;
			}
		}
		receipt.event("witness.shared", "shared", shared, "detail", detail);
	}

	private static Object field(Object o, String name) throws ReflectiveOperationException
	{
		Field f = RaccoonPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		return f.get(o);
	}

	// ------------------------------------------------------------------ R2

	private void maybeScheduleR2(GameState state)
	{
		if (r2Scheduled || state != GameState.LOGIN_SCREEN)
		{
			return;
		}
		r2Scheduled = true;
		receipt.event("r2.scheduled", "gameState", String.valueOf(state));
		clientThread.invokeLater(this::runR2);
	}

	private void runR2()
	{
		boolean onClientThread = client.isClientThread();
		receipt.event("r2.start", "thread", Thread.currentThread().getName(), "clientThread", onClientThread,
			"gameState", String.valueOf(client.getGameState()));
		boolean passed = false;
		try
		{
			SuiteRunner.Outcome o = SuiteRunner.run(SuiteRunner.ADAPTER_SUITES, receipt);
			passed = o.passed() && onClientThread;
			Path perf = Paths.get("build", "perf", "fr005-performance.txt");
			if (Files.isRegularFile(perf))
			{
				for (String line : Files.readAllLines(perf))
				{
					receipt.event("r2.perf", "line", line);
				}
			}
			else
			{
				passed = false;
				receipt.event("r2.perf", "line", "missing report " + perf);
			}
			receipt.event("verdict", "step", "R2", "result", passed ? "PASS" : "FAIL", "tests", o.run, "failed", o.failed,
				"skipped", o.ignored, "clientThread", onClientThread);
		}
		catch (Throwable t)
		{
			receipt.event("verdict", "step", "R2", "result", "FAIL", "error", t.toString());
		}
		finally
		{
			if (Boolean.getBoolean("raccoon.playtest.exitAfterR2"))
			{
				receipt.close();
				Thread exit = new Thread(() ->
				{
					try
					{
						Thread.sleep(1500);
					}
					catch (InterruptedException ignored)
					{
						// exit anyway
					}
					System.exit(0);
				}, "raccoon-playtest-exit");
				exit.setDaemon(true);
				exit.start();
			}
		}
	}

	// ------------------------------------------------------------------ R3 observation

	private void onAccount(AccountSnapshot s)
	{
		AccountScope scope = s.scope().orElse(null);
		receipt.event("account", "revision", s.revision(), "state", String.valueOf(s.state()), "publishable", s.isPublishable(),
			"awaitingFreshIdentity", s.isAwaitingFreshIdentity(), "membership", String.valueOf(s.membership()),
			"lastRetire", String.valueOf(s.lastRetireReason()),
			"accountOrdinal", scope == null ? null : scope.accountId(), "login", scope == null ? null : scope.loginToken(),
			"epoch", scope == null ? null : scope.epoch(), "authority", scope == null ? null : scope.authority());
	}

	private void onBank(BankSnapshot s)
	{
		receipt.event("bank", "revision", s.revision(), "status", String.valueOf(s.status()), "reason", String.valueOf(s.reason()),
			"streak", s.streak(), "current", s.current().isPresent(), "historical", s.historical().isPresent(),
			"distinctItems", s.current().map(e -> e.contents().size()).orElse(null));
	}

	@Subscribe(priority = 1_000_000f)
	public void onGameStateChangedFirst(GameStateChanged e)
	{
		stateStart = System.nanoTime();
	}

	@Subscribe(priority = -1_000_000f)
	public void onGameStateChangedLast(GameStateChanged e)
	{
		long nanos = System.nanoTime() - stateStart;
		GameState state = e.getGameState();
		if ("R2".equals(step))
		{
			maybeScheduleR2(state);
			return;
		}
		receipt.event("gameState", "state", String.valueOf(state), "worldTypes", state == GameState.LOGGED_IN ? String.valueOf(client.getWorldType()) : null);
		receipt.event("timing", "event", "GameStateChanged", "nanos", nanos);
	}

	@Subscribe(priority = 1_000_000f)
	public void onGameTickFirst(GameTick e)
	{
		tickStart = System.nanoTime();
	}

	@Subscribe(priority = -1_000_000f)
	public void onGameTickLast(GameTick e)
	{
		long nanos = System.nanoTime() - tickStart;
		if (!"R3".equals(step))
		{
			return;
		}
		receipt.event("timing", "event", "GameTick", "nanos", nanos);
		AccountScope scope = accounts.view().snapshot().scope().orElse(null);
		if (scope != null)
		{
			NativeBankProbe.Sample sample = probe.sample(scope, client.getTickCount());
			if (sample != null)
			{
				receipt.event("bankProbe", "nanos", sample.nanos, "valid", sample.valid, "failure", sample.failure,
					"bankSlots", sample.bankSlots);
			}
		}
	}

	@Subscribe(priority = 1_000_000f)
	public void onChatMessageFirst(ChatMessage e)
	{
		chatStart = System.nanoTime();
		BrutusKcView view = kc.view();
		kcRevisionBefore = view.revision();
		kcTotalBefore = view.currentLoginTotal();
	}

	@Subscribe(priority = -1_000_000f)
	public void onChatMessageLast(ChatMessage e)
	{
		long nanos = System.nanoTime() - chatStart;
		if (!"R3".equals(step))
		{
			return;
		}
		receipt.event("timing", "event", "ChatMessage", "nanos", nanos);
		String text = e.getMessage() == null ? "" : COLOR.matcher(e.getMessage()).replaceAll("");
		Matcher m = BRUTUS.matcher(text);
		if (!m.matches())
		{
			return;
		}
		// genuine game line only: the text is the game's own Brutus KC message (no player names)
		BrutusKcView view = kc.view();
		OptionalLong after = view.currentLoginTotal();
		receipt.event("brutusLine", "chatType", String.valueOf(e.getType()), "typed", e.getType() == ChatMessageType.GAMEMESSAGE
				|| e.getType() == ChatMessageType.SPAM, "count", m.group(1), "text", text,
			"revisionBefore", kcRevisionBefore, "revisionAfter", view.revision(),
			"totalBefore", kcTotalBefore.isPresent() ? kcTotalBefore.getAsLong() : null,
			"totalAfter", after.isPresent() ? after.getAsLong() : null, "quarantined", view.isQuarantined());
	}
}
