package com.feralenterprises.raccoon.playtest.r02.panel.tools;

import com.feralenterprises.raccoon.playtest.r02.panel.home.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.home.MiniJson;
import com.feralenterprises.raccoon.playtest.r02.shared.RouteId;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Tools' verified inputs: the action list ({@code A/home-and-tools.json} {@code toolsActions}) and the frozen rail icons
 * the actions show, read by accessible label from the frozen {@code Project-Raccoon-R02.html} rail (SHA-256 checked).
 */
public final class ToolsInputs {
	public static final String FROZEN_HTML_SHA256 = "7a299ad1d4af98e8f8a9b86a558a230667ff7845c1034a54c8d39867db996899";
	/** Frozen {@code freshTools} icon labels per route (Settings has none). */
	private static final Map<RouteId, String> ICON_LABEL = Map.of(RouteId.BANK, "Bank Memory", RouteId.QUESTS, "Quests",
		RouteId.CHRONICLE, "Boss Chronicle", RouteId.CAPTURE, "Account Capture");

	/**
	 * The frozen page replaces the Account Capture rail image at runtime with the capture source "Account type" icon
	 * ({@code captureIcon}); its SHA-256 matches the lane asset map's {@code rail:capture}.
	 */
	static final String CAPTURE_ICON_SHA256 = "d89d8b3d226a4656cbf8dd43ca77dc1f3d406724ec68ba4dc258b48a8918a932";
	private static final Pattern CAPTURE_ICON = Pattern.compile("\\{id:'mode',name:'Account type',icon:'data:image/png;base64,([A-Za-z0-9+/=]+)'");

	/** One Tools action: a typed route, or the local Biggest Wins view when {@code route} is null. */
	static final class Action {
		final RouteId route;
		final String label;
		final BufferedImage icon;

		Action(RouteId route, String label, BufferedImage icon) {
			this.route = route;
			this.label = label;
			this.icon = icon;
		}
	}

	final List<Action> actions;

	private ToolsInputs(List<Action> actions) { this.actions = Collections.unmodifiableList(actions); }

	public static ToolsInputs load(LaneInputs lane, Path frozenReview) throws IOException {
		byte[] bytes = Files.readAllBytes(frozenReview.resolve("Project-Raccoon-R02.html"));
		if (!FROZEN_HTML_SHA256.equals(LaneInputs.sha256(bytes))) throw new IOException("Frozen R02 review SHA-256 mismatch");
		String html = new String(bytes, StandardCharsets.UTF_8);
		Map<String, BufferedImage> rail = new HashMap<>();
		for (String label : ICON_LABEL.values()) {
			Matcher m = Pattern.compile("<button[^>]*aria-label=\"" + Pattern.quote(label)
				+ "\"[^>]*>\\s*<img[^>]*src=\"data:image/png;base64,([A-Za-z0-9+/=]+)\"").matcher(html);
			if (m.find()) rail.put(label, ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(m.group(1)))));
		}
		Matcher cap = CAPTURE_ICON.matcher(html);
		rail.remove("Account Capture");
		if (cap.find()) {
			byte[] png = Base64.getDecoder().decode(cap.group(1));
			if (CAPTURE_ICON_SHA256.equals(LaneInputs.sha256(png))) rail.put("Account Capture", ImageIO.read(new ByteArrayInputStream(png)));
		}
		List<Action> actions = new ArrayList<>();
		for (Object a : MiniJson.list(lane.json("A/home-and-tools.json"), "toolsActions")) {
			String route = MiniJson.str(a, "route"), action = MiniJson.str(a, "action"), label = MiniJson.str(a, "label");
			if (route == null && !"wins".equals(action)) throw new IOException("Unknown Tools action " + action);
			RouteId r = route == null ? null : RouteId.of(route);
			actions.add(new Action(r, label, r == null ? null : rail.get(ICON_LABEL.get(r))));
		}
		return new ToolsInputs(actions);
	}
}
