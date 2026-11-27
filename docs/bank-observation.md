# Bank observation (FR-003) — design record

Read-only evidence about what the active account holds in its bank. Written **before**
implementation, as required by the FR-003 authorization. Requirements source: owner-provided R03
contract for FR-003 (B01–B10), restated here in implementation terms.

## Donor reconciliation (FR-003 repair, 2026-11-26)

The coordinator made the pinned donor available. Read-only; nothing in Burrow was modified, and no
donor code is copied (Burrow uses Java 16+ records and deprecated `WidgetInfo`/`Varbits` APIs).

| Donor file @ `eb962df3962eb56b258ee065e50c9ee1d457e479` | Git blob | SHA-256 |
|---|---|---|
| `Project-Burrow/.../core/BankCaptureGate.java` | `77e14b27` | `7f1c5712abc33f4befbe11d1a09825a7eb1a6f8f59b6ec2d2afc5cd34b553726` |
| `Project-Burrow/.../core/BankScanTracker.java` | `018605f9` | `eb60d7e232e3ea96ef3dfe88b75e0784fb1fa9742cdadba4748da1dfdddd9049` |
| `Project-Burrow/.../runelite/AutomaticBankSnapshotReader.java` | `b7639912` | `d9417b42a3a353534efc0a63ed0f7b92a969c12ca1b1b44d8acaef8f9f5b3c5f` |

| Behaviour | Donor (verified) | Raccoon after repair | Decision |
|---|---|---|---|
| Stability witness | `Read.sameContent`: account, raw bank slot count, tab sum, both carried reporting flags, bank, inventory and equipment maps | Same fields, plus the full raw bank slot sequence (id, quantity, kind), exact per-tab counts and full scope authority | **Adopt donor fields; keep stricter R03 extras.** A changed valid sample restarts the streak at 1 |
| Streak lengths | 2 populated, 3 empty | 2 / 3 | Same |
| Unknown tab witness | Populated accepted; empty needs a witness | Populated → `DEGRADED_POPULATED` only | **Keep stricter R03 B01** |
| Container bounds | BANK ≤ 4096, each carried ≤ 40, else `CONTAINER_TOO_LARGE` | Same limits, `CONTAINER_TOO_LARGE` | **Adopt** |
| Before/after identity | `getAccountHash()` and `GameState` read before and after the container reads; must be equal and `LOGGED_IN` | Frame carries before/after identity readings and phases; the tracker requires both `LOGGED_IN` and asks the **central lifecycle** (`confirms(scope, reading)`) that both readings are the current session's account. The hash never leaves the lifecycle | **Adopt, via lifecycle authority** (no second account authority) |
| Item widget | Root visible **and** items widget visible, PIN hidden (`WIDGET_NOT_READY` otherwise) | `Bankmain.UNIVERSE` and `Bankmain.ITEMS` both visible, PIN hidden; items hidden → `ITEMS_NOT_READY` (no capture, current revoked) | **Adopt** |
| Definition identity | `def != null && def.getId() == itemId` | Same; mismatch → `UNREADABLE` | **Adopt** |
| Noted classification | `getNote() != -1` (bank only; carried noted counted) | `getNote() == 799` noted; any other non −1 value fails closed; noted excluded from bank **and** carried; noted carried blocks combined claims | **Keep stricter R03 B06**; donor "list" = `getNote() != -1` |
| Zero/negative quantities, id ≤ 0 | Silently skipped | Malformed → frame rejected; id 0 is a real item | **Keep stricter R03 B06** |
| Retry cadence | `RETRY_TICKS = 3` after a failed read, `RESAMPLE_TICKS = 1` after a changing sample; unbounded | After a failed capture the next attempt waits 3 ticks; a changing sample is re-read next tick; at most 5 consecutive failures per open-bank session | **Adopt donor cadence; keep R03 bound** |
| Accepted announcement | `ACCEPT` once, then `ACCEPTED` phase | One qualification announcement; unchanged revalidation does not republish | **Adopt** |
| Last good / history | Cleared on scope/account rebind | History in a lifecycle login slot; published status recomputed on **every** lifecycle change | **Adopt intent; enforced centrally** |
| Fencing | Scope token + account | Scope authority **plus** bank evidence generation: `isCurrent(evidence)` / `fenced(evidence, work)` | **Stricter**: account scope alone cannot fence bank results |

### Previous note (superseded)

At the first FR-003 handback Burrow source was **not accessible**; that design was written from R03 alone.
This design is written from the R03 requirements alone, and **no migration parity with Burrow is
claimed**. Where Burrow's behaviour is unknown, the choice below is the conservative reading of R03
and is marked *provisional* so it can be reconciled when the donor is available.

