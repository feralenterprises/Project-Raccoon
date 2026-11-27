package com.feralenterprises.raccoon.playtest.r02.host;

import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenDataset;
import com.feralenterprises.raccoon.playtest.r02.assembly.FrozenPackageAssets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The one frozen R02 review package the native host accepts. The package's own {@code SHA256SUMS.txt} verifies every
 * file; this pin verifies {@code SHA256SUMS.txt} itself, so a package whose files and sums were both changed is refused.
 * The package stays outside the repository (it may hold personal bank data); only these digests are committed.
 */
public final class PackagePin {
	/** SHA-256 of {@code Project-Raccoon-R02/SHA256SUMS.txt} in the accepted frozen review package (831 entries). */
	public static final String SUMS_SHA256 = "4266612cc8d66c71369397032d010f88fdb419bb0fe64fe87da75632265722c4";
	/** SHA-256 of the delivered review ZIP ({@code Project-Raccoon-R02-Review.zip}); informational, for the owner's check. */
	public static final String REVIEW_ZIP_SHA256 = "ff4a67ab234b101c60c7d68ac5b714643c5952358683a90e1630189156f1d3ba";

	private PackagePin() { }

	/**
	 * Accepts either the extracted {@code Project-Raccoon-R02} folder or its parent. Returns the package root.
	 * @throws IOException when the folder is missing, incomplete or not the pinned package
	 */
	public static Path verify(Path given) throws IOException {
		if (given == null) throw new IOException("no frozen package path given");
		Path root = given;
		if (!Files.isRegularFile(root.resolve("SHA256SUMS.txt")) && Files.isDirectory(root.resolve("Project-Raccoon-R02")))
			root = root.resolve("Project-Raccoon-R02");
		Path sums = root.resolve("SHA256SUMS.txt");
		if (!Files.isRegularFile(sums)) throw new IOException("not an extracted R02 package (no SHA256SUMS.txt): " + given);
		if (!Files.isRegularFile(root.resolve(FrozenDataset.PAGE))) throw new IOException("package has no " + FrozenDataset.PAGE + ": " + root);
		String d = FrozenPackageAssets.sha256(Files.readAllBytes(sums));
		if (!SUMS_SHA256.equals(d))
			throw new IOException("frozen package is not the accepted R02 package: SHA256SUMS.txt is " + d + ", expected " + SUMS_SHA256);
		return root.toAbsolutePath().normalize();
	}
}
