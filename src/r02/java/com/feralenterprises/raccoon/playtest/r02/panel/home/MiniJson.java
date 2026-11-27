package com.feralenterprises.raccoon.playtest.r02.panel.home;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal strict JSON reader for the Lane A input files (objects, arrays, strings, numbers, booleans, null). */
public final class MiniJson {
	private final String s;
	private int i;

	private MiniJson(String s) { this.s = s; }

	public static Object parse(String text) {
		MiniJson p = new MiniJson(text);
		Object v = p.value();
		p.ws();
		if (p.i != p.s.length()) throw p.error("trailing content");
		return v;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> obj(Object o, String key) {
		Object v = ((Map<String, Object>) o).get(key);
		if (!(v instanceof Map)) throw new IllegalArgumentException("expected object at " + key);
		return (Map<String, Object>) v;
	}

	@SuppressWarnings("unchecked")
	public static List<Object> list(Object o, String key) {
		Object v = ((Map<String, Object>) o).get(key);
		if (!(v instanceof List)) throw new IllegalArgumentException("expected array at " + key);
		return (List<Object>) v;
	}

	@SuppressWarnings("unchecked")
	public static String str(Object o, String key) {
		Object v = ((Map<String, Object>) o).get(key);
		return v == null ? null : v.toString();
	}

	@SuppressWarnings("unchecked")
	public static long num(Object o, String key) {
		Object v = ((Map<String, Object>) o).get(key);
		if (!(v instanceof Number)) throw new IllegalArgumentException("expected number at " + key);
		return ((Number) v).longValue();
	}

	private Object value() {
		ws();
		if (i >= s.length()) throw error("unexpected end");
		char c = s.charAt(i);
		switch (c) {
			case '{': return object();
			case '[': return array();
			case '"': return string();
			case 't': return literal("true", Boolean.TRUE);
			case 'f': return literal("false", Boolean.FALSE);
			case 'n': return literal("null", null);
			default: return number();
		}
	}

	private Map<String, Object> object() {
		Map<String, Object> m = new LinkedHashMap<>();
		i++;
		ws();
		if (peek('}')) { i++; return m; }
		while (true) {
			ws();
			String k = string();
			ws();
			expect(':');
			m.put(k, value());
			ws();
			if (peek(',')) { i++; continue; }
			expect('}');
			return m;
		}
	}

	private List<Object> array() {
		List<Object> l = new ArrayList<>();
		i++;
		ws();
		if (peek(']')) { i++; return l; }
		while (true) {
			l.add(value());
			ws();
			if (peek(',')) { i++; continue; }
			expect(']');
			return l;
		}
	}

	private String string() {
		expect('"');
		StringBuilder b = new StringBuilder();
		while (true) {
			if (i >= s.length()) throw error("unterminated string");
			char c = s.charAt(i++);
			if (c == '"') return b.toString();
			if (c != '\\') { b.append(c); continue; }
			char e = s.charAt(i++);
			switch (e) {
				case 'n': b.append('\n'); break;
				case 't': b.append('\t'); break;
				case 'r': b.append('\r'); break;
				case 'b': b.append('\b'); break;
				case 'f': b.append('\f'); break;
				case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
				default: b.append(e);
			}
		}
	}

	private Number number() {
		int start = i;
		while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
		String t = s.substring(start, i);
		if (t.isEmpty()) throw error("unexpected character");
		if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.valueOf(t);
		return Long.valueOf(t);
	}

	private Object literal(String word, Object v) {
		if (!s.startsWith(word, i)) throw error("bad literal");
		i += word.length();
		return v;
	}

	private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
	private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }
	private void expect(char c) { if (!peek(c)) throw error("expected '" + c + "'"); i++; }
	private IllegalArgumentException error(String m) { return new IllegalArgumentException("JSON " + m + " at " + i); }
}
