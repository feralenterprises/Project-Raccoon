# Release evidence

One entry per checkpoint. Local and CI measurements are **not** the official Plugin Hub packager
result; that gate is FR-001.

## FR-000 — release-compatible scaffold

| Item | Value |
|---|---|
| Source SHA | recorded in the PR handback and in CI `foundation-evidence.txt` |
| Build | `./gradlew --no-daemon --no-build-cache clean build` (CI, Temurin 11.0.33+10) |
| Checks | `java tools/verify/FoundationVerifier.java jdk|selftest|source|tests|artifact` |
| Local JAR | `build/libs/project-raccoon.jar`, bytes and SHA-256 in CI evidence |
| Size limit | 262144 bytes (256 KiB), measured on local JAR only |
| Official packager (FR-001) | **Not run — pending** |

## FR-001 — official strict non-upload packager

**Result: PASS** (pending owner acceptance). Workflow `.github/workflows/official-packager.yml`.

| Item | Value |
|---|---|
| Source commit packaged | `f3a73150c624749785e271646bef3c5f4a43c861` (cloned by the packager over public HTTPS) |
| Packager | `runelite/plugin-hub-tooling` release `v6` = `c9e2fb4fb1d49a860ef68bc6fe34c0dab4f60aa9` |
| Packager bundle SHA-256 | `8ca79aa71611b85ea42ecd81d01e483027d84c158fc34d554521c4407fe76acd` (equals GitHub published digest) |
| Plugin Hub state | `runelite/plugin-hub` @ `f7ed590e4e2599d81b3bd7ffeaec62314178ae3c`, RuneLite 1.13.8 |
| Mode | strict (template defaults and disallowed APIs fatal); no upload credentials; marker committed only in the runner-local checkout |
| JDK | Temurin 11.0.33+10 |
| Post-processed JAR | `project-raccoon.jar`, **1,691 bytes**, SHA-256 `cdba98e78707174d2045924d608b2a2e3591aa091cfec58f82be3111d5664f4f` |
| Packager log | `built jar with hash zbqY54cHF00gRZJNYIsqLjWRqgkc_sWPgr4xEdVmT08 from commit f3a73150…` (same SHA-256, base64url) |
| Verification | `FoundationVerifier packaged`: bytecode major 55, entries = manifest + 1 class + `runelite_plugin.json`, allowed references only, log/hash/commit match |
| Retained | Actions artifact `fr-001-official-package-f3a73150c624749785e271646bef3c5f4a43c861` (JAR, packager log, source zip, manifest diff, evidence, SHA256SUMS), 90-day retention |
| CI job | https://github.com/feralenterprises/Project-Raccoon/actions/runs/45893596318/job/140161948355 |

The post-processed JAR embeds the commit prefix in `runelite_plugin.json`, so every commit
produces a different JAR hash. Each push produces its own retained receipt.

## FR-001 — accepted receipt (roadmap source)

Issue #1 names the FR-000 source `cb3bc8b1942377de8e95711d8d57e45b6b1f003c` for FR-001. The first
FR-001 runs packaged later commits (identical plugin source), so this exact commit was packaged
on 2026-11-26 and its receipt independently re-verified on a fresh runner.

| Item | Value |
|---|---|
| Accepted source SHA | `cb3bc8b1942377de8e95711d8d57e45b6b1f003c` |
| Packager | plugin-hub-tooling `v6` (`c9e2fb4fb1d49a860ef68bc6fe34c0dab4f60aa9`), bundle `8ca79aa7…fe76acd` |
| Post-processed JAR | **1,690 bytes**, SHA-256 `509fef9d67fd1cb2188562b73e761e8356be48c97dda2fd76225de88e5b69e3d` |
| Retained | artifact `15156441662` (`fr-001-official-package-cb3bc8b1…`), run `45901758622`, zip digest `sha256:f3887911eb6b48d10920bc50acfde8907c21a6388897598e89b33cfa26c45453`, expires 2027-02-24 |
| Packaging job | `140188975299` |
| Independent verification job | `140189267305`: SHA256SUMS OK, evidence names the commit, source archive 22/22 files equal the commit tree, verifier `packaged` PASS (major 55, 7 entries) |
| Earlier receipts re-verified | `f3a73150` (1,691 B, `cdba98e7…`) job `140188975677`; `65aac3d2` (1,690 B, `25a3eed6…`) job `140188975528` |

