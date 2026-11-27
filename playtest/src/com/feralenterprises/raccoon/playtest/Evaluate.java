package com.feralenterprises.raccoon.playtest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Turns playtest receipts into verdicts. Missing genuine evidence is BLOCKED, never PASS; an
 * optional observation that did not happen is NOT_OBSERVED. Exit code: 0 all required PASS,
 * 1 any FAIL, 2 otherwise (BLOCKED).
 *
 * <pre>
 * Evaluate R1|R2|R3 &lt;receipt.jsonl&gt;
 * Evaluate R4|LaunchPath &lt;client.log&gt; &lt;sideloaded jar&gt; &lt;expected sha256&gt; &lt;client lib dirs, ';'-separated&gt;
 * </pre>
 */
public final class Evaluate
{
	static final long EVENT_P95_LIMIT = 2_000_000L;
	static final long BANK_P95_LIMIT = 10_000_000L;

	private final List<String[]> rows = new ArrayList<>();
	private final java.io.PrintStream out;

	private Evaluate(java.io.PrintStream out)
	{
		this.out = out;
	}

	public static void main(String[] args) throws Exception
	{
		System.exit(run(args, System.out));
	}

	/** Evaluates and prints the verdicts to {@code out}; returns the exit code (test seam). */
	static int run(String[] args, java.io.PrintStream out) throws Exception
	{
		Evaluate ev = new Evaluate(out);
		String step = args[0];
		switch (step)
		{
			case "R1":
			case "R2":
				ev.verdictStep(step, read(Paths.get(args[1])));
				break;
			case "R3":
				ev.r3(read(Paths.get(args[1])));
				break;
			case "R4":
				ev.r4(Paths.get(args[1]), Paths.get(args[2]), args[3], args[4], false, args.length > 5 ? args[5] : null);
				break;
			case "LaunchPath":
				ev.r4(Paths.get(args[1]), Paths.get(args[2]), args[3], args[4], true, null);
				break;
			default:
				throw new IllegalArgumentException("unknown step " + step);
		}
		return ev.print(step);
	}

	private void row(String id, String verdict, String detail)
	{
		rows.add(new String[]{id, verdict, detail});
	}

	private int print(String step)
	{
		boolean fail = false;
		boolean blocked = false;
		out.println(step + " verdicts");
		for (String[] r : rows)
		{
			out.println(String.format("  %-36s %-13s %s", r[0], r[1], r[2]));
			fail |= "FAIL".equals(r[1]);
			blocked |= "BLOCKED".equals(r[1]);
		}
		String overall = fail ? "FAIL" : blocked ? "BLOCKED" : "PASS";
		out.println(step + " overall: " + overall);
		return fail ? 1 : blocked ? 2 : 0;
	}

	// ------------------------------------------------------------------ R1 / R2

	private void verdictStep(String step, List<Map<String, Object>> events)
	{
		Map<String, Object> verdict = last(events, "verdict");
		Map<String, Object> suites = last(events, "suites");
		if (verdict == null || suites == null)
		{
			row(step, "BLOCKED", "no verdict in receipt (run incomplete)");
			return;
		}
		row(step + ".suites", "PASS".equals(verdict.get("result")) ? "PASS" : "FAIL",
			"tests=" + suites.get("run") + " failed=" + suites.get("failed") + " skipped=" + suites.get("skipped")
				+ " missing=" + suites.get("missing"));
		if ("R2".equals(step))
		{
			Map<String, Object> start = last(events, "r2.start");
			boolean onThread = start != null && Boolean.TRUE.equals(start.get("clientThread"));
			row("R2.clientThread", onThread ? "PASS" : "FAIL", start == null ? "no start record"
				: "thread=" + start.get("thread") + " gameState=" + start.get("gameState"));
			for (Map<String, Object> p : all(events, "r2.perf"))
			{
				String line = String.valueOf(p.get("line"));
				if (line.contains("result="))
				{
					row("R2.perf", line.contains("result=PASS") ? "PASS" : "FAIL", line);
				}
			}
		}
	}

	// ------------------------------------------------------------------ R3

