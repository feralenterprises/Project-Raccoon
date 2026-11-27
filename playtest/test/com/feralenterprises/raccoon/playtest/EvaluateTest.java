package com.feralenterprises.raccoon.playtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

/**
 * FR-006 kit review (a0ef3e4c): evaluator verdicts. Negative cases are the confirmed false
 * PASS paths; positive cases are genuine-shaped receipts. These receipts are test inputs for
 * the evaluator only, never playtest evidence.
 */
public class EvaluateTest
{
	private static final String SHA = "5eb3405a5be817340a626f4625859f47ec9591afc10e792322dd290d3b456bd5";

	// ------------------------------------------------------------------ receipt builder

	private final List<String> lines = new ArrayList<>();
	private long mono = 1;

	private EvaluateTest e(String type, Object... kv)
	{
		StringBuilder b = new StringBuilder("{\"t\":1,\"mono\":").append(mono++).append(",\"type\":\"").append(type).append('"');
		for (int i = 0; i + 1 < kv.length; i += 2)
		{
			b.append(",\"").append(kv[i]).append("\":");
			Object v = kv[i + 1];
			b.append(v == null ? "null" : v instanceof String ? "\"" + v + "\"" : v.toString());
		}
		lines.add(b.append('}').toString());
		return this;
	}

	private EvaluateTest account(String state, boolean pub, Integer ordinal, Long login, Long epoch, String retire)
	{
		return e("account", "state", state, "publishable", pub, "accountOrdinal", ordinal, "login", login, "epoch", epoch,
			"lastRetire", retire, "hashAvailable", pub);
	}

	private EvaluateTest bank(String status, String reason, int streak, boolean current)
	{
		return e("bank", "status", status, "reason", reason, "streak", streak, "current", current, "historical", false);
	}

	private EvaluateTest brutus(long count, Long before, Long after, long revBefore, long revAfter, long login, long epoch)
	{
		return e("brutusLine", "chatType", "GAMEMESSAGE", "typed", true, "count", String.valueOf(count),
			"revisionBefore", revBefore, "revisionAfter", revAfter, "totalBefore", before, "totalAfter", after,
			"quarantined", false, "login", login, "epoch", epoch);
	}

	private EvaluateTest raccoon(boolean enabled, String gameState)
	{
		return e("raccoonPlugin", "enabled", enabled, "gameState", gameState);
	}

	private Result r3() throws Exception
	{
		Path f = Files.createTempFile("r3", ".jsonl");
		Files.write(f, lines, StandardCharsets.UTF_8);
		return run("R3", f.toString());
	}

	private static final class Result
	{
		final int code;
		final String text;

		Result(int code, String text)
		{
			this.code = code;
			this.text = text;
		}

		String verdict(String id)
		{
			for (String l : text.split("\n"))
			{
				String[] parts = l.trim().split("\\s+");
				if (parts.length >= 2 && parts[0].equals(id))
				{
					return parts[1];
				}
			}
			return "ABSENT";
		}
	}

