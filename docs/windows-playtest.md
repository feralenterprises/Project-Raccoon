# FR-006 — Windows playtest kit (preparation for review)

**Status: prepared for Solis review. No owner live test is requested until the launch path,
isolation, plan and evidence package below are reviewed.** Candidate: source
`aefb1d79f680ef2c184e24b82e487db08d05c879` (FR-005 accepted), retained official JAR artifact
`15168762052` (76,270 B, SHA-256 `5eb3405a5be817340a626f4625859f47ec9591afc10e792322dd290d3b456bd5`).
Requirements are restated in our own words; the private handoff is not published.

## 1. Canonical workflow (one place, no worktrees)

| Item | Value |
|---|---|
| Branch | `test/fr-windows-playtest`, cut from evidence head `ef5ceafb`; adds only `playtest/`, this record and `.github/workflows/fr-windows-playtest.yml` |
| Production identity | CI fails unless `src/`, build files and the Gradle wrapper equal the candidate exactly (`git diff --exit-code aefb1d79 HEAD -- src build.gradle settings.gradle runelite-plugin.properties gradle gradlew gradlew.bat`) |
| Build | `fr-windows-playtest` workflow: Linux job builds the Java parts with an init script (`playtest/kit.gradle`, no change to `build.gradle`); Windows job adds the runtime, writes the manifest, runs the self-check and R1, and attempts the unattended client runs |
| Delivered to the owner | one workflow artifact, `raccoon-playtest-kit` (a folder: extract, double-click). No git, Gradle, IDE, Java install or worktree |
| Not produced | no release, no Plugin Hub submission, no merge; the kit is never committed as a binary |

## 2. Kit contents and the four classpaths

```
Raccoon-Playtest.cmd / .ps1   menu launcher (Windows PowerShell 5.1)
READ-ME-FIRST.txt             plain-English owner instructions (playtest/OWNER-INSTRUCTIONS.md)
R3-CHECKLIST.txt, R4-CHECKLIST.txt
kit-manifest.json             candidate, kit commit, official JAR identity, SHA-256 of every file
runtime\                      Temurin 11.0.33+10 windows-x64 (jmods and src.zip removed)
lib\client\                   RuneLite client 1.13.8 and dependencies (no Raccoon classes; checked)
lib\dev\project-raccoon.jar   the candidate's reproducible development JAR (checked: 75,907 B, eebb6ecc…)
lib\dev\raccoon-tests.jar     candidate test classes + playtest witness/runner (never in production)
lib\dev\junit, hamcrest       test runner
official\project-raccoon.jar  the exact retained official JAR (re-verified with verify-retained.sh)
fixtures\runelite-plugin.properties
```

| Step | Main class | Classpath | Raccoon code loaded |
|---|---|---|---|
| R1 | `playtest.R1Runner` (plain JVM) | `lib\client\*;lib\dev\*` | candidate dev JAR + candidate tests |
| R2 | `playtest.PlaytestClient` (RuneLite) | `lib\client\*;lib\dev\*` | candidate dev JAR + witness |
| R3 | `playtest.PlaytestClient` (RuneLite) | `lib\client\*;lib\dev\*` | candidate dev JAR + witness |
| R4 / I03 | `net.runelite.client.RuneLite` | `lib\client\*` only | **only** the side-loaded official JAR |

## 3. Isolation

- Every client JVM gets `-Duser.home=<kit>\home` and `-Djava.io.tmpdir=<kit>\tmp`. RuneLite places
  its whole data directory (config, profiles, plugin state, side-loaded plugins, logs, game cache,
  developer sign-in) under `user.home\.runelite`; this is pinned as a CI fact on the 1.13.8 jar.
- Before and after every client step the launcher snapshots `%USERPROFILE%\.runelite`
  (relative path, size and write time of every file, digested) and the Java preferences registry
  key, and writes `isolation.json` (PASS only if both are unchanged). RuneLite and the Jagex
  Launcher must be closed (checked).
- The owner's normal RuneLite installation, launcher, settings and credentials are never read.
  Jagex-account sign-in uses the developer client's own Jagex account panel; its data stays in
  `<kit>\home` and menu option 7 deletes it. No `credentials.properties` is created or copied.
- Receipts record paths with the kit folder and profile folder replaced (`<kit>`, `%USERPROFILE%`).

## 4. Exact commands (what the menu runs)

```
SelfCheck  every manifest file re-hashed; official JAR and dev JAR identities shown
R1         runtime\bin\java.exe -ea -Xmx1024m -Duser.home=<kit>\home -Djava.io.tmpdir=<kit>\tmp
             -Draccoon.playtest.receipts=<run> -cp "lib\client\*;lib\dev\*"
             com.feralenterprises.raccoon.playtest.R1Runner                (working dir <run>)
R2         ... -Draccoon.playtest.mode=R2 -Draccoon.playtest.exitAfterR2=true -cp "lib\client\*;lib\dev\*"
             com.feralenterprises.raccoon.playtest.PlaytestClient --developer-mode --debug
R3         ... -Draccoon.playtest.mode=R3 -cp "lib\client\*;lib\dev\*"
             com.feralenterprises.raccoon.playtest.PlaytestClient --developer-mode --debug
R4         copy official\project-raccoon.jar to <kit>\home\.runelite\sideloaded-plugins\ (hash-checked;
             nothing else in that folder; previous client.log set aside), then
           ... -cp "lib\client\*" net.runelite.client.RuneLite --developer-mode --debug
Evaluate   ... -cp "lib\client\*;lib\dev\*" com.feralenterprises.raccoon.playtest.Evaluate <step> <evidence>
```

