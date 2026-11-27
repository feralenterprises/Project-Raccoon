package com.feralenterprises.raccoon.playtest.r02.shared;

import com.feralenterprises.raccoon.ui.shell.Environment;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-route environment from the frozen R02 stylesheet: the base texture, its tile size and the frozen CSS overlay
 * (kept verbatim as the reference a native painter must match). Texture identities were checked by SHA-256: the
 * frozen {@code --stone} texture is {@code PR-028_weathered_fieldstone.png} (the framework's SURFACE_FIELDSTONE), and
 * PR-026/PR-032 are byte-identical to the framework review assets.
 */
public final class R02Environment {
	/** Base textures. {@code artId} is the frozen asset id; null when the frozen source has no single asset. */
	public enum Texture {
		FIELDSTONE("PR-028"),
		TIMBER("PR-032"),
		/** Settings-dependent surface ({@code var(--surface)}); resolved through the shared appearance model. */
		APPEARANCE_SURFACE(null),
		/** Feral Friends {@code var(--ff-field)}: no frozen single asset; accessible flat fallback until resolved. */
		FRIENDS_FIELD(null);

		private final String artId;
		Texture(String artId) { this.artId = artId; }
		public String artId() { return artId; }
	}

	private static final String PANEL_DEFAULT = "linear-gradient(#29251c75,#201c17b0),var(--stone) repeat; background-size:auto,160px 160px";
	private static final Map<RouteId, R02Environment> TABLE = new EnumMap<>(RouteId.class);

	static {
		put(RouteId.HOME, Texture.FIELDSTONE, 160, Environment.FULL, PANEL_DEFAULT);
		put(RouteId.OUTFIT, Texture.FIELDSTONE, 160, Environment.FULL, PANEL_DEFAULT + " (encounter scenes via --of-* region art)");
		put(RouteId.ACTIVITY, Texture.APPEARANCE_SURFACE, 180, Environment.FULL,
			"linear-gradient(0deg,color-mix(in srgb,var(--ink) 88%,transparent),color-mix(in srgb,var(--ink) 70%,transparent)),var(--surface) center/180px repeat");
		put(RouteId.BANK, Texture.FIELDSTONE, 160, Environment.PLAIN,
			"linear-gradient(90deg,#12100b70,transparent 13% 87%,#12100b70),linear-gradient(#29251b25,#211d1755),var(--stone) center/160px repeat");
		put(RouteId.CAPTURE, Texture.FIELDSTONE, 160, Environment.PLAIN,
			"linear-gradient(90deg,#12100b70,transparent 13% 87%,#12100b70),linear-gradient(#29251b25,#211d1755),var(--stone) center/160px repeat");
		put(RouteId.FILES, Texture.FIELDSTONE, 160, Environment.PLAIN,
			"linear-gradient(90deg,#12100b80,transparent 12% 88%,#12100b80),linear-gradient(#29251b25,#211d1755),var(--stone) center/160px");
		put(RouteId.CHRONICLE, Texture.FIELDSTONE, 160, Environment.PLAIN, "linear-gradient(180deg,#181c1be8,#151310e8),var(--stone) center/160px");
		put(RouteId.PATHFINDER, Texture.FIELDSTONE, 145, Environment.PLAIN,
			"linear-gradient(90deg,#141611b8,#292b1faa 50%,#131710d9),var(--stone) center/145px");
		put(RouteId.QUESTS, Texture.FIELDSTONE, 145, Environment.PLAIN,
			"radial-gradient(ellipse at 80% 3%,#99704432,transparent 34%),linear-gradient(90deg,#19170fe0,#262318a0 50%,#151610df),var(--stone) center/145px");
		put(RouteId.XPSTASH, Texture.FIELDSTONE, 145, Environment.PLAIN,
			"radial-gradient(ellipse at 88% 4%,#a76c3038,transparent 35%),linear-gradient(90deg,#19140ee0,#2b2419c9,#14120ee9),var(--stone) center/145px");
		put(RouteId.BOUNTIES, Texture.TIMBER, 135, Environment.PLAIN, "linear-gradient(90deg,#2a1e13ad,#37271b9f,#21170cce),var(--r02-timber) center/135px");
		put(RouteId.FRIENDS, Texture.FRIENDS_FIELD, 0, Environment.PLAIN,
			"radial-gradient(ellipse at 10% 10%,#a489533b,transparent 60%),linear-gradient(#423d2eea,#292f23ec),var(--ff-field) center/cover");
		put(RouteId.TOOLS, Texture.FIELDSTONE, 160, Environment.PLAIN, PANEL_DEFAULT);
		put(RouteId.SETTINGS, Texture.FIELDSTONE, 160, Environment.PLAIN, PANEL_DEFAULT);
	}

	private final RouteId route;
	private final Texture texture;
	private final int tileSize;
	private final Environment framework;
	private final String frozenCss;

	private R02Environment(RouteId route, Texture texture, int tileSize, Environment framework, String frozenCss) {
		this.route = route;
		this.texture = texture;
		this.tileSize = tileSize;
		this.framework = framework;
		this.frozenCss = frozenCss;
	}

	private static void put(RouteId r, Texture t, int tile, Environment e, String css) { TABLE.put(r, new R02Environment(r, t, tile, e, css)); }

	public static R02Environment of(RouteId r) { return Objects.requireNonNull(TABLE.get(r), "no environment for " + r); }

	public RouteId route() { return route; }
	public Texture texture() { return texture; }
	/** Tile size in CSS px at 1x; 0 means cover. */
	public int tileSize() { return tileSize; }
	/** Framework page environment: FULL keeps the environmental header band (Home, Outfitter, Current Activity). */
	public Environment frameworkEnvironment() { return framework; }
	public String frozenCss() { return frozenCss; }
}
