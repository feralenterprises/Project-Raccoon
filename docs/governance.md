# Governance

- **Authority:** issue #1 is the canonical roadmap and authorization record. The governing
  handoff is retained separately from public source until the owner clears it for publication.
- **Branching:** one foundation work branch (`foundation`) and one draft PR against `main`, with
  seven logical checkpoints (FR-000 … FR-006). `main` stays stable; the owner approves promotion.
- **Gates:** FR-001 must pass before FR-002. Missing inputs or artifact = BLOCKED; an actual
  failure = FAIL. No official check is bypassed. CI passing is not native acceptance.
- **Final acceptance:** all required cases and four distinct receipts on the same final source SHA.
- **Not authorized:** Plugin Hub submission, public product identity, cutover, feature expansion,
  production UI, plugin-originated networking, account persistence, game writes, extra runtime
  dependencies, personal account records, secrets, unapproved assets.
- **Handback (every checkpoint):** exact source SHA, artifact or explicit blocker, commands and
  results, next assignment.
- **Pins:** `release/pins.properties`; changing a pin is a reviewed change.