	/** Bank reasons that mean a read or evaluation genuinely failed while the bank was open. */
	static final java.util.Set<String> FAILED_READ = new java.util.HashSet<>(java.util.Arrays.asList(
		"VISIBILITY_UNREADABLE", "MISSING_CONTAINER", "READ_FAILURE", "MALFORMED_SLOT", "MALFORMED_QUANTITY",
		"UNREADABLE_DEFINITION", "OVERFLOW", "WITNESS_EXCEEDS_SLOTS", "CARRIED_UNREADABLE", "CONTAINER_TOO_LARGE",
		"ITEMS_NOT_READY", "RETRY_LIMIT"));

	private void r3(List<Map<String, Object>> ev)
	{
		Map<String, Object> shared = last(ev, "witness.shared");
		row("R3.sharedInstances", shared != null && Boolean.TRUE.equals(shared.get("shared")) ? "PASS" : "BLOCKED",
			shared == null ? "no record" : String.valueOf(shared.get("detail")));
		lifecycle(ev);
		bank(ev);
		identities(ev);
		brutus(ev);
		timings(ev);
	}

	private void lifecycle(List<Map<String, Object>> ev)
	{
		boolean login = false;
		boolean hop = false;
		Long activeLogin = null;
		Long activeEpoch = null;
		boolean suspended = false;
		// plugin toggles: late enable (off at login, enabled while logged in) and disable/re-enable
		boolean lateEnable = false;
		boolean disableReenable = false;
		String pending = null;
		Long loginBeforeDisable = null;
		boolean offWhileNotLoggedIn = false;
		int known = 0;
		int unknown = 0;
		for (Map<String, Object> e : ev)
		{
			String type = String.valueOf(e.get("type"));
			if ("raccoonPlugin".equals(type))
			{
				boolean enabled = Boolean.TRUE.equals(e.get("enabled"));
				boolean loggedIn = "LOGGED_IN".equals(e.get("gameState"));
				if (!enabled)
				{
					offWhileNotLoggedIn = !loggedIn;
					pending = loggedIn ? "disabledLoggedIn" : null;
					loginBeforeDisable = loggedIn ? activeLogin : null;
				}
				else if (loggedIn)
				{
					pending = "disabledLoggedIn".equals(pending) ? "reenable" : offWhileNotLoggedIn ? "late" : null;
				}
				continue;
			}
			if (!"account".equals(type))
			{
				continue;
			}
			if (e.get("hashAvailable") != null)
			{
				if (Boolean.TRUE.equals(e.get("hashAvailable")))
				{
					known++;
				}
				else
				{
					unknown++;
				}
			}
			String state = String.valueOf(e.get("state"));
			Long lg = asLong(e.get("login"));
			Long ep = asLong(e.get("epoch"));
			if ("ACTIVE".equals(state) && Boolean.TRUE.equals(e.get("publishable")))
			{
				login = true;
				if (suspended && Objects.equals(lg, activeLogin) && !Objects.equals(ep, activeEpoch))
				{
					hop = true;
				}
				if ("late".equals(pending))
				{
					lateEnable = true;
				}
				if ("reenable".equals(pending) && !Objects.equals(lg, loginBeforeDisable))
				{
					disableReenable = true;
				}
				pending = null;
				activeLogin = lg;
				activeEpoch = ep;
				suspended = false;
			}
			else if ("SUSPENDED_CONTINUITY".equals(state))
			{
				suspended = activeLogin != null;
			}
			else if ("RETIRED".equals(state))
			{
				activeLogin = "PLUGIN_STOPPED".equals(String.valueOf(e.get("lastRetire"))) ? activeLogin : null;
				suspended = false;
			}
		}
		row("R3.genuineLogin", login ? "PASS" : "BLOCKED", login ? "ACTIVE and publishable after a genuine login" : "not observed");
		row("R3.verifiedHop", hop ? "PASS" : "BLOCKED", hop ? "same login restored in a new epoch" : "no verified same-login hop observed");
		row("R3.lateEnable", lateEnable ? "PASS" : "BLOCKED", lateEnable ? "off at login, enabled while logged in, then a fresh identity read"
			: "not observed: disable before login, enable after login");
		row("R3.disableReenable", disableReenable ? "PASS" : "BLOCKED", disableReenable ? "disabled and re-enabled while logged in; a new login, nothing restored"
			: "not observed");
		row("R3.identityAvailability", known + unknown > 0 ? "REPORTED" : "BLOCKED",
			"account hash available on " + known + " reads, unavailable on " + unknown + " (boolean only; no hash recorded)");
	}

