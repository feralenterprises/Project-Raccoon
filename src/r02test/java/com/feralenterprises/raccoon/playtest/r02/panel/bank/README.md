# Lane C (Account and supplies): verification

Base: accepted common framework `6684228b`. Lane C writes only `panel/bank/`, `panel/capture/`, `panel/xpstash/` and
these tests. No shared, fixture, assembly, host or other-lane files are changed.

```sh
./gradlew --offline compileR02testJava
"$JAVA_HOME/bin/java" -Djava.awt.headless=true \
  -cp 'build/classes/java/main:build/classes/java/r02:build/classes/java/r02test' \
  com.feralenterprises.raccoon.playtest.r02.panel.bank.BankLaneTests \   # also panel.capture.CaptureLaneTests
  /path/to/Project-Raccoon-R02-Lane-Inputs.zip /path/to/Project-Raccoon-R02 [evidence-dir]
```

Inputs stay private: the accepted Lane Inputs ZIP (SHA-256 `1f6a821d…cece`, refused if different, every file checked
against its `SHA256SUMS`) and the extracted frozen review. Evidence PNGs contain private art: do not publish them.
Functional build on shared components; visual polish follows owner playtesting.
