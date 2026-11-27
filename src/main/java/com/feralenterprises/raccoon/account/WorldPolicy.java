package com.feralenterprises.raccoon.account;

import com.feralenterprises.raccoon.core.account.Membership;
import com.feralenterprises.raccoon.core.account.WorldContext;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Supported-world policy. Conservative allowlist: a world is supported only if every world type
 * it carries is listed here; anything else (temporary, seasonal, beta, tournament, no-save,
 * speedrun, minigame or unknown types) yields no observations. Account membership is not
 * observed in FR-002, so it is always {@link Membership#UNKNOWN}.
 */
public final class WorldPolicy
{
	/** World type names on which account observation is allowed. */
	public static final Set<String> SUPPORTED_WORLD_TYPES =
		Collections.unmodifiableSet(new LinkedHashSet<>(java.util.Arrays.asList("MEMBERS", "SKILL_TOTAL")));

	private WorldPolicy()
	{
	}

	/** {@code typeNames} null means the world could not be read: unsupported. */
	public static WorldContext evaluate(Set<String> typeNames)
	{
		if (typeNames == null)
		{
			return WorldContext.unsupported();
		}
		for (String t : typeNames)
		{
			if (!SUPPORTED_WORLD_TYPES.contains(t))
			{
				return WorldContext.unsupported();
			}
		}
		return WorldContext.supported(Membership.UNKNOWN);
	}
}