	private void bank(List<Map<String, Object>> ev)
	{
		boolean qualifiedOpen = false; // qualified, bank still open
		boolean failedRead = false;
		boolean failedReadRevoked = false;
		boolean failedReadFresh = false;
		String failedReason = null;
		int opens = 0;
		boolean twoOpens = false;
		boolean closedAfterQualified = false;
		boolean freshAfterClose = false;
		boolean pin = false;
		boolean anyQualified = false;
		boolean awaitFresh = false;
		String awaitFreshFor = null;
		boolean wasClosed = true;
		for (Map<String, Object> b : all(ev, "bank"))
		{
			String status = String.valueOf(b.get("status"));
			String reason = String.valueOf(b.get("reason"));
			boolean current = Boolean.TRUE.equals(b.get("current"));
			Long streak = asLong(b.get("streak"));
			pin |= "PIN".equals(reason);
			boolean closed = "BANK_CLOSED".equals(reason) || "NOT_PUBLISHABLE".equals(reason) || "SCOPE_CHANGED".equals(reason)
				|| "IDENTITY_CHANGED".equals(reason);
			if (!closed && wasClosed && !"PIN".equals(reason))
			{
				opens++;
			}
			wasClosed = closed;
			if (awaitFresh && streak != null && streak == 1 && !current)
			{
				if ("failure".equals(awaitFreshFor))
				{
					failedReadFresh = true;
				}
				else
				{
					freshAfterClose = true;
				}
				awaitFresh = false;
				awaitFreshFor = "requalify-" + awaitFreshFor;
				continue;
			}
			if ("QUALIFIED_CURRENT".equals(status))
			{
				anyQualified = true;
				if ("requalify-failure".equals(awaitFreshFor) && failedReadFresh)
				{
					failedRead = true;
				}
				if ("requalify-close".equals(awaitFreshFor) && freshAfterClose && opens >= 2)
				{
					twoOpens = true;
				}
				awaitFreshFor = null;
				qualifiedOpen = true;
				continue;
			}
			if (qualifiedOpen && FAILED_READ.contains(reason))
			{
				// the first record after qualification with a failed read must already be revoked
				failedReadRevoked = !current;
				failedReason = reason;
				awaitFresh = failedReadRevoked;
				awaitFreshFor = "failure";
			}
			else if (qualifiedOpen && "BANK_CLOSED".equals(reason))
			{
				closedAfterQualified = !current;
				awaitFresh = closedAfterQualified;
				awaitFreshFor = "close";
			}
			qualifiedOpen = false;
		}
		row("R3.bankQualified", anyQualified ? "PASS" : "BLOCKED", anyQualified ? "QUALIFIED_CURRENT from a genuine open bank" : "not observed");
		row("R3.failedRevalidation", failedRead ? "PASS" : "BLOCKED", failedRead
			? "genuine failed read (" + failedReason + ") while open after qualification: revoked at once, re-qualified only through a fresh streak"
			: "not observed: needs a genuine failed bank read while the bank is open, after qualification");
		row("R3.bankTwoOpens", twoOpens ? "PASS" : "BLOCKED", twoOpens ? "qualified, closed (revoked), reopened, fresh streak, re-qualified"
			: "not observed: open, qualify, close, reopen");
		row("R3.bankPin", pin ? "PASS" : "NOT_OBSERVED", pin ? "PIN seen, no capture" : "optional: account without a bank PIN or PIN not shown");

		int known = 0;
		int unknown = 0;
		int unclassified = 0;
		List<Long> probe = new ArrayList<>();
		int invalid = 0;
		for (Map<String, Object> p : all(ev, "bankProbe"))
		{
			Object w = p.get("witnessKnown");
			if (w == null)
			{
				unclassified++;
			}
			else if (Boolean.TRUE.equals(w))
			{
				known++;
			}
			else
			{
				unknown++;
			}
			if (Boolean.TRUE.equals(p.get("valid")))
			{
				probe.add(asLong(p.get("nanos")));
			}
			else
			{
				invalid++;
			}
		}
		String witness = "tab witness per read: known=" + known + " unknown=" + unknown + " unclassified=" + unclassified;
		if (known + unknown + unclassified == 0)
		{
			row("R3.bankWitnessPerRead", "BLOCKED", "no genuine open-bank reads");
		}
		else if (unclassified > 0 || (anyQualified && known == 0))
		{
			row("R3.bankWitnessPerRead", "FAIL", witness + (anyQualified && known == 0 ? "; qualified without any known-witness read" : ""));
		}
		else
		{
			row("R3.bankWitnessPerRead", "PASS", witness);
		}
		perf("R3.perf.bankCapture", probe, 50, BANK_P95_LIMIT, "genuine open bank, read + evaluate; invalid=" + invalid);
	}