The post-processed JAR embeds packaging-time metadata (`runelite_plugin.json`), so repackaging
the same commit gives a different hash; the retained artifact above is the accepted one.

## FR-002 — account lifecycle (A01–A06)

**Result: implemented; all regression and packaging checks PASS** (awaiting owner review).

| Item | Value |
|---|---|
| Source SHA | `9257838fe5227d06bd7482f425bbff1c1a3cc431` |
| Tests | 3 suites, **54 tests**, 0 failures, 0 errors, 0 skipped (39 lifecycle A01–A06, 10 adapter, 5 descriptor) |
| Verifier self-test | 40/40 |
| Development JAR | **27,398 bytes**, SHA-256 `0e2ba235a39b5226df506fb4c4d01acf635fa121f7615945ff51ab41ef04fc05`, 22 classes, major 55, reproducible |
| Official post-processed JAR | **27,763 bytes**, SHA-256 `aa490c6046f16336e49cf1a8b51f000872898a4b794c8783b5876fc4d01ced23` (strict, non-upload, plugin-hub-tooling v6) |
| Retained | artifact `15157520643`, run `45904083364`; re-verified on a fresh runner (job `140196840758`): source archive 48/48 files equal the commit tree |
| FR-001 regression | accepted `cb3bc8b1` receipt re-verified (job `140196552456`); `cb3bc8b1` repackaged and verified (jobs `140196552345`, `140196840774`) |
| CI jobs | foundation `140196551830`, `140196570537`; package head `140196551875` |

## FR-002 — independent audit repair (2026-11-26)

**Status: awaiting independent re-review (not accepted).**

| Item | Value |
|---|---|
| Reproduction commit | `1792d0d65ca32e532859551819ea0007e2a8d3fd`: 12 new regression tests; CI failed at "Clean build and tests" (jobs `140201108174`, `140201126359`); packaging unaffected |
| Repair source SHA | `aedb673dc081dcd85ed533188122919bbf2f371c` |
| Tests | 7 suites, **81 tests**, 0 failures, 0 errors, 0 skipped |
| Verifier self-test | 40/40 |
| Development JAR | **30,106 bytes**, SHA-256 `095b1425a611770d3f44c6d6b591ddb042d72e51dabfd1b26b5a988fd302ce7c`, 24 classes, major 55, reproducible |
| Official post-processed JAR | **30,469 bytes**, SHA-256 `3b41e7edf54515e134ec668339b5a06a613464e7e9e2e4205bf69b3b291ae8fe` |
| Retained | artifact `15156989066` (zip `sha256:f415b242f843ef599d7163fdf98dbfd09f6158b4fd1d9f67dc2b7a94b58bce50`), run `45906081364`; fresh-runner re-verification job `140203270893`, source archive 53/53 |
| CI jobs | foundation `140203001141`, `140203017891`; package head `140203001603` |
| Regression of earlier receipts | `cb3bc8b1`, `f3a73150`, `65aac3d2`, `9257838f` all re-verified (jobs `140203001565`, `140203001721`, `140203001308`, `140203001607`) |

## FR-002 — re-review repair (2026-11-26)

**Status: awaiting independent re-review (not accepted).**