| Area | This design | Relation to Burrow |
|---|---|---|
| Gate (`BankCaptureGate`) | `BankObservationTracker.onTick` decides whether to capture: publishable account scope, bank visible, no PIN, new tick, retry budget left | Reconciled above |
| Tracker (`BankScanTracker`) | Streak of matching valid observations on consecutive tick indices under one scope authority | Reconciled above |
| Account state | Uses the FR-002 lifecycle only: scope authority, `AccountSlot`, fencing. No private account copy (SR-1) | Intentional: Burrow's own account handling is not reused |
| Noted variants (B06) | **Provisional:** every item whose definition is noted (`getNote() == 799`) is excluded; R03 refers to a donor-defined list that was not supplied | Possible difference: Burrow's list may be narrower or wider |
| Retry bound | **Provisional:** at most 5 consecutive failed capture attempts per open-bank session, one per tick | Unknown |
| Carried items | Reported separately; they do not take part in streak matching | Unknown |
| Persistence | None: evidence and history live only in memory for the current login | Intentional (FR-003 authorization forbids persistence) |

## Statuses

| Status | Meaning | Ownership claims |
|---|---|---|
| `QUALIFIED_CURRENT` | Qualifying streak reached under the current scope authority, bank still open and still validating | **Yes**: positive and negative, for the bank; combined with carried items only where those are reported |
| `DEGRADED_POPULATED` | Valid populated observation(s), but the tab witness is unknown | **None** |
| `WAITING` | Waiting or unknown: nothing qualified now and no same-login history | **None** |
| `HISTORICAL` | Nothing qualified now; the last qualified evidence of **this login** is available as provenance | **None** (provenance only) |

## Qualified observation (input checks)

A capture is attempted only when all of these hold: the lifecycle snapshot is publishable (fresh
identity, current scope authority), the bank interface is visible, the PIN keypad is not visible,
this tick index has not been seen, and the retry budget for this open-bank session is not
exhausted. At most one attempt per tick. Nothing is read while the bank is closed.

The bank is open only when `Bankmain.UNIVERSE` and its item widget `Bankmain.ITEMS` are visible
and the PIN keypad is not; root visible but items hidden is `ITEMS_NOT_READY` (no capture, current
authority revoked). A captured frame is **valid** when:

- the BANK container has at most 4096 raw slots and each carried container at most 40 (donor bounds);
- every item definition describes the slot's own id (`def.getId() == id`);
- the `BANK` container (`InventoryID.BANK = 95`) is present and readable;
- every slot is well formed: an empty slot is `id = -1, quantity = 0`; a real item has
  `id >= 0`, a readable definition and `quantity >= 1`; placeholders and filler have
  `quantity >= 0`; anything else is malformed;
- placeholders (`getPlaceholderTemplateId() != -1`), bank filler (`ItemID.BANK_FILLER`) and noted
  variants are excluded from contents;
- per-item totals, and combined bank + carried totals, are summed with overflow checks; overflow
  invalidates the frame;
- the tab witness (`VarbitID.BANK_TAB_1..9`), when known, has only nonnegative counts and its sum
  does not exceed the raw slot count of the `BANK` container; a known witness that exceeds the
  slots means the container is not complete, and the frame is invalid;
- carried containers (`INV = 93`, `WORN = 94`): missing → *unreported* (never "empty"); present but
  unreadable or malformed → the whole frame is invalid;
- the scope the frame was captured under is still current when it is evaluated.

The bank view (search filters, Bank Tags layouts) is never read for contents: only the underlying
`BANK` container counts, so a filtered view qualifies exactly when that container passes the checks
above (B05).

## Streak and authority

- Two observations *match* when they are on consecutive tick indices (`t` then `t + 1`), under the
  same scope (including authority), with the same exact witness, the identical raw BANK slot
  sequence (id, quantity, kind), identical bank contents, and identical inventory and equipment
  evidence including whether each was reported. A changed valid sample starts a fresh streak at 1.
- Qualification is announced once: unchanged revalidation keeps the same `BankEvidence` and
  publishes nothing; any failure or change revokes it at once. `BankEvidence.lastTick()` is the
  tick on which the streak **qualified**, not the latest successful revalidation.
- Bank listeners use cancellation-aware registrations: a listener cancelled earlier in the same
  round is skipped, self-cancellation is immediate, and `cancel()` from another thread waits for
  the round in progress (`BankSubscriptionTest`).
- Every qualification has a new evidence **generation**. Bank results are fenced by
  `BankObservationView.isCurrent(evidence)` / `fenced(evidence, work)`: false after the bank closes,
  a failed revalidation, a changed sample or any scope change, even when the account scope alone is
  still current.
- A capture is accepted only if the client phase was `LOGGED_IN` and the account identity read
  before **and** after the container reads is confirmed by the lifecycle (`confirms(scope, reading)`)
  as the current session's account. Otherwise `IDENTITY_CHANGED`.
- After a failed capture the next attempt waits `RETRY_TICKS = 3` ticks (donor cadence); a changing
  valid sample is re-read on the next tick.
- The published history follows every lifecycle change: masked while not publishable, gone after
  stop, logout, retirement or an account change, even when the bank was already closed.
