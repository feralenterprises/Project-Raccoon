# Settings regression

Register `SettingsPanels.registration(dispatcher)` using the same dispatcher passed to `R02Composition`. Settings emits
`RESET_OBSERVATIONS` to the host scenario driver; preference reset updates the existing shared appearance model only.
Default and Large update the existing shared Swing text scale. Accent and text weight update the shared model for
preview. Global painting for those choices and the frozen Bronze surface require support from the common owner.

From the repository root on Temurin 11.0.33+10 and Gradle 8.10:

```sh
./gradlew --offline compileR02testJava
"$JAVA_HOME/bin/java" -Djava.awt.headless=true \
  -cp 'build/classes/java/main:build/classes/java/r02:build/classes/java/r02test' \
  com.feralenterprises.raccoon.playtest.r02.panel.settings.SettingsLaneTests \
  /path/to/Project-Raccoon-R02-Lane-Inputs.zip
```

On Windows use semicolon classpath separators.
