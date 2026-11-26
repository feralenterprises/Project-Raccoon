# Release evidence

One entry per checkpoint. Local and CI measurements are **not** the official Plugin Hub packager
result; that gate is FR-001.

## FR-000 — release-compatible scaffold

| Item | Value |
|---|---|
| Source SHA | recorded in the PR handback and in CI `foundation-evidence.txt` |
| Build | `./gradlew --no-daemon --no-build-cache clean build` (CI, Temurin 11.0.33+10) |
| Checks | `java tools/verify/FoundationVerifier.java jdk|selftest|source|tests|artifact` |
| Local JAR | `build/libs/project-raccoon.jar`, bytes and SHA-256 in CI evidence |
| Size limit | 262144 bytes (256 KiB), measured on local JAR only |
| Official packager (FR-001) | **Not run — pending** |

## FR-001 — official strict non-upload packager

**Result: PASS** (pending owner acceptance). Workflow `.github/workflows/official-packager.yml`.

| Item | Value |
|---|---|
| Source commit packaged | `f3a73150c624749785e271646bef3c5f4a43c861` (cloned by the packager over public HTTPS) |
| Packager | `runelite/plugin-hub-tooling` release `v6` = `c9e2fb4fb1d49a860ef68bc6fe34c0dab4f60aa9` |
| Packager bundle SHA-256 | `8ca79aa71611b85ea42ecd81d01e483027d84c158fc34d554521c4407fe76acd` (equals GitHub published digest) |
| Plugin Hub state | `runelite/plugin-hub` @ `f7ed590e4e2599d81b3bd7ffeaec62314178ae3c`, RuneLite 1.13.8 |
| Mode | strict (template defaults and disallowed APIs fatal); no upload credentials; marker committed only in the runner-local checkout |
| JDK | Temurin 11.0.33+10 |
| Post-processed JAR | `project-raccoon.jar`, **1,691 bytes**, SHA-256 `cdba98e78707174d2045924d608b2a2e3591aa091cfec58f82be3111d5664f4f` |
| Packager log | `built jar with hash zbqY54cHF00gRZJNYIsqLjWRqgkc_sWPgr4xEdVmT08 from commit f3a73150…` (same SHA-256, base64url) |
| Verification | `FoundationVerifier packaged`: bytecode major 55, entries = manifest + 1 class + `runelite_plugin.json`, allowed references only, log/hash/commit match |
| Retained | Actions artifact `fr-001-official-package-f3a73150c624749785e271646bef3c5f4a43c861` (JAR, packager log, source zip, manifest diff, evidence, SHA256SUMS), 90-day retention |
| CI job | https://github.com/feralenterprises/Project-Raccoon/actions/runs/45893596318/job/140161948355 |

The post-processed JAR embeds the commit prefix in `runelite_plugin.json`, so every commit
produces a different JAR hash. Each push produces its own retained receipt.

## FR-001 — accepted receipt (roadmap source)

Issue #1 names the FR-000 source `cb3bc8b1942377de8e95711d8d57e45b6b1f003c` for FR-001. The first
FR-001 runs packaged later commits (identical plugin source), so this exact commit was packaged
on 2026-11-26 and its receipt independently re-verified on a fresh runner.

| Item | Value |
|---|---|
| Accepted source SHA | `cb3bc8b1942377de8e95711d8d57e45b6b1f003c` |
| Packager | plugin-hub-tooling `v6` (`c9e2fb4fb1d49a860ef68bc6fe34c0dab4f60aa9`), bundle `8ca79aa7…fe76acd` |
| Post-processed JAR | **1,690 bytes**, SHA-256 `509fef9d67fd1cb2188562b73e761e8356be48c97dda2fd76225de88e5b69e3d` |
| Retained | artifact `15156441662` (`fr-001-official-package-cb3bc8b1…`), run `45901758622`, zip digest `sha256:f3887911eb6b48d10920bc50acfde8907c21a6388897598e89b33cfa26c45453`, expires 2027-02-24 |
| Packaging job | `140188975299` |
| Independent verification job | `140189267305`: SHA256SUMS OK, evidence names the commit, source archive 22/22 files equal the commit tree, verifier `packaged` PASS (major 55, 7 entries) |
| Earlier receipts re-verified | `f3a73150` (1,691 B, `cdba98e7…`) job `140188975677`; `65aac3d2` (1,690 B, `25a3eed6…`) job `140188975528` |

The post-processed JAR embeds packaging-time metadata (`runelite_plugin.json`), so repackaging
the same commit gives a different hash; the retained artifact above is the accepted one.

## FR-002 — account lifecycle (A01–A06)

**Result: implemented; all regression and packaging checks PASS** (awaiting owner review).

| Item | Value |
|---|---|
| Source SHA | `9257838fe5227d06bd7482f425bbff1c1a3cc431` |
| Tests | 3 suites, **54 tests**, 0 failures, 0 errors, 0 skipped (39 lifecycle A01–A06, 10 adapter, 5 descriptor) |
| Verifier self-test | 40/40 |
| Development JAR | **27,398 bytes**, SHA-256 `0e2ba235a39b5226df506fb4c4d01acf635fa121f7615945ff51ab41ef04fc05`, 22 classes, major 55, reproducible |
| Official post-processed JAR | **27,763 bytes**, SHA-256 `aa490c6046f16336e49cf1a8b51f000872898a4b794c8783b5876fc4d01ced23` (strict, non-upload, plugin-hub-tooling v6) |
| Retained | artifact `15157520643`, run `45904083364`; re-verified on a fresh runner (job `140196840758`): source archive 48/48 files equal the commit tree |
| FR-001 regression | accepted `cb3bc8b1` receipt re-verified (job `140196552456`); `cb3bc8b1` repackaged and verified (jobs `140196552345`, `140196840774`) |
| CI jobs | foundation `140196551830`, `140196570537`; package head `140196551875` |

## Size history (SR-6)

| Handback | Source | Dev JAR bytes | Dev JAR SHA-256 | Official JAR bytes | Official JAR SHA-256 | Δ official |
|---|---|---|---|---|---|---|
| FR-000 | `cb3bc8b1` | 1,326 | `96edc7c9…a97c1ea` | not run | — | — |
| FR-001 | `f3a73150` | 1,326 (Δ 0) | `96edc7c9…a97c1ea` | 1,691 | `cdba98e7…5664f4f` | first measurement |
| FR-001 accepted | `cb3bc8b1` | 1,326 | `96edc7c9…a97c1ea` | 1,690 | `509fef9d…b69e3d` | −1 vs `f3a73150` (metadata) |
| FR-002 | `9257838f` | 27,398 (+26,072) | `0e2ba235…04fc05` | 27,763 | `aa490c60…01ced23` | +26,073 vs accepted FR-001 (lifecycle: 22 classes) |

Limits: foundation 262,144 bytes · release target < 8,000,000 bytes · ceiling 10,485,760 bytes.
