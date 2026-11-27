package com.feralenterprises.raccoon.playtest.host.test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads class-file constant pools: every class a class refers to (CONSTANT_Class and every L...; type in descriptors
 * and signatures) and every method it calls (owner.name). Used to prove what the host can and cannot touch.
 */
final class ClassScan {
	private static final Pattern TYPE = Pattern.compile("L([A-Za-z_$][A-Za-z0-9_$/]*);");

	final Set<String> types = new TreeSet<>();
	final Set<String> methods = new TreeSet<>();
	int classes;

	static ClassScan of(Path... roots) throws IOException {
		ClassScan s = new ClassScan();
		for (Path r : roots) {
			if (!Files.isDirectory(r)) throw new IOException("no compiled classes at " + r);
			List<Path> files = new ArrayList<>();
			try (Stream<Path> w = Files.walk(r)) { w.filter(p -> p.toString().endsWith(".class")).forEach(files::add); }
			for (Path f : files) try (InputStream in = Files.newInputStream(f)) { s.read(in); }
		}
		return s;
	}

	void read(InputStream raw) throws IOException {
		DataInputStream in = new DataInputStream(raw);
		if (in.readInt() != 0xCAFEBABE) throw new IOException("not a class file");
		in.readUnsignedShort();
		in.readUnsignedShort();
		int n = in.readUnsignedShort();
		String[] utf = new String[n];
		int[] classIdx = new int[n];
		int[][] ref = new int[n][];
		int[][] nat = new int[n][];
		for (int i = 1; i < n; i++) {
			int tag = in.readUnsignedByte();
			switch (tag) {
				case 1: utf[i] = in.readUTF(); break;
				case 3: case 4: in.readInt(); break;
				case 5: case 6: in.readLong(); i++; break;
				case 7: classIdx[i] = in.readUnsignedShort(); break;
				case 8: case 16: case 19: case 20: in.readUnsignedShort(); break;
				case 9: case 10: case 11: ref[i] = new int[] {tag, in.readUnsignedShort(), in.readUnsignedShort()}; break;
				case 12: nat[i] = new int[] {in.readUnsignedShort(), in.readUnsignedShort()}; break;
				case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
				case 17: case 18: in.readUnsignedShort(); in.readUnsignedShort(); break;
				default: throw new IOException("unknown constant tag " + tag);
			}
		}
		classes++;
		for (int i = 1; i < n; i++) {
			if (classIdx[i] != 0) {
				String c = utf[classIdx[i]];
				types.add(c.startsWith("[") ? c.replaceAll("^\\[+L?|;$", "") : c);
			}
			if (utf[i] != null) { Matcher m = TYPE.matcher(utf[i]); while (m.find()) types.add(m.group(1)); }
			if (ref[i] != null && ref[i][0] != 9) {
				String owner = utf[classIdx[ref[i][1]]];
				String name = utf[nat[ref[i][2]][0]];
				methods.add(owner + "." + name);
			}
		}
	}
}
