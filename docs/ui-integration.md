# Integration guide: account lifecycle for the UI workstream and simulator

For the parallel universal-UI workstream. This describes what the account lifecycle (FR-002)
offers and what any consumer must do. It does not assume anything about individual panels, and
FR-002 contains no UI.

## Interfaces (package `com.feralenterprises.raccoon.core.account`)

| Type | Use |
|---|---|
| `AccountLifecycleView` | Read side. `snapshot()`, `subscribe(listener)`, `isCurrent(scope)`, `fenced(scope, work)`. |
| `AccountSnapshot` | Immutable; safe on any thread. `state()`, `isPublishable()`, `isAwaitingFreshIdentity()`, `scope()`, `membership()`, `lastRetireReason()`, `revision()`. |
| `AccountLifecycleListener` | Called with every snapshot, in order. Gets the current snapshot immediately on subscribe. |
| `Subscription` | Returned by `subscribe`; `cancel()` / `close()`. |
| `AccountScope` | Opaque (account id, login token, epoch, authority). Authority for writes/callbacks; `sameEpoch` / `sameLogin` tell whether data continuity holds across a renewed scope. |
| `AccountDataRegistry` / `AccountSlot<T>` | Central account data: `loginSlot`, `epochSlot`; `get()` masked unless publishable, `get(scope)` only for the scope you hold, `set(scope, value)` only from the current scope. Each call is atomic. |
| `EpochBaseline` | Counter deltas; the first value in an epoch is 0. |
| `ConsumerScopeGate` | `new ConsumerScopeGate(view)`; confine one per consumer thread (e.g. the EDT): `onSnapshot` (ignores older revisions), `accepts(scope)` before applying any value. `accepts` is authoritative: it also asks the lifecycle, at application time, whether the scope is still current. |

In the plugin, obtain them by injecting `com.feralenterprises.raccoon.account.AccountLifecycleService`
and calling `view()` (read side) or `data()` (slot registry). Only that service drives
transitions.

## Rules for every consumer (panels, overlays, simulator views)

1. **Render account data only when `snapshot.isPublishable()`.** On any other snapshot, show the
   neutral state and drop every account-derived value you hold. This includes `ACTIVE` snapshots
   with `isAwaitingFreshIdentity()` (for example during `LOADING`); they are not publishable.
2. **Never keep authority across scopes.** When `snapshot.scope()` changes, re-tag everything with
   the new scope; values tagged with the old one are rejected. If `old.sameEpoch(new)` (a region
   change), the data itself is still valid and can be re-read under the new scope; otherwise
   discard caches. Use `sameLogin` only for presentation (for example "reconnected").
3. **Fence asynchronous work.** Wrap anything that completes later (images, timers, background
   computation) with `view.fenced(scope, work)`, or check `isCurrent(scope)` before applying it.
4. **Thread hand-off is the consumer's job; use `ConsumerScopeGate`.** Listeners run on the
   driving thread under the delivery lock. In the listener, only enqueue the snapshot to your
   thread (e.g. `SwingUtilities.invokeLater`); never block, wait on another thread, or do heavy
   work there. On your thread call `gate.onSnapshot(s)`; if it returns true, re-render (clearing
   everything when not publishable).
5. **Tag and gate every value.** Read slot values with `get(scope)` for the scope you hold, pass
   immutable copies tagged with that scope, and apply them on your thread only if
   `gate.accepts(scope)`. The check consults the lifecycle at that moment, so a value is rejected
   even if the retirement snapshot is still queued for your thread.
6. **Never display or log account hashes.** None of these types expose one. `accountId()` is a
   per-run ordinal, not an identity to show or persist.
7. **Treat `Membership.UNKNOWN` as unknown,** never as members.
8. **No persistence, no transitions.** Consumers must not store account state or call lifecycle
   inputs; only the adapter does.
9. **Make no assumptions about the retirement reason** other than for wording; every
   non-publishable snapshot means "show no account data".
10. **Subscriptions:** a new subscriber immediately receives the latest snapshot; revisions are
    strictly increasing per subscriber; nothing arrives after `cancel()`. Subscriptions survive
    plugin stop/start, so cancel them when the consumer is disposed.

## Bank observation (FR-003, package `com.feralenterprises.raccoon.core.bank`)

| Type | Use |
|---|---|
| `BankObservationView` | `snapshot()`, `subscribe(listener)`, `bankOwnership(itemId)`, `combinedOwnership(itemId)`, `isCurrent(evidence)`, `fenced(evidence, work)`. Obtain via `BankObservationService.view()`. |
| `BankSnapshot` | Immutable: `status()`, `reason()`, `current()` (only `QUALIFIED_CURRENT`), `historical()` (only `HISTORICAL`), `streak()`, `revision()`. |
| `BankEvidence` | Contents (item id → quantity), `inventory()` / `equipment()` (`CarriedEvidence`, possibly unreported), `firstTick()` / `lastTick()` (**qualification** tick, not the latest revalidation; never present it as "last seen"), `generation()`, `scope()`. |
| `Ownership` | `OWNED` (with quantity), `NOT_OWNED`, `UNKNOWN`. |

Rules:

1. **Ownership comes only from `bankOwnership` / `combinedOwnership`.** They are authoritative
   (they check the lifecycle at call time). Any status other than `QUALIFIED_CURRENT` answers
   `UNKNOWN`; never infer "not owned" from `DEGRADED_POPULATED`, `WAITING` or `HISTORICAL`.
