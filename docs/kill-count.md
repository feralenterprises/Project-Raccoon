# Brutus kill count (FR-004) — design record

Read-only observation of the Brutus kill count from typed game chat. Written **before**
implementation. Requirements: owner-provided R03 FR-004 contract (K01–K05), restated here in
implementation terms; the private R03 document is not published.

## Verified inputs (pinned RuneLite 1.13.8, `runelite-parent-1.13.8` = `8397ec71`)

| Fact | Source |
|---|---|
| `ChatMessageType.GAMEMESSAGE(0)` "A normal game message"; `SPAM(105)` "A message that was filtered" | `runelite-api/.../ChatMessageType.java` |
| RuneLite's own KC reader accepts `GAMEMESSAGE` and `SPAM` (also `TRADE`, `FRIENDSCHATNOTIFICATION`, which Raccoon does **not** accept) | `ChatCommandsPlugin.onChatMessage` |
| Server format: `Your <boss> kill count is: <col=hex>N</col>` (count colour-tagged, commas allowed, optional trailing period) | `ChatCommandsPlugin.KILLCOUNT_PATTERN` |
| Brutus is a boss in this client (`HiscoreSkill.BRUTUS`, boss timer `NpcID.COWBOSS`); a distinct "Demonic Brutus" NPC exists | `HiscoreSkill.java`, `bosstimer/Boss.java`, `gameval/NpcID.java` |
| `ChatMessage.getType()`, `getMessage()` | `runelite-api/.../events/ChatMessage.java` |

Accepted line, after removing only `<col=hex>` / `</col>` tags: exactly
`Your Brutus kill count is: N` or `… is: N.` (one optional space after the colon), where `N` is
digits or comma-grouped digits (`1,234`), at most `Integer.MAX_VALUE`. Case-sensitive; anchored;
no other boss, prefix (`completion count for`, `subdued`), or suffix is accepted.

## Contract

| Rule | Implementation |
|---|---|
| Brutus-only typed game-chat observer | `BrutusKcParser`: types GAME and SPAM only; anything else (player chat, trade, clan, notifications, loot, NPC death) is not KC |
| One authoritative KC service on the central lifecycle | `BrutusKcTracker` registers lifecycle slots; adapter `kc/BrutusKcService` only forwards chat |
| First absolute count per epoch is the baseline (zero delta) | Last count kept in a lifecycle **epoch** slot together with the scope authority it was read under; missing, or from an older authority → baseline |
| Verified increases contribute their delta only | `delta = count − last`, total in a lifecycle **login** slot, `Math.addExact` |
| Regression quarantines until a new valid epoch (K03) | Quarantine flag in an epoch slot; cleared only by a new lifecycle epoch (restore or new login) |
| Stale token / wrong account / wrong epoch (K04) | Every observation carries the scope captured at the chat event; rejected unless `isCurrent(scope)` (namespace, login, epoch, authority) |
| Same-login hop (K05) | Total survives in the login slot, masked while suspended; new epoch → baseline; unverified/expired/retired hop, logout or disable → lifecycle clears the login slot |

## Donor reconciliation (Burrow `eb962df3962eb56b258ee065e50c9ee1d457e479`, read-only)

| Donor file | Git blob | SHA-256 |
|---|---|---|
| `Project-Burrow/.../core/BossKcDeltaTracker.java` | `3933b213` | `b7e74b3a0ab3902753a3a74f3d96b603a78a20fb4160085d146b50915c03dd39` |
| `Project-Burrow/.../runelite/RuneLiteBossKcAdapter.java` | `acad726f` | `e0ba7b258116d01b8c7dd729bb11de8e17face43f0f454916cff5a2a30014b3a` |

| Behaviour | Donor (verified) | Raccoon | Decision |
|---|---|---|---|
| Chat types | `GAMEMESSAGE` or `SPAM` | Same | Adopt |
| Tags | Strips `<col…>` only; other markup unrecognised | Same | Adopt |
| Pattern | Case-insensitive, any catalog boss, `kill count`/`chest count`, `is:` or `is ` | `Your Brutus kill count is: N` exactly, case-sensitive, `is:` required | **Stricter** (Brutus-only, no catalog import) |
| Length bound | ≤ 256 characters | Same | Adopt |
| Count | Long; comma grouping | Int range; comma grouping; out of range → rejected | Stricter (game counts are ints) |
| Baseline / delta / quarantine | First reliable KC per boss per epoch = baseline; decrease → quarantine until new epoch; `addExact` before mutation | Same rules, on lifecycle slots | Adopt |
| Epoch on region `LOADING` | Adapter opens a new epoch after every `LOGGED_IN` following `LOADING` ("may unnecessarily rebaseline") | Lifecycle keeps the epoch on region change but renews scope authority; the KC baseline is tied to authority, so it re-baselines after `LOADING` too. Quarantine is tied to the **lifecycle epoch**, so it does **not** lift on a region change | Adopt rebaseline; quarantine stricter |
| Replay protection | Per-sample sequence numbers inside one adapter | Synchronous client-thread observation; absolute counts make a repeated line a zero delta; stale scopes rejected centrally | Equivalent outcome; no second sequence authority |
| Identity / login | Adapter keeps its own account string, session and epoch generations | Central lifecycle scope (namespace, login token, epoch, authority) only | **Intentional:** no second account authority (SR-1) |
| Rejected outcomes | Never return totals for rejected samples | Totals readable only through lifecycle-masked slots | Same intent |
| Reliability UNKNOWN / UNRELIABLE inputs | Supported | Not needed: only parsed lines reach the tracker | Not adopted |

