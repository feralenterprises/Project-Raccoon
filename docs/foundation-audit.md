# FR-005 — foundation audit, accumulated regressions and performance

**Status: reported for independent QA; not accepted.** FR-002, FR-003 and FR-004 were accepted at
source `41eb2aced6472363f836ffd0aa07e1fb0d0a828b` (evidence head `07969720`). This record restates
the governing FR-005 requirements in our own words; the private handoff is not published.

Evidence classes used below:
- **CI** — automated, on the exact source SHA, routine on every push (`foundation.yml`, two runs).
- **Static** — the artifact verifier on the development and official post-processed JARs.
- **Simulation** — deterministic, through the production wiring (Guice and the real RuneLite
  1.13.8 `EventBus`, client proxied), on a GitHub runner. **Not native RuneLite.**
- **FR-006** — requires the genuine client; not claimed here (BLOCKED until FR-006).

## 1. Regression matrix

Every listed test runs in every build; the verifier's `tests` step rejects a skipped test or a
missing permanent suite (`REQUIRED_SUITES`). Counts are test methods.

| Case | Requirement (paraphrased) | Permanent tests | Defect regressions touching it |
|---|---|---|---|
| A01 | No account authority without a verified identity | `AccountLifecycleTest.a01_*` (6) | `AuditRegressionTest` (LOADING / unknown identity), `EventAuthorityTest` (observed identity loss) |
| A02 | Enable mid-session: identity read fresh, nothing assumed | `a02_*` (3), `AccountLifecycleServiceTest.enabledMidSessionLearnsPhaseThenReadsIdentityFresh` | `AccountLifecycleServiceTest` phase reconciliation (`finding4_*`) |
| A03 | Account changes never carry data across | `a03_*` (4) | `EventAuthorityTest` (A→B→A between ticks, other identity), `ScopeNamespaceTest` (5) |
| A04 | Verified same-login hop/reconnect within limits keeps the login; anything else retires | `a04_*` (15), `AccountLifecycleServiceTest.hopThroughServiceRestoresSameLogin`, `clientTickExpiresSuspension` | `AuditRegressionTest` (15), `LifecycleBoundaryTest` (11), `EventAuthorityTest` (16), `ObservedTransitionTest` (18) |
| A05 | Stale scopes and callbacks never regain authority | `a05_*` (4) | `ConsumerScopeGateTest` (3), `ScopeNamespaceTest`, `LifecycleConcurrencyTest` (1), `SubscriptionCancelTest` (2) |
| A06 | Supported worlds only; membership never assumed | `a06_*` (3), `worldPolicy*` (2), `unreadableWorldIsUnsupported` | `EventAuthorityTest` (observed unsupported world, active and held) |
| B01 | Populated bank qualifies after two consecutive matching ticks | `b01_*` (5: tracker 4, repair 1) | `BankRepairRegressionTest` (19) |
| B02 | Empty bank needs three; unknown witness never qualifies empty | `b02_*` (3) | — |
| B03 | PIN, hidden, missing or unreadable bank never captured | `b03_*` (5), `BankObservationServiceTest.itemWidgetMustBeVisible` | — |
| B04 | Any change, gap or duplicate restarts the streak | `b04_*` (9: tracker 5, repair 4) | `BankRepairRegressionTest` |
| B05 | Filtered views qualify only with a complete container | `b05_*` (2) | — |
| B06 | Placeholders, fillers, noted and malformed data excluded; overflow guarded | `b06_*` (7) | — |
| B07 | Carried items: missing is unreported, malformed invalidates | `b07_*` (6) | — |
| B08 | Identity change during capture revokes at once; no cross-account history | `b08_*` (4) | `BankRepairRegressionTest`, `ScopeNamespaceTest` |
| B09 | Close revokes; history is per login and never becomes current | `b09_*` (5), `BankObservationServiceTest.lifecycleStopRevokesBankAuthority` | `BankSubscriptionTest` (4) |
| B10 | Evaluation failures never publish stale or partial evidence | `b10_*` (4) | `BankRepairRegressionTest` |
| K01 | Genuine-shaped sequence 428 → 429 → 429 → 431 totals +3 | `BrutusKcTrackerTest.k01_*` (2) | — |
| K02 | Player/unrelated chat, other bosses, loot, malformed or overflow: zero | `k02_*` (5), `BrutusKcServiceTest.onlyGameMessageAndSpamAreKcTypes` | — |
| K03 | A regressing count quarantines until a new valid epoch | `k03_*` (1) | — |
| K04 | Duplicates, stale token, wrong account/epoch: no gain, no foreign total | `k04_*` (7) | `BrutusKcEventValidationTest` (8), `EventAuthorityTest` |
| K05 | Verified hop: total masked, then 431 baseline and 432 → +4; otherwise clear | `k05_*` (6) | `ObservedTransitionTest` (18), `EventAuthorityTest` |
| I01 | Exactly one subscription and publisher after two enable/disable cycles | `FoundationIntegrationTest.i01_*` (1) | — (new in FR-005) |
| I02 | Typed event traced to sanitized output; no panel, persistence, network or game mutation | `FoundationIntegrationTest.i02_*` (1) + static checks (§3) | — (new in FR-005) |

