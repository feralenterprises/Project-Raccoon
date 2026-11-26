# Account lifecycle (FR-002)

The single source of truth for "which account is this, and may anything be shown or recorded for
it right now" (SR-1). Implemented in `core/account` (pure Java, no RuneLite types) and driven by
one RuneLite adapter, `account/AccountLifecycleService`. Every later feature consumes it; none
keeps its own copy of identity or account data.

Requirements source: owner-provided R03 §10 (account lifecycle contract and cases A01–A06),
restated here in implementation terms.

## What it owns

| Concept | Meaning |
|---|---|
| Account identity | Opaque per-run ordinal (`AccountScope.accountId()`). The account hash is held privately for comparison and never exposed. |
| Login token | One login. Survives a verified hop or reconnect; any new login gets a new token. |
| Observation epoch | One activation. Every restore and every new login opens a new epoch. |
| Scope | (identity, login token, epoch). All writes and callbacks carry the scope they were made under. |
| Login slots | Data for one login (e.g. KC totals). Masked while suspended, kept privately across verified continuity, cleared on retirement. |
| Epoch slots | Data for one epoch (e.g. KC baseline, bank witness/streak, queued messages). Cleared on every new epoch. |

## States

| State | Meaning | Publishes? |
|---|---|---|
| `UNAUTHENTICATED` | Started; no fresh verified identity yet. | No |
| `ACTIVE` | Verified identity on a supported world. | **Only while identity is fresh** |
| `SUSPENDED_CONTINUITY` | Explicitly witnessed `HOPPING` or `CONNECTION_LOST`. Data masked, callbacks fenced, login held privately. | No |
| `RETIRED` | Login ended; its data cleared. Reason in `lastRetireReason()`. | No |

**Freshness.** `ACTIVE` publishes only while its identity is fresh. Any client phase event
(`LOADING`, a repeated `LOGGED_IN`) makes it stale: the snapshot reports
`isAwaitingFreshIdentity()`, reads are masked, writes and fenced callbacks are rejected. Only a
`LOGGED_IN` game tick that verifies the same hash on a supported world makes it fresh again, in the
same login and epoch (a region change is not a hop). Every tick, in any phase, is checked: an
unknown identity, a read failure, a different hash or an unsupported world retires the login.

## Transitions

| From | Input | Result |
|---|---|---|
| any | plugin start | `UNAUTHENTICATED`; phase forgotten; nothing carried over |
| any | plugin stop | `RETIRED (PLUGIN_STOPPED)`, all data cleared, ordinals forgotten |
| `UNAUTHENTICATED`/`RETIRED` | tick in `LOGGED_IN`, verified hash, supported world | new login, new epoch → `ACTIVE` |
| `UNAUTHENTICATED`/`RETIRED` | unknown hash, read failure, or unsupported world | stay (no view) |
| `ACTIVE` | `HOPPING` / `CONNECTION_LOST` | `SUSPENDED_CONTINUITY`; clock starts |
| `ACTIVE` | `LOADING` or a repeated `LOGGED_IN` | stays `ACTIVE`, **not publishable** until a fresh `LOGGED_IN` tick (LOADING never establishes hop continuity) |
| `ACTIVE` | `LOGIN_SCREEN` / authenticator | `RETIRED (LOGOUT / AUTHENTICATOR)` |
| `ACTIVE` | `LOGGING_IN`, `STARTING`, unknown phase | `RETIRED (UNRECOGNIZED_CONTINUITY)` |
| `ACTIVE` | tick in any phase: unknown hash or read failure | `RETIRED (IDENTITY_LOST)` (no witnessed transition) |
| `ACTIVE` | tick in any phase: different hash | `RETIRED (DIFFERENT_ACCOUNT)`; new login only on a `LOGGED_IN` tick |
| `ACTIVE` | tick in any phase: unsupported world | `RETIRED (UNSUPPORTED_WORLD)` |
| `ACTIVE` | `LOGGED_IN` tick, same hash, supported world | fresh again (same login, same epoch) |
| `SUSPENDED` | `HOPPING`, `CONNECTION_LOST`, `LOADING`, `LOGGED_IN` | stay; limits keep running |
| `SUSPENDED` | `LOGGING_IN` | `RETIRED (CONTINUITY_INTERRUPTED)` (see evidence below) |
| `SUSPENDED` | tick in any phase: unsupported world (whatever the identity) | `RETIRED (UNSUPPORTED_WORLD)`; a later supported tick starts a **new** login |
| `SUSPENDED` | `LOGGED_IN` tick: unknown hash / read failure | authentication pending (counts toward tick limit) |
| `SUSPENDED` | tick: same hash, supported world, within limits | restore same login token, **new epoch** → `ACTIVE` |
| `SUSPENDED` | tick: different hash | `RETIRED (DIFFERENT_ACCOUNT)`, then new login if world supported |
| `SUSPENDED` | `LOGIN_SCREEN` / authenticator / other phase | `RETIRED` |
| `SUSPENDED` | 120 s since suspension, or 20 ticks awaiting authentication | `RETIRED (CONTINUITY_TIMEOUT / AUTHENTICATION_TIMEOUT)` |