Full donor parity is not claimed: two files reviewed, no native comparison.

## Implementation

| Part | Location |
|---|---|
| Pure core | `core/kc`: `BrutusKcParser`, `BrutusKcTracker` (implements `BrutusKcView`), `ChatKind`, `KcResult` |
| RuneLite adapter | `kc/BrutusKcService`: `ChatMessageType.GAMEMESSAGE` → GAME, `SPAM` → SPAM, all else OTHER; uses `AccountLifecycleService.scopeForEventNow()`: the live state, world and identity are first given to the central lifecycle (`onEventObservation`, see `docs/account-lifecycle.md`), which retires or masks a contradicted login at once; the line counts only if the scope is still current, the live state `LOGGED_IN`, the world supported and the identity confirmed, otherwise `REJECTED_SCOPE` |
| Wiring | `RaccoonPlugin.onChatMessage(ChatMessage)`; checker allows exactly `ChatMessageType` and `events.ChatMessage` |
| State | lifecycle login slot `kc.brutus.total`; epoch slots `kc.brutus.last` (count + authority), `kc.brutus.quarantined` |

## Acceptance cases → tests (`core/kc/BrutusKcTrackerTest`, `kc/BrutusKcServiceTest`)

| Case | Tests |
|---|---|
| K01 | `k01_genuineSequenceAddsThree` (428 → 429 → 429 → 431: baseline, +1, 0, +2 = 3), `k01_spamTypeAndPlainOrGroupedCountsAreAccepted` |
| K02 | `k02_*` (5): player/unrelated chat, other bosses (Demonic Brutus, Vorkath, completion/harvest variants), loot-only, malformed and out-of-range counts, total overflow quarantines |
| K03 | `k03_regressionQuarantinesUntilNewEpoch` (stays quarantined over a region change; lifted by a verified hop) |
| K04 | `k04_*` (7): duplicate sequence, repeated line, stale token (pre-LOADING authority), wrong epoch, wrong account (missed logout), foreign lifecycle scope, no scope while suspended |
| K05 | `k05_*` (6): verified hop keeps 3, masked while suspended, 431 baseline, 432 → 4; unverified, expired, retired hop, logout and disable clear, next observation baseline zero |
| Review 2026-11-26 | `BrutusKcEventValidationTest` (8; 7 reproduced at `4371f279`): identity changed, LOGIN_SCREEN, unsupported world, unknown / failed identity read, unreadable game state before reconciliation; rejected line never becomes a baseline; ordinary operation. Corrected at the re-review: the old login's total is not readable after a confirmed contradiction |
| Re-review 2026-11-26 (2) | `ObservedTransitionTest` (18 test methods; 12 reproduced at `a60ed27d` / `83c0c830`), HOPPING and CONNECTION_LOST: seen only at chat, restoration in a new epoch, expiry after 121 s, event past the deadline, delayed phase event (past and within the limit), duplicate observations and events, re-observed after LOADING; ordinary hop and reconnect. `EventAuthorityTest` corrected: an observed hop suspends |
| Re-review 2026-11-26 | `EventAuthorityTest` (16; 11 reproduced at `20b7e63a` with 7 corrected tests): immediate retirement on identity / logout / world / identity loss; A→B→A between ticks; LOADING and HOPPING masks without a fabricated hop; logout, LOGGING_IN, unsupported world or other identity during suspension end continuity; guards: verified hop and reconnect, unknown read with a witnessed hop, no restoration or activation from an observation |
| Other | region change rebaselines without bridging; revision only on observable events; adapter type mapping over every `ChatMessageType`; service scope capture |

Mutation check (local): 11 rule removals; 10 caught. The survivor (dropping the tracker's own
`isCurrent` check) is equivalent: every lifecycle slot read and write re-checks the scope, so a
stale or foreign scope still yields `REJECTED_SCOPE` with no state change.

## Limitations

- Message format and types verified against pinned RuneLite source only; a real Brutus kill line
  in a native client is FR-006 (R3).
- RuneLite chat events are not proof of server origin; no plugin or test may synthesise them in
  production.
