status: pending
# 08 — `:model` flash job and controller: pass 2, late returns, guides and start-up after an install, flash chip state, V16 summary

Depends on: 04, 07

## Why
The controller must stop showing a "Checking" stage someone might switch off during (pass 2 is writing). It must finish
an install when the FM-1 comes back running it after a late loss, and record the guide and start-up an install
leaves, so that V15 after V16 is routed and warned about correctly. It must also expose the FM-1's flash chip and name
V16 on Home. It reads task 04's `summary()`/`Fm1DeviceSummary` and return timing (and shares `FlashingTest.kt` with
04), and task 07's `FlashHistory.record(item, guide)`, `SwitchingGuide` and `FixedFirmware`. Rests on: D1, D5, P-D1,
P-D2, P-D7.

## Description
Implement **PLAN.md › Implementation Plan › Task 08, steps 1–7**, verbatim; they are binding. It ports the controller
half of iOS 76293d0 and fc809ca's app side (`mightHaveFinished`). Read `research-v16.md` §2 and §7 first. The iOS source
is `/home/dabit/git/FM-SWITCH` @ `fcf4a78`.

- P-D2: rename `FlashPhase.Verifying` to `SecondPass` (label `"WRITING PASS 2"`), counted as `Stage.Installing`;
  `InstallProgress.Stage.Checking` is deleted. Mechanical `:app` consumers: drop `InstallingOverlay.kt`'s
  `Stage.Checking` branch, and delete `install_stage_checking` from `strings_install.xml` (lint).
- P-D7: no new simulator option in `configure()`.
- Virtual time: the return search is `withTimeoutOrNull(RETURN_SEARCH)` with `delay(2.seconds)` steps. No clock.
- `FlashHistory.kt` is **not** edited here (task 07 added `record(item, guide)` and `FlashRecord.guide`).
- The controller test `aRefusalWithMVavesStartUpSuggestsV15First` stays, as the guard of the start-up fallback. The
  guide branches are new (`aRefusalNeedsMIDIMONUnlessTheFirmwareRunningTakesV15`).
- `installingV16SetsV16sStartUp` builds `SamplePackage.withLoader("FM-1_016", <V15's loaderBytes>)` from task 03,
  lifting V15's loader from the bundled file (`System.getProperty("fm1.v15")`).

**What later tasks read from this one** (observation 0654; exactly these names):
- `FlashJob.startedOnV16`, `FlashJob.confirmsReinstallingOfficial`, `FlashJob.guide` and `FlashJob.writtenFraction`;
  `FlashJob.needsAStepFirst` (the existing property) and the companions `FlashJob.mightHaveFinished`,
  `FlashJob.needsAStepFirst(…)`, `FlashJob.failureMessage` and `FlashJob.RETURN_SEARCH`.
- `DeviceController.flashChipId: StateFlow<Int?>` and `DeviceController.runningGuide`.
- `DeviceController.flash(steps, confirmsSwitchingFirmware, confirmsSameIdentity, confirmsReinstallingOfficial,
  startedOnV16)` and `DeviceController.installFirst(fixedFirmware)`.
- `FlashPhase.SecondPass`, and `DeviceSummary`'s V16 name.

**Test method names are binding:** use exactly the names in Verification step 2, and keep every surviving base case listed there through any rewrite. Where a name differs from the Testing Strategy's wording, step 2 wins; UAT step 3 re-checks the same names.

**Executor environment.** Codex in a sandbox: no network, no emulator, `.git` read-only. Never `git add` or commit. This
task runs alone in its wave. Delete any string this task orphans (lint `UnusedResources`).

## Files
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/FlashPhase.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/InstallProgress.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/FlashJob.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/DeviceController.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/DeviceSummary.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/FlashingTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/InstallFinishTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/StandardLookTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/SwitchingGuideTest.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/install/InstallingOverlay.kt
- app/src/main/res/values/strings_install.xml

## Verification
Setup for every step: `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"; M=model/build/test-results/test; P=app/build/test-results/testDebugUnitTest`.
Use the helpers `ran` (prints every expected test method that did not run) and `tally`, defined exactly as in task 02's
Verification setup.
Base: `:model` 92 (`FlashingTest` 19, `StandardLookTest` 6) and `:app` 15 tests, 0 failures (Splitter, `f5b9505`).

1. `./gradlew :model:test --rerun` — expected: `BUILD SUCCESSFUL`.
   red when: any `:model` test fails or the module doesn't compile.
   at base: `BUILD SUCCESSFUL`, 92 tests.
