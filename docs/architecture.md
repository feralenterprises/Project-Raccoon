# Architecture

Status: FR-000 foundation. Roadmap and authorization: issue #1 (authoritative).

## Build shape

- One root Gradle project (`settings.gradle`: `project-raccoon`), one Gradle wrapper, Groovy DSL, tab-indented.
- Follows `runelite/example-plugin` (pinned commit in `release/pins.properties`).
- `runelite-plugin.properties` declares `build=standard`, so the Plugin Hub packager compiles only
  `src/main/java`, `src/main/resources` and `lombok.config` with its own build script. Our
  `build.gradle` is for development and CI only and must stay compatible with that standard script.
- Production bytecode: `options.release.set(11)`; every class in the JAR must be major ≤ 55.
- Runtime dependencies: none beyond what the RuneLite client provides (`compileOnly` only).

## Packages and dependency direction

```
com.feralenterprises.raccoon                 plugin entry and event forwarding (RuneLite-facing)
com.feralenterprises.raccoon.account         RuneLite adapter for the account lifecycle
com.feralenterprises.raccoon.bank            RuneLite adapter for bank observation (reads only)
        │
        ▼  (allowed)
com.feralenterprises.raccoon.core            pure domain: java.lang / java.util / java.time only
com.feralenterprises.raccoon.core.account    account lifecycle: single source of truth (FR-002)
com.feralenterprises.raccoon.core.bank       read-only bank observation over the lifecycle (FR-003)
```

- `core` must never depend on the plugin package, on RuneLite, or on any other library.
- The plugin package may use only the FR-000 surface: `java.lang`, `java.util`, `java.time`,
  `javax.inject`, `com.google.inject`, `org.slf4j`, and `net.runelite.client.plugins.Plugin` /
  `PluginDescriptor`, plus (FR-002, exact types only) `net.runelite.api.Client` (and `com.jagex.oldscape.pub.OAuthApi`, which declares
  `getAccountHash`), `GameState`,
  `WorldType`, `events.GameStateChanged`, `events.GameTick`, `events.ClientTick` and
  `net.runelite.client.eventbus.Subscribe`; (FR-003, exact types only) `net.runelite.api.ItemContainer`,
  `Item`, `ItemComposition` and `widgets.Widget` for read-only bank observation. Game-value constants
  (`gameval.*`) compile to literals and add no references. All other `net.runelite.api` types
  stay forbidden.
- Account state lives only in `core.account.AccountLifecycle` (see `docs/account-lifecycle.md`);
  consumers use `AccountLifecycleView` and `AccountDataRegistry` (see `docs/ui-integration.md`).
- Later assignments widen the allowed surface only by editing the allowlist in
  `tools/verify/FoundationVerifier.java` in a reviewed checkpoint (for example FR-003 adds the
  read-only `net.runelite.api` types it needs).

## Forbidden in this checkpoint

Enforced on the compiled JAR by `tools/verify`: networking (`java.net`, `okhttp3`), file and
persistence I/O (`java.io`, `java.nio`, `java.sql`, RuneLite config storage), UI (`javax.swing`,
`java.awt`, `net.runelite.client.ui`), game observation (`net.runelite.api`), reflection and
dynamic loading, external processes, `Thread.sleep`, internal JDK APIs, shaded or third-party
classes, service descriptors, native libraries, `module-info`, and assets.

## Verification

`tools/verify/FoundationVerifier.java` is offline, dependency-free Java 11, run as a single
source file. CI (`.github/workflows/foundation.yml`) runs: JDK pin → verifier self-test →
source/pin/governance checks → `clean build` → test counts → artifact checks → reproducibility.
