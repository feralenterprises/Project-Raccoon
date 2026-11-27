# Lane D (Progression): verification

Base: accepted common framework `6684228b`. Lane D writes only `panel/pathfinder/`, `panel/quests/`,
`panel/bounties/` and these tests. No shared, fixture, assembly, host or other-lane files are changed.

```sh
./gradlew --offline compileR02testJava
"$JAVA_HOME/bin/java" -Djava.awt.headless=true \
  -cp 'build/classes/java/main:build/classes/java/r02:build/classes/java/r02test' \
  com.feralenterprises.raccoon.playtest.r02.panel.pathfinder.PathfinderLaneTests \
  /path/to/Project-Raccoon-R02-Lane-Inputs.zip /path/to/Project-Raccoon-R02 [evidence-dir]
# Quests & Diaries: same classpath and arguments, class
#   com.feralenterprises.raccoon.playtest.r02.panel.quests.QuestsLaneTests
# Bandit's Bounties: same classpath and arguments, class
#   com.feralenterprises.raccoon.playtest.r02.panel.bounties.BountiesLaneTests
```

Host wiring (shared owner): load `panel.pathfinder.ProgressionPanels.registrations(laneInputsZip, frozenReview)` off the
EDT and pass the three registrations (exactly `Lane.PROGRESSION.routes()`) to `R02Composition` on the EDT. Entry contract
suite (same arguments, no evidence dir): `panel.pathfinder.ProgressionEntryTests`.

Inputs stay private: the accepted Lane Inputs ZIP (SHA-256 `1f6a821d…cece`, refused if different, every file checked
against its `SHA256SUMS`) and the extracted frozen review. Evidence PNGs contain private art: do not publish them.
Functional build on shared components; visual polish follows owner playtesting.
