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

## Simulator

The simulator reuses the same interfaces (SR-4):

- Construct `AccountLifecycle` directly and drive it with `start`, `onClientPhase`, `onGameTick`,
  `poll` and `stop`, using a synthetic monotonic clock (see test `LifecycleHarness`), or
- Construct `AccountLifecycleService(gameStateSupplier, hashSupplier, worldTypesSupplier, clock)`
  to exercise the adapter path without a RuneLite client.

Production panels then consume the simulator's `AccountLifecycleView` exactly as in the client.

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
