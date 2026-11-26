# Standing engineering requirements

Owner directive, 2026-11-26. These apply to every assignment from FR-001 onward. They describe how
authorized work must be built; they do not authorize any new work. The governing R03 master plan
and issue #1 remain authoritative for scope and order. Where an item below needs a component that
does not exist yet, it binds the assignment that first introduces that component.

| ID | Requirement | Applies when | How it is checked |
|---|---|---|---|
| SR-1 | **Single source of truth.** Account state, data collection, caching, calibration and account lifecycle are managed by one central owner each. No feature keeps a private copy of account state or runs its own collection or cache. | FR-002 onward | Architecture review at each checkpoint; dependency rules in `tools/verify` once the state package exists |
| SR-2 | **Shared assets.** Get authentic OSRS sprites and icons from RuneLite at runtime wherever RuneLite supports it. Do not bundle copies of game artwork. Any bundled asset needs explicit approval and a provenance row. | First UI or asset use | `tools/verify` rejects assets in source and JAR unless approved |
| SR-3 | **Universal UI system.** Every panel uses shared design tokens, components, typography, spacing and asset services. No panel defines its own colours, fonts, spacing or sprite loading. | First UI assignment | Review; verifier rules added with the UI package |
| SR-4 | **Simulator reuse.** The simulator reuses production UI components and shared interfaces wherever practical, rather than parallel copies. | First simulator assignment | Review; simulator code depends on production interfaces, not the reverse |
| SR-5 | **UI development playbook.** Keep Burrow's proven workflow of design, implementation, simulation and visual verification. It is recorded in `docs/ui-playbook.md` when the first UI assignment is authorized, with private donor material removed. | First UI assignment | Playbook exists and is followed before any UI checkpoint is accepted |
| SR-6 | **Package size.** Foundation budget: post-processed JAR ≤ 256 KiB (262,144 bytes). Final release target: < 8 MB. Mandatory publication ceiling: 10 MB (the Plugin Hub default limit). Every engineering handback reports exact artifact sizes and the change since the previous handback. | Every handback | `tools/verify` size gate; size table in `docs/release-evidence.md` |
| SR-7 | **Accessibility of knowledge.** The product must stay useful whatever the player's existing OSRS knowledge. Features explain themselves and do not assume expert vocabulary or prior research. | Every user-facing feature | Feature acceptance cases include a no-prior-knowledge check |
| SR-8 | **Migration discipline.** Keep proven Burrow functionality. Avoid needless reinvention, duplication and feature expansion. Port only what an authorized assignment needs, record it in `docs/migration-provenance.md`, and never modify Burrow. | Every migration | Provenance record; review |

## Handback size reporting (SR-6)

Every engineering handback includes:

| Artifact | Bytes | SHA-256 | Δ bytes vs previous handback |
|---|---|---|---|
| Local development JAR (`build/libs/project-raccoon.jar`) | | | |
| Official post-processed JAR (packager output) | | | |

Limits: foundation ≤ 262,144 bytes; release target < 8,000,000 bytes; ceiling ≤ 10 MiB
(10,485,760 bytes, the packager's `jarSizeLimitMiB` default).
