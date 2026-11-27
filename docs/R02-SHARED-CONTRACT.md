# R02 shared contract: path mapping and entry points

Base: accepted re-pin `5a48601d`. Dispatch: *Project Raccoon R02 — Implementation dispatch*, Assignment 0.

## Path mapping (dispatch ownership contract → this repository)

| Dispatch path | Repository path (Java package `com.feralenterprises.raccoon.playtest.r02`) | Writer |
| --- | --- | --- |
| dev module build | `build.gradle` source sets `r02`, `r02test`; tasks `r02ContractTests`, `routeTests` | Shared owner |
| `shared/**` | `src/r02/java/.../r02/shared/` | Shared owner |
| `fixture/**` | `src/r02/java/.../r02/fixture/` | Shared owner |
| `host/**` | `src/r02/java/.../r02/host/` (reserved; no launch authorized) | Shared owner |
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
| Images | `shared.R02Assets` (null → draw `R02Assets.fallbackLabel(name)`) |
| Environment | `shared.R02Environment` |
| Dropdown | `ui.component.RDropdown` |
| Existing components | `ui.component.{Card, SectionHeader, RButton, SegmentedSelector, SearchField, FilterList, Expandable, ProgressBar, StatusIndicator, StateNotice, TextBlock, Divider, EquipmentGrid, InventoryGrid, ItemSlots}` |

Scope rule (enforced by `assembly.R02Composition`): a new `AccountScope` discards every route page, closes every page
subscription and resets history to Home; same-scope revisions arrive through `RouteContext.onSnapshot`; stale revisions
and work from a retired binding generation are refused by `fixture.FixtureDispatcher`.