| Item | Value |
|---|---|
| Reproduction commit | `95d2133aa99f8fb0fb2148206672a45eb63d8edf`: 5 new tests (4 failing, 1 guard); CI failed at "Clean build and tests" (jobs `140207352136`, `140207366835`); packaging unaffected |
| Repair source SHA | `9e625d23c130f822607d6e0a6740032e324f6113` |
| Tests | 7 suites, **86 tests**, 0 failures, 0 errors, 0 skipped; verifier 40/40 |
| Development JAR | **30,385 bytes**, SHA-256 `01223c918c0551002b9e56795da4b90f089782ffac02b73d734c0e5e4fa823a3`, 24 classes, major 55 |
| Official post-processed JAR | **30,749 bytes**, SHA-256 `6fefbf4b600edcec7054b00b8371c987c73c5d4db728d0ff6a5dce0a75ab5354` |
| Retained | artifact `15157699896` (zip `sha256:0086b379fc71c5c1675b8d5adcda948493517e08a3ac4fb3a4c3b7511048f284`), run `45907558652`; fresh-runner re-verification job `140208099921`, source archive 53/53 |
| CI jobs | foundation `140207845581`, `140207861867`; package head `140207845683`; all 5 earlier receipts re-verified |

## FR-003 — read-only bank observation (B01–B10)

**Status: HOLD at `f556ebb5` (event-time validation); repair below awaiting re-review.** FR-002 accepted at
`abf11bfacff86573a9de0451775f4d14e5c4f0b1`.

| Item | Value |
|---|---|
| Design record (before code) | `16ef87d8f567c6698c1cfab7da5acee833179660` (`docs/bank-observation.md`, migration manifest) |
| Source SHA | `4d7ac59561514d83207bf8cd5b01a5e513d10822` (`93c95b1b` + exact gameval allowlist; `93c95b1b` failed the artifact check, see PR) |
| Tests | 9 suites, **139 tests**, 0 failures, 0 errors, 0 skipped (45 bank B01–B10 + limits, 3 bank adapter, 86 earlier) |
| Verifier self-test | 40/40 |
| Development JAR | **63,214 bytes**, SHA-256 `ae387b9f4c6080eea7f84b543a034eb510ad0a78dc0f4a40c26a1f2cf245e4e4`, 50 classes, major 55 |
| Official post-processed JAR | **63,578 bytes**, SHA-256 `c33070c601249dc3ee5ffb394a59df7f88ac1e4fb3468f5235ebd5ea747de583` |
| Retained | artifact `15160040730` (zip `sha256:eb179a3a3ef0624f9aacd16b3a3611eecbd2b71192db3324c602c847540e6414`), run `45910387217`; fresh-runner job `140217308730`, source archive 77/77 |
| CI jobs | foundation `140217064267`, `140217078672`; package head `140217064345`; 6 earlier receipts re-verified |

## FR-003 — independent review repair (2026-11-26)

**Status: awaiting independent re-review (not accepted).**

| Item | Value |
|---|---|
| Donor reconciliation (before code) | `98c35ad8474d39ad5845ab73fb4d09fa0e70d03f` (Burrow `eb962df3`, three files, read-only) |
| Reproduction | `dd814398cecb0b5a6ce4836208fb7be17fea8a7b`: 21 tests (20 failing, 1 guard); CI build/test failed (jobs `140221820130`, `140221837782`) |
| Repair | `407c68b851d529a8ad0728145b851cf1485e12ef` (one of two CI runs failed; diagnosed below) |
| CI diagnostics | `7f76ee5e8fec57287e07f15873e08f0bb42faddc`: failure annotations; both runs then showed `LifecycleConcurrencyTest` "delivery after cancel" |
| FR-002 contract fix | `312e5e8c54487354d9d957e04ea7a92aa04fcee8`: `cancel()` waits for an in-progress delivery (lifecycle and bank tracker) |
| Tests @ `312e5e8c` | 10 suites, **160 tests**, 0 failures/errors/skips, both CI runs; verifier 40/40 |
| Development JAR | **65,916 bytes**, SHA-256 `101a22b5d4deeae77f97a3a19c8370d62d6f8f5a32e849c261873aea89a400d5`, 50 classes, major 55 |
| Official post-processed JAR | **66,280 bytes**, SHA-256 `b27cfb326b16f3bcff5c7e51970f8147ecab4d305352fdc3ce7e2f219d9b51e3` |
| Retained | artifact `15161276694` (zip `sha256:a6019101a3d2f5cee189120908dcf03272b6a48cbd2ba0f229fee13c0df625cd`), run `45913262497`; fresh-runner job `140226611409`, source archive 78/78 |
| CI jobs | foundation `140226372467`, `140226387143`; package head `140226373359`; 7 earlier receipts re-verified |

