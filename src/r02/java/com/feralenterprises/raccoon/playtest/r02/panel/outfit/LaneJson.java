package com.feralenterprises.raccoon.playtest.r02.panel.outfit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the JSON value syntax used by the pinned lane input; never evaluates source text. */
final class LaneJson {
	private final String text;
	private int pos;

	private LaneJson(String text) { this.text = text; }

	static Map<String, Object> read(String text) throws IOException {
		LaneJson reader = new LaneJson(text);
		Object value = reader.value(0);
		reader.space();
		if (reader.pos != text.length() || !(value instanceof Map)) throw reader.error("expected one JSON object");
		return object(value);
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> object(Object value) { return (Map<String, Object>) value; }

	@SuppressWarnings("unchecked")
	static List<Object> array(Object value) { return (List<Object>) value; }

	private Object value(int depth) throws IOException {
		if (depth > 32) throw error("nesting");
		space();
		if (pos == text.length()) throw error("missing value");
		switch (text.charAt(pos)) {
			case '{': return objectValue(depth + 1);
			case '[': return arrayValue(depth + 1);
			case '"': return string();
			case 't': literal("true"); return Boolean.TRUE;
			case 'f': literal("false"); return Boolean.FALSE;
			case 'n': literal("null"); return null;
			default: return number();
		}
	}

	private Map<String, Object> objectValue(int depth) throws IOException {
		pos++;
		Map<String, Object> map = new LinkedHashMap<>();
		if (take('}')) return map;
		do {
			space();
			String key = string();
			expect(':');
			if (map.containsKey(key)) throw error("duplicate key");
			map.put(key, value(depth));
		} while (take(','));
		expect('}');
		return map;
	}

	private List<Object> arrayValue(int depth) throws IOException {
		pos++;
		List<Object> array = new ArrayList<>();
		if (take(']')) return array;
		do { array.add(value(depth)); } while (take(','));
		expect(']');
		return array;
	}

	private String string() throws IOException {
		if (!take('"')) throw error("expected string");
		StringBuilder result = new StringBuilder();
		while (pos < text.length()) {
			char c = text.charAt(pos++);
			if (c == '"') return result.toString();
			if (c < 32) throw error("control character");
			if (c != '\\') {
				result.append(c);
				continue;
			}
			if (pos == text.length()) throw error("incomplete escape");
			char escape = text.charAt(pos++);
			switch (escape) {
				case '"': case '\\': case '/': result.append(escape); break;
				case 'b': result.append('\b'); break;
				case 'f': result.append('\f'); break;
				case 'n': result.append('\n'); break;
				case 'r': result.append('\r'); break;
				case 't': result.append('\t'); break;
				case 'u':
					if (pos + 4 > text.length()) throw error("incomplete unicode escape");
					try { result.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16)); }
					catch (NumberFormatException e) { throw error("invalid unicode escape"); }
					pos += 4;
					break;
				default: throw error("invalid escape");
			}
		}
		throw error("unterminated string");
	}

	private Number number() throws IOException {
		int start = pos;
		while (pos < text.length() && "-+0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
		String number = text.substring(start, pos);
		try {
			if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0)
				return Double.valueOf(number);
			return Long.valueOf(number);
		} catch (NumberFormatException e) {
			throw error("invalid number");
		}
	}

	private void literal(String token) throws IOException {
		if (!text.startsWith(token, pos)) throw error("invalid literal");
		pos += token.length();
	}

	private void space() {
		while (pos < text.length() && " \r\n\t".indexOf(text.charAt(pos)) >= 0) pos++;
	}

	private boolean take(char c) {
		space();
		if (pos == text.length() || text.charAt(pos) != c) return false;
		pos++;
		return true;
	}

	private void expect(char c) throws IOException {
		if (!take(c)) throw error("expected " + c);
	}

	private IOException error(String message) { return new IOException(message + " at JSON offset " + pos); }
}
