# Outfitter and Boss Chronicle verification

Use Temurin 11.0.33+10 and Gradle 8.10. From the repository root, run:

```sh
./gradlew --offline compileR02testJava
"$JAVA_HOME/bin/java" -Djava.awt.headless=true \
  -cp 'build/classes/java/main:build/classes/java/r02:build/classes/java/r02test' \
  com.feralenterprises.raccoon.playtest.r02.panel.outfit.CombatLaneTests \
  /path/to/Project-Raccoon-R02-Lane-Inputs.zip /path/to/Project-Raccoon-R02
```

The archive and extracted review directory are private test inputs. An optional third argument writes offscreen PNGs.
On a Swing display, run with `-Djava.awt.headless=false` to also run the Robot focus regression. On Windows, use
semicolon classpath separators. This suite uses simulated accounts and has no RuneLite dependency.

The simulated host should load `OutfitPanels.registrations(laneInputs, frozenReview)` off the Swing event thread and
pass both registrations to its existing `R02Composition` on the event thread. The shared registry and dispatcher
supply account observations. The catalog contains only encounter references, artwork and example setups.