## FR-003 — namespace and cancellation repair (2026-11-26)

**Status: awaiting independent re-review (not accepted).**

| Item | Value |
|---|---|
| Reproduction | `de93b5bee3a38617e0ca908fffd164208b8f697c`: CI failed on both runners with exactly the 4 intended tests (3 `ScopeNamespaceTest`, 1 `BankSubscriptionTest` same-round); jobs `140229919828`, `140229936938` |
| Repair | `8452cd8cd4b8469e03ef4bb4a3ba3843fab6a454`: 13 suites, **171 tests**, 0 failures/errors/skips on both runners (`140230717206`, `140230730880`); verifier 40/40 |
| Development JAR | **66,976 bytes**, SHA-256 `de7ce90907d97cb508f198af27acee990d40214194a57b5e807fdcce79e80065`, 51 classes, major 55 |
| Official packaging @ `8452cd8c` | **Not produced**: the official packager's `prepare.sh` could not download `protobuf-javalite:3.21.12` from Maven Central (job `140230717268`); artifact `15161622293` holds no JAR and is not a receipt. The following evidence-only commit packages identical code; its receipt is the handback artifact |

## FR-002 + FR-003 accepted receipt

Independently accepted at `7f0d580bcc825b4ce7d7c136fca61d8c90ad9597`: official JAR **67,341 bytes**,
SHA-256 `81523f3bdadaae16da613d69f32d1d9ca0c447fc7afca2652b5ee39b19943eca`, artifact `15161991699`
(run `45915565584`); now in continuing CI re-verification (`verify-recorded`).

## FR-004 — Brutus kill count (K01–K05)

**Status: implemented; awaiting independent review (not accepted).**

| Item | Value |
|---|---|
| Design record (before code) | `5c6acb7b2762fb6ea558d4b264c3bd5980fe6113` (`docs/kill-count.md`; Burrow `eb962df3` KC files, read-only) |
| Implementation | `05c4dfde9c0793bbd1ad2a0e62479dd3ca48968d` (both foundation runs failed resolving `lombok` from Maven Central before compiling; packaging passed) |
| Source SHA | `e21a232681a2c3df159065405886be5d2b4dadd3` (+ CI retry on dependency-download failure only) |
| Tests | 15 suites, **196 tests**, 0 failures/errors/skips (job `140241993607`); the second run (`140242010721`) failed resolving `lombok` on both attempts, no tests ran |
| Verifier self-test | 40/40 |
| Development JAR | **75,212 bytes**, SHA-256 `13934b87f98007b89df3ab333d56e2465ed815ea9f37884ddf9b58c3717ec1fa`, 58 classes, major 55 |
| Official post-processed JAR | **75,576 bytes**, SHA-256 `e91ed9d869dc44030380d1537c8bfeb919a82c720d2070847bd94542e08f1f98` |
| Retained | artifact `15162748952` (zip `sha256:252e3295b26e2b076175f9d994dc45ad730ec7d98cd01c41baefca5149781e82`), run `45918076749`; fresh-runner job `140242250366`, source archive 90/90 |

### FR-004 review repair (HOLD at `f556ebb5`: event-time validation)

**Status: three KC gain defects confirmed fixed at re-review; lifecycle authority defect found (see next section).**

