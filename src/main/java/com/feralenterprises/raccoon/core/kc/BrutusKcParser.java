package com.feralenterprises.raccoon.core.kc;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the server's Brutus kill count line. Only GAME and SPAM messages; only colour tags are
 * removed; the remaining text must be exactly {@code Your Brutus kill count is: N} (optional
 * trailing period), N digits or comma-grouped digits within int range.
 */
public final class BrutusKcParser
{
	static final int MAX_LENGTH = 256;
	private static final Pattern COLOUR_TAG = Pattern.compile("</?col(?:=[0-9a-fA-F]{6})?>");
	private static final Pattern LINE =
		Pattern.compile("^Your Brutus kill count is: ?([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\.?$");

	private BrutusKcParser()
	{
	}

	public static OptionalInt parse(ChatKind kind, String message)
	{
		if ((kind != ChatKind.GAME && kind != ChatKind.SPAM) || message == null || message.length() > MAX_LENGTH)
		{
			return OptionalInt.empty();
		}
		Matcher m = LINE.matcher(COLOUR_TAG.matcher(message).replaceAll("").trim());
		if (!m.matches())
		{
			return OptionalInt.empty();
		}
		String digits = m.group(1).replace(",", "");
		if (digits.length() > 10)
		{
			return OptionalInt.empty();
		}
		long value = Long.parseLong(digits);
		return value > Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of((int) value);
	}
}
