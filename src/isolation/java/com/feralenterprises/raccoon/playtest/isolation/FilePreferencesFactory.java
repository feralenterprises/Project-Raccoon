package com.feralenterprises.raccoon.playtest.isolation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * Java preferences for the playtest client, kept as files in the isolated profile instead of the shared per-user
 * store ({@code HKCU\Software\JavaSoft\Prefs} on Windows, which the client's FlatLaf library writes). Selected by the
 * launcher with {@code -Djava.util.prefs.PreferencesFactory=...} and {@code -Draccoon.r02.prefsDir=<profile>/java-prefs}.
 * Without that folder it refuses rather than fall back to the shared store. Not Raccoon UI code; never in a release.
 */
public final class FilePreferencesFactory implements PreferencesFactory {
	public static final String DIR_PROPERTY = "raccoon.r02.prefsDir";
	private static Preferences user;
	private static Preferences system;

	private static Path base() {
		String d = System.getProperty(DIR_PROPERTY);
		if (d == null || d.isEmpty()) throw new IllegalStateException(DIR_PROPERTY + " unset: refusing to use the shared preference store");
		return Paths.get(d).toAbsolutePath().normalize();
	}

	@Override public synchronized Preferences userRoot() {
		if (user == null) user = new FilePreferences(null, "", base().resolve("user"));
		return user;
	}

	@Override public synchronized Preferences systemRoot() {
		if (system == null) system = new FilePreferences(null, "", base().resolve("system"));
		return system;
	}
}
