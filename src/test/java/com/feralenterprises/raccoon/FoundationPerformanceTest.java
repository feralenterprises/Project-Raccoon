package com.feralenterprises.raccoon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.feralenterprises.raccoon.core.bank.BankObservationTracker;
import com.feralenterprises.raccoon.core.bank.BankStatus;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * FR-005 performance measurements. DETERMINISTIC SIMULATION, not native RuneLite: the plugin's
 * production wiring (Guice, real RuneLite EventBus) is driven by a recording client proxy on the
 * build machine (see {@link PluginHarness}). Native measurements remain required in FR-006.
 *
 * <p>Method: each sample is one {@code EventBus.post} of a single event, timed with
 * {@link System#nanoTime()} around the post (bus dispatch plus every plugin callback for that
 * event). Warm-up samples are discarded. Percentiles are nearest-rank. Results are written to
 * {@code build/perf/fr005-performance.txt}.
 * <ul>
 * <li>Event callback replay: a fixed cycle of typed events (Brutus KC lines, other game
 * messages, player chat, spam, game ticks with the bank closed, client ticks); 3,000 warm-up,
 * 1,000 measured. Requirement p95 &le; 2 ms.</li>
 * <li>Bank-open capture: one game tick with the bank open, each producing exactly one capture
 * (lifecycle tick, visibility, before/after identity, bank/inventory/equipment reads, item
 * definitions for every slot, tab varbits, evaluation and publication); 50 warm-up, 200
 * measured, at 1,000 occupied bank slots and at the tracker bound of 4,096. Requirement p95
 * &le; 10 ms.</li>
 * </ul>
 */
public class FoundationPerformanceTest
{
	private static final long HASH = 0x5EC12E7AB1E5L;
	private static final long EVENT_P95_LIMIT_NANOS = 2_000_000L;
	private static final long BANK_P95_LIMIT_NANOS = 10_000_000L;
	private static final List<String> report = new ArrayList<>();

	@Test
	public void eventCallbackReplayP95WithinTwoMilliseconds() throws Exception
	{
		PluginHarness h = new PluginHarness();
		h.enable();
		h.logIn(HASH);
		int[] kc = {1000};
		Runnable[] cycle = {
			() -> h.eventBus.post(PluginHarness.brutus(++kc[0])),
			() -> h.eventBus.post(PluginHarness.chat(ChatMessageType.GAMEMESSAGE, "Your Vorkath kill count is: <col=ff0000>12</col>.")),
			() -> h.eventBus.post(PluginHarness.chat(ChatMessageType.PUBLICCHAT, "Your Brutus kill count is: 9999.")),
			() -> h.eventBus.post(PluginHarness.chat(ChatMessageType.SPAM, "You eat the shark.")),
			() -> h.eventBus.post(PluginHarness.newEvent(net.runelite.api.events.GameTick.class)),
			() -> h.eventBus.post(PluginHarness.clientTick()),
		};
		long[] samples = run(cycle, 3_000, 1_000, () -> h.tick++);
		assertTrue(h.subscriberErrors.isEmpty());
		assertTrue(h.unexpected.isEmpty());
		assertTrue("replay counted", h.kc.view().currentLoginTotal().getAsLong() > 0);
		assertEquals(GameState.LOGGED_IN, h.gameState);
		long p95 = record("event.callback", samples, EVENT_P95_LIMIT_NANOS);
		assertTrue("event callback p95 " + p95 + " ns > 2 ms", p95 <= EVENT_P95_LIMIT_NANOS);
		h.disable();
	}

	@Test
	public void bankOpenCaptureP95WithinTenMillisecondsAt1000Slots() throws Exception
	{
		bankCapture(1_000);
	}

	@Test
	public void bankOpenCaptureP95WithinTenMillisecondsAtTheTrackerBound() throws Exception
	{
		bankCapture(4_096);
	}