Other permanent suites: `RaccoonPluginDescriptorTest` (5), `BankObservationServiceTest` (5 in
all), `BrutusKcServiceTest` (2), `AccountLifecycleServiceTest` (12 in all), `FoundationPerformanceTest`
(3, §4). **Total: 20 suites, 243 tests.**

Count reconciliations made here: `ObservedTransitionTest` has **18** test methods (12 red on
the faulty baseline, 6 guards; an earlier record said 22). K04 has **7** `k04_*` tests (the KC
record said 6); B01 and B04 counts include the `BankRepairRegressionTest` cases.

## 2. Defect regression ledger

Each confirmed defect: red on the faulty baseline in CI, green on the repair, still running.

| # | Gate | Finding | Red (faulty baseline) | Repair | Permanent suite(s) |
|---|---|---|---|---|---|
| 1 | FR-002 | LOADING freshness, unsupported world while authenticating, LOGGING_IN continuity, publication races, phase reconciliation | `1792d0d6` (CI jobs `140201108174`, `140201126359`) | `aedb673d` | `AuditRegressionTest`, `AccountLifecycleServiceTest` |
| 2 | FR-002 | Contradictory identity during suspension; consumer gate authority | `95d2133a` (4 red, 1 guard) | `9e625d23` | `AuditRegressionTest` |
| 3 | FR-003 | Stability contract, single announcement, history masking, generation fencing, identity checks, cadence, bounds | `dd814398` (20 red, 1 guard) | `407c68b8` | `BankRepairRegressionTest`, `BankObservationTrackerTest` |
| 4 | FR-002/003 | Delivery after `cancel()` (CI stress diagnosis `7f76ee5e`) | `LifecycleConcurrencyTest` failed in CI at `407c68b8` and with diagnostics at `7f76ee5e` ("delivery after cancel") | `312e5e8c` | `LifecycleConcurrencyTest` |
| 5 | FR-002/003 | Foreign lifecycle-instance scope collision; bank same-round cancel | `de93b5be` (4 red; jobs `140229919828`, `140229936938`) | `8452cd8c` | `ScopeNamespaceTest`, `BankSubscriptionTest`, `SubscriptionCancelTest` |
| 6 | FR-004 | KC gain after identity / phase / world changed before reconciliation | `4371f279` (7 red) | `0f983cff` | `BrutusKcEventValidationTest` |
| 7 | FR-004 | Contradicted authority left readable; interrupted continuity survived | `20b7e63a` (18 of 219 red) | `3e5cd549` | `EventAuthorityTest`, corrected `BrutusKcEventValidationTest` |
| 8 | FR-004 | Observed transition did not start the continuity deadline; old epoch survived | `a60ed27d` (10 of 234), `83c0c830` (14 of 238) | `41eb2ace` | `ObservedTransitionTest`, corrected `EventAuthorityTest` |

No confirmed defect is open. FR-005 found no new defect in code (see §3); the gaps it closed are
evidence gaps (I01/I02, member-level static check, skipped/missing-suite rejection).

## 3. Architecture and safety audit