Non-interactive form (CI, or a reviewer): `Raccoon-Playtest.ps1 -Step SelfCheck|R1|R2|R3|R4|Collect -NonInteractive`.

## 5. Receipts and verdicts

Each step writes `receipts\<step>-<UTC>\`: a JSON Lines receipt (environment, every observation,
the verdict), `isolation.json`, `verdicts.txt`, and for R4 a filtered `client-log-excerpt.txt`
(plugin-loading, Raccoon and error lines only). `Evaluate` decides: **PASS**, **FAIL**,
**BLOCKED** (required genuine evidence missing), **NOT_OBSERVED** (optional), **REPORTED**
(informational). Missing evidence is never PASS; CI proves an empty R3 receipt is BLOCKED.

| Receipt | Criteria (paraphrased) | Evidence | Verdict rule |
|---|---|---|---|
| **R1** | Complete deterministic regression replay | all 20 permanent suites in a plain JVM on the test machine | PASS only with every suite run, 0 failures, 0 skipped |
| **R2** | Native adapter replay, synthetic fixtures, actual client thread | genuine client at the login screen; the witness runs the 8 adapter suites (incl. I01, I02, ordinary/observed-transition KC, performance) inside `ClientThread.invokeLater`; `client.isClientThread()` recorded | PASS only if all pass on the client thread and the performance report passes |
| **R3** | Genuine lifecycle, bank capture, failed revalidation, Brutus KC | witness subscribed to the production views (same singletons, checked) | each item below PASS only when genuinely observed |
| **R4** | Exact official JAR starts and stops twice; no fixtures or launcher classes in it | client log, side-loaded JAR hash, JAR and classpath scans | started ≥2 and stopped ≥2 in order, side-load line, no plugin errors |
| **I03** (interpretation, §8) | The exact retained official JAR runs in the genuine client | R4: identity + side-load + start + clean classpath | PASS only with all four |

R3 items: shared instances; genuine login (ACTIVE, publishable); verified same-login hop
(SUSPENDED → ACTIVE, same login, new epoch); failed revalidation (retired, e.g. LOGOUT, then a new
login, old data not restored); bank QUALIFIED_CURRENT; revocation on close; fresh streak on
reopen; Brutus line type (GAMEMESSAGE or SPAM, the FR-004 native-line question), zero-delta
baseline, gain equal to the count increase; native timings. Optional: bank PIN, K05 baseline after
a hop.

**The witness never drives a production input and never manufactures game events.** It only
reads views and subscriptions, brackets genuine events with the highest and lowest event-bus
priorities, and (R3) calls the read-only production capture path and the pure evaluator on
genuine open-bank ticks for timing; those frames never reach the production tracker. R2's
synthetic fixtures run on private instances with a proxied client; the genuine client and the
production instances are untouched. In witness runs the lifecycle has one extra subscriber (the
witness); I01 is evidenced by R2's replay of `FoundationIntegrationTest` and by R4 (no witness).

## 6. Native performance

| Measurement | Where | Samples | Requirement |
|---|---|---|---|
| Event callback replay | R2, genuine client thread (`FoundationPerformanceTest` in the client JVM) | 1,000 | p95 ≤ 2 ms |
| Bank-open capture (synthetic) | R2, client thread, 1,000 and 4,096 slots | 200 each | p95 ≤ 10 ms |
| Bank-open capture (genuine) | R3, production read path on the genuine open bank + evaluation | ≥ 50 (≈ 30 s open) | p95 ≤ 10 ms |
| Genuine game-tick callbacks | R3, witness bracket (upper bound: includes every subscriber at priority 0) | ≥ 1,000 (≈ 10 min) | p95 ≤ 2 ms |
| Genuine chat callbacks | R3, witness bracket | as observed | reported only |

Machine, OS, CPU, JVM and heap are in every receipt's `environment` record.

## 7. Owner instructions

`playtest/OWNER-INSTRUCTIONS.md` (shipped as `READ-ME-FIRST.txt`), plus the on-screen R3/R4
checklists. They start with "do not start until the reviewer approves". About 30 minutes; the
owner never troubleshoots: any problem → screenshot to the reviewer.

## 8. Decisions and risks for review

1. **I03.** Read here as "the exact retained official JAR loads and runs in the genuine client",
   evidenced in R4. If the governing definition differs, the evaluator criteria change, not the
   kit.
2. **"Failed revalidation" (R3).** Evidenced by a genuine logout and new login (the held login is
   retired and never restored; the next Brutus count baselines) and by bank revocation and fresh
   streak. A genuine hop that fails continuity (for example over 120 s) cannot be produced safely
   on demand; it stays covered by A04 regressions, not claimed natively.
3. **R1 location.** The kit runs R1 on the owner's machine; CI also runs it on a Windows runner
   (preparation evidence). Which run is the R1 receipt is Solis's choice.
4. **Genuine tick timing is an upper bound** (other core plugins share priority 0); the exact
   production callback cost natively is the R2 replay.
5. **R4 toggling.** Start/stop twice is done in the plugin list (startup at client start, then
   off/on/off); a second client launch is an alternative if preferred.
6. **Jagex accounts.** The developer client's Jagex account panel is used (pinned-client fact
   checked in CI); a non-Jagex account types its details. Sign-in data stays in the kit.
7. **CI client runs** (R2 attempt and R4 launch path on a Windows runner) depend on the runner
   having a desktop and network access to RuneLite and Jagex; if they cannot run there, they
   are reported as such and are not receipts either way.
