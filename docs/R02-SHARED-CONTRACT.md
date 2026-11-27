# R02 shared contract: path mapping and entry points

Base: accepted re-pin `5a48601d`. Dispatch: *Project Raccoon R02 — Implementation dispatch*, Assignment 0.

## Path mapping (dispatch ownership contract → this repository)

| Dispatch path | Repository path (Java package `com.feralenterprises.raccoon.playtest.r02`) | Writer |
| --- | --- | --- |
| dev module build | `build.gradle` source sets `r02`, `r02test`; tasks `r02ContractTests`, `routeTests` | Shared owner |
| `shared/**` | `src/r02/java/.../r02/shared/` | Shared owner |
| `fixture/**` | `src/r02/java/.../r02/fixture/` | Shared owner |
| `host/**` | `src/r02/java/.../r02/host/` (RuneLite-independent host core), `playtest/host-plugin/` (RuneLite glue), `src/launcher/java/` (launcher); native launch needs separate clearance | Shared owner |
| `assembly/**` (composition root) | `src/r02/java/.../r02/assembly/` | Shared owner only |
| `acceptance/**` | `src/r02test/java/.../r02/acceptance/` | Shared owner |
| framework files approved for this slice | `src/main/java/.../ui/shell/{Navigator,RaccoonShell,RouteRequest,RouteAware,LocalBack}.java`, `ui/component/RDropdown.java`, `src/test/.../RouteTests.java` | Shared owner |
| `panel/<route>/**` | `src/r02/java/.../r02/panel/<route>/` | Owning lane (`Lane`) |
| panel tests | `src/r02test/java/.../r02/panel/<route>/` | Owning lane |

Lanes never edit another lane's `panel/<route>/`, `shared/`, `fixture/`, `assembly/` or framework files. Shared changes are
requested with a failing case. Frozen package files (art, sprites, datasets) are never committed: tests and hosts read
the extracted package through `-Pr02Package=PATH` and verify every file against its `SHA256SUMS.txt`.

## What a lane implements

```java
// src/r02/java/.../r02/panel/outfit/OutfitPanels.java  (Combat lane)
public static RouteRegistration registration() {
    return new RouteRegistration(RouteId.OUTFIT, Lane.COMBAT.name(), OutfitPanel::new);
}
// OutfitPanel(RouteContext c) extends a framework component; implements RouteAware (encounter payload)
// and LocalBack (slot detail) as needed.
```

| Need | Entry point |
| --- | --- |
| Route ids, rail order, labels | `shared.RouteId` |
| Lane ownership | `shared.Lane` |
| Page factory and context | `shared.PanelFactory`, `shared.RouteContext` (snapshot, registry, assets, clock, appearance, navigate, dispatch) |
| Typed cross-panel link | `RouteContext.navigate(RouteId, RouteRequest.Param.{ENCOUNTER,SESSION,MILESTONE}, id)` |
| Receive a payload | implement `ui.shell.RouteAware` (`returning == true` on Back: keep own state) |
| Detail levels and Back | implement `ui.shell.LocalBack`; call the listener when `hasLocalBack()` may change; restore opener focus on close |
| Observations | `fixture.FixtureSnapshot`, `Evidence` / `EvidenceStatus`, `ContainerObservation`, `SkillObservation` (level and exact XP independent) |
| Names, categories, KC keys | `fixture.Registry` (`ItemDef`, `EncounterDef`) |
| Time | `fixture.FixtureClock` (never the system clock) |
| Fixture-only controls | `fixture.FixtureAction` via `RouteContext.dispatch` |
| Tiny lane fixtures | `fixture.TestFixtures` (`demoA`, `demoB`, `signedOut`, `registry`) |
| Frozen A/B fixtures | `assembly.FrozenDataset.load(packageRoot)` (verified page, registry with D-09/D-15/D-18 corrections, `literal(name)` / `jsonBlock(id)` for lane catalogs) and `assembly.FrozenScenarios.snapshot(Selection, generation, revision, now)` (`Selection.demoA/demoB/signedOut` with bank, archive, quest-data, route and activity states) |
| Images | `shared.R02Assets` (null → draw `R02Assets.fallbackLabel(name)`) |
| Environment | `shared.R02Environment` |
| Dropdown | `ui.component.RDropdown` |
| Existing components | `ui.component.{Card, SectionHeader, RButton, SegmentedSelector, SearchField, FilterList, Expandable, ProgressBar, StatusIndicator, StateNotice, TextBlock, Divider, EquipmentGrid, InventoryGrid, ItemSlots}` |

