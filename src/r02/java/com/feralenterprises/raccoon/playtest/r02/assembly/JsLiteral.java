package com.feralenterprises.raccoon.playtest.r02.assembly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict reader for the data literals in the frozen R02 page: JSON plus the JavaScript literal forms the frozen
 * datasets use (single-quoted strings, unquoted keys, comments, {@code Object.freeze(x)}, trailing commas) and
 * call expressions such as {@code ffRecord(...)}, which are returned as {@link Call} nodes for the caller to map.
 * Nothing is evaluated. Anything else (functions, operators, templates) is refused with its position.
 *
 * <p>Values: {@code Map<String,Object>} (insertion order), {@code List<Object>}, {@code String}, {@code Double},
 * {@code Boolean}, {@code null}, {@link Call}.
 */
public final class JsLiteral {
	/** An uninterpreted call {@code name(args...)}. */
	public static final class Call {
		public final String name;
		public final List<Object> args;
		Call(String name, List<Object> args) { this.name = name; this.args = Collections.unmodifiableList(args); }
		public Object arg(int i) { return i < args.size() ? args.get(i) : null; }
		@Override public String toString() { return name + args; }
	}

	private final String s;
	private int i;

	private JsLiteral(String s, int start) { this.s = s; this.i = start; }

	/** Parses the literal that follows {@code const NAME=} (or let/var) in {@code source}. */
	public static Object declaration(String source, String name) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:const|let|var)\\s+" + java.util.regex.Pattern.quote(name) + "\\s*=").matcher(source);
		if (!m.find()) throw new IllegalArgumentException("no declaration of " + name);
		JsLiteral p = new JsLiteral(source, m.end());
		return p.value();
	}

	/** Parses one complete literal (the whole string). */
	public static Object parse(String text) {
		JsLiteral p = new JsLiteral(text, 0);
		Object v = p.value();
		p.ws();
		if (p.i != text.length()) throw p.error("trailing content");
		return v;
	}

	private Object value() {
		ws();
		if (i >= s.length()) throw error("unexpected end");
		char c = s.charAt(i);
		if (c == '{') return object();
		if (c == '[') return array();
		if (c == '\'' || c == '"') return string();
		if (c == '-' || (c >= '0' && c <= '9') || c == '.') return number();
		if (Character.isJavaIdentifierStart(c)) {
			String id = identifier();
			while (peek('.')) { i++; id += "." + identifier(); }
			switch (id) {
				case "true": return Boolean.TRUE;
				case "false": return Boolean.FALSE;
				case "null": case "undefined": return null;
				default: break;
			}
			ws();
			if (!peek('(')) throw error("identifier " + id + " is not a literal");
			i++;
			List<Object> args = new ArrayList<>();
			ws();
			if (!peek(')')) {
				while (true) {
					args.add(value());
					ws();
					if (peek(',')) { i++; ws(); if (peek(')')) break; continue; }
					break;
				}
			}
			expect(')');
			if (id.equals("Object.freeze") && args.size() == 1) return args.get(0);
			return new Call(id, args);
		}
		throw error("unsupported token '" + c + "'");
	}

	private Map<String, Object> object() {
		expect('{');
		Map<String, Object> m = new LinkedHashMap<>();
		ws();
		while (!peek('}')) {
			String key;
			char c = s.charAt(i);
			if (c == '\'' || c == '"') key = string();
			else if (c >= '0' && c <= '9') key = number().toString().replaceAll("\\.0$", "");
			else key = identifier();
			ws();
			expect(':');
			if (m.put(key, value()) != null) throw error("duplicate key " + key);
			ws();
			if (peek(',')) { i++; ws(); } else break;
		}
		ws();
		expect('}');
		return Collections.unmodifiableMap(m);
	}

	private List<Object> array() {
		expect('[');
		List<Object> l = new ArrayList<>();
		ws();
		while (!peek(']')) {
			l.add(value());
			ws();
			if (peek(',')) { i++; ws(); } else break;
		}
		ws();
		expect(']');
		return Collections.unmodifiableList(l);
	}

	private String string() {
		char q = s.charAt(i++);
		StringBuilder b = new StringBuilder();
		while (true) {
			if (i >= s.length()) throw error("unterminated string");
			char c = s.charAt(i++);
			if (c == q) return b.toString();
			if (c == '\n') throw error("newline in string");
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

	private Double number() {
		int start = i;
		if (peek('-')) i++;
		while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) {
			char c = s.charAt(i);
			if ((c == '+' || c == '-') && !(s.charAt(i - 1) == 'e' || s.charAt(i - 1) == 'E')) break;
			i++;
		}
		try { return Double.valueOf(s.substring(start, i)); }
		catch (NumberFormatException e) { throw error("bad number " + s.substring(start, i)); }
	}

	private String identifier() {
		int start = i;
		if (i >= s.length() || !Character.isJavaIdentifierStart(s.charAt(i))) throw error("identifier expected");
		while (i < s.length() && Character.isJavaIdentifierPart(s.charAt(i))) i++;
		return s.substring(start, i);
	}

	private void ws() {
		while (i < s.length()) {
			char c = s.charAt(i);
			if (Character.isWhitespace(c)) i++;
			else if (s.startsWith("//", i)) { while (i < s.length() && s.charAt(i) != '\n') i++; }
			else if (s.startsWith("/*", i)) { int e = s.indexOf("*/", i + 2); if (e < 0) throw error("unterminated comment"); i = e + 2; }
			else break;
		}
	}

	private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }

	private void expect(char c) {
		ws();
		if (!peek(c)) throw error("'" + c + "' expected");
		i++;
	}

	private IllegalArgumentException error(String what) {
		int from = Math.max(0, i - 30), to = Math.min(s.length(), i + 30);
		return new IllegalArgumentException(what + " at " + i + ": ..." + s.substring(from, to).replace('\n', ' ') + "...");
	}
}
