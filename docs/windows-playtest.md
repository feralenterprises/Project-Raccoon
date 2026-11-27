# FR-006 — Windows playtest kit (preparation for review)

**Status: Windows preparation evidence accepted by Solis (review of `a0ef3e4c`); owner login HOLD.
This revision applies only the bounded launcher-safety and receipt-correctness repairs from that
review; no owner test is requested.** Candidate: source
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
- Java preferences, normally the shared registry key `HKCU\Software\JavaSoft\Prefs` (used by
  the client's FlatLaf UI library; the Windows runner showed a key being added), are redirected
  to files in `<kit>\home\java-prefs` by `-Djava.util.prefs.PreferencesFactory=playtest.isolation.KitPreferencesFactory`
  (own jar `lib\isolation\kit-isolation.jar`, not Raccoon code; R4 scans still find 0 Raccoon classes).
- The injected game client also reads `%USERPROFILE%` (CI isolation scan), so besides
  `%USERPROFILE%\.runelite` the launcher also watches `jagexcache`, the classic Jagex files
  (`jagex_cl_oldschool_LIVE.dat`, `random.dat`, `.jagex_cache_32`) and new top-level profile
  entries. Before and after every client step it snapshots these and the Java preferences key
  and writes `isolation.json` (PASS only if all are unchanged). RuneLite and the Jagex
  Launcher must be closed (checked).
- The owner's normal RuneLite installation, launcher, settings and credentials are never read.
  Jagex-account sign-in uses the developer client's own Jagex account panel and runs (R3) only
  in a dedicated standard Windows account `RaccoonTest` (checked before launch). Its data stays in
  `<kit>\home` and `<kit>\tmp` (WebView2, verified) and option 7 deletes it; the Credential Manager
  key is removed with the test account, never by the kit (see `jagex-authentication.md` §4a–4b). No `credentials.properties` is created or copied.
- Receipts record paths with the kit folder and profile folder replaced (`<kit>`, `%USERPROFILE%`).

### Fail-closed behaviour (review repairs)

- A watched location that changed, or a check that could not be completed (any read error while
  snapshotting), fails the step and writes `KIT-STOPPED.txt`; every later client launch refuses.
  Only the reviewer clears it (by replacing the kit). The evaluator's verdict cannot override it.
- Java preferences are compared by every key, value name, kind and data, not by key count.
- Integrity covers every file present (unlisted files fail), and every classpath is the explicit
  list of manifest JARs (no `dir\*` wildcards), so an added JAR is never loaded.
- R2/R3 empty the side-load folder first and fail if the session log shows any side-loaded
  plugin; R4 empties it and places only the hash-checked official JAR.
- Every delete, move and side-load write refuses paths outside the kit and any junction, symbolic
  link or reparse point on the path or inside a folder being deleted (the kit should be
  extracted to an ordinary folder such as `C:\RaccoonPlaytest`).
- Messages state what was checked; there is no unconditional reassurance.
- The R4 log excerpt keeps only side-load, plugin-manager Raccoon and Raccoon logger lines; error
  lines are counted and named by logger, never echoed into receipts.

## 4. Exact commands (what the menu runs)

```
SelfCheck  every manifest file re-hashed; official JAR and dev JAR identities shown
           (-cp is always the explicit list of manifest JARs in the named folders; shown here by folder)
R1         runtime\bin\java.exe -ea -Xmx1024m -Duser.home=<kit>\home -Djava.io.tmpdir=<kit>\tmp
             -Djava.util.prefs.PreferencesFactory=playtest.isolation.KitPreferencesFactory
             -Draccoon.playtest.receipts=<run> -cp "lib\client\*;lib\isolation\*;lib\dev\*"
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

| Receipt | Criteria (paraphrased from the governing handoff, as clarified in review) | Verdict rule |
|---|---|---|
| **R1** | Complete deterministic regression replay; the owner-machine run is the receipt, CI runs are preparation | every suite run, 0 failures, 0 skipped |
| **R2** | Native adapter replay, synthetic fixtures, actual client thread | all adapter suites pass on the client thread, performance passes, no side-loaded plugin |
| **R3** | Genuine lifecycle, bank capture, failed revalidation, Brutus KC | each item below PASS only when genuinely observed, otherwise BLOCKED |
| **R4** | Exact official JAR: two complete alternating cycles (start, stop, start, stop; plugin-list toggles accepted) and a clean client shutdown; no fixtures or launcher classes in the artifact | started = stopped ≥ 2 in order, client exit code 0, side-load line, no plugin errors |
| **I03** | The exact official JAR loads and is disabled in the genuine client, with a clean artifact and classpath | identity + side-load + start + stop + 0 Raccoon classes elsewhere + no errors |

R3 items: shared instances; genuine login; **late enable** (Raccoon off at login, enabled while
logged in, then a fresh identity read); **disable/re-enable** while logged in (a new login,
nothing restored); verified same-login hop (new epoch); **failed revalidation** = a genuine
failed read while the bank is open, after qualification, revoked in the same record and
re-qualified only through a fresh streak (logout does **not** count); **two bank opens** (open,
qualify, close with revocation, reopen, fresh streak, re-qualify); **tab witness recorded per
read** (known/unknown counts; qualification without any known-witness read fails); **two
consenting identities** (two run-local account ordinals) with **no carry-over** of bank or KC
data across any login change; Brutus line type; **two consecutive genuine counts +1 in the same
authenticated epoch adding exactly 1**; account-hash availability recorded as a boolean only
(no hash is ever recorded); native timings. Optional: bank PIN. Genuine-tick timing is an upper
bound: under the limit it is a conservative PASS, over the limit BLOCKED (not attributable to
the plugin without measurement).

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

## 8. Preparation evidence (CI, kit commit `a0ef3e4c`)

Linux job: production tree identical to `aefb1d79`; retained official JAR re-verified (76,270 B,
`5eb3405a…`, source archive 97/97); candidate dev JAR rebuilt byte-identical (75,907 B,
`eebb6ecc…`); 0 playtest/test classes in either production JAR; R4 classpath 50 jars, 0 Raccoon
entries; pinned 1.13.8 facts: `RUNELITE_DIR = user.home/.runelite`, `SIDELOADED_PLUGINS =
RUNELITE_DIR/sideloaded-plugins` loaded only in developer mode (log line `Side-loading plugin {}`),
`EventBus.register(Class, Consumer, float)`, Jagex auth plugin/panel present; empty R3 receipt →
BLOCKED. Windows runner (not owner receipts): kit 491+ files, self-check PASS; R1 PASS (243
tests, 0 skipped); R2 attempt in the genuine client PASS (66 adapter tests on the `Client` thread
at the login screen; event callback p95 ≈0.003–0.005 ms, bank capture p95 ≈1.8–1.9 ms);
launch path PASS (official JAR side-loaded and started); isolation PASS for every step.

## 8a. Jagex sign-in (FR-006 authentication preparation)

Mechanism, source evidence and the Credential Manager / AppData isolation additions:
`docs/jagex-authentication.md`. R3 stays on HOLD until Solis clears that procedure.

## 9. Review decisions (Solis, review of `a0ef3e4c`) and remaining items

Applied: I03 = load and disable; R3 failed revalidation = genuine open-bank failed read after
qualification (not logout); added late enable, disable/re-enable, two consenting identities,
per-read tab witness, two distinct bank opens with a fresh streak, consecutive +1 in one epoch,
hash availability as a boolean only; R4 = two complete cycles plus clean shutdown (toggles
accepted); R1 owner replay preferred (CI is preparation); upper-bound tick rule as above.

Regressions (permanent, red at `de1bedc7`/`be24b4de`, green on the repair): `EvaluateTest` (15:
the confirmed false-PASS paths and genuine-shaped positives, kit CI job `kit-tests`) and
`playtest/tests/Launcher.Tests.ps1` (isolation latch, incomplete check, preference values,
unlisted files and classpaths, residual side-load, reparse points, no unconditional
reassurance; Windows job).

Remaining: owner login HOLD; R3/R4/I03 receipts need the owner. Whether a genuine failed bank
read can be produced on demand is not known; if it is not observed, R3 failed revalidation stays
BLOCKED. The Jagex sign-in path of the developer client is reported from the pinned client in
CI (classes and calls, see the workflow's client facts) and still needs confirmation in the
first owner session. The official packager's network failure on the kit-branch head is
unrelated to the retained candidate JAR, which is not rebuilt.
