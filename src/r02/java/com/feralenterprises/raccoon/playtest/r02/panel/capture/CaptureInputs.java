package com.feralenterprises.raccoon.playtest.r02.panel.capture;

import com.feralenterprises.raccoon.playtest.r02.panel.bank.LaneInputs;
import com.feralenterprises.raccoon.playtest.r02.panel.bank.MiniJson;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Account Capture's verified inputs: the ten frozen sources in order ({@code shared/bank-and-capture.json}
 * {@code captureSources}: id, name, guidance) and their frozen icons, read by source id from the frozen
 * {@code Project-Raccoon-R02.html} {@code capSources} (SHA-256 checked). Presentation only.
 */
public final class CaptureInputs {
	public static final String FROZEN_HTML_SHA256 = "7a299ad1d4af98e8f8a9b86a558a230667ff7845c1034a54c8d39867db996899";

	/** One capture source. */
	static final class Source {
		final String id, name, help;
		final BufferedImage icon;

		Source(String id, String name, String help, BufferedImage icon) {
			this.id = id;
			this.name = name;
			this.help = help;
			this.icon = icon;
		}
	}

	final List<Source> sources;

	private CaptureInputs(List<Source> sources) { this.sources = Collections.unmodifiableList(sources); }

	public static CaptureInputs load(LaneInputs lane, Path frozenReview) throws IOException {
		byte[] bytes = Files.readAllBytes(frozenReview.resolve("Project-Raccoon-R02.html"));
		if (!FROZEN_HTML_SHA256.equals(LaneInputs.sha256(bytes))) throw new IOException("Frozen R02 review SHA-256 mismatch");
		String html = new String(bytes, StandardCharsets.UTF_8);
		List<Source> out = new ArrayList<>();
		for (Object o : MiniJson.list(lane.json("shared/bank-and-capture.json"), "captureSources")) {
			String id = MiniJson.str(o, "id");
			Matcher m = Pattern.compile("\\{id:'" + Pattern.quote(id) + "',name:'[^']*',icon:'data:image/png;base64,([A-Za-z0-9+/=]+)'").matcher(html);
			BufferedImage icon = m.find() ? ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(m.group(1)))) : null;
			if (icon == null && id.equals("bank")) {
				// frozen capSources uses capRail('Bank Memory'): the Bank Memory rail button image
				Matcher rail = Pattern.compile("<button[^>]*aria-label=\"Bank Memory\"[^>]*>\\s*<img[^>]*src=\"data:image/png;base64,([A-Za-z0-9+/=]+)\"").matcher(html);
				if (rail.find()) icon = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(rail.group(1))));
			}
			out.add(new Source(id, MiniJson.str(o, "name"), MiniJson.str(o, "help"), icon));
		}
		if (out.size() != 10) throw new IOException("Expected ten frozen capture sources, found " + out.size());
		return new CaptureInputs(out);
	}
}
