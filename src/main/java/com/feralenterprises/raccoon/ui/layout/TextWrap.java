package com.feralenterprises.raccoon.ui.layout;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

/** Greedy word wrap. Words wider than the line are broken by character, so text is never clipped horizontally. */
public final class TextWrap {
	private TextWrap() { }

	public static List<String> wrap(String text, FontMetrics fm, int width) {
		List<String> out = new ArrayList<>();
		if (text == null || text.isEmpty()) return out;
		int w = Math.max(1, width);
		for (String para : text.split("\n", -1)) {
			StringBuilder line = new StringBuilder();
			for (String word : para.split(" ")) {
				if (word.isEmpty()) continue;
				String candidate = line.length() == 0 ? word : line + " " + word;
				if (fm.stringWidth(candidate) <= w) { line.setLength(0); line.append(candidate); continue; }
				if (line.length() > 0) { out.add(line.toString()); line.setLength(0); }
				while (fm.stringWidth(word) > w && word.length() > 1) {
					int k = word.length() - 1;
					while (k > 1 && fm.stringWidth(word.substring(0, k)) > w) k--;
					out.add(word.substring(0, k));
					word = word.substring(k);
				}
				line.append(word);
			}
			out.add(line.toString());
		}
		return out;
	}

	public static int widest(List<String> lines, FontMetrics fm) {
		int m = 0;
		for (String l : lines) m = Math.max(m, fm.stringWidth(l));
		return m;
	}
}
