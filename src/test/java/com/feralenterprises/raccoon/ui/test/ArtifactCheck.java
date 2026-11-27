package com.feralenterprises.raccoon.ui.test;

import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Checks packaged artifacts: Java 11 bytecode, no images in the framework, no simulator or foundation classes. */
public final class ArtifactCheck {
	public static void main(String[] args) throws Exception {
		int bad = 0;
		bad += check(new File(args[0]), true);
		bad += check(new File(args[1]), false);
		System.out.println("ArtifactCheck: " + (bad == 0 ? "PASS" : bad + " problem(s)"));
		System.exit(bad == 0 ? 0 : 1);
	}

	static int check(File jar, boolean framework) throws Exception {
		int bad = 0, classes = 0, maxMajor = 0;
		try (ZipFile z = new ZipFile(jar)) {
			for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
				ZipEntry e = en.nextElement();
				String n = e.getName();
				if (e.isDirectory() || n.startsWith("META-INF/")) continue;
				if (n.endsWith(".class")) {
					classes++;
					try (InputStream in = z.getInputStream(e); DataInputStream d = new DataInputStream(in)) {
						d.readInt(); d.readUnsignedShort();
						maxMajor = Math.max(maxMajor, d.readUnsignedShort());
					}
					if (framework && (n.contains("/workbench/") || n.contains("/core/account/") || n.contains("/integration/"))) {
						System.out.println("  framework contains non-framework class " + n); bad++;
					}
				} else {
					System.out.println("  " + jar.getName() + " contains non-class entry " + n); bad++;
				}
			}
		}
		if (maxMajor > 55) { System.out.println("  class major " + maxMajor + " > 55 (Java 11)"); bad++; }
		byte[] dig = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar.toPath()));
		StringBuilder sb = new StringBuilder();
		for (byte b : dig) sb.append(String.format("%02x", b));
		System.out.println("  " + jar.getName() + ": " + jar.length() + " bytes, " + classes + " classes, max major " + maxMajor + ", sha256 " + sb);
		return bad;
	}
}
