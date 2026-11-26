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

## Acceptance cases → tests

Filled in with the implementation (`core/bank/BankObservationTrackerTest`, adapter tests).
