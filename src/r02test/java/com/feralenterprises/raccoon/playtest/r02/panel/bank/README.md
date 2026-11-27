# Lane C (Account and supplies): verification

Base: accepted common framework `6684228b`. Lane C writes only `panel/bank/`, `panel/capture/`, `panel/xpstash/` and
these tests. No shared, fixture, assembly, host or other-lane files are changed.

```sh
./gradlew --offline compileR02testJava
"$JAVA_HOME/bin/java" -Djava.awt.headless=true \
  -cp 'build/classes/java/main:build/classes/java/r02:build/classes/java/r02test' \
  com.feralenterprises.raccoon.playtest.r02.panel.bank.BankLaneTests \
  /path/to/Project-Raccoon-R02-Lane-Inputs.zip /path/to/Project-Raccoon-R02 [evidence-dir]
```

Page suites (same arguments): `panel.bank.BankLaneTests`, `panel.capture.CaptureLaneTests`, `panel.xpstash.XpStashLaneTests`.

XP Stash engine: `panel.xpstash.XpEngineTests <Lane Inputs ZIP> [reference.json]` checks the three frozen arithmetic
vectors; with a reference file it also compares every seeded scenario with the frozen engine. Produce the reference with
`node src/r02test/java/.../panel/xpstash/reference-diff.js <extracted R02-Lane-Inputs> reference.json 600` (it runs the
private `C/reference-xp.js.txt` with one injected option hook; its arithmetic is unchanged).

Inputs stay private: the accepted Lane Inputs ZIP (SHA-256 `1f6a821d…cece`, refused if different, every file checked
against its `SHA256SUMS`) and the extracted frozen review. Evidence PNGs contain private art: do not publish them.
Functional build on shared components; visual polish follows owner playtesting.