Scope rule (enforced by `assembly.R02Composition`): a new `AccountScope` discards every route page, closes every page
subscription and resets history to Home; same-scope revisions arrive through `RouteContext.onSnapshot`; stale revisions
and work from a retired binding generation are refused by `fixture.FixtureDispatcher`.

## Native playtest host

`playtest/OWNER-INSTRUCTIONS.md` covers running the host.

### Lane entry contract

Each lane has a single entry class, `Lane.entryClass()`:

| Lane | Entry class (`...r02.panel.`) | State at the lane heads (A `3557733`, B `81b024e`, C `d6cd752`, D `0e37db9`, E `0eb88fa`) |
| --- | --- | --- |
| ADVENTURE (A) | `home.AdventurePanels` | present |
| COMBAT (B) | `outfit.OutfitPanels` | present |
| ACCOUNT (C) | `bank.AccountPanels` | **to add**: per-route `registration(...)` only |
| PROGRESSION (D) | `pathfinder.ProgressionPanels` | **to add**: per-route `registration(...)` only |
| COLLECTION (E) | `friends.CollectionPanels` | present, with the extra `FixtureDispatcher` argument (accepted for compatibility) |

The method is `public static List<RouteRegistration> registrations(Path laneInputsZip, Path frozenReview) throws IOException`.

`registrations(Path, Path, FixtureDispatcher)` is accepted for compatibility. Prefer the two-argument form: Settings
can now use `RouteContext.setAppearance`.

The host behaves as follows:
- It verifies the private Lane Inputs ZIP (`1f6a821d…cece`) and calls each present entry off the EDT. `frozenReview` is the verified extracted `Project-Raccoon-R02` folder.
- It installs the result on the EDT.
- A lane whose entry is absent keeps placeholders.
- Anything else stops the host start. That covers an entry that is malformed or throws, a route the lane does not own, an owner other than `Lane.name()`, and a route registered twice.

### Lane findings (issue #1) and their resolution

| Finding | Resolution |
| --- | --- |
| A1 saved record needs active time and count | Evidence constructor of `ArchiveRecord`: both may be UNKNOWN |
| A2 no elapsed time, deaths, supplies | `elapsedTime()`, `deaths()`, `suppliesLedger()` as Evidence |
| A3 Activity given the Home header and footer | `R02Environment` ACTIVITY is PLAIN (page-owned hero, as frozen) |
| A4 page background cannot fill the viewport | the page sets `PageHost.FILL_VIEWPORT` (opt-in, no change for others) |
| C1 incomplete bank cannot carry the earlier capture | `ContainerObservation.withRetained(historical)` / `retained()`; quantities unchanged |
| C2 `capture("bank")` can contradict the BANK container | derived from the container; a builder value for "bank" is ignored |
| C3 missing frozen bank sprites | `FrozenPackageAssets.withLaneSprites(LaneInputsArchive)`: verified `shared/osrs-items/<id>.png` as the last source (the host wires it) |
| C4 no minimum width for truncation audits | `TextFit.requiredTextWidth()` on `RButton`, `RDropdown`, `SegmentedSelector` (reporting only) |
| C5 `Expandable` titles text-only | `Expandable(ctx, title, icon, body)` |
| D1 no quest/diary coverage | `FixtureSnapshot.questCoverage()` / `diaryCoverage()` (`Coverage` COMPLETE/PARTIAL; frozen data PARTIAL) |
| D4 test registry split the Kings, lacked routed encounters | `TestFixtures` registry has the frozen `dagannoth-kings` (three KC keys) and barrows, giant-mole, sarachnis, zulrah, phantom-muspah |
| E Settings needed the whole dispatcher | `RouteContext.setAppearance` |
| D2, D3, D5, D7 | No contract change. These are lane decisions for Solis (see the UI report). |

### What the shared contract now carries (lanes built on `7954ce4` can adopt it)

These close the gaps the lanes worked around on `7954ce4`:
- **Saved records.** `ArchiveRecord.elapsedTime()`, `lootLedger()` and `suppliesLedger()` are `Evidence`. `VERIFIED_ABSENT` (observed empty) is distinct from `UNAVAILABLE`/`UNKNOWN` (not observed).
- **Registry items.** `ItemDef` carries category, illustrative price and tradeability (UNKNOWN unless established).
- **Populated fixtures.** `FrozenScenarios` gives populated Demo A, Demo B and signed-out snapshots from the frozen review.

Every pre-existing accessor is unchanged. All three lanes compile unchanged on top of this module.

### Assets and naming

- Item images and interface sprites come from the game cache (`ItemManager`, `SpriteManager`).
- R02 art comes from the verified frozen package.
- Item names follow native naming (4550 is "Bullseye lantern"). Each run writes `native-name-audit.csv`, comparing every registry name with the cache name.
