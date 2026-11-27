package com.feralenterprises.raccoon.playtest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Append-only JSON Lines receipt. {@link #event} only enqueues (cheap, safe on the client thread);
 * a daemon thread writes and flushes every 250 ms, and a shutdown hook drains the queue, so an
 * abrupt client exit loses at most the last quarter second. FR-006 playtest only; never part of
 * the production artifact.
 */
final class Receipt implements AutoCloseable
{
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

	private final Path file;
	private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
	private final BufferedWriter out;
	private final Thread writer;
	private volatile boolean open = true;
	private boolean closed;

	private Receipt(Path file) throws IOException
	{
		this.file = file;
		Files.createDirectories(file.getParent());
		out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		writer = new Thread(this::run, "raccoon-playtest-receipt");
		writer.setDaemon(true);
		writer.start();
		Runtime.getRuntime().addShutdownHook(new Thread(this::drain, "raccoon-playtest-receipt-drain"));
	}

	/** Opens {@code <receipts dir>/<step>-<UTC stamp>.jsonl}; the directory comes from the launcher. */
	static Receipt open(String step) throws IOException
	{
		Path dir = Paths.get(System.getProperty("raccoon.playtest.receipts", "receipts"));
		return new Receipt(dir.resolve(step + "-" + STAMP.format(Instant.now()) + ".jsonl"));
	}

	Path file()
	{
		return file;
	}

	/** One JSON object: {"t": epoch ms, "mono": nanoTime, "type": type, k1: v1, ...}. */
	void event(String type, Object... kv)
	{
		StringBuilder b = new StringBuilder(96);
		b.append("{\"t\":").append(System.currentTimeMillis()).append(",\"mono\":").append(System.nanoTime())
			.append(",\"type\":");
		quote(b, type);
		for (int i = 0; i + 1 < kv.length; i += 2)
		{
			b.append(',');
			quote(b, String.valueOf(kv[i]));
			b.append(':');
			value(b, kv[i + 1]);
		}
		queue.add(b.append('}').toString());
	}

	private static void value(StringBuilder b, Object v)
	{
		if (v == null)
		{
			b.append("null");
		}
		else if (v instanceof Number || v instanceof Boolean)
		{
			b.append(v);
		}
		else
		{
			quote(b, v.toString());
		}
	}

	static void quote(StringBuilder b, String s)
	{
		b.append('"');
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			switch (c)
			{
				case '"':
					b.append("\\\"");
					break;
				case '\\':
					b.append("\\\\");
					break;
				case '\n':
					b.append("\\n");
					break;
				case '\r':
					b.append("\\r");
					break;
				case '\t':
					b.append("\\t");
					break;
				default:
					if (c < 0x20)
					{
						b.append(String.format("\\u%04x", (int) c));
					}
					else
					{
						b.append(c);
					}
			}
		}
		b.append('"');
	}

	private void run()
	{
		while (open)
		{
			drain();
			try
			{
				Thread.sleep(250);
			}
			catch (InterruptedException e)
			{
				return;
			}
		}
	}

	private synchronized void drain()
	{
		if (closed)
		{
			return;
		}
		try
		{
			String line;
			while ((line = queue.poll()) != null)
			{
				out.write(line);
				out.write('\n');
			}
			out.flush();
		}
		catch (IOException e)
		{
			// the receipt is evidence: a write failure must be visible, not silent
			System.err.println("raccoon playtest: receipt write failed: " + e);
		}
	}

	@Override
	public void close()
	{
		open = false;
		writer.interrupt();
		drain();
		synchronized (this)
		{
			closed = true;
		}
		try
		{
			out.close();
		}
		catch (IOException e)
		{
			System.err.println("raccoon playtest: receipt close failed: " + e);
		}
	}
}