| Item | Value |
|---|---|
| Red regressions | `4371f2798ccb0cc548238e510ab2886b7ec6c0c0`: `BrutusKcEventValidationTest`, 7 failed in both foundation runs (`140248970810`, `140248949231`), ordinary-operation guard passed |
| Source SHA | `0f983cffe542192bc998790ad73d3d6b5224eb54` (`AccountLifecycleService.scopeForEventNow()`; CI retry limited to dependency transport failures, both attempt logs uploaded) |
| Tests | 16 suites, **204 tests**, 0 failures/errors/skips, both foundation runs (`140249430632`; `140250584265` after a lombok transport failure on both attempts in `140249451695`, retried correctly, then re-run) |
| Verifier self-test | 40/40 |
| Development JAR | **75,499 bytes**, SHA-256 `528e6d7235031d50f36b1b22968e5f83500c8194fe786e80b7be6c7f77d6301c`, 58 classes, major 55 |
| Official post-processed JAR | **75,863 bytes**, SHA-256 `07baa3e37a3a6324d887b5c8eed461eaa0629fca972dff6999bbcfcc2f2c99b2`, 71 entries |
| Retained | artifact `15164481747` (zip `sha256:0a4e18156bc8365ce33f22f3e7a45204d58ad4db16b43592e26af664d1c82a35`), run `45920384599`; fresh-runner job `140249662936`, source archive 91/91 |

### FR-004 targeted re-review repair (lifecycle authority at event time)

**Status: FR-002 integration reopened, FR-003 dependency HOLD, FR-004 HOLD (re-review of `0f983cff`); repair below awaiting targeted re-review (not accepted).**

| Item | Value |
|---|---|
| Red regressions | `20b7e63a5dfeea43cbf49e747a02fb4ccfbf36aa`: `EventAuthorityTest` (new) and corrected `BrutusKcEventValidationTest`; 219 tests completed, **18 failed** in both foundation runs (`140257544870`, `140257530787`); guards for verified hop, reconnect, no restoration and no activation passed |
| Source SHA | `3e5cd549a500d0364bafd2d14ac165a20f40df06` (`AccountLifecycle.onEventObservation`; `scopeForEventNow` reads once and uses it) |
| Tests | 17 suites, **220 tests**, 0 failures/errors/skips, both foundation runs (`140258295136`, `140258311207`) |
| Verifier self-test | 40/40 |
| Development JAR | **75,974 bytes**, SHA-256 `aad0eb63589bd5145558fdb18cb23ac7a0292369b0fb2c11043db8acfe230ad2`, 58 classes, major 55 |
| Official post-processed JAR | **76,337 bytes**, SHA-256 `37e5727a369b05f1f5d8fa51bed3d4f47e0a697c6283a76c6508d1cf5264f76b`, 71 entries |
| Retained | artifact `15164758300` (zip `sha256:83744d07c4742f4c0fdf9fe38355e7f0139a4df0953edcfa6461f00966ed3f28`), run `45923139849`; fresh-runner job `140262308717`, source archive 92/92 |
| Infrastructure | historical `cb3bc8b1` packaging leg failed resolving protobuf/lombok from Maven Central; re-run green (`140262308782`) |

### FR-004 targeted re-review repair (A04/K05: observed transition deadline)

**Status: re-review of `3e5cd549` verified the authority repair; one A04/K05 boundary defect found; repair below awaiting targeted re-review (not accepted).**

