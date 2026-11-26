# Migration provenance

The previous project is a read-only donor. Its repository is not modified, and its private
material (research, personal or account data, internal plans) is not copied into this public
repository.

## Imported so far

| File in this repo | Origin | Licence | Notes |
|---|---|---|---|
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | `runelite/example-plugin` @ `46e14f2c` (Gradle 8.10 wrapper) | Apache-2.0 (Gradle) | Unmodified; jar SHA-256 pinned |
| `build.gradle` structure | `runelite/example-plugin` @ `46e14f2c` | BSD-2-Clause | Renamed, versions pinned, reproducible archives |
| `src/test/resources/logback-test.xml` | `runelite/example-plugin` @ `46e14f2c` | BSD-2-Clause | Logger name changed |

**Donor code imported: none.**

## Rules for future imports

1. Import only what an authorized assignment needs; no broad legacy host imports.
2. Add a row here in the same commit: destination, origin category, licence, what changed.
3. Public rows identify public upstream sources by repository and commit. Donor-derived rows name
   only the destination file and a neutral description; private donor paths stay out of source.
4. Imported code must pass `tools/verify` like any other code.
