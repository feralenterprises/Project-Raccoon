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

Pending. Must package a public HTTPS exact-SHA clone with `runelite/plugin-hub-tooling` and
`runelite/plugin-hub` at the commits in `release/pins.properties`, retain the post-processed JAR
and its evidence, and show it ≤ 256 KiB.