	private void identities(List<Map<String, Object>> ev)
	{
		java.util.Set<Long> ordinals = new java.util.LinkedHashSet<>();
		Long currentLogin = null;
		boolean changed = false;
		boolean expectBank = false;
		boolean expectKc = false;
		boolean carryOver = false;
		int changes = 0;
		for (Map<String, Object> e : ev)
		{
			String type = String.valueOf(e.get("type"));
			if ("account".equals(type) && "ACTIVE".equals(e.get("state")) && Boolean.TRUE.equals(e.get("publishable")))
			{
				Long ordinal = asLong(e.get("accountOrdinal"));
				Long lg = asLong(e.get("login"));
				if (ordinal != null)
				{
					ordinals.add(ordinal);
				}
				if (currentLogin != null && !Objects.equals(lg, currentLogin))
				{
					changes++;
					expectBank = true;
					expectKc = true;
				}
				currentLogin = lg;
			}
			else if ("bank".equals(type) && expectBank)
			{
				carryOver |= Boolean.TRUE.equals(e.get("current")) || Boolean.TRUE.equals(e.get("historical"));
				expectBank = false;
			}
			else if ("brutusLine".equals(type) && expectKc && Boolean.TRUE.equals(e.get("typed")))
			{
				long before = asLong(e.get("totalBefore")) == null ? 0 : asLong(e.get("totalBefore"));
				long after = asLong(e.get("totalAfter")) == null ? 0 : asLong(e.get("totalAfter"));
				carryOver |= before != 0 || after != 0;
				expectKc = false;
			}
		}
		changed = ordinals.size() >= 2;
		row("R3.twoIdentities", changed ? "PASS" : "BLOCKED", changed ? ordinals.size() + " distinct consenting accounts (run-local ordinals)"
			: "not observed: log in with a second consenting account");
		row("R3.noCarryOver", changes == 0 ? "BLOCKED" : carryOver ? "FAIL" : "PASS",
			changes == 0 ? "no login change observed" : carryOver ? "bank or KC data carried across a login change" : changes + " login changes, nothing carried over");
	}

	private void brutus(List<Map<String, Object>> ev)
	{
		Map<String, Integer> types = new LinkedHashMap<>();
		boolean consecutive = false;
		boolean contradicted = false;
		Map<String, Object> previous = null;
		for (Map<String, Object> b : all(ev, "brutusLine"))
		{
			types.merge(String.valueOf(b.get("chatType")), 1, Integer::sum);
			if (!Boolean.TRUE.equals(b.get("typed")))
			{
				previous = null;
				continue;
			}
			if (previous != null && Objects.equals(previous.get("login"), b.get("login")) && Objects.equals(previous.get("epoch"), b.get("epoch"))
				&& b.get("login") != null)
			{
				long c1 = count(previous);
				long c2 = count(b);
				long before = asLong(b.get("totalBefore")) == null ? 0 : asLong(b.get("totalBefore"));
				long after = asLong(b.get("totalAfter")) == null ? 0 : asLong(b.get("totalAfter"));
				boolean revised = asLong(b.get("revisionAfter")) > asLong(b.get("revisionBefore"));
				if (c2 == c1 + 1)
				{
					if (after - before == 1 && revised)
					{
						consecutive = true;
					}
					else
					{
						contradicted = true;
					}
				}
			}
			previous = b;
		}
		row("R3.brutusLineType", types.isEmpty() ? "BLOCKED" : "PASS", types.isEmpty() ? "no genuine Brutus line" : "observed chat types " + types);
		row("R3.brutusConsecutive", contradicted ? "FAIL" : consecutive ? "PASS" : "BLOCKED", contradicted
			? "consecutive genuine counts +1 did not add exactly 1"
			: consecutive ? "consecutive genuine counts +1 in the same authenticated epoch added exactly 1"
			: "not observed: two consecutive genuine kills in the same epoch");
	}

	private static long count(Map<String, Object> b)
	{
		return Long.parseLong(String.valueOf(b.get("count")).replace(",", ""));
	}