	private static Result run(String... args) throws Exception
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		int code = Evaluate.run(args, new PrintStream(bytes, true, "UTF-8"));
		return new Result(code, new String(bytes.toByteArray(), StandardCharsets.UTF_8));
	}

	/** A genuine-shaped R3 session covering every required item. */
	private EvaluateTest fullSession()
	{
		e("witness.shared", "shared", true, "detail", "same instances");
		raccoon(false, "LOGIN_SCREEN");
		account("RETIRED", false, null, null, null, "PLUGIN_STOPPED");
		raccoon(true, "LOGGED_IN"); // late enable
		account("ACTIVE", true, 1, 1L, 1L, "NONE");
		// first bank open: qualifies, genuine failed read while open, immediate revoke, fresh streak
		bank("WAITING", "STREAK_BUILDING", 1, false);
		bank("QUALIFIED_CURRENT", "NONE", 2, true);
		bank("WAITING", "ITEMS_NOT_READY", 0, false);
		bank("WAITING", "STREAK_BUILDING", 1, false);
		bank("QUALIFIED_CURRENT", "NONE", 2, true);
		bank("WAITING", "BANK_CLOSED", 0, false);
		// second open: fresh streak
		bank("WAITING", "STREAK_BUILDING", 1, false);
		bank("QUALIFIED_CURRENT", "NONE", 2, true);
		for (int i = 0; i < 60; i++)
		{
			e("bankProbe", "nanos", 1_500_000L, "valid", true, "failure", null, "bankSlots", 800, "witnessKnown", i % 10 != 0,
				"identityKnown", true);
		}
		// verified hop
		account("SUSPENDED_CONTINUITY", false, null, null, null, "NONE");
		account("ACTIVE", true, 1, 1L, 2L, "NONE");
		// two consecutive genuine kills in the same epoch
		brutus(428, null, 0L, 0, 1, 1, 2);
		brutus(429, 0L, 1L, 1, 2, 1, 2);
		// disable / re-enable while logged in: new login, nothing restored
		raccoon(false, "LOGGED_IN");
		account("RETIRED", false, null, null, null, "PLUGIN_STOPPED");
		raccoon(true, "LOGGED_IN");
		account("ACTIVE", true, 1, 2L, 3L, "NONE");
		bank("WAITING", "BANK_CLOSED", 0, false);
		// second consenting identity
		account("RETIRED", false, null, null, null, "LOGOUT");
		account("ACTIVE", true, 2, 3L, 4L, "NONE");
		bank("WAITING", "BANK_CLOSED", 0, false);
		brutus(10, null, 0L, 2, 3, 3, 4);
		for (int i = 0; i < 1200; i++)
		{
			e("timing", "event", "GameTick", "nanos", 300_000L);
		}
		return this;
	}

	// ------------------------------------------------------------------ R3 positives

	@Test
	public void genuineShapedSessionPasses() throws Exception
	{
		Result r = fullSession().r3();
		for (String id : Arrays.asList("R3.failedRevalidation", "R3.bankTwoOpens", "R3.bankWitnessPerRead", "R3.lateEnable",
			"R3.disableReenable", "R3.twoIdentities", "R3.noCarryOver", "R3.brutusConsecutive", "R3.verifiedHop",
			"R3.perf.GameTick", "R3.perf.bankCapture"))
		{
			assertEquals(id + "\n" + r.text, "PASS", r.verdict(id));
		}
		assertEquals(r.text, 0, r.code);
	}

	// ------------------------------------------------------------------ R3 confirmed false PASS paths

	@Test
	public void logoutIsNotAFailedRevalidation() throws Exception
	{
		e("witness.shared", "shared", true, "detail", "same instances");
		account("ACTIVE", true, 1, 1L, 1L, "NONE");
		account("RETIRED", false, null, null, null, "LOGOUT");
		account("ACTIVE", true, 1, 2L, 2L, "NONE");
		Result r = r3();
		assertEquals(r.text, "BLOCKED", r.verdict("R3.failedRevalidation"));
	}

	@Test
	public void revokeAndRequalifyWithoutCloseOrFreshStreakIsNotTwoOpens() throws Exception
	{
		e("witness.shared", "shared", true, "detail", "same instances");
		account("ACTIVE", true, 1, 1L, 1L, "NONE");
		bank("QUALIFIED_CURRENT", "NONE", 2, true);
		bank("WAITING", "EVIDENCE_CHANGED", 1, false);
		bank("QUALIFIED_CURRENT", "NONE", 2, true);
		Result r = r3();
		assertFalse(r.text, "PASS".equals(r.verdict("R3.bankTwoOpens")));
		assertFalse(r.text, "PASS".equals(r.verdict("R3.failedRevalidation")));
	}

	@Test
	public void closedBankIsNotAnOpenBankFailedRead() throws Exception
	{
		e("witness.shared", "shared", true, "detail", "same instances");
		account("ACTIVE", true, 1, 1L, 1L, "NONE");
		bank("QUALIFIED_CURRENT", "NONE", 2, true);
		bank("WAITING", "BANK_CLOSED", 0, false);
		bank("WAITING", "STREAK_BUILDING", 1, false);
		bank("QUALIFIED_CURRENT", "NONE", 2, true);
		Result r = r3();
		assertEquals(r.text, "BLOCKED", r.verdict("R3.failedRevalidation"));
	}

	@Test
	public void baselineAndGainInDifferentEpochsAreNotConsecutive() throws Exception
	{
		e("witness.shared", "shared", true, "detail", "same instances");
		account("ACTIVE", true, 1, 1L, 1L, "NONE");
		brutus(428, null, 0L, 0, 1, 1, 1);
		brutus(431, 0L, 3L, 1, 2, 1, 2); // other epoch, +3
		Result r = r3();
		assertFalse(r.text, "PASS".equals(r.verdict("R3.brutusConsecutive")));
	}

	@Test
	public void gainOfTwoIsNotAConsecutivePlusOne() throws Exception
	{
		e("witness.shared", "shared", true, "detail", "same instances");
		account("ACTIVE", true, 1, 1L, 1L, "NONE");
		brutus(428, null, 0L, 0, 1, 1, 1);
		brutus(430, 0L, 2L, 1, 2, 1, 1);
		Result r = r3();
		assertFalse(r.text, "PASS".equals(r.verdict("R3.brutusConsecutive")));
	}

	@Test
	public void missingIdentitiesAndPluginTogglesAreBlocked() throws Exception
	{
		e("witness.shared", "shared", true, "detail", "same instances");
		account("ACTIVE", true, 1, 1L, 1L, "NONE");
		Result r = r3();
		for (String id : Arrays.asList("R3.twoIdentities", "R3.lateEnable", "R3.disableReenable", "R3.failedRevalidation",
			"R3.bankTwoOpens", "R3.brutusConsecutive"))
		{
			assertEquals(id + "\n" + r.text, "BLOCKED", r.verdict(id));
		}
		assertEquals(2, r.code);
	}

	@Test
	public void qualificationWithoutAnyKnownWitnessReadFails() throws Exception
	{
		e("witness.shared", "shared", true, "detail", "same instances");
		account("ACTIVE", true, 1, 1L, 1L, "NONE");
		bank("QUALIFIED_CURRENT", "NONE", 2, true);
		for (int i = 0; i < 60; i++)
		{
			e("bankProbe", "nanos", 1_000_000L, "valid", true, "failure", null, "bankSlots", 10, "witnessKnown", false, "identityKnown", true);
		}
		Result r = r3();
		assertEquals(r.text, "FAIL", r.verdict("R3.bankWitnessPerRead"));
	}

	@Test
	public void tickUpperBoundOverTheLimitIsNotAttributedToThePlugin() throws Exception
	{
		e("witness.shared", "shared", true, "detail", "same instances");
		for (int i = 0; i < 1200; i++)
		{
			e("timing", "event", "GameTick", "nanos", 5_000_000L);
		}
		Result r = r3();
		assertEquals(r.text, "BLOCKED", r.verdict("R3.perf.GameTick"));
	}

	@Test
	public void receiptNeverContainsAnAccountHashField() throws Exception
	{
		Result r = fullSession().r3();
		assertFalse(r.text.contains("accountHash"));
		for (String l : lines)
		{
			assertFalse(l, l.contains("\"hash\""));
		}
	}

	// ------------------------------------------------------------------ R4 / I03

	private static Path officialJar() throws Exception
	{
		// stands in for the official JAR: the identity check is by hash, so the expected hash is
		// this file's own hash in these tests
		Path jar = Files.createTempFile("official", ".jar");
		try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar)))
		{
			z.putNextEntry(new ZipEntry("com/feralenterprises/raccoon/RaccoonPlugin.class"));
			z.write(new byte[]{1, 2, 3});
		}
		return jar;
	}

	private static Path clientLib() throws Exception
	{
		Path dir = Files.createTempDirectory("lib");
		try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(dir.resolve("client.jar"))))
		{
			z.putNextEntry(new ZipEntry("net/runelite/client/RuneLite.class"));
			z.write(new byte[]{1});
		}
		return dir;
	}

	private static Result r4(String exitCode, String... log) throws Exception
	{
		Path f = Files.createTempFile("client", ".log");
		Files.write(f, Arrays.asList(log), StandardCharsets.UTF_8);
		Path jar = officialJar();
		String sha = Evaluate.sha256(Files.readAllBytes(jar));
		return run("R4", f.toString(), jar.toString(), sha, clientLib().toString(), exitCode);
	}

	private static final String SIDE = "10:00:00 [main] INFO  n.r.client.plugins.PluginManager - Side-loading plugin C:\\k\\home\\.runelite\\sideloaded-plugins\\project-raccoon.jar";
	private static final String START = "10:00:01 [AWT] DEBUG c.f.raccoon.RaccoonPlugin - Project Raccoon started";
	private static final String STOP = "10:00:02 [AWT] DEBUG c.f.raccoon.RaccoonPlugin - Project Raccoon stopped";

	@Test
	public void twoCompleteCyclesAndCleanShutdownPass() throws Exception
	{
		Result r = r4("0", SIDE, START, STOP, START, STOP);
		assertEquals(r.text, "PASS", r.verdict("R4.startStopTwice"));
		assertEquals(r.text, "PASS", r.verdict("R4.cleanShutdown"));
		assertEquals(r.text, "PASS", r.verdict("I03.exactOfficialJar"));
		assertEquals(r.text, 0, r.code);
	}

	@Test
	public void incompleteSecondCycleIsNotTwoCycles() throws Exception
	{
		Result r = r4("0", SIDE, START, STOP, START, STOP, START);
		assertFalse(r.text, "PASS".equals(r.verdict("R4.startStopTwice")));
	}

	@Test
	public void uncleanClientShutdownFails() throws Exception
	{
		Result r = r4("1", SIDE, START, STOP, START, STOP);
		assertEquals(r.text, "FAIL", r.verdict("R4.cleanShutdown"));
		assertTrue(r.code != 0);
	}

	@Test
	public void i03NeedsLoadAndDisable() throws Exception
	{
		Result r = r4("0", SIDE, START);
		assertFalse(r.text, "PASS".equals(r.verdict("I03.exactOfficialJar")));
	}

	@Test
	public void errorLinesAreCountedNotEchoed() throws Exception
	{
		String secret = "10:00:03 [Client] ERROR n.r.c.SomePlugin - raccoon player SecretName said hello";
		Result r = r4("0", SIDE, START, secret, STOP, START, STOP);
		assertFalse(r.text, r.text.contains("SecretName"));
		assertEquals(r.text, "FAIL", r.verdict("R4.noErrors"));
	}
}
