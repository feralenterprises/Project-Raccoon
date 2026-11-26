# Project-Raccoon
An intelligent RuneLite companion for Old School RuneScape. Built by Feral Enterprises.

**Status:** foundation scaffold (FR-000). No features yet. Roadmap and authorization: issue #1.

## Build

Requires JDK 11 (pinned: Temurin 11.0.33+10).

```
./gradlew clean build
java tools/verify/FoundationVerifier.java source .
java tools/verify/FoundationVerifier.java artifact . build/libs/project-raccoon.jar
./gradlew run   # development client
```

Records: [standing engineering requirements](docs/engineering-requirements.md) · [architecture](docs/architecture.md) · [account lifecycle](docs/account-lifecycle.md) · [UI integration](docs/ui-integration.md) · [bank observation](docs/bank-observation.md) · [feature parity](docs/feature-parity.md) ·
[migration provenance](docs/migration-provenance.md) · [release evidence](docs/release-evidence.md) ·
[governance](docs/governance.md) · [pins](release/pins.properties)

Licence: BSD-2-Clause.