- Populated bank: 2 matching valid observations with a known witness → `QUALIFIED_CURRENT`.
- Empty bank: 3 matching valid observations with a known witness → `QUALIFIED_CURRENT`.
- Unknown witness: a populated valid observation gives `DEGRADED_POPULATED` only, never authority;
  an empty one gives nothing.
- While `QUALIFIED_CURRENT`, every tick must revalidate; the first failed or non-matching tick
  revokes current authority immediately, and recovery needs a fresh full streak.
- Revocation triggers: PIN, bank hidden or closed, missing container, read exception, invalid
  frame, changed contents or witness, a different scope or authority (including after `LOADING`
  revalidation, an epoch change or retirement), a skipped tick, a repeated tick index, or a capture
  whose scope stopped being current.
- History: the last qualified evidence is stored in a lifecycle **login slot**, so it is masked
  while not publishable and cleared on retirement (no cross-account history). It never becomes
  current again; reopening the bank needs a fresh streak.

## Implementation

| Part | Location |
|---|---|
| Pure core (no RuneLite types) | `core/bank`: `BankObservationTracker` (gate, streak, authority), `BankEvaluator` (frame checks), value types |
| RuneLite reads (read-only, client thread) | `bank/RuneLiteBankCaptureSource`: `getWidget(InterfaceID.BankpinKeypad.UNIVERSE / Bankmain.UNIVERSE)`, `getItemContainer(InventoryID.BANK / INV / WORN)`, `getItemDefinition`, `getVarbitValue(VarbitID.BANK_TAB_1..9)` |
| Wiring | `bank/BankObservationService`; `RaccoonPlugin.onGameTick` runs the lifecycle tick, then the bank tick; tick index = `client.getTickCount()` |
| History | lifecycle login slot `bank.history` |

All RuneLite names were checked against the pinned 1.13.8 source (`8397ec71`): `InventoryID.BANK = 95`,
`INV = 93`, `WORN = 94`; `VarbitID.BANK_TAB_1..9 = 4171..4179`; `ItemID.BANK_FILLER = 20594`;
`ItemComposition.getNote()` 799 = noted, `getPlaceholderTemplateId()` 14401 = placeholder.

## Acceptance cases → tests (`core/bank/BankObservationTrackerTest`, `bank/BankObservationServiceTest`)

| Case | Tests |
|---|---|
| B01 | `b01_*` (4 here + 1 in `BankRepairRegressionTest`): two consecutive matching populated ticks, unknown witness degraded only, negative witness count, witness becoming known |
| B02 | `b02_*` (3): three for an empty bank, unknown witness never, placeholder-only bank is empty |
| B03 | `b03_*` (5): PIN (no capture), hidden bank, missing container, read exception, unreadable container/visibility |
| B04 | `b04_*` (5 here + 4 in `BankRepairRegressionTest`): changed contents, changed witness, skipped tick, duplicate tick (no second capture), LOADING revalidation needs fresh streak |
| B05 | `b05_*` (2): filtered view qualifies with a complete container; witness exceeding slots never qualifies |
| B06 | `b06_*` (7): placeholders/filler/noted excluded; malformed quantities and slots; unreadable definition; per-item, combined and witness overflow |
| B07 | `b07_*` (6): missing carried = unreported; combined claims; unreadable or malformed carried invalidates; noted carried blocks combined; carried changes keep the streak |
| B08 | `b08_*` (4): identity change and hop during capture, immediate revocation without a tick, no cross-account history |
| B09 | `b09_*` (5): close revokes and keeps same-login history; no capture while closed; reopen needs fresh streak; history cleared on retirement; history survives a same-login hop but never becomes current |
| B10 | `b10_*` (4): evaluation failure after populated acceptance; pre-read failure and evaluation failure after empty acceptance; stale result |
| Limits | retry bound, no capture while not publishable, failure budget reset, ordered snapshots, no hash in diagnostics; adapter wiring and item classification |
| Review repair 2026-11-26 | `BankRepairRegressionTest` (21: 20 reproduced the findings at `dd814398`, 1 guard): raw slots, carried maps and flags, equipment, single announcement, history after stop/logout/account change/non-publishable, generation fencing, before/after identity and phase, bounds, cadence; adapter: item widget, definition identity |

Mutation check (local): 13 deliberate rule removals (streak lengths, consecutive ticks, post-capture
scope check, unknown witness, scope-change revocation, retry bound, capture while closed, duplicate
tick, combined overflow, missing = empty, witness slot check, noted counted) are each caught.

## Remaining limitations

- Donor reviewed from three files only (see reconciliation table); full parity not claimed. The
  R03 retry bound (5) and stricter noted handling remain Raccoon choices.
- RuneLite reads are compile-verified against the pinned API and covered by fakes; behaviour in a
  real client (widget visibility during PIN, container timing, Bank Tags layouts) needs FR-006.
- Carried items are evidence for the observed tick only; they do not take part in streak matching.
