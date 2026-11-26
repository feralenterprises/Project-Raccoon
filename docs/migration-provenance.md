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

**Donor code imported: none** (FR-000 to FR-003). The account lifecycle is new code written to
the R03 §10 contract; Burrow was not accessed and no parity with it is claimed.

## Rules for future imports

1. Import only what an authorized assignment needs; no broad legacy host imports.
2. Add a row here in the same commit: destination, origin category, licence, what changed.
3. Public rows identify public upstream sources by repository and commit. Donor-derived rows name
   only the destination file and a neutral description; private donor paths stay out of source.
4. Imported code must pass `tools/verify` like any other code.

## Migration manifest

| Donor component | Status | Project Raccoon counterpart | Parity |
|---|---|---|---|
| Account lifecycle (Burrow) | Not accessed | `core/account` (FR-002, written to R03 §10) | Not claimed |
| `BankCaptureGate` (Burrow) | **Not accessible** | `core/bank/BankObservationTracker` capture gate (FR-003) | **Not claimed**; differences in `docs/bank-observation.md` |
| `BankScanTracker` (Burrow) | **Not accessible** | `core/bank/BankObservationTracker` streak and authority (FR-003) | **Not claimed**; differences in `docs/bank-observation.md` |
| Noted-variant list (Burrow) | **Not supplied** | Provisional: all noted definitions excluded | Not claimed |
