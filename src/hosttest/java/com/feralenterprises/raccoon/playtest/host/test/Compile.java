package com.feralenterprises.raccoon.playtest.host.test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Compiles test-only sources (fake lanes, fake client entry points) into a folder at run time. */
public final class Compile {
	private Compile() { }

	public static void into(Path out, String classpath, Map<String, String> sources) throws IOException {
		Path src = Files.createTempDirectory("hosttest-src");
		List<String> args = new ArrayList<>();
		args.add("-d"); args.add(out.toString());
		args.add("--release"); args.add("11");
		args.add("-nowarn");
		if (classpath != null) { args.add("-cp"); args.add(classpath); }
		for (Map.Entry<String, String> e : sources.entrySet()) {
			Path f = src.resolve(e.getKey().replace('.', File.separatorChar) + ".java");
			Files.createDirectories(f.getParent());
			Files.write(f, e.getValue().getBytes(StandardCharsets.UTF_8));
			args.add(f.toString());
		}
		JavaCompiler c = ToolProvider.getSystemJavaCompiler();
		int rc = c.run(null, null, null, args.toArray(new String[0]));
		if (rc != 0) throw new IOException("test source compilation failed");
	}
}