| Area | Verdict | Evidence |
|---|---|---|
| Dependency direction | PASS | Static: `core/**` may reference only `java.lang`, `java.util`, `java.time` and itself; adapters only the reviewed RuneLite types (`ALLOWED_EXTERNAL_EXACT`); self-test cases "core -> host/RuneLite rejected" |
| RuneLite surface | PASS | Static (new): every RuneLite member referenced by the artifact must be on `ALLOWED_RUNELITE_MEMBERS`; all are reads (getters, `isHidden`, enum `name()`, two `ChatMessageType` constants). Official JAR evidence `packaged.runeliteMembers` lists 24. Self-test: a `Client.setGameState` call is rejected |
| Single event/identity authority | PASS | One `AccountLifecycle` (constructed once, in the `@Singleton` `AccountLifecycleService`); adapters translate only; chat uses `scopeForEventNow()` → `onEventObservation` (central rules); bank runs on the tick after reconciliation. I01 asserts one instance of each service through Guice |
| Duplicate tracking authorities | PASS | One `BankObservationTracker` and one `BrutusKcTracker`, each constructed once by its `@Singleton` service; slot names are unique and a second registration throws; I01: one lifecycle subscriber, one dispatch per event after two cycles |
| Account isolation | PASS | Scope = (lifecycle namespace, run-local account ordinal, login token, epoch, authority); login/epoch slots cleared on retirement; A03, A05, B08, K04, `ScopeNamespaceTest`, `EventAuthorityTest` |
| Subscription cancellation | PASS | `cancel()` waits for an in-progress delivery and the listener is never called afterwards (lifecycle and bank); `SubscriptionCancelTest`, `BankSubscriptionTest`, `LifecycleConcurrencyTest`; plugin disable leaves no event-bus dispatch (I01) |
| Concurrency and lock order | PASS | Outermost first: lifecycle delivery lock → bank tracker lock → lifecycle state lock (leaf). The state lock never calls out (publication only enqueues); deliveries run outside it. KC takes no lock while calling the observation, then the KC tracker lock → state lock. No reversed path in source; documented in `docs/ui-integration.md` |
| Persistent writes | PASS (none) | Static: `java/io`, `java/nio`, `java/sql`, RuneLite `client/config` denied; no `ConfigManager`; account data lives only in lifecycle slots in memory and is cleared on retirement and disable (I02) |
| Network requests | PASS (none) | Static: `java/net`, `javax/net`, `okhttp3` denied; no runtime dependency (`source` check) |
| Game mutation | PASS (none) | Static member allowlist (reads only); I02 runtime: every client call recorded is one of 8 reviewed reads, events unmodified |
| Panel / UI | PASS (none) | Static: `javax/swing`, `java/awt`, RuneLite `client/ui` denied; no overlay or panel; UI binding guard belongs to the UI workstream |
| Personal account data | PASS | The account hash is held privately for comparison and never exposed; snapshots and scopes print only run-local ordinals; I02 checks every published surface for the hash (decimal and hex) and raw chat; logging is two fixed strings |
| Package size | PASS | Recorded per handback (§5); foundation budget 262,144 bytes |

## 4. Performance (deterministic simulation)

`FoundationPerformanceTest`, through Guice and the real RuneLite 1.13.8 `EventBus`, client
proxied. Each sample is one `EventBus.post`, timed with `System.nanoTime` (dispatch plus every
plugin callback for that event); warm-up discarded; nearest-rank percentiles. Report:
`build/perf/fr005-performance.txt`, annotated by CI and uploaded with the foundation evidence.

| Measurement | Samples (warm-up) | Workload | Requirement |
|---|---|---|---|
| Event callback replay | 1,000 (3,000) | fixed cycle: Brutus KC line, other boss line, player chat, spam, game tick (bank closed), client tick | p95 ≤ 2 ms |
| Bank-open capture | 200 (50), at 1,000 slots and at the 4,096-slot tracker bound | one game tick each, exactly one capture each (checked): lifecycle tick, visibility, identity before/after, bank + 28 inventory + 14 equipment reads, a definition per slot, tab varbits, evaluation, publication | p95 ≤ 10 ms |

Results, machine, environment and toolchain for the exact source SHA: `docs/release-evidence.md`
(FR-005). These are **simulation** numbers; the genuine client's object costs and threading are
not represented. **Native performance: BLOCKED until FR-006.**

## 5. Carried to FR-006 (not claimed)

- I03 and the four distinct verification receipts on the same final source SHA.
- Genuine live events: the native Brutus line and its message type, real game-state sequences
  (hop, reconnect, logout), real bank open with PIN.
- Native performance measurements.
- Loading the exact official JAR in the real client (plugin manager, event bus, injector).