	private void bankCapture(int slots) throws Exception
	{
		PluginHarness h = new PluginHarness();
		h.bank = PluginHarness.items(slots, 2);
		h.inventory = PluginHarness.items(28, 1);
		h.equipment = PluginHarness.items(14, 11);
		h.enable();
		h.logIn(HASH);
		h.bankOpen = true;
		BankObservationTracker tracker = (BankObservationTracker) h.bankService.view();
		long[] attempts = {tracker.captureAttempts()};
		int[] missed = {0};
		Runnable tick = () ->
		{
			h.tick++;
			h.eventBus.post(PluginHarness.newEvent(net.runelite.api.events.GameTick.class));
		};
		Runnable check = () ->
		{
			long now = tracker.captureAttempts();
			if (now != attempts[0] + 1)
			{
				missed[0]++;
			}
			attempts[0] = now;
		};
		long[] samples = runChecked(tick, check, 50, 200);
		assertEquals("every sample captured exactly once", 0, missed[0]);
		assertEquals(BankStatus.QUALIFIED_CURRENT, h.bankService.view().snapshot().status());
		assertTrue(h.subscriberErrors.isEmpty());
		assertTrue(h.unexpected.isEmpty());
		long p95 = record("bank.capture." + slots + "slots", samples, BANK_P95_LIMIT_NANOS);
		assertTrue("bank capture p95 " + p95 + " ns > 10 ms", p95 <= BANK_P95_LIMIT_NANOS);
		h.disable();
	}

	// ------------------------------------------------------------------ measurement

	private static long[] run(Runnable[] cycle, int warmup, int measured, Runnable between)
	{
		long[] out = new long[measured];
		for (int i = 0; i < warmup + measured; i++)
		{
			Runnable r = cycle[i % cycle.length];
			between.run();
			long t0 = System.nanoTime();
			r.run();
			long t1 = System.nanoTime();
			if (i >= warmup)
			{
				out[i - warmup] = t1 - t0;
			}
		}
		return out;
	}

	private static long[] runChecked(Runnable sample, Runnable check, int warmup, int measured)
	{
		long[] out = new long[measured];
		for (int i = 0; i < warmup + measured; i++)
		{
			long t0 = System.nanoTime();
			sample.run();
			long t1 = System.nanoTime();
			check.run();
			if (i >= warmup)
			{
				out[i - warmup] = t1 - t0;
			}
		}
		return out;
	}

	static long percentile(long[] sorted, double p)
	{
		int rank = (int) Math.ceil(p * sorted.length);
		return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
	}

	private static long record(String name, long[] samples, long limit)
	{
		long[] s = samples.clone();
		Arrays.sort(s);
		long sum = 0;
		for (long x : s)
		{
			sum += x;
		}
		long p95 = percentile(s, 0.95);
		report.add(String.format("%s samples=%d p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms mean=%.3fms limit=%.0fms result=%s",
			name, s.length, ms(percentile(s, 0.50)), ms(p95), ms(percentile(s, 0.99)), ms(s[s.length - 1]),
			ms(sum / s.length), ms(limit), p95 <= limit ? "PASS" : "FAIL"));
		return p95;
	}

	private static double ms(long nanos)
	{
		return nanos / 1_000_000.0;
	}

	@AfterClass
	public static void writeReport() throws IOException
	{
		List<String> lines = new ArrayList<>();
		lines.add("kind=deterministic simulation (not native RuneLite; native measurements are FR-006)");
		lines.add("method=EventBus.post per sample, System.nanoTime, warm-up discarded, nearest-rank percentiles");
		lines.add("os=" + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
		lines.add("cpus=" + Runtime.getRuntime().availableProcessors() + " cpu=" + cpuModel());
		lines.add("jvm=" + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version")
			+ " (" + System.getProperty("java.vendor") + ", java " + System.getProperty("java.version") + ")"
			+ " maxHeap=" + Runtime.getRuntime().maxMemory() / (1024 * 1024) + "MiB");
		lines.add("runner=" + env("RUNNER_OS") + "/" + env("RUNNER_ARCH") + " image=" + env("ImageOS") + " " + env("ImageVersion")
			+ " runner.name=" + env("RUNNER_NAME"));
		lines.add("toolchain=Gradle test task, javac --release 11, RuneLite client 1.13.8 (test classpath), JUnit 4.12");
		lines.addAll(report);
		Path out = Paths.get("build", "perf", "fr005-performance.txt");
		Files.createDirectories(out.getParent());
		Files.write(out, lines, StandardCharsets.UTF_8);
		for (String l : lines)
		{
			System.out.println("PERF " + l);
		}
	}

	private static String env(String k)
	{
		String v = System.getenv(k);
		return v == null ? "n/a" : v;
	}

	private static String cpuModel()
	{
		File f = new File("/proc/cpuinfo");
		if (!f.isFile())
		{
			return "n/a";
		}
		try
		{
			for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
			{
				if (l.startsWith("model name"))
				{
					return l.substring(l.indexOf(':') + 1).trim();
				}
			}
		}
		catch (IOException e)
		{
			return "unreadable";
		}
		return "n/a";
	}
}
