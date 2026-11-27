# Lane A (Adventure): verification

Base: accepted common framework `6684228b`. Lane A writes only `panel/home/` (and later `panel/activity/`,
`panel/files/`, `panel/tools/`) plus these tests. No shared, fixture, assembly, host or Lane B files are changed.

```sh
./gradlew --offline compileR02testJava
"$JAVA_HOME/bin/java" -Djava.awt.headless=true \
  -cp 'build/classes/java/main:build/classes/java/r02:build/classes/java/r02test' \
  com.feralenterprises.raccoon.playtest.r02.panel.home.HomeLaneTests \
  /path/to/Project-Raccoon-R02-Lane-Inputs.zip /path/to/Project-Raccoon-R02 [evidence-dir]
```

Inputs stay private and outside the repository: the accepted Lane Inputs ZIP (SHA-256
`1f6a821d…cece`, refused if different; every file checked against its `SHA256SUMS`) and the extracted frozen review
(its own `SHA256SUMS.txt`, read by the shared `FrozenPackageAssets`). The optional evidence directory receives offscreen
PNGs, which contain private frozen art: do not publish them. Use `;` classpath separators on Windows.

Suites: `panel.home.HomeLaneTests`, `panel.activity.ActivityLaneTests`, `panel.files.FilesLaneTests` (same arguments).

Host wiring (shared owner): load `AdventurePanels.registrations(laneInputsZip, frozenReview)` off the EDT and pass them
to the existing `R02Composition` on the EDT. Home is content-only; the `FULL` environment (header scene, vines, burrow-entrance footer
art) stays with the shared `PageHost`.

Evidence rules covered: official KC only from a verified kill-log observation (C-04, C-10), raw tuna only from a current
bank observation with absent = verified zero and incomplete never "fewer than" (C-05), quest progress Partial/Unknown and
never complete (C-06), unavailable levels stay unavailable (C-07), saved replay versus current login with no invented
live XP (C-08, C-09), saved sessions never become kill or chest claims (C-11), scope change discards the page and local
state, same-scope revisions keep local state, stale revisions refused, typed navigation, profile as local Back, and
no clipping at 225/200 px in Default and Large text.