	private void timings(List<Map<String, Object>> ev)
	{
		for (String name : new String[]{"GameTick", "ChatMessage"})
		{
			List<Long> t = new ArrayList<>();
			for (Map<String, Object> x : all(ev, "timing"))
			{
				if (name.equals(x.get("event")))
				{
					t.add(asLong(x.get("nanos")));
				}
			}
			if ("GameTick".equals(name))
			{
				upperBound("R3.perf.GameTick", t);
			}
			else
			{
				perf("R3.perf." + name, t, 1000, EVENT_P95_LIMIT, "genuine events, upper bound", false);
			}
		}
	}

	/**
	 * The genuine tick bracket includes every subscriber between the witness priorities: under the
	 * limit it is a conservative PASS; over the limit it is not attributable to the plugin.
	 */
	private void upperBound(String id, List<Long> samples)
	{
		if (samples.size() < 1000)
		{
			row(id, "BLOCKED", samples.size() + " samples (< 1000); genuine ticks, upper bound");
			return;
		}
		List<Long> s = new ArrayList<>(samples);
		Collections.sort(s);
		long p95 = pct(s, 0.95);
		String d = String.format("n=%d p50=%.3fms p95=%.3fms max=%.3fms (limit %.0fms); upper bound incl. other subscribers",
			s.size(), ms(pct(s, 0.5)), ms(p95), ms(s.get(s.size() - 1)), ms(EVENT_P95_LIMIT));
		row(id, p95 <= EVENT_P95_LIMIT ? "PASS" : "BLOCKED", p95 <= EVENT_P95_LIMIT ? d + "; conservative pass" : d + "; over the limit, not attributable to the plugin without measurement");
	}

	private void perf(String id, List<Long> samples, int minSamples, long limit, String what)
	{
		perf(id, samples, minSamples, limit, what, true);
	}

	private void perf(String id, List<Long> samples, int minSamples, long limit, String what, boolean gate)
	{
		if (samples.isEmpty() || (gate && samples.size() < minSamples))
		{
			row(id, gate ? "BLOCKED" : "REPORTED", samples.size() + " samples (< " + minSamples + "); " + what);
			return;
		}
		if (!gate)
		{
			List<Long> s = new ArrayList<>(samples);
			Collections.sort(s);
			row(id, "REPORTED", String.format("n=%d p50=%.3fms p95=%.3fms max=%.3fms; %s", s.size(), ms(pct(s, 0.5)), ms(pct(s, 0.95)),
				ms(s.get(s.size() - 1)), what));
			return;
		}
		List<Long> s = new ArrayList<>(samples);
		Collections.sort(s);
		long p95 = pct(s, 0.95);
		row(id, p95 <= limit ? "PASS" : "FAIL", String.format("n=%d p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms (limit %.0fms); %s",
			s.size(), ms(pct(s, 0.5)), ms(p95), ms(pct(s, 0.99)), ms(s.get(s.size() - 1)), ms(limit), what));
	}

	static long pct(List<Long> sorted, double p)
	{
		int rank = (int) Math.ceil(p * sorted.size());
		return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
	}

	private static double ms(long nanos)
	{
		return nanos / 1_000_000.0;
	}

	// ------------------------------------------------------------------ R4 / I03

	/**
	 * R4/I03, or (launchPath) the unattended launch-path check used by CI, which only shows that
	 * the official JAR side-loads and starts; it is never an R4 receipt.
	 */
	private void r4(Path log, Path sideloaded, String expectedSha, String clientLibs, boolean launchPath, String exitCode) throws IOException
	{
		String p = launchPath ? "LaunchPath." : "R4.";
		String sha = Files.isRegularFile(sideloaded) ? sha256(Files.readAllBytes(sideloaded)) : "missing";
		row(p + "officialJarIdentity", sha.equals(expectedSha) ? "PASS" : "FAIL", "sideloaded sha256=" + sha);
		row(p + "officialJarContents", officialJarClean(sideloaded), "no playtest, test or launcher classes in the production artifact");
		int foreign = 0;
		int jars = 0;
		for (String dir : clientLibs.split(";"))
		{
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(Paths.get(dir), "*.jar"))
			{
				for (Path j : ds)
				{
					jars++;
					foreign += countEntries(j, "com/feralenterprises/");
				}
			}
		}
		row(p + "clientClasspathClean", jars > 0 && foreign == 0 ? "PASS" : "FAIL", jars + " client jars, " + foreign + " Raccoon entries");

