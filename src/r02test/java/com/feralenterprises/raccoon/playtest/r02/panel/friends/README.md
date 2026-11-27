# Feral Friends regression

The host can load this route alone with `FriendsPanels.registration(laneInputs, frozenReview)` or both Lane E routes with
`CollectionPanels.registrations(laneInputs, frozenReview, dispatcher)`. Load from the private frozen inputs before entering
the Swing event thread, then pass the registrations and the same dispatcher to the existing composition. The published code
contains no catalog, sprites, or account observations.

Ownership comes from `RouteContext.snapshot().ownership(petKey)` only. Wishlists and active hunts are temporary local
intent. The current contract carries no acquisition history, roll events, or companion trade observations. Counters and
account odds remain unavailable. The exact pet IDs and categories follow the prepared TSV, which classifies Orion as Raid.

From the repository root on Temurin 11.0.33+10 and Gradle 8.10:

```sh
./gradlew --offline compileR02testJava
"$JAVA_HOME/bin/java" -Djava.awt.headless=true \
  -cp 'build/classes/java/main:build/classes/java/r02:build/classes/java/r02test' \
  com.feralenterprises.raccoon.playtest.r02.panel.friends.FriendsLaneTests \
  /path/to/Project-Raccoon-R02-Lane-Inputs.zip /path/to/Project-Raccoon-R02
```

On Windows use semicolon classpath separators.