| Item | Value |
|---|---|
| Red regressions | `a60ed27dc06a633c48bd0fdc65b8f3189b498c52`: 234 tests, **10 failed** in both foundation runs (`140268797213`, `140268819938`); `83c0c8301d3859f7992f3a9d4bc6d92daab168a7` (re-observed transition, event past the deadline): 238 tests, **14 failed** in both runs (`140269550408`, `140269564222`) |
| Source SHA | `41eb2aced6472363f836ffd0aa07e1fb0d0a828b` (observed HOPPING / CONNECTION_LOST suspends, as the phase event) |
| Tests | 18 suites, **238 tests**, 0 failures/errors/skips, both foundation runs (`140269633894`, `140269652631`) |
| Verifier self-test | 40/40 |
| Development JAR | **75,875 bytes**, SHA-256 `cd899d3f5236561d1ef45f960b82d89785e3acf6ee84889078f67663f6b99f83`, 58 classes, major 55 |
| Official post-processed JAR | **76,238 bytes**, SHA-256 `807089c91bc8651ded67b3ffb5b5ec13cc765c6d69f48653a8774d9601f65d0c`, 71 entries |
| Retained | artifact `15166766655` (zip `sha256:2296476377c390c1f86388bdc7b384d72ff978cc7b0016e6ebdcf800c78bf595`), run `45926719072`; fresh-runner job `140269888590`, source archive 93/93 |

## Size history (SR-6)

| Handback | Source | Dev JAR bytes | Dev JAR SHA-256 | Official JAR bytes | Official JAR SHA-256 | Δ official |
|---|---|---|---|---|---|---|
| FR-000 | `cb3bc8b1` | 1,326 | `96edc7c9…a97c1ea` | not run | — | — |
| FR-001 | `f3a73150` | 1,326 (Δ 0) | `96edc7c9…a97c1ea` | 1,691 | `cdba98e7…5664f4f` | first measurement |
| FR-001 accepted | `cb3bc8b1` | 1,326 | `96edc7c9…a97c1ea` | 1,690 | `509fef9d…b69e3d` | −1 vs `f3a73150` (metadata) |
| FR-002 | `9257838f` | 27,398 (+26,072) | `0e2ba235…04fc05` | 27,763 | `aa490c60…01ced23` | +26,073 vs accepted FR-001 (lifecycle: 22 classes) |
| FR-002 repair | `aedb673d` | 30,106 (+2,708) | `095b1425…302ce7c` | 30,469 | `3b41e7ed…91ae8fe` | +2,706 vs `9257838f` (freshness, delivery, gate: 24 classes) |
| FR-002 re-review repair | `9e625d23` | 30,385 (+279) | `01223c91…fa823a3` | 30,749 | `6fefbf4b…ab5354` | +280 vs `aedb673d` |
| FR-003 | `4d7ac595` | 63,214 (+32,829) | `ae387b9f…245e4e4` | 63,578 | `c33070c6…747de583` | +32,829 vs `9e625d23` (bank observation: 26 classes) |
| FR-003 review repair | `312e5e8c` | 65,916 (+2,702) | `101a22b5…89a400d5` | 66,280 | `b27cfb32…9d9b51e3` | +2,702 vs `4d7ac595` |
| FR-002/003 accepted | `7f0d580b` | 66,976 | `de7ce909…963b5` | 67,341 | `81523f3b…19943eca` | accepted receipt |
| FR-004 | `e21a2326` | 75,212 (+8,236) | `13934b87…3717ec1fa` | 75,576 | `e91ed9d8…08f1f98` | +8,235 vs `7f0d580b` (Brutus KC: 7 classes) |
| FR-004 review repair | `0f983cff` | 75,499 (+287) | `528e6d72…d6301c` | 75,863 | `07baa3e3…2c99b2` | +287 vs `e21a2326` (event-time validation) |
| FR-004 re-review repair | `3e5cd549` | 75,974 (+475) | `aad0eb63…e230ad2` | 76,337 | `37e5727a…5264f76b` | +474 vs `0f983cff` (central event-time observation) |
| FR-004 re-review repair (2) | `41eb2ace` | 75,875 (−99) | `cd899d3f…6b99f83` | 76,238 | `807089c9…01f65d0c` | −99 vs `3e5cd549` (special case removed) |

Limits: foundation 262,144 bytes · release target < 8,000,000 bytes · ceiling 10,485,760 bytes.
