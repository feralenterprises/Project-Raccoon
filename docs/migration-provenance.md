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

**Donor code imported: none** (FR-000 to FR-004). The account lifecycle is new code written to
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
| `BankCaptureGate` (Burrow `eb962df3`, blob `77e14b27`) | Read-only reviewed | `core/bank/BankEvaluator`, `BankObservationTracker` gate | **Behaviour reconciled, not copied**; adopted: full stability fields, container bounds, before/after identity, item-widget and definition-identity checks; stricter R03 rules kept (see `docs/bank-observation.md`) |
| `BankScanTracker` (Burrow `eb962df3`, blob `018605f9`) | Read-only reviewed | `core/bank/BankObservationTracker` streak and authority | **Behaviour reconciled, not copied**; adopted: 2/3 streaks, retry cadence 3 / resample 1, single acceptance; R03 retry bound and bank-generation fencing added |
| `AutomaticBankSnapshotReader` (Burrow `eb962df3`, blob `b7639912`) | Read-only reviewed | `bank/RuneLiteBankCaptureSource` | **Behaviour reconciled, not copied**; uses pinned `gameval` names instead of deprecated `WidgetInfo`/`Varbits` |
| `BossKcDeltaTracker` (Burrow `eb962df3`, blob `3933b213`) | Read-only reviewed | `core/kc/BrutusKcTracker` | **Behaviour reconciled, not copied**: baseline, delta, quarantine, `addExact`; lifecycle scope replaces donor identity/session/sequence (see `docs/kill-count.md`) |
| `RuneLiteBossKcAdapter` (Burrow `eb962df3`, blob `acad726f`) | Read-only reviewed | `core/kc/BrutusKcParser`, `kc/BrutusKcService` | **Behaviour reconciled, not copied**: GAMEMESSAGE/SPAM, colour-tag stripping, length bound; Brutus-only and stricter pattern; no catalog import |
| Noted variants (Burrow) | Read-only reviewed | `getNote() == 799` noted, other non −1 values fail closed | Donor uses `getNote() != -1`; Raccoon stricter |
| Full donor parity | — | — | **Not claimed**: only these three files were reviewed; no native comparison |
