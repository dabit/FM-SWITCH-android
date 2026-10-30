status: pending
# 05 — `:fm1` package checks (`.fwsc`, container, application area, official firmware, sample builder)

Depends on: 02, 03

## Why
Before anything is sent to an FM-1 the package must be parsed and checked as FM1Kit does — markers, container CRCs,
parts, the official V15 constants — and the V15-over-other-bootloaders refusal is kept as a safety check; the `:model`
library and every updater test build on these types and on the sample-package builder. Rests on: Protocol source;
V15-over-other-bootloaders refusal.

## Description
Plan step 4. Depends on task 02 because `bundledV15IsTheOfficialFirmware` reads the bundled V15 through the `fm1.v15`
system property (task 01 points it at task 02's asset).

**Getting FM1Kit:** clone it **outside the repository** and check the commit:
`git clone --depth 1 --branch 0.4.5 https://github.com/ocean/FM1Kit "$SCRATCH/FM1Kit" && git -C "$SCRATCH/FM1Kit" rev-parse HEAD`
→ `88b609cd8c631b884d977b7954cb0b21351f2f04`. Paths are under `Sources/FM1Kit/` unless they start with `Tests/`.

**Ports:** `Package/FwscPackage.swift` (parse, marker stripping, `sha256` of the **file**, the 2,097,152-byte cap,
`refusesV15OverOtherBootloaders`, `usesFeluccaUpdateCode`), `Package/FirmwareContainer.swift:81-191`
(`init(image:)` at `:81`), `Package/ApplicationArea.swift:20-158` with **M-Flasher's MIT notice (`:1-19`) at the top
of `ApplicationArea.kt`**, official firmware constants `Package/FwscPackage.swift:195-239` (`OfficialFirmware`,
`matching` at `:237`), `Package/SamplePackage.swift:45-149`. Test-only builder ported from FM1Kit
`Tests/FM1RescueTests/SampleFlash.swift:65-115` (`applicationArea(code:regions:)`, `record`, `sign` only — not the boot
area, not FM1Rescue). Each ported file starts with the FM1Kit notice line used in task 03.

- `sha256` is of the **file**; the logical image drops the 20 marker bytes; `applicationRange` = flash offset + 0x4000
  ..< end. `SamplePackage` goes in **main** (`dev.drewr.fm1.testing`), mirroring FM1Kit's `@_spi(Testing)`, so `:model`
  and `:app` tests can build packages — no `java-test-fixtures`.
- `refusesV15OverOtherBootloaders` and `usesFeluccaUpdateCode` are ported for the Before-you-start start-up question.
- Virtual-time rule: no `Thread.sleep`, wall clock, `runBlocking` or `Dispatchers.*` in this code.

**Tests** — every case under `### :fm1 — step 4` in `PLAN.md`'s Testing Strategy, **except**
`readsOutsideTheRegionLeaveProgressUnchanged` / `readsAcrossTheRegionCountOnlyTheirInsidePart`, which test `WriteProgress`
and belong to task 09. Method names:
- `PackageCheckTest` (FM1Kit `Tests/FM1KitTests/PackageCheckTests.swift`, `ProtocolTests.swift:85-98`;
  `aSampleFilePassesEveryCheck` from `Tests/FM1KitTests/OpenFirmwareTests.swift:254`): `decodesProductAndStripsMarkers`,
  `rejectsOtherFiles`, `crc16IsXModem`, `decipheringTwiceGivesBackTheOriginal`, `readsTheFirmwareImageAndLoader`,
  `aFirmwareImageNoBiggerThanTheBootAreaIsAllApplication`, `refusesADamagedHeader`, `refusesADamagedTable`,
  `refusesAnotherChip`, `refusesADamagedPart`, `doesNotCheckEncipheredParts`, `refusesAPackageWithoutALoader`,
  `refusesAFirmwareImageThatIsNotWholeSectors`, `refusesOverlappingParts`, `acceptsPaddingPastTheEnd`,
  `refusesAPartPastTheEnd`, `refusesAFileTooLargeForAnFM1`, `aSampleFilePassesEveryCheck`.
- `OfficialFirmwareTest`: `bundledV15IsTheOfficialFirmware` (size 699,956, SHA-256 `db1642b2…edb8a`,
  `OfficialFirmware.V15.matches`, product `FM-1_015`, loader-part SHA-256
  `ef1f2db89b011fa53e71df363f0122de0ea896618c53fb53e8da676322004541` — `PLAN.md` Appendix, `v15.py`).
- `ApplicationAreaTest` (port of FM1Kit `Tests/FM1RescueTests/DownloadModeTests.swift:159-166`):
  `refusesV15WhenFeluccasUpdateCodeLacksTheFix`, `takesV15WhenFixed` (the `AC79BLID` clause), `takesV15WhenHashed`
  (the 32 V15 image-hash bytes) — the plan's `takesV15WhenFixedOrHashed`, one method per clause —
  `plainSampleDoesNotUseFeluccasCode`.

## Files
- fm1/src/main/kotlin/dev/drewr/fm1/pkg/FwscPackage.kt
- fm1/src/main/kotlin/dev/drewr/fm1/pkg/FirmwareContainer.kt
- fm1/src/main/kotlin/dev/drewr/fm1/pkg/Crc16.kt
- fm1/src/main/kotlin/dev/drewr/fm1/pkg/Decipher.kt
- fm1/src/main/kotlin/dev/drewr/fm1/pkg/OfficialFirmware.kt
- fm1/src/main/kotlin/dev/drewr/fm1/pkg/ApplicationArea.kt
- fm1/src/main/kotlin/dev/drewr/fm1/testing/SamplePackage.kt
- fm1/src/test/kotlin/dev/drewr/fm1/pkg/SampleApplicationArea.kt
- fm1/src/test/kotlin/dev/drewr/fm1/pkg/PackageCheckTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/pkg/OfficialFirmwareTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/pkg/ApplicationAreaTest.kt

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
`R=fm1/build/test-results/test`. Task 06 (docs) runs beside this one and touches no code.

1. `./gradlew :fm1:test --rerun` — expected: `BUILD SUCCESSFUL` (task 03's tests stay green too).
   red when: any `:fm1` test fails or the module warns.
2. `comm -23 <(printf '%s\n' decodesProductAndStripsMarkers rejectsOtherFiles crc16IsXModem decipheringTwiceGivesBackTheOriginal readsTheFirmwareImageAndLoader aFirmwareImageNoBiggerThanTheBootAreaIsAllApplication refusesADamagedHeader refusesADamagedTable refusesAnotherChip refusesADamagedPart doesNotCheckEncipheredParts refusesAPackageWithoutALoader refusesAFirmwareImageThatIsNotWholeSectors refusesOverlappingParts acceptsPaddingPastTheEnd refusesAPartPastTheEnd refusesAFileTooLargeForAnFM1 aSampleFilePassesEveryCheck bundledV15IsTheOfficialFirmware refusesV15WhenFeluccasUpdateCodeLacksTheFix takesV15WhenFixed takesV15WhenHashed plainSampleDoesNotUseFeluccasCode | sort -u) <(cat $R/TEST-*.xml | grep -o 'testcase name="[^"]*"' | sed 's/testcase name="//;s/"$//' | sort -u)`
   — expected: no output (all 23 methods ran).
   red when: a case is missing or renamed.
   control: the same command with `zzNoSuchCase` added → prints `zzNoSuchCase`.
   at base: none — written by tasks 03 and 05.
3. `grep -ho 'failures="[0-9]*"' $R/TEST-*.xml | sort -u` — expected: only `failures="0"`.
   red when: any class recorded a failure.
   at base: none — written by this task.
4. `grep -o 'testcase name="bundledV15IsTheOfficialFirmware"[^>]*' $R/TEST-dev.drewr.fm1.pkg.OfficialFirmwareTest.xml; grep -c '<skipped' $R/TEST-dev.drewr.fm1.pkg.OfficialFirmwareTest.xml`
   — expected: one testcase line, then `0`.
   red when: the V15 test was skipped or assumed away because `fm1.v15` didn't resolve.
   at base: none — written by this task.
5. `head -25 fm1/src/main/kotlin/dev/drewr/fm1/pkg/ApplicationArea.kt | grep -c 'Julius Vitkauskas'` — expected: `1`.
   red when: the M-Flasher notice isn't at the top of the file that ports its code.
   at base: none — written by this task.
6. `grep -L 'Ported from FM1Kit 0.4.5' fm1/src/main/kotlin/dev/drewr/fm1/pkg/*.kt fm1/src/main/kotlin/dev/drewr/fm1/testing/SamplePackage.kt`
   — expected: no output.
   red when: a ported file lacks FM1Kit's attribution.
   control: `grep -L 'Ported from FM1Kit 0.4.5' fm1/build.gradle.kts` → prints it.
   at base: none — written by this task.
7. `grep -nE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|runBlocking|Dispatchers\.' fm1/src/main/kotlin/dev/drewr/fm1/pkg/*.kt fm1/src/main/kotlin/dev/drewr/fm1/testing/SamplePackage.kt`
   — expected: no output.
   red when: blocking or wall-clock code enters `:fm1`.
   control: `printf 'runBlocking { }\n' | grep -nE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|runBlocking|Dispatchers\.'` → 1 line.
   at base: none — written by this task.
   compliant hits: none.
8. Mutation — `fm1/src/main/kotlin/dev/drewr/fm1/pkg/FwscPackage.kt`, property `sha256`: hash the logical image instead
   of the file; `./gradlew :fm1:test --rerun --tests 'dev.drewr.fm1.pkg.OfficialFirmwareTest'` → `FAILED` on
   `bundledV15IsTheOfficialFirmware`; revert, re-run → green.
   red when: the real-file test doesn't notice the wrong hash input.
9. Mutation — `fm1/src/main/kotlin/dev/drewr/fm1/pkg/Crc16.kt`: initial value `0xFFFF`;
   `./gradlew :fm1:test --rerun --tests 'dev.drewr.fm1.pkg.PackageCheckTest'` → `FAILED` on `crc16IsXModem`; revert,
   re-run → green.
   red when: the XModem check value is unguarded.
10. Mutation — `fm1/src/main/kotlin/dev/drewr/fm1/pkg/ApplicationArea.kt`, the per-block key: drop `^ (block >> 2)`;
    `./gradlew :fm1:test --rerun --tests 'dev.drewr.fm1.pkg.ApplicationAreaTest'` → `FAILED` on
    `refusesV15WhenFeluccasUpdateCodeLacksTheFix`; revert, re-run → green.
    red when: a wrong decipher key goes unnoticed.
