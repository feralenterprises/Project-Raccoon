package com.feralenterprises.raccoon.playtest.r02.host;

import com.feralenterprises.raccoon.playtest.r02.fixture.ItemDef;
import com.feralenterprises.raccoon.playtest.r02.fixture.Registry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Owner decision (27 Nov 2026): native item naming and exact item ids. In the native host every registry item is
 * compared with the game cache name (RuneLite {@code ItemManager}); the CSV lists each id, the fixture name, the
 * native name and the verdict. The fixture registry is not rewritten at runtime: a MISMATCH is a correction to make
 * in {@code FrozenDataset.NAME_CORRECTIONS} on a later candidate, with this file as the evidence.
 */
public final class NameAudit {
	private NameAudit() { }

	public static final class Result {
		public final String csv;
		public final int checked;
		public final int mismatches;
		public final int unresolved;

		Result(String csv, int checked, int mismatches, int unresolved) {
			this.csv = csv;
			this.checked = checked;
			this.mismatches = mismatches;
			this.unresolved = unresolved;
		}
	}

	/** {@code nativeName} returns null when the cache has no name for the id. */
	public static Result run(Registry registry, IntFunction<String> nativeName) {
		List<ItemDef> items = new ArrayList<>(registry.items());
		items.sort(Comparator.comparingInt(ItemDef::id));
		StringBuilder b = new StringBuilder("item_id,fixture_name,native_name,verdict\n");
		int mismatch = 0;
		int unresolved = 0;
		for (ItemDef d : items) {
			String n = nativeName.apply(d.id());
			String verdict = n == null ? "UNRESOLVED" : n.equals(d.name()) ? "MATCH" : "MISMATCH";
			if (n == null) unresolved++;
			else if (!n.equals(d.name())) mismatch++;
			b.append(d.id()).append(',').append(csv(d.name())).append(',').append(csv(n == null ? "" : n)).append(',').append(verdict).append('\n');
		}
		return new Result(b.toString(), items.size(), mismatch, unresolved);
	}

	private static String csv(String s) {
		return s.indexOf(',') >= 0 || s.indexOf('"') >= 0 ? '"' + s.replace("\"", "\"\"") + '"' : s;
	}
}