Limits (`ContinuityLimits`): 120 monotonic seconds from suspension; at most 20 `LOGGED_IN` game
ticks awaiting authentication; whichever comes first. They are constants, counted across repeated
hops within one suspension, and never extended. Boundaries: restore is allowed at 119.999 s and
on the 20th tick; at 120 s, or when the 20th tick is still unverified, the login retires. Expiry
is checked before any restore and on every client tick while suspended. A clock reading earlier
than the suspension time retires (`CONTINUITY_TIMEOUT`): the limit cannot be proven unexpired.

### `LOGGING_IN` evidence (pinned RuneLite 1.13.8, `runelite-parent-1.13.8` = `8397ec71`)

- `runelite-api/.../GameState.java`: `LOGGING_IN(20)` is documented only as "There is a player
  logging in"; nothing describes it as part of a reconnect.
- `runelite-client/.../xptracker/XpTrackerPlugin.java`: treats `LOGGING_IN` like `HOPPING` as a
  login boundary and re-checks the account hash at the following `LOGGED_IN`; it also notes
  that `LOGGED_IN` fires on every region change.
- `runelite-client/.../grandexchange/GrandExchangePlugin.java`: the client clears trade state during
  `LOGIN_SCREEN`/`HOPPING`/`LOGGING_IN`.

The pinned source gives no evidence that `LOGGING_IN` preserves account continuity, so it retires
a suspended login. Cost: a reconnect that passes through `LOGGING_IN` starts a new login with
cleared totals (data is never shown wrongly). Native confirmation belongs to FR-006 (R3).

## Guarantees

- Nothing is readable (`AccountSlot.get()`) and no scope is current unless `ACTIVE`.
- A write or fenced callback from any earlier scope (before stop, before an account switch, or
  before an epoch change) is discarded.
- After restore, epoch data is empty, so a counter's first value in the new epoch is a zero delta
  (`EpochBaseline`): no gap gains, no replay of queued messages.
- Membership is `UNKNOWN` unless observed in the current epoch; an unknown reading never keeps
  or produces `MEMBER`. FR-002 does not observe membership, so it is always `UNKNOWN` for now.
- Unsupported worlds: a world is supported only if all its world types are in
  `WorldPolicy.SUPPORTED_WORLD_TYPES` (`MEMBERS`, `SKILL_TOTAL`). Anything else, including
  unknown future types, yields no observations.
- Diagnostics (`toString` of every lifecycle type) never contain the account hash; tested.

## Threading and delivery

- Inputs arrive on the client thread and are synchronized, so a plugin stop racing a tick stays
  consistent. Snapshots are immutable and safe on any thread.
- Delivery is serialized: one thread delivers at a time, every listener sees strictly increasing
  revisions, never concurrently. An input made from inside a listener is delivered after the
  current snapshot reached every listener. A cancelled subscription receives nothing further,
  even within the same delivery round. A new subscriber starts from the latest snapshot.
- Listeners run without the state lock but under the delivery lock: they must not block on
  other threads that drive the lifecycle.
- Slot operations (`get`, `get(scope)`, `set`) are each atomic with respect to transitions.
- `fenced(scope, work)` checks the scope when the work starts; work that also writes must use
  `set(scope, …)`, which re-checks atomically. Work started before a transition is not interrupted.
- Subscriptions survive `stop()`/`start()`; they see the `RETIRED` and `UNAUTHENTICATED` snapshots.
- The adapter reconciles the lifecycle phase with the client's actual game state on every tick
  (an unreadable state counts as `UNKNOWN`) before reading identity, so a missed event cannot
  leave a stale `LOGGED_IN`.

## Acceptance cases → tests

| Case | Tests (`core/account/AccountLifecycleTest`, `account/AccountLifecycleServiceTest`) |
|---|---|
| A01 | `a01_*` (6): no identity, signed out, unknown hash, identity loss and read failure outside a transition, LOADING not a transition |
| A02 | `a02_*` (3) + `enabledMidSessionLearnsPhaseThenReadsIdentityFresh` |
| A03 | `a03_*` (4): A→B→A, missed logout, identical display names, different account after hop |
| A04 | `a04_*` (15): immediate/delayed identity, read failure, repeated unknown, 120 s, no extension, tick limit, whichever first, logout/authenticator/disable, unsupported world, missing continuity, no publication or replay, zero-delta baseline |
| A05 | `a05_*` (4): after stop, account switch, epoch change, stop+restart |
| A06 | `a06_*` (3) + `worldPolicy*` (2), `unreadableWorldIsUnsupported` |
| Audit 2026-11-26 | `AuditRegressionTest` (10), `finding4_*` adapter (2): reproduced each finding before repair (CI failed at `1792d0d6`) |
| Boundaries | `LifecycleBoundaryTest` (11): region change, masking, limits at 119.999 s / 120 s / 20 ticks, clock backwards, enable during LOADING |
| Concurrency | `LifecycleConcurrencyTest` (real threads), `ConsumerScopeGateTest` (3) |

## Open policy inputs

- **Supported world types:** the allowlist above is a conservative default; R03's exact list was
  not supplied. Widening it is a one-line reviewed change in `WorldPolicy`.
- **Membership source:** not observed until a later assignment defines it.
