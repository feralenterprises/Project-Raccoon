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
 * Evaluate R4 &lt;client.log&gt; &lt;sideloaded jar&gt; &lt;expected sha256&gt; &lt;client lib dir&gt;
 * </pre>
 */
public final class Evaluate
{
	static final long EVENT_P95_LIMIT = 2_000_000L;
	static final long BANK_P95_LIMIT = 10_000_000L;

	private final List<String[]> rows = new ArrayList<>();

	private Evaluate()
	{
	}

	public static void main(String[] args) throws Exception
	{
		Evaluate ev = new Evaluate();
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
				ev.r4(Paths.get(args[1]), Paths.get(args[2]), args[3], Paths.get(args[4]));
				break;
			default:
				throw new IllegalArgumentException("unknown step " + step);
		}
		System.exit(ev.print(step));
	}

	private void row(String id, String verdict, String detail)
	{
		rows.add(new String[]{id, verdict, detail});
	}

	private int print(String step)
	{
		boolean fail = false;
		boolean blocked = false;
		System.out.println(step + " verdicts");
		for (String[] r : rows)
		{
			System.out.println(String.format("  %-28s %-13s %s", r[0], r[1], r[2]));
			fail |= "FAIL".equals(r[1]);
			blocked |= "BLOCKED".equals(r[1]);
		}
		String overall = fail ? "FAIL" : blocked ? "BLOCKED" : "PASS";
		System.out.println(step + " overall: " + overall);
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

	private void r3(List<Map<String, Object>> ev)
	{
		Map<String, Object> shared = last(ev, "witness.shared");
		row("R3.sharedInstances", shared != null && Boolean.TRUE.equals(shared.get("shared")) ? "PASS" : "BLOCKED",
			shared == null ? "no record" : String.valueOf(shared.get("detail")));

		List<Map<String, Object>> acct = all(ev, "account");
		boolean login = false;
		boolean hop = false;
		boolean failedRevalidation = false;
		Long activeLogin = null;
		Long activeEpoch = null;
		boolean suspendedSinceActive = false;
		Long retiredLogin = null;
		String retireReason = null;
		long hopAtMono = -1;
		for (Map<String, Object> a : acct)
		{
			String state = String.valueOf(a.get("state"));
			boolean pub = Boolean.TRUE.equals(a.get("publishable"));
			Long lg = asLong(a.get("login"));
			Long ep = asLong(a.get("epoch"));
			if ("ACTIVE".equals(state) && pub)
			{
				login = true;
				if (suspendedSinceActive && Objects.equals(lg, activeLogin) && !Objects.equals(ep, activeEpoch))
				{
					hop = true;
					hopAtMono = asLong(a.get("mono"));
				}
				if (retiredLogin != null && !Objects.equals(lg, retiredLogin))
				{
					failedRevalidation = true;
				}
				activeLogin = lg;
				activeEpoch = ep;
				suspendedSinceActive = false;
				retiredLogin = null;
			}
			else if ("SUSPENDED_CONTINUITY".equals(state))
			{
				suspendedSinceActive = activeLogin != null;
			}
			else if ("RETIRED".equals(state))
			{
				String reason = String.valueOf(a.get("lastRetire"));
				if (activeLogin != null && !"PLUGIN_STOPPED".equals(reason) && !"NONE".equals(reason))
				{
					retiredLogin = activeLogin;
					retireReason = reason;
				}
				activeLogin = null;
				suspendedSinceActive = false;
			}
		}
		row("R3.genuineLogin", login ? "PASS" : "BLOCKED", login ? "ACTIVE and publishable after a genuine login" : "not observed");
		row("R3.verifiedHop", hop ? "PASS" : "BLOCKED", hop ? "same login restored in a new epoch" : "no verified same-login hop observed");
		row("R3.failedRevalidation", failedRevalidation ? "PASS" : "BLOCKED",
			failedRevalidation ? "retired (" + retireReason + "), next login is a new login" : "no retirement followed by a new login observed");

		List<Map<String, Object>> bank = all(ev, "bank");
		boolean qualified = false;
		boolean revoked = false;
		boolean requalified = false;
		boolean pin = false;
		for (Map<String, Object> b : bank)
		{
			boolean current = Boolean.TRUE.equals(b.get("current"));
			pin |= "PIN".equals(b.get("reason"));
			if ("QUALIFIED_CURRENT".equals(b.get("status")))
			{
				if (revoked)
				{
					requalified = true;
				}
				qualified = true;
			}
			else if (qualified && !current)
			{
				revoked = true;
			}
		}
		row("R3.bankQualified", qualified ? "PASS" : "BLOCKED", qualified ? "QUALIFIED_CURRENT from a genuine open bank" : "not observed");
		row("R3.bankRevokedOnClose", revoked ? "PASS" : "BLOCKED", revoked ? "current evidence revoked after qualification" : "not observed");
		row("R3.bankFreshStreak", requalified ? "PASS" : "BLOCKED", requalified ? "re-qualified only through a fresh streak" : "no reopen observed");
		row("R3.bankPin", pin ? "PASS" : "NOT_OBSERVED", pin ? "PIN seen, no capture" : "optional: account without a bank PIN or PIN not shown");

		List<Long> probe = new ArrayList<>();
		int invalid = 0;
		for (Map<String, Object> p : all(ev, "bankProbe"))
		{
			if (Boolean.TRUE.equals(p.get("valid")))
			{
				probe.add(asLong(p.get("nanos")));
			}
			else
			{
				invalid++;
			}
		}
		perf("R3.perf.bankCapture", probe, 50, BANK_P95_LIMIT, "genuine open bank, read + evaluate; invalid=" + invalid);

		brutus(ev, hopAtMono);

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
			// GameTick is the frequent genuine callback (1,000 ticks = 10 minutes) and gates; genuine
			// chat is sparse, so it is reported only (the 1,000-sample replay gate is R2)
			perf("R3.perf." + name, t, 1000, EVENT_P95_LIMIT, "genuine events; bracket includes every subscriber between the witness priorities (upper bound)",
				"GameTick".equals(name));
		}
	}

	private void brutus(List<Map<String, Object>> ev, long hopAtMono)
	{
		boolean baseline = false;
		boolean gain = false;
		boolean consistent = true;
		boolean afterHopBaseline = false;
		Long previousCount = null;
		Map<String, Integer> types = new LinkedHashMap<>();
		for (Map<String, Object> b : all(ev, "brutusLine"))
		{
			types.merge(String.valueOf(b.get("chatType")), 1, Integer::sum);
			if (!Boolean.TRUE.equals(b.get("typed")))
			{
				continue;
			}
			long before = asLong(b.get("totalBefore")) == null ? 0 : asLong(b.get("totalBefore"));
			long after = asLong(b.get("totalAfter")) == null ? 0 : asLong(b.get("totalAfter"));
			long count = Long.parseLong(String.valueOf(b.get("count")).replace(",", ""));
			boolean changed = asLong(b.get("revisionAfter")) > asLong(b.get("revisionBefore"));
			if (changed && after == before)
			{
				baseline = true;
				if (hopAtMono > 0 && asLong(b.get("mono")) > hopAtMono)
				{
					afterHopBaseline = true;
				}
			}
			else if (after > before)
			{
				gain = true;
				if (previousCount != null && after - before != count - previousCount)
				{
					consistent = false;
				}
			}
			previousCount = count;
		}
		row("R3.brutusLineType", types.isEmpty() ? "BLOCKED" : "PASS", types.isEmpty() ? "no genuine Brutus line" : "observed chat types " + types);
		row("R3.brutusBaseline", baseline ? "PASS" : "BLOCKED", baseline ? "first count in the epoch is a zero-delta baseline" : "not observed");
		row("R3.brutusGain", !gain ? "BLOCKED" : consistent ? "PASS" : "FAIL",
			!gain ? "no genuine kill after a baseline" : consistent ? "gain equals the count increase" : "gain differs from the count increase");
		row("R3.brutusAfterHop", afterHopBaseline ? "PASS" : "NOT_OBSERVED", "optional K05: first line after a verified hop baselines");
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

	private void r4(Path log, Path sideloaded, String expectedSha, Path clientLib) throws IOException
	{
		String sha = Files.isRegularFile(sideloaded) ? sha256(Files.readAllBytes(sideloaded)) : "missing";
		row("R4.officialJarIdentity", sha.equals(expectedSha) ? "PASS" : "FAIL", "sideloaded sha256=" + sha);
		row("R4.officialJarContents", officialJarClean(sideloaded), "no playtest, test or launcher classes in the production artifact");
		int foreign = 0;
		int jars = 0;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(clientLib, "*.jar"))
		{
			for (Path j : ds)
			{
				jars++;
				foreign += countEntries(j, "com/feralenterprises/");
			}
		}
		row("R4.clientClasspathClean", jars > 0 && foreign == 0 ? "PASS" : "FAIL", jars + " client jars, " + foreign + " Raccoon entries");

		if (!Files.isRegularFile(log))
		{
			row("R4.startStopTwice", "BLOCKED", "no client log at " + log.getFileName());
			row("I03.exactOfficialJar", "BLOCKED", "no client log");
			return;
		}
		List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
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
				problems.add(l.length() > 200 ? l.substring(0, 200) : l);
			}
		}
		row("R4.sideloaded", sideload ? "PASS" : "BLOCKED", sideload ? "client log shows the official JAR side-loaded" : "no side-load line for project-raccoon");
		boolean twice = started >= 2 && stopped >= 2 && alternating;
		row("R4.startStopTwice", twice ? "PASS" : started + stopped == 0 ? "BLOCKED" : "FAIL",
			"started=" + started + " stopped=" + stopped + (alternating ? "" : " (out of order)"));
		row("R4.noErrors", problems.isEmpty() ? "PASS" : "FAIL", problems.isEmpty() ? "no plugin errors or stack frames" : problems.size() + " lines, first: " + problems.get(0));
		boolean i03 = sha.equals(expectedSha) && sideload && started >= 1 && foreign == 0 && problems.isEmpty();
		row("I03.exactOfficialJar", i03 ? "PASS" : started == 0 ? "BLOCKED" : "FAIL",
			"the exact retained official JAR (and no other Raccoon classes) loaded and ran in the genuine client");
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
