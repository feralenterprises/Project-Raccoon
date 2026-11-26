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

## Size history (SR-6)

| Handback | Source | Dev JAR bytes | Dev JAR SHA-256 | Official JAR bytes | Official JAR SHA-256 | Δ official |
|---|---|---|---|---|---|---|
| FR-000 | `cb3bc8b1` | 1,326 | `96edc7c9…a97c1ea` | not run | — | — |
| FR-001 | `f3a73150` | 1,326 (Δ 0) | `96edc7c9…a97c1ea` | 1,691 | `cdba98e7…5664f4f` | first measurement |

Limits: foundation 262,144 bytes · release target < 8,000,000 bytes · ceiling 10,485,760 bytes.
