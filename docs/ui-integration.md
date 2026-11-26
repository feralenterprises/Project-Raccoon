# Integration guide: account lifecycle for the UI workstream and simulator

For the parallel universal-UI workstream. This describes what the account lifecycle (FR-002)
offers and what any consumer must do. It does not assume anything about individual panels, and
FR-002 contains no UI.

## Interfaces (package `com.feralenterprises.raccoon.core.account`)

| Type | Use |
|---|---|
| `AccountLifecycleView` | Read side. `snapshot()`, `subscribe(listener)`, `isCurrent(scope)`, `fenced(scope, work)`. |
| `AccountSnapshot` | Immutable; safe on any thread. `state()`, `isPublishable()`, `scope()`, `membership()`, `lastRetireReason()`, `revision()`. |
| `AccountLifecycleListener` | Called with every snapshot, in order. Gets the current snapshot immediately on subscribe. |
| `Subscription` | Returned by `subscribe`; `cancel()` / `close()`. |
| `AccountScope` | Opaque (account id, login token, epoch). Key any per-account cache by it. |
| `AccountDataRegistry` / `AccountSlot<T>` | Central account data: `loginSlot`, `epochSlot`; `get()` masked unless ACTIVE, `set(scope, value)` only from the current scope. |
| `EpochBaseline` | Counter deltas; the first value in an epoch is 0. |

In the plugin, obtain them by injecting `com.feralenterprises.raccoon.account.AccountLifecycleService`
and calling `view()` (read side) or `data()` (slot registry). Only that service drives
transitions.

## Rules for every consumer (panels, overlays, simulator views)

1. **Render account data only when `snapshot.isPublishable()`.** On any other snapshot, show the
   neutral state (signed out, waiting, reconnecting) and drop every account-derived value you hold.
2. **Never keep data across scopes.** When `snapshot.scope()` changes (different login token or
   epoch), discard caches built for the previous scope. Use `AccountScope.sameLogin` only to decide
   presentation (for example "reconnected"), not to reuse epoch data.
3. **Fence asynchronous work.** Wrap anything that completes later (images, timers, background
   computation) with `view.fenced(scope, work)`, or check `isCurrent(scope)` before applying it.
4. **Thread hand-off is the consumer's job.** Listeners run on the client thread. Copy the snapshot
   and marshal to the Swing EDT yourself; do not block or do heavy work in the listener.
5. **Do not read slots from the EDT.** Slots are client-thread data. Pass values to the UI as
   immutable copies, tagged with the scope they were read under.
6. **Never display or log account hashes.** None of these types expose one. `accountId()` is a
   per-run ordinal, not an identity to show or persist.
7. **Treat `Membership.UNKNOWN` as unknown,** never as members.
8. **No persistence, no transitions.** Consumers must not store account state or call lifecycle
   inputs; only the adapter does.
9. **Make no assumptions about the retirement reason** other than for wording; all non-ACTIVE
   states mean "show no account data".

## Simulator

The simulator reuses the same interfaces (SR-4):

- Construct `AccountLifecycle` directly and drive it with `start`, `onClientPhase`, `onGameTick`,
  `poll` and `stop`, using a synthetic monotonic clock (see test `LifecycleHarness`), or
- Construct `AccountLifecycleService(gameStateSupplier, hashSupplier, worldTypesSupplier, clock)`
  to exercise the adapter path without a RuneLite client.

Production panels then consume the simulator's `AccountLifecycleView` exactly as in the client.

## Requests to the UI workstream

- Provide a neutral "no account data" presentation for `UNAUTHENTICATED`, `SUSPENDED_CONTINUITY`
  and `RETIRED` that panels share, so no panel invents its own.
- Keep panel state keyed by `AccountScope` and clear it on scope change.
- Report any additional read-only lifecycle facts panels need; they are added to `AccountSnapshot`
  centrally rather than computed in panels.