2. `ran $M looksAgainWhenTheFM1DidNotReturn looksAgainWhenTheWriteStoppedNearTheEnd doesNotLookAgainPartWayThrough doesNotLookAgainForAReinstallThatStopped doesNotLookAgainForOtherErrors aLateLossStillFinishesWhenTheFm1ComesBackRunningIt anInstallFromV16IsRemembered anInstallSavesItsGuide installingV15SetsMVavesStartUp installingV16SetsV16sStartUp aRefusalNeedsMIDIMONUnlessTheFirmwareRunningTakesV15 scanningKeepsTheFlashChip progressRunsThroughTheStagesInOrder summarySaysWhatTheFm1IsDoing aRefusalWithMVavesStartUpSuggestsV15First resumesAfterTheCableIsPulled flashesThroughALoaderThatReadsBackwardsAndVerifies`
   — expected: no output. Covers T08-might-have-finished, late-return, started-on-v16, records-guide-and-startup,
   needs-a-step, chip-state, progress and v16-name, plus base guards (the start-up fallback, cable-pull resume, and
   pass 2 on a backwards loader).
   red when: a T08 case is missing or renamed, or a guard was deleted.
   control: `ran $M zzNoSuchCase` prints `zzNoSuchCase`.
   at base: prints the 12 new names; the five existing names ran.
3. `tally $M` — expected: `tests=` ≥ 151 (139 after task 07 + 12), `failures=0 errors=0 skipped=0`.
   red when: tests were lost, or a failure or skip was recorded.
   at base: `tests=92 failures=0 errors=0 skipped=0`.
4. `grep -rnE 'FlashPhase\.Verifying|is Verifying|class Verifying|Stage\.Checking|Checking\("Checking"\)|install_stage_checking|"VERIFYING"' model/src app/src`
   — expected: no output.
   red when: the old phase or the "Checking" stage survived anywhere (Risk 1).
   control: `grep -rnE 'FlashPhase\.Verifying|Stage\.Checking' plans/ke4h-ios-0-2-1-catch-up/PLAN.md` prints ≥ 1 line.
   at base: 11 lines: `FlashPhase.kt` (2), `FlashJob.kt` (1), `InstallProgress.kt` (2, the enum entry and the
   `Verifying` mapping), `DeviceController.kt` (2), `StandardLookTest.kt` (2), `InstallingOverlay.kt` (1) and
   `strings_install.xml` (1).
   compliant hits: none. `InstallCheckState.Checking` is a different symbol, and the pattern doesn't match it.
5. `grep -c '"WRITING PASS 2"' model/src/main/kotlin/dev/drewr/fmswitch/model/device/FlashPhase.kt` — expected: `1`.
   red when: the renamed phase lost its label.
   at base: `0`.
   compliant hits: none.
6. Mutation T08-might-have-finished. In `model/src/main/kotlin/dev/drewr/fmswitch/model/device/FlashJob.kt`, use
   `written >= 0.5` in `mightHaveFinished`. Then run `./gradlew :model:test --rerun --tests '*InstallFinishTest'`:
   expected `FAILED` on `doesNotLookAgainPartWayThrough`. Revert: green.
   red when: a part-way failure could be reported as finished.
   at base: none. The test is written by this task.
7. Mutation T08-late-return. In `model/src/main/kotlin/dev/drewr/fmswitch/model/device/DeviceController.kt`, remove the
   `confirmed()` fallback from `run`. Then run `./gradlew :model:test --rerun --tests '*FlashingTest'`: expected
   `FAILED` on `aLateLossStillFinishesWhenTheFm1ComesBackRunningIt`. Revert: green.
   red when: the late-return path is unguarded.
   at base: none. The test is written by this task.
8. Mutation T08-records-guide-and-startup. In `DeviceController.kt`, don't set `settings.startUp` in `record()`. Then run
   `./gradlew :model:test --rerun --tests '*FlashingTest'`: expected `FAILED` on `installingV15SetsMVavesStartUp` and
   `installingV16SetsV16sStartUp`. Revert: green.
   red when: the start-up an install leaves isn't remembered (Risk 4).
   at base: none. The tests are written by this task.
9. Mutation T08-chip-state. In `DeviceController.kt`, make `scan()` use `updater.identify()`. Then run
   `./gradlew :model:test --rerun --tests '*FlashingTest'`: expected `FAILED` on `scanningKeepsTheFlashChip`. Revert:
   green.
   red when: the chip never reaches the screens.
   at base: none. The test is written by this task.
10. `./gradlew :app:testDebugUnitTest --rerun && ./gradlew :app:lintDebug` — expected: `BUILD SUCCESSFUL` twice, and
    `tally $P` shows `failures=0`.
    red when: the overlay still names the deleted stage, or `install_stage_checking` is orphaned or still present.
    at base: `BUILD SUCCESSFUL`; `tests=15 failures=0 errors=0 skipped=0`.
