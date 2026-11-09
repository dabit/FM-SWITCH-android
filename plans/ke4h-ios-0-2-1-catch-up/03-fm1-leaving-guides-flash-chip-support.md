status: pending
# 03 — `:fm1`: update codes, leaving guides, flash chip support, sample packages with code

Depends on: 02

## Why
Routing an install through MIDIMON, and telling TransitFM1 from V15, both need FM1Kit 0.6.0's update codes and leaving
guides. Warning about firmwares that only know the older flash chip needs `FlashChipSupport`. Both read V16's identity
(`Fm1Identity.isOfficial`, `officialV16`) from task 02, and `FlashChipSupport` names `Fm1FlashChip`'s IDs, also from
task 02. Rests on: D5, D9, P-D3.

## Description
Implement **PLAN.md › Implementation Plan › Task 03, steps 1 and 3–6**, verbatim; they are binding. It ports FM1Kit
0172d0e, 9a0eecf, 31f12a5 and cee1a84's package half: FM1Kit, not FM1Rescue (`research-v16.md` §1). FK is at
`/tmp/claude-1000/-home-dabit-git-FM-SWITCH-android/5e980def-0c27-4c1a-9352-a41b3172dfbb/scratchpad/FM1Kit` (@ `53f8511`).

Split changes:
- **Step 2 (`Fm1FlashChip`) moved to task 02.** Read `Fm1FlashChip.P25Q80`/`UC25HQ80` from there; don't create the file.
- **`HubFlashChip.support` is no longer task 05's** (it moved to task 07). Nothing in this task changes because of that.
- `refusesV15OverOtherBootloaders` stays in `ApplicationArea.kt` with its new meaning (step 4). `ApplicationAreaTest`
  keeps every base case.

**What later tasks read from this one** (observation 0654):
- From `KF/testing/SamplePackage.kt`: `SamplePackage.firmwareWithCode(code, product = "FM-1_378001")`,
  `SamplePackage.firmwareWithAppRecord(code, address, length, product)` and `SamplePackage.withLoader(product, loader)`.
  Tasks 07 and 08 use them in tests.
- `FwscPackage.loaderBytes`, which task 08 uses to lift V15's real loader.
- From `KF/pkg/LeavingGuide.kt`: `UpdateCode` (with `raw`), `Fm1BootArea` (with `raw`), `LeavingDestination`,
  `LeavingRoute`, `LeavingGuide` with `from(...)`, `route(to)`, `warnings`, `instructions(to)` and `FIXED_FIRMWARES`, and
  the extension `FwscPackage.updateCode`.
- `FlashChipSupport` (`BOTH_CHIPS`, `FIRST_CHIP_ONLY`, `NO_CHECK`) and `FwscPackage.flashChipSupport`.

Task 07 needs `LeavingGuide`'s `Fm1Identity identity`, `updateCode` and `bootArea` to be public constructor properties
(`SavedGuide` round-trips them), and `LeavingGuide`'s constructor public (iOS's `transit` test guide is hand-built).

**VERIFY-AT-EXECUTION** (step 1): check that `loaderSha256` is the SHA-256 of exactly the bytes `loaderBytes` returns, by
reading `FwscPackage`'s init. If it isn't, expose whatever range `loaderSha256` hashes, and say so in the hand-back.
Task 08's `installingV16SetsV16sStartUp` depends on this.

New and touched `:fm1` files carry the `// Ported from FM1Kit 0.6.0 …` MIT header.

**Test method names are binding:** use exactly the names in Verification step 2, and keep every surviving base case listed there through any rewrite. Where a name differs from the Testing Strategy's wording, step 2 wins; UAT step 3 re-checks the same names.

**Executor environment.** Codex in a sandbox: no network, no emulator, `.git` read-only. Never `git add` or commit.
Tasks 04, 06 and 09 run alongside this one; 04 also edits `:fm1`. A compile or test error whose path is outside this
task's `## Files` comes from a sibling mid-edit: wait and re-run. If a symbol you need is missing from a file this task
doesn't own, stop and report it.

## Files
- fm1/src/main/kotlin/dev/drewr/fm1/testing/SamplePackage.kt
- fm1/src/main/kotlin/dev/drewr/fm1/pkg/FwscPackage.kt
- fm1/src/main/kotlin/dev/drewr/fm1/pkg/ApplicationArea.kt
- fm1/src/main/kotlin/dev/drewr/fm1/pkg/LeavingGuide.kt
- fm1/src/main/kotlin/dev/drewr/fm1/pkg/FlashChipSupport.kt
- fm1/src/test/kotlin/dev/drewr/fm1/pkg/SampleApplicationArea.kt
- fm1/src/test/kotlin/dev/drewr/fm1/pkg/ApplicationAreaTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/pkg/LeavingGuideTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/pkg/FlashChipSupportTest.kt

## Verification
Setup for every step: `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"; F=fm1/build/test-results/test`.
Use the helpers `ran` (prints every expected test method that did not run) and `tally`, defined exactly as in task 02's
Verification setup.
Base: `:fm1` tests=124, 0 failures (Splitter, `f5b9505`).

1. `./gradlew :fm1:test --rerun` — expected: `BUILD SUCCESSFUL`.
   red when: any `:fm1` test fails or the module doesn't compile.
   at base: `BUILD SUCCESSFUL`, 124 tests.