2. **`HISTORICAL` is provenance only:** show it as "last seen at tick N" if at all, never as current.
3. **Unreported is not empty:** `CarriedEvidence.isReported() == false` means unknown.
4. **Fence on the evidence, not only the account scope:** any value derived from bank evidence is
   applied only if `bank.isCurrent(evidence)` holds at that moment (and, on your own thread,
   `ConsumerScopeGate.accepts(evidence.scope())`). After the bank closes or a revalidation fails the
   account scope can still be current while the evidence is not.
7. **Qualification is announced once.** While the same evidence stays current no new snapshot is
   published; do not poll for "freshness". A new `generation()` means new evidence.
8. **History follows the account.** A published `HISTORICAL` snapshot is withdrawn as soon as the
   account stops being publishable, logs out, retires or changes, even if the bank was already closed.
5. Bank listeners run on the client thread under the tracker lock: enqueue only, never block.
6. Call `bankOwnership` on the client thread or accept that it reflects the moment of the call.

## Brutus kill count (FR-004, package `com.feralenterprises.raccoon.core.kc`)

`BrutusKcView` (from `BrutusKcService.view()`): `currentLoginTotal()`, `currentLoginTotal(scope)`,
`isQuarantined()`, `revision()`.

- The total is "kills gained this login", not the absolute kill count. It is empty unless the
  account is publishable; show nothing (not 0) when empty.
- Read on the client thread with `currentLoginTotal(scope)` for the scope you hold, hand an
  immutable copy to the UI thread tagged with that scope, and apply it only under the account gate
  **and** your binding generation (see below).
- `revision()` changes on baseline, gain and quarantine; there is no listener in FR-004.
- `isQuarantined()` means "KC observation paused until the next hop or login", not an error.

## Lock order and safe disposal

Locks, outermost first: lifecycle **delivery** lock → bank **tracker** lock → lifecycle **state**
lock (a leaf). Lifecycle listeners run under the delivery lock; bank listeners run under the
tracker lock.

- **Never acquire an outer lock while holding an inner one.** From inside a bank listener (or a
  `BankCaptureSource`), do not drive lifecycle inputs, subscribe to the lifecycle, or cancel a
  lifecycle subscription: each takes the delivery lock.
- `cancel()` on either kind of subscription waits for a delivery round in progress and, once it
  returns, the listener is not running and is never called again. Cancelling from inside the same
  kind of listener is re-entrant and safe (same round and self cancellation are covered by tests).
- **Never wait for another thread from a listener** (for example `SwingUtilities.invokeAndWait`):
  a UI thread cancelling at that moment would wait for the listener, and the listener for it.
- **Disposal order for a panel or simulator view** (from the EDT or the client thread, never from
  inside a listener of the other kind): cancel the bank subscription, then the lifecycle
  subscription, then clear the consumer's state and show the neutral presentation. Values queued to
  the EDT before disposal are still rejected by `ConsumerScopeGate` / `bank.isCurrent(evidence)`.
- Scopes are namespaced per lifecycle instance: a value tagged with a scope from another instance
  (for example a previous simulator run) is never accepted.

### Binding/disposal guard (required in addition to authority checks)

Cancelling a subscription stops future callbacks; it does **not** invalidate work already queued to
the EDT, and that work may carry account or bank evidence that is still perfectly current. Account
authority (`ConsumerScopeGate`) and bank authority (`bank.isCurrent(evidence)`) answer "is this
data still valid?", not "is this consumer still bound?". Every consumer therefore keeps its own
**binding generation**, confined to its thread:

1. Increment the generation on every bind, rebind, detach and dispose; capture it when work is
   queued.
2. On application, apply only if the captured generation equals the current one **and** the
   authority checks pass. A detached or disposed consumer shows the neutral state and applies
   nothing, even for current evidence.
3. Test detach, rebind and delayed callbacks (queued before and run after disposal).

This guard belongs to each consumer (for example the UI workstream's binding layer); the core does
not provide it, because only the consumer knows when it was detached.

## Simulator

The simulator reuses the same interfaces (SR-4):

- Construct `AccountLifecycle` directly and drive it with `start`, `onClientPhase`, `onGameTick`,
  `poll` and `stop`, using a synthetic monotonic clock (see test `LifecycleHarness`), or
- Construct `AccountLifecycleService(gameStateSupplier, hashSupplier, worldTypesSupplier, clock)`
  to exercise the adapter path without a RuneLite client.

Production panels then consume the simulator's `AccountLifecycleView` exactly as in the client.
For bank observation, construct `BankObservationTracker(lifecycle, lifecycle)` and drive
`onTick(tickIndex, source)` with any `BankCaptureSource` (see test `BankHarness`), or
`BankObservationService(accounts, source, tickSupplier)`.

## Changes in the 2026-11-26 audit repair

Consumers must now also: treat `ACTIVE` + `isAwaitingFreshIdentity()` as not publishable (rule 1);
use `get(scope)` and `ConsumerScopeGate` for thread hand-off (rules 4–5); expect a hop or reconnect
that passes through `LOGGING_IN` to arrive as a new login (new `loginToken`, cleared totals).
`AccountSlot` gained `get(AccountScope)`; no method was removed.

## Changes in the 2026-11-26 re-review repair

`ConsumerScopeGate` now requires the `AccountLifecycleView` (constructor argument) and checks it at
application time. `AccountScope` gained `authority()` and `sameEpoch()`; a region change now yields
a new scope with the same epoch, so consumers re-tag values after every `LOADING`.

## Requests to the UI workstream

- Provide a neutral "no account data" presentation for `UNAUTHENTICATED`, `SUSPENDED_CONTINUITY`
  and `RETIRED` that panels share, so no panel invents its own.
- Keep panel state keyed by `AccountScope` and clear it on scope change.
- Report any additional read-only lifecycle facts panels need; they are added to `AccountSnapshot`
  centrally rather than computed in panels.
