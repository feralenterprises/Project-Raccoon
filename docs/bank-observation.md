# Bank observation (FR-003) — design record

Read-only evidence about what the active account holds in its bank. Written **before**
implementation, as required by the FR-003 authorization. Requirements source: owner-provided R03
contract for FR-003 (B01–B10), restated here in implementation terms.

## Relation to Burrow (`BankCaptureGate`, `BankScanTracker`)

**Burrow source was not accessible in this session.** No Burrow code was read, ported or compared.
This design is written from the R03 requirements alone, and **no migration parity with Burrow is
claimed**. Where Burrow's behaviour is unknown, the choice below is the conservative reading of R03
and is marked *provisional* so it can be reconciled when the donor is available.

| Area | This design | Relation to Burrow |
|---|---|---|
| Gate (`BankCaptureGate`) | `BankObservationTracker.onTick` decides whether to capture: publishable account scope, bank visible, no PIN, new tick, retry budget left | Unknown; not compared |
| Tracker (`BankScanTracker`) | Streak of matching valid observations on consecutive tick indices under one scope authority | Unknown; not compared |
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

A captured frame is **valid** when:

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
  same scope (including authority), with the same known witness and identical bank contents.
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
| B01 | `b01_*` (4): two consecutive matching populated ticks, unknown witness degraded only, negative witness count, witness becoming known |
| B02 | `b02_*` (3): three for an empty bank, unknown witness never, placeholder-only bank is empty |
| B03 | `b03_*` (5): PIN (no capture), hidden bank, missing container, read exception, unreadable container/visibility |
| B04 | `b04_*` (5): changed contents, changed witness, skipped tick, duplicate tick (no second capture), LOADING revalidation needs fresh streak |
| B05 | `b05_*` (2): filtered view qualifies with a complete container; witness exceeding slots never qualifies |
| B06 | `b06_*` (7): placeholders/filler/noted excluded; malformed quantities and slots; unreadable definition; per-item, combined and witness overflow |
| B07 | `b07_*` (6): missing carried = unreported; combined claims; unreadable or malformed carried invalidates; noted carried blocks combined; carried changes keep the streak |
| B08 | `b08_*` (4): identity change and hop during capture, immediate revocation without a tick, no cross-account history |
| B09 | `b09_*` (5): close revokes and keeps same-login history; no capture while closed; reopen needs fresh streak; history cleared on retirement; history survives a same-login hop but never becomes current |
| B10 | `b10_*` (4): evaluation failure after populated acceptance; pre-read failure and evaluation failure after empty acceptance; stale result |
| Limits | retry bound, no capture while not publishable, failure budget reset, ordered snapshots, no hash in diagnostics; adapter wiring and item classification |

Mutation check (local): 13 deliberate rule removals (streak lengths, consecutive ticks, post-capture
scope check, unknown witness, scope-change revocation, retry bound, capture while closed, duplicate
tick, combined overflow, missing = empty, witness slot check, noted counted) are each caught.

## Remaining limitations

- Burrow not accessible: no parity; noted-variant policy and retry bound provisional.
- RuneLite reads are compile-verified against the pinned API and covered by fakes; behaviour in a
  real client (widget visibility during PIN, container timing, Bank Tags layouts) needs FR-006.
- Carried items are evidence for the observed tick only; they do not take part in streak matching.