2. `ran $F readsHowAFirmwareInstallsOthers sendsPeopleThroughAFixedFirmwareFromTheOriginalCheckOnM_Bootloader letsTheOriginalCheckTakeOnlyTheBootloaderTheFM1Has letsAFixedFirmwareGoAnywhere warnsThatTransitFM1ReportsV15sIdentityAndKeepsTheBootloader keepsTheBootloaderWhenAFirmwareComesWithItsOwnLoader letsV15InstallAnything readsWhichChipsTheCodeChecksFor saysNothingForAnInstaller transitFm1TakesV15 takesV15WhenFixed takesV15WhenHashed plainSampleDoesNotUseFeluccasCode rejectsAnOverflowingApplicationRange refusesV15WhenFeluccasUpdateCodeLacksTheFix`
   — expected: no output. Covers T03-update-code, T03-routes (the seven FK `LeavingGuideTests` other than
   `canBeSavedWithTheFM1`, which moved to task 07), T03-chip-support, T03-transit-takes-v15, and the five base
   `ApplicationAreaTest` cases.
   red when: a case is missing or renamed, or a base `ApplicationAreaTest` case was lost in the builder move.
   control: `ran $F zzNoSuchCase` prints `zzNoSuchCase`.
   at base: prints the ten new names; the five `ApplicationAreaTest` names ran.
3. `tally $F` — expected: `tests=` ≥ 134 (124 + 10), `failures=0 errors=0 skipped=0`.
   red when: a base test was deleted, or a failure or skip was recorded.
   at base: `tests=124 failures=0 errors=0 skipped=0`.
4. `grep -nE "fun (firmwareWithCode|firmwareWithAppRecord|withLoader)\(" fm1/src/main/kotlin/dev/drewr/fm1/testing/SamplePackage.kt; grep -n "val FwscPackage.loaderBytes\|val loaderBytes" fm1/src/main/kotlin/dev/drewr/fm1/pkg/FwscPackage.kt`
   — expected: three hits in `SamplePackage.kt` (one per builder), then one hit in `FwscPackage.kt`.
   red when: a builder that `:model` tests need is still test-only or missing (observation 0654: tasks 07 and 08 can't add
   it), or `loaderBytes` is missing.
   at base: no output from either grep. The builder lives in the test-only `SampleApplicationArea.kt`, which hard-codes
   `"FM-1_378001"`.
   compliant hits: none.
5. Mutation T03-update-code. In `fm1/src/main/kotlin/dev/drewr/fm1/pkg/LeavingGuide.kt`, drop the
   `OWN_LOADER_MARKER` branch from `FwscPackage.updateCode`. Then run `./gradlew :fm1:test --rerun --tests '*LeavingGuideTest'`:
   expected `FAILED` on `readsHowAFirmwareInstallsOthers`. Revert: green. Then drop the `identity.isOfficial ||` clause:
   expected `FAILED` on the same test. Revert: green.
   red when: update codes are read without the real decipher path, or the official identity isn't consulted.
   at base: none. The test is written by this task.
6. Mutation T03-routes. In `LeavingGuide.kt`, make the route by boot area return `DIRECT` for V15 whatever the boot area.
   Then run `./gradlew :fm1:test --rerun --tests '*LeavingGuideTest'`: expected `FAILED` on
   `sendsPeopleThroughAFixedFirmwareFromTheOriginalCheckOnM_Bootloader`. Revert: green.
   red when: the m-bootloader route is unguarded.
   at base: none. The test is written by this task.
7. Mutation T03-chip-support. In `fm1/src/main/kotlin/dev/drewr/fm1/pkg/FlashChipSupport.kt`, search for the IDs as
   big-endian bytes. Then run `./gradlew :fm1:test --rerun --tests '*FlashChipSupportTest'`: expected `FAILED` on
   `readsWhichChipsTheCodeChecksFor`. Revert: green.
   red when: the byte order is unguarded.
   at base: none. The test is written by this task.
8. Mutation T03-transit-takes-v15. In `fm1/src/main/kotlin/dev/drewr/fm1/pkg/ApplicationArea.kt`, restore
   `refusesV15OverOtherBootloaders` to the 0.4.5 marker rule. Then run
   `./gradlew :fm1:test --rerun --tests '*ApplicationAreaTest'`: expected `FAILED` on `transitFm1TakesV15`. Revert:
   green.
   red when: TransitFM1 is still said to refuse V15.
   at base: none. The test is written by this task.
9. `grep -L "Ported from FM1Kit 0.6.0" fm1/src/main/kotlin/dev/drewr/fm1/pkg/FwscPackage.kt fm1/src/main/kotlin/dev/drewr/fm1/pkg/ApplicationArea.kt fm1/src/main/kotlin/dev/drewr/fm1/pkg/LeavingGuide.kt fm1/src/main/kotlin/dev/drewr/fm1/pkg/FlashChipSupport.kt`
   — expected: no output.
   red when: a touched file still claims 0.4.5, or a new file lacks the MIT header.
   control: `grep -L "Ported from FM1Kit 0.6.0" fm1/src/main/kotlin/dev/drewr/fm1/protocol/SevenBit.kt` prints that path.
   at base: lists `FwscPackage.kt` and `ApplicationArea.kt`; the two new files don't exist.
10. `./gradlew :model:compileTestKotlin` — expected: `BUILD SUCCESSFUL` (`:model` doesn't call the moved builder yet).
    red when: the move broke a `:model` caller.
    at base: `BUILD SUCCESSFUL`.
