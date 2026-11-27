package com.feralenterprises.raccoon.playtest.r02.host;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The host runs only inside a launcher-made isolated profile. The launcher sets {@code user.home} to the profile
 * folder (so RuneLite's {@code ~/.runelite}, the game cache, saved Jagex accounts and config all live there) and
 * writes {@link #MARKER}. The plugin refuses to start anywhere else, so it can never run in the owner's real
 * RuneLite folder, even if the JAR is copied there by mistake.
 */
public final class ProfileGuard {
	public static final String MARKER = "RACCOON-R02-PLAYTEST-PROFILE";
	public static final String MARKER_CONTENT = "raccoon-r02-playtest-profile v1\n";
	public static final String PROFILE_PROPERTY = "raccoon.r02.profile";

	private ProfileGuard() { }

	/**
	 * @param userHome the JVM's {@code user.home}
	 * @param runeliteDir RuneLite's {@code RUNELITE_DIR}
	 * @param profileProperty the launcher's {@value #PROFILE_PROPERTY} value
	 * @return the verified profile folder
	 */
	public static Path check(String userHome, Path runeliteDir, String profileProperty) {
		if (profileProperty == null || profileProperty.isEmpty())
			throw new IllegalStateException("not started by the R02 playtest launcher (" + PROFILE_PROPERTY + " unset); refusing to run");
		Path profile = Paths.get(profileProperty).toAbsolutePath().normalize();
		Path home = Paths.get(userHome).toAbsolutePath().normalize();
		if (!home.equals(profile)) throw new IllegalStateException("user.home is not the isolated playtest profile; refusing to run");
		if (!runeliteDir.toAbsolutePath().normalize().equals(profile.resolve(".runelite")))
			throw new IllegalStateException("RuneLite folder is outside the isolated playtest profile; refusing to run");
		Path marker = profile.resolve(MARKER);
		try {
			if (!Files.isRegularFile(marker) || !MARKER_CONTENT.equals(new String(Files.readAllBytes(marker), StandardCharsets.UTF_8)))
				throw new IllegalStateException("profile marker missing or wrong; refusing to run");
		} catch (IOException e) {
			throw new IllegalStateException("profile marker unreadable; refusing to run", e);
		}
		return profile;
	}
}
