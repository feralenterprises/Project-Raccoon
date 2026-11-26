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
| `ACTIVE` | Fresh verified identity on a supported world. | **Yes (only state that may)** |
| `SUSPENDED_CONTINUITY` | Explicitly witnessed `HOPPING` or `CONNECTION_LOST`. Data masked, callbacks fenced, login held privately. | No |
| `RETIRED` | Login ended; its data cleared. Reason in `lastRetireReason()`. | No |

## Transitions

| From | Input | Result |
|---|---|---|
| any | plugin start | `UNAUTHENTICATED`; phase forgotten; nothing carried over |
| any | plugin stop | `RETIRED (PLUGIN_STOPPED)`, all data cleared, ordinals forgotten |
| `UNAUTHENTICATED`/`RETIRED` | tick in `LOGGED_IN`, verified hash, supported world | new login, new epoch → `ACTIVE` |
| `UNAUTHENTICATED`/`RETIRED` | unknown hash, read failure, or unsupported world | stay (no view) |
| `ACTIVE` | `HOPPING` / `CONNECTION_LOST` | `SUSPENDED_CONTINUITY`; clock starts |
| `ACTIVE` | `LOADING` | no change (LOADING alone is not a hop) |
| `ACTIVE` | `LOGIN_SCREEN` / authenticator | `RETIRED (LOGOUT / AUTHENTICATOR)` |
| `ACTIVE` | `LOGGING_IN`, `STARTING`, unknown phase | `RETIRED (UNRECOGNIZED_CONTINUITY)` |
| `ACTIVE` | tick: unknown hash or read failure | `RETIRED (IDENTITY_LOST)` (no witnessed transition) |
| `ACTIVE` | tick: different hash | `RETIRED (DIFFERENT_ACCOUNT)`, then new login for the new hash |
| `ACTIVE` | tick: unsupported world | `RETIRED (UNSUPPORTED_WORLD)` |
| `SUSPENDED` | `HOPPING`, `CONNECTION_LOST`, `LOADING`, `LOGGING_IN` | stay; limits keep running |
| `SUSPENDED` | tick: unknown hash / read failure | authentication pending (counts toward tick limit) |
| `SUSPENDED` | tick: same hash, supported world, within limits | restore same login token, **new epoch** → `ACTIVE` |
| `SUSPENDED` | tick: different hash | `RETIRED (DIFFERENT_ACCOUNT)`, then new login if world supported |
| `SUSPENDED` | tick: unsupported world | `RETIRED (UNSUPPORTED_WORLD)` |
| `SUSPENDED` | `LOGIN_SCREEN` / authenticator / other phase | `RETIRED` |
| `SUSPENDED` | 120 s since suspension, or 20 ticks awaiting authentication | `RETIRED (CONTINUITY_TIMEOUT / AUTHENTICATION_TIMEOUT)` |

Limits (`ContinuityLimits`): 120 monotonic seconds from suspension; at most 20 game ticks
awaiting authentication; whichever comes first. They are constants, counted across repeated hops
within one suspension, and never extended. Expiry is checked before any restore and on every
client tick while suspended.

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

## Threading

Inputs arrive on the client thread and are synchronized, so a plugin stop racing a tick stays
consistent. Snapshots are immutable and safe on any thread. Listeners are called after the lock is
released, on the driving thread, in publication order. Slots are for client-thread use.

## Acceptance cases → tests

| Case | Tests (`core/account/AccountLifecycleTest`, `account/AccountLifecycleServiceTest`) |
|---|---|
| A01 | `a01_*` (6): no identity, signed out, unknown hash, identity loss and read failure outside a transition, LOADING not a transition |
| A02 | `a02_*` (3) + `enabledMidSessionLearnsPhaseThenReadsIdentityFresh` |
| A03 | `a03_*` (4): A→B→A, missed logout, identical display names, different account after hop |
| A04 | `a04_*` (15): immediate/delayed identity, read failure, repeated unknown, 120 s, no extension, tick limit, whichever first, logout/authenticator/disable, unsupported world, missing continuity, no publication or replay, zero-delta baseline |
| A05 | `a05_*` (4): after stop, account switch, epoch change, stop+restart |
| A06 | `a06_*` (3) + `worldPolicy*` (2), `unreadableWorldIsUnsupported` |

## Open policy inputs

- **Supported world types:** the allowlist above is a conservative default; R03's exact list was
  not supplied. Widening it is a one-line reviewed change in `WorldPolicy`.
- **Membership source:** not observed until a later assignment defines it.