		if (!Files.isRegularFile(log))
		{
			row(p + "clientLog", "BLOCKED", "no client log at " + log.getFileName());
			if (!launchPath)
			{
				row("I03.exactOfficialJar", "BLOCKED", "no client log");
			}
			return;
		}
		// the client log may contain platform-encoded bytes on Windows: decode leniently, never throw
		List<String> lines = java.util.Arrays.asList(new String(Files.readAllBytes(log), StandardCharsets.UTF_8).split("\\r?\\n"));
		boolean sideload = false;
		int started = 0;
		int stopped = 0;
		boolean alternating = true;
		boolean expectStart = true;
		List<String> problems = new ArrayList<>();
		for (String l : lines)
		{
			String lower = l.toLowerCase();
			if ((lower.contains("side-load") || lower.contains("sideload")) && lower.contains("project-raccoon"))
			{
				sideload = true;
			}
			if (l.contains("Project Raccoon started"))
			{
				started++;
				alternating &= expectStart;
				expectStart = false;
			}
			if (l.contains("Project Raccoon stopped"))
			{
				stopped++;
				alternating &= !expectStart;
				expectStart = true;
			}
			if ((l.contains(" ERROR ") || l.contains(" WARN ")) && lower.contains("raccoon") || l.contains("at com.feralenterprises."))
			{
				// never echo the line: it may carry names or chat; record only where it came from
				problems.add(l.contains("at com.feralenterprises.") ? "stack frame" : logger(l));
			}
		}
		row(p + "sideloaded", sideload ? "PASS" : "BLOCKED", sideload ? "client log shows the official JAR side-loaded" : "no side-load line for project-raccoon");
		if (launchPath)
		{
			row(p + "startedFromOfficialJar", started >= 1 ? "PASS" : "BLOCKED", "started=" + started + " stopped=" + stopped);
			row(p + "noErrors", problems.isEmpty() ? "PASS" : "FAIL", problems.isEmpty() ? "no plugin errors or stack frames"
				: problems.size() + " lines from " + new java.util.TreeSet<>(problems));
			return;
		}
		// two complete alternating cycles: start, stop, start, stop
		boolean twice = started >= 2 && started == stopped && alternating;
		row("R4.startStopTwice", twice ? "PASS" : started + stopped == 0 ? "BLOCKED" : "FAIL",
			"started=" + started + " stopped=" + stopped + (alternating ? "" : " (out of order)") + (started == stopped ? "" : " (incomplete cycle)"));
		boolean clean = "0".equals(exitCode);
		row("R4.cleanShutdown", exitCode == null ? "BLOCKED" : clean ? "PASS" : "FAIL", exitCode == null ? "client exit code not recorded"
			: "client exit code " + exitCode);
		row("R4.noErrors", problems.isEmpty() ? "PASS" : "FAIL", problems.isEmpty() ? "no plugin errors or stack frames"
			: problems.size() + " lines from " + new java.util.TreeSet<>(problems));
		// I03: the exact official JAR loads and disables in the genuine client, clean artifact and path
		boolean i03 = sha.equals(expectedSha) && sideload && started >= 1 && stopped >= 1 && foreign == 0 && problems.isEmpty();
		row("I03.exactOfficialJar", i03 ? "PASS" : started == 0 ? "BLOCKED" : stopped == 0 ? "BLOCKED" : "FAIL",
			i03 ? "the exact retained official JAR loaded and was disabled cleanly in the genuine client (no other Raccoon classes)"
				: "needs the exact official JAR to load and then be disabled, without errors");
	}

	/** The logger name of a log line ("... LEVEL logger - message"), never the message. */
	private static String logger(String line)
	{
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:ERROR|WARN)\\s+(\\S+)\\s+-").matcher(line);
		return m.find() ? m.group(1) : "unparsed line";
	}

	private static String officialJarClean(Path jar) throws IOException
	{
		if (!Files.isRegularFile(jar))
		{
			return "FAIL";
		}
		try (InputStream in = Files.newInputStream(jar); ZipInputStream z = new ZipInputStream(in))
		{
			ZipEntry e;
			while ((e = z.getNextEntry()) != null)
			{
				String n = e.getName();
				if (n.contains("/playtest/") || n.endsWith("Test.class") || n.contains("PluginHarness") || n.contains("NativeBankProbe")
					|| n.contains("NativeEvaluationProbe") || n.contains("WitnessPlugin") || n.contains("PlaytestClient"))
				{
					return "FAIL";
				}
			}
		}
		return "PASS";
	}

	private static int countEntries(Path jar, String prefix) throws IOException
	{
		int n = 0;
		try (InputStream in = Files.newInputStream(jar); ZipInputStream z = new ZipInputStream(in))
		{
			ZipEntry e;
			while ((e = z.getNextEntry()) != null)
			{
				if (e.getName().startsWith(prefix))
				{
					n++;
				}
			}
		}
		return n;
	}

	static String sha256(byte[] data)
	{
		try
		{
			StringBuilder b = new StringBuilder();
			for (byte x : MessageDigest.getInstance("SHA-256").digest(data))
			{
				b.append(String.format("%02x", x));
			}
			return b.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}

	// ------------------------------------------------------------------ minimal flat JSON lines reader

	static List<Map<String, Object>> read(Path file) throws IOException
	{
		List<Map<String, Object>> out = new ArrayList<>();
		if (!Files.isRegularFile(file))
		{
			return out;
		}
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
		{
			if (!line.trim().isEmpty())
			{
				out.add(parse(line));
			}
		}
		return out;
	}

	static Map<String, Object> parse(String s)
	{
		Map<String, Object> m = new LinkedHashMap<>();
		int[] i = {skip(s, 0)};
		expect(s, i, '{');
		while (true)
		{
			i[0] = skip(s, i[0]);
			if (s.charAt(i[0]) == '}')
			{
				return m;
			}
			String key = string(s, i);
			i[0] = skip(s, i[0]);
			expect(s, i, ':');
			i[0] = skip(s, i[0]);
			m.put(key, value(s, i));
			i[0] = skip(s, i[0]);
			if (s.charAt(i[0]) == ',')
			{
				i[0]++;
			}
		}
	}

	private static Object value(String s, int[] i)
	{
		char c = s.charAt(i[0]);
		if (c == '"')
		{
			return string(s, i);
		}
		int start = i[0];
		while (i[0] < s.length() && ",}".indexOf(s.charAt(i[0])) < 0)
		{
			i[0]++;
		}
		String t = s.substring(start, i[0]).trim();
		switch (t)
		{
			case "null":
				return null;
			case "true":
				return Boolean.TRUE;
			case "false":
				return Boolean.FALSE;
			default:
				return Long.parseLong(t);
		}
	}

	private static String string(String s, int[] i)
	{
		expect(s, i, '"');
		StringBuilder b = new StringBuilder();
		while (true)
		{
			char c = s.charAt(i[0]++);
			if (c == '"')
			{
				return b.toString();
			}
			if (c == '\\')
			{
				char e = s.charAt(i[0]++);
				switch (e)
				{
					case 'n':
						b.append('\n');
						break;
					case 'r':
						b.append('\r');
						break;
					case 't':
						b.append('\t');
						break;
					case 'u':
						b.append((char) Integer.parseInt(s.substring(i[0], i[0] + 4), 16));
						i[0] += 4;
						break;
					default:
						b.append(e);
				}
			}
			else
			{
				b.append(c);
			}
		}
	}

	private static void expect(String s, int[] i, char c)
	{
		if (s.charAt(i[0]) != c)
		{
			throw new IllegalArgumentException("expected '" + c + "' at " + i[0] + " in " + s);
		}
		i[0]++;
	}

	private static int skip(String s, int i)
	{
		while (i < s.length() && Character.isWhitespace(s.charAt(i)))
		{
			i++;
		}
		return i;
	}

	private static Long asLong(Object o)
	{
		return o instanceof Long ? (Long) o : null;
	}

	private static Map<String, Object> last(List<Map<String, Object>> ev, String type)
	{
		Map<String, Object> found = null;
		for (Map<String, Object> e : ev)
		{
			if (type.equals(e.get("type")))
			{
				found = e;
			}
		}
		return found;
	}

	private static List<Map<String, Object>> all(List<Map<String, Object>> ev, String type)
	{
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> e : ev)
		{
			if (type.equals(e.get("type")))
			{
				out.add(e);
			}
		}
		return out;
	}
}
